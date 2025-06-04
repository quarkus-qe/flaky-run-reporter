package io.quarkus.qe.reporter.flakyrun.commentator;

import io.quarkus.qe.reporter.flakyrun.FlakyReporterUtils;
import io.quarkus.qe.reporter.flakyrun.reporter.FlakyTest;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.quarkus.qe.reporter.flakyrun.FlakyReporterUtils.getRequiredArgument;
import static io.quarkus.qe.reporter.flakyrun.FlakyReporterUtils.readFile;
import static io.quarkus.qe.reporter.flakyrun.reporter.FlakyRunReporter.parseFlakyTestsReport;

/**
 * This class is used by a Jbang script to simplify commenting in GitHub PRs when a flake were detected.
 */
public final class CreateGhPrComment {

    public static final String TEST_BASE_DIR = CreateGhPrComment.class.getSimpleName() + ".test-base-dir";
    public static final String OVERVIEW_FILE_KEY = "overview-file";
    public static final String FLAKY_REPORTS_FILE_PREFIX_KEY = "flaky-reports-file-prefix";
    public static final String GH_REPO_ENV_VAR_NAME = "GH_REPO";
    public static final String WORKFLOW_ID_ENV_VAR_NAME = "WORKFLOW_ID";
    private static final Path CURRENT_DIR = Path.of(".");
    private final String comment;
    private final Path baseDir;

    public CreateGhPrComment(String[] args) {
        this(args, getRequiredEnv(GH_REPO_ENV_VAR_NAME), getRequiredEnv(WORKFLOW_ID_ENV_VAR_NAME));
    }

    public CreateGhPrComment(String[] args, String ghRepo, String workflowId) {
        if (System.getProperty(TEST_BASE_DIR) != null) {
            baseDir = Path.of(System.getProperty(TEST_BASE_DIR));
        } else {
            baseDir = CURRENT_DIR;
        }
        var jobs = getJobs(args);
        var failureOverview = getFailureOverview(jobs);
        var flakyTestsReports = getFlakyTestReports(args, jobs);
        var prNumber = getPrNumber();

        this.comment = """
                Following jobs contain at least one flaky test:
                %s

                Run summary: https://github.com/%s/actions/runs/%s?pr=%s

                **Flaky tests:**

                ---
                %s
                """.formatted(failureOverview, ghRepo, workflowId, prNumber, flakyTestsReports);
    }

    private Set<String> getJobs(String[] args) {
        // expected format:
        // 'PR - Linux - JVM build - Latest Version', 'PR - Linux - Native build - Latest Version',
        // 'PR - Windows - JVM build - Latest Version'
        var overviewPath = baseDir.resolve(getRequiredArgument(OVERVIEW_FILE_KEY, args));
        if (Files.exists(overviewPath)) {
            var overview = readFile(overviewPath);
            return Arrays.stream(overview.split(",")).map(String::trim).map(job -> {
                if (job.startsWith("'")) {
                    return job.substring(1);
                }
                return job;
            }).map(job -> {
                if (job.endsWith("'")) {
                    return job.substring(0, job.length() - 1);
                }
                return job;
            }).collect(Collectors.toSet());
        }
        throw new IllegalStateException("File '" + overviewPath + "' not found");
    }

    private String getPrNumber() {
        var prNumber = FlakyReporterUtils.readFile(baseDir.resolve("pr-number"));
        if (prNumber == null || prNumber.isBlank()) {
            throw new IllegalStateException("File 'pr-number' not found, cannot proceed without the PR number");
        }
        return prNumber.trim();
    }

    public String getComment() {
        return comment;
    }

    public void printToStdOut() {
        System.out.println(comment);
    }

    private String getFlakyTestReports(String[] args, Set<String> jobs) {
        var reportFilePrefix = getRequiredArgument(FLAKY_REPORTS_FILE_PREFIX_KEY, args);
        var listOfDirFiles = baseDir.toFile().listFiles();
        if (listOfDirFiles == null || listOfDirFiles.length == 0) {
            return "No flaky test reports found";
        }
        Map<String, FlakyTestWithFiles> testNameToDetail = new HashMap<>();
        var result = new StringBuilder();
        for (File file : listOfDirFiles) {
            if (file.getName().startsWith(reportFilePrefix)) {
                // well, this is obviously imprecise in the sense that we expect stacktrace and failure message
                // to be always same in all the runs, but here:
                // https://github.com/quarkus-qe/quarkus-test-suite/pull/2050#issuecomment-2376769937
                // it was requested that we list tests with list of jobs where they failed,
                // and we cannot have both (stacktrace per each job and one stacktrace)
                parseFlakyTestsReport(file.toPath()).forEach(flakyTest -> testNameToDetail
                        .computeIfAbsent(flakyTest.fullTestName(),
                                tn -> new FlakyTestWithFiles(new HashSet<>(), flakyTest))
                        .fileNames().add(file.getName()));
            }
        }

        testNameToDetail.values().forEach(flakyTest -> result.append("""
                **`%s`**
                 - Failure message: `%s`
                 - Failed in jobs:
                %s
                <details>
                <summary>Failure stacktrace</summary>

                ```
                %s
                ```

                </details>

                ---
                """.formatted(flakyTest.detail.fullTestName(), flakyTest.detail.failureMessage(),
                toFailedInJobs(flakyTest.fileNames, jobs), flakyTest.detail.failureStackTrace())));

        return result.toString();
    }

    private static String toFailedInJobs(Set<String> fileNames, Set<String> jobs) {
        // produce:
        // - ABC
        // - EFG
        return fileNames.stream().map(fileName -> toJobName(fileName, jobs)).map(jobName -> "   - " + jobName)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String toJobName(String fileName, Set<String> jobs) {
        // this can be heavily simplified and made more precise
        // by changing format we receive
        // so the obvious intersection were the last 3 words
        // now we receive something like: Linux JVM and the filename is 'linux-build-jvm-latest'
        // so we could just match 'Linux' and 'JVM' parts, but it's more reliable to have job name to filename list

        var jobFileToJobName = getJobFileToJobName(jobs);
        var jobName = jobFileToJobName.entrySet().stream().filter(e -> fileName.endsWith(e.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        if (jobName != null) {
            return jobName;
        }

        // fallback, probably not used anymore, but keeping it to make this job name resolution bit more resilient
        // previously we received something like:
        // PR - Linux - JVM build - Latest Version
        // flaky-run-report-linux-jvm-latest.json
        var words = fileName.transform(fn -> {
            // drop .json
            if (fn.endsWith(".json")) {
                return fn.substring(0, fn.length() - 5);
            }
            return fn;
        }).split("-");
        if (words.length > 3) {
            var last3Words = Arrays.stream(words).skip(words.length - 3).map(String::toLowerCase)
                    .collect(Collectors.toSet());
            jobName = jobs.stream().filter(job -> last3Words.stream().allMatch(job.toLowerCase()::contains)).findFirst()
                    .orElse(null);

            if (jobName != null) {
                return jobName.trim();
            }
        }

        // fallback to the filename
        System.out.println("Unknown format for flaky report filename: " + fileName);
        return fileName;
    }

    private static Map<String, String> getJobFileToJobName(Set<String> jobs) {
        return jobs.stream().map(jobName -> {
            if ("Linux JVM".equalsIgnoreCase(jobName)) {
                return Map.entry("linux-build-jvm-latest.json", "Linux JVM");
            }
            if ("Windows JVM".equalsIgnoreCase(jobName)) {
                return Map.entry("windows-build-jvm-latest.json", "Windows JVM");
            }
            if ("Linux Native".equalsIgnoreCase(jobName)) {
                return Map.entry("linux-build-native-latest.json", "Linux Native");
            }
            return Map.entry(jobName.toLowerCase(), jobName);
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String getFailureOverview(Set<String> jobs) {
        // produce:
        // * PR - Linux - JVM build - Latest Version
        // * PR - Linux - Native build - Latest Version
        // * PR - Windows - JVM build - Latest Version
        return jobs.stream().map(job -> " * " + job).collect(Collectors.joining(System.lineSeparator()));
    }

    private static String getRequiredEnv(String environmentVariableName) {
        var envVar = System.getenv(environmentVariableName);
        if (envVar == null) {
            throw new IllegalArgumentException("Missing environment variable: " + environmentVariableName);
        }
        return envVar;
    }

    private record FlakyTestWithFiles(Set<String> fileNames, FlakyTest detail) {

    }
}
