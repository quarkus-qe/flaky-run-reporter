package io.quarkus.qe.reporter.flakyrun.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.qe.reporter.flakyrun.reporter.FlakyRunReporter;
import io.quarkus.qe.reporter.flakyrun.reporter.FlakyTest;
import io.quarkus.qe.reporter.flakyrun.summary.FlakyRunSummary.FlakyRunProjectSummary;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static io.quarkus.qe.reporter.flakyrun.FlakyReporterUtils.isArgument;
import static io.quarkus.qe.reporter.flakyrun.FlakyReporterUtils.parseIntArgument;
import static io.quarkus.qe.reporter.flakyrun.FlakyReporterUtils.parseStringArgument;
import static java.util.stream.Collectors.groupingBy;

public class FlakyRunSummaryReporter {
    public static final String TEST_BASE_DIR = FlakyRunSummaryReporter.class.getSimpleName() + ".test-base-dir";
    public static final String FLAKY_SUMMARY_REPORT = "flaky-summary-report.json";
    public static final String CI_BUILD_NUMBER = "flaky-report-ci-build-number";
    public static final String DAY_RETENTION = "day-retention";
    private static final Path CURRENT_DIR = Path.of(".");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String MAX_FLAKES_PER_TEST = "max-flakes-per-test";
    private static final String PREVIOUS_SUMMARY_REPORT_PATH = "previous-summary-report-path";
    private static final String NEW_SUMMARY_REPORT_PATH = "new-summary-report-path";
    private static final String NEW_FLAKY_REPORT_PATH = "new-flaky-report-path";
    private static final String CI_JOB_NAME = "flaky-report-ci-job-name";
    private final int dayRetention;
    private final int maxFlakesPerTest;
    private final Path newBuildReportPath;
    private final Path previousSummaryReportPath;
    private final String ciJobName;
    private final int ciJobBuildNumber;
    private final Path newSummaryReportPath;

    public FlakyRunSummaryReporter(String[] args) {
        int dayRetention = 30;
        int maxFlakesPerTest = 50;
        final Path baseDir;
        if (System.getProperty(TEST_BASE_DIR) != null) {
            baseDir = Path.of(System.getProperty(TEST_BASE_DIR));
        } else {
            baseDir = CURRENT_DIR;
        }
        Path newBuildReportPath = baseDir.resolve(FlakyRunReporter.FLAKY_RUN_REPORT);
        Path previousSummaryReportPath = baseDir.resolve(FLAKY_SUMMARY_REPORT);
        Path newSummaryReportPath = baseDir.resolve(FLAKY_SUMMARY_REPORT);
        String ciJobName = "";
        int ciJobBuildNumber = -1;
        for (String arg : args) {
            if (isArgument(DAY_RETENTION, arg)) {
                dayRetention = parseIntArgument(DAY_RETENTION, arg);
            }
            if (isArgument(MAX_FLAKES_PER_TEST, arg)) {
                maxFlakesPerTest = parseIntArgument(MAX_FLAKES_PER_TEST, arg);
            }
            if (isArgument(NEW_FLAKY_REPORT_PATH, arg)) {
                newBuildReportPath = Path.of(parseStringArgument(NEW_FLAKY_REPORT_PATH, arg));
            }
            if (isArgument(PREVIOUS_SUMMARY_REPORT_PATH, arg)) {
                previousSummaryReportPath = Path.of(parseStringArgument(PREVIOUS_SUMMARY_REPORT_PATH, arg));
            }
            if (isArgument(CI_BUILD_NUMBER, arg)) {
                ciJobBuildNumber = parseIntArgument(CI_BUILD_NUMBER, arg);
            }
            if (isArgument(CI_JOB_NAME, arg)) {
                ciJobName = parseStringArgument(CI_JOB_NAME, arg);
            }
            if (isArgument(NEW_SUMMARY_REPORT_PATH, arg)) {
                newSummaryReportPath = Path.of(parseStringArgument(NEW_SUMMARY_REPORT_PATH, arg));
            }
        }
        this.dayRetention = dayRetention;
        this.maxFlakesPerTest = maxFlakesPerTest;
        this.newBuildReportPath = newBuildReportPath;
        this.previousSummaryReportPath = previousSummaryReportPath;
        this.ciJobName = ciJobName;
        this.ciJobBuildNumber = ciJobBuildNumber;
        this.newSummaryReportPath = newSummaryReportPath;
    }

    public void createReport() {
        List<FlakyTest> flakyTests = FlakyRunReporter.parseFlakyTestsReport(newBuildReportPath);
        if (!flakyTests.isEmpty()) {
            var previousSummaries = toProjectSummaries(parsePreviousSummary(previousSummaryReportPath));
            var newSummary = createNewSummary(previousSummaries, flakyTests);
            saveSummaryToFileSystem(newSummary);
        }
    }

    private FlakyRunSummary createNewSummary(List<FlakyRunProjectSummary> existingProjects,
            List<FlakyTest> newFlakyTests) {
        List<FlakyRunProjectSummary> projectSummaries = new ArrayList<>(flakyTestsToSummaries(newFlakyTests));
        if (!existingProjects.isEmpty()) {
            projectSummaries.addAll(existingProjects);
        }
        // if there is one project multiple times, merge the project summaries into one
        projectSummaries = mergeProjectSummaries(projectSummaries);
        return new FlakyRunSummary(List.copyOf(projectSummaries));
    }

    private List<FlakyRunProjectSummary> flakyTestsToSummaries(List<FlakyTest> flakyTests) {
        // at this point: one flaky tests == one flaky summary
        return flakyTests.stream().map(this::createProjectSummaryFromFlakyTest).toList();
    }

    private FlakyRunProjectSummary createProjectSummaryFromFlakyTest(FlakyTest flakyTest) {
        return new FlakyRunProjectSummary(flakyTest.projectName(), flakyTest.projectBaseDir(),
                createFlakyTestSummaryFromTest(flakyTest));
    }

    private List<FlakyRunSummary.FlakyRunTestSummary> createFlakyTestSummaryFromTest(FlakyTest flakyTest) {
        return List.of(
                new FlakyRunSummary.FlakyRunTestSummary(flakyTest.fullTestName(), createFlakyRunFromTest(flakyTest)));
    }

    private List<FlakyRunSummary.FlakyRunFlake> createFlakyRunFromTest(FlakyTest flakyTest) {
        return List.of(new FlakyRunSummary.FlakyRunFlake(flakyTest.failureMessage(), flakyTest.failureType(),
                flakyTest.failureStackTrace(), flakyTest.dateTime(), ciJobName, Integer.toString(ciJobBuildNumber)));
    }

    private List<FlakyRunProjectSummary> mergeProjectSummaries(List<FlakyRunProjectSummary> projectSummaries) {
        record FlakyRunProjectInfo(String projectName, String baseDir) {
        }
        return projectSummaries.stream()
                .collect(groupingBy(s -> new FlakyRunProjectInfo(s.projectName(), s.projectBaseDir()),
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new FlakyRunProjectSummary(entry.getKey().projectName(), entry.getKey().baseDir(),
                        mergeTestSummaries(entry.getValue())))
                // at this point: projects are grouped by project name
                .toList();
    }

    private List<FlakyRunSummary.FlakyRunTestSummary> mergeTestSummaries(
            List<FlakyRunProjectSummary> projectSummaries) {
        return projectSummaries.stream().map(FlakyRunProjectSummary::flakeTests).flatMap(Collection::stream)
                .collect(Collectors.groupingBy(FlakyRunSummary.FlakyRunTestSummary::fullTestName, Collectors.toList()))
                .entrySet().stream()
                .map(s -> new FlakyRunSummary.FlakyRunTestSummary(s.getKey(), filterTestFlakes(s.getValue()))).toList();
    }

    private List<FlakyRunSummary.FlakyRunFlake> filterTestFlakes(
            List<FlakyRunSummary.FlakyRunTestSummary> testSummaries) {
        ZonedDateTime dayRetentionDateTime = ZonedDateTime.now().minusDays(this.dayRetention);
        return testSummaries.stream().map(FlakyRunSummary.FlakyRunTestSummary::flakes).flatMap(Collection::stream)
                .map(s -> new FlakyRunSummary.FlakyRunFlake(s.failureMessage(), s.failureType(), s.failureStackTrace(),
                        s.dateTime(), s.ciJobName(), s.ciBuildNumber()))
                // flakes sorted in descending order
                .sorted(new Comparator<FlakyRunSummary.FlakyRunFlake>() {
                    @Override
                    public int compare(FlakyRunSummary.FlakyRunFlake o1, FlakyRunSummary.FlakyRunFlake o2) {
                        return o1.compareTo(o2);
                    }
                }.reversed()).limit(maxFlakesPerTest)
                .filter(flake -> ZonedDateTime.parse(flake.dateTime()).isAfter(dayRetentionDateTime)).toList();
    }

    private void saveSummaryToFileSystem(FlakyRunSummary summary) {
        if (Files.exists(newSummaryReportPath)) {
            try {
                Files.delete(newSummaryReportPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try (FileOutputStream file = new FileOutputStream(newSummaryReportPath.toFile())) {
            OBJECT_MAPPER.writeValue(file, summary);
        } catch (Exception e) {
            System.err.println("Unable to create the %s file: %s" + e);
        }
    }

    private static List<FlakyRunProjectSummary> toProjectSummaries(FlakyRunSummary previousSummary) {
        final List<FlakyRunProjectSummary> flakyProjects;
        if (previousSummary != null && previousSummary.flakyProjects() != null) {
            flakyProjects = List.copyOf(previousSummary.flakyProjects());
        } else {
            flakyProjects = List.of();
        }
        return flakyProjects;
    }

    private static FlakyRunSummary parsePreviousSummary(Path summaryPath) {
        if (Files.exists(summaryPath) && Files.isRegularFile(summaryPath)) {
            try {
                return OBJECT_MAPPER.readValue(summaryPath.toFile(), FlakyRunSummary.class);
            } catch (IOException e) {
                // previous summary path is not required, however should at least inform something went wrong
                System.err.printf("""
                        Detected previous summary report on path '%s',
                        however the file is not deserializable and will be ignored: %s
                        %n""", summaryPath, e.getMessage());
            }
        }
        return null;
    }
}
