package io.quarkus.qe.reporter.flakyrun.commentator;

import io.quarkus.qe.reporter.flakyrun.reporter.FlakyRunReporter;
import io.quarkus.qe.reporter.flakyrun.reporter.FlakyTest;
import io.quarkus.qe.reporter.flakyrun.summary.FlakyRunSummaryReporter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.quarkus.qe.reporter.flakyrun.FlakyReporterUtils.getRequiredArgument;
import static io.quarkus.qe.reporter.flakyrun.FlakyReporterUtils.readFile;

/**
 * This class is used by a Jbang script to simplify commenting in GitHub PRs when a flake were detected.
 */
public final class CreateGhPrComment {

    public static final String TEST_BASE_DIR = CreateGhPrComment.class.getSimpleName() + ".test-base-dir";
    public static final String OVERVIEW_FILE_KEY = "overview-file";
    public static final String FLAKY_REPORTS_FILE_PREFIX_KEY = "flaky-reports-file-prefix";
    private static final Path CURRENT_DIR = Path.of(".");
    private final String comment;
    private final Path baseDir;

    public CreateGhPrComment(String[] args) {
        if (System.getProperty(TEST_BASE_DIR) != null) {
            baseDir = Path.of(System.getProperty(TEST_BASE_DIR));
        } else {
            baseDir = CURRENT_DIR;
        }
        var failureOverview = getFailureOverview(args);
        var flakyTestsReports = getFlakyTestReports(args);
        this.comment = """
                Following jobs contain at least one flaky test: %s

                %s
                """.formatted(failureOverview, flakyTestsReports);
    }

    public String getComment() {
        return comment;
    }

    public void printToStdOut() {
        System.out.println(comment);
    }

    private String getFlakyTestReports(String[] args) {
        var reportFilePrefix = getRequiredArgument(FLAKY_REPORTS_FILE_PREFIX_KEY, args);
        var listOfDirFiles = baseDir.toFile().listFiles();
        if (listOfDirFiles == null || listOfDirFiles.length == 0) {
            return "No flaky test reports found";
        }
        var result = new StringBuilder();
        for (File file : listOfDirFiles) {
            if (file.getName().startsWith(reportFilePrefix)) {
                var flakyTests = FlakyRunReporter.parseFlakyTestsReport(file.toPath());
                if (!flakyTests.isEmpty()) {
                    result.append("**Artifact `%s` contains following failures:**".formatted(file.getName()));
                    for (FlakyTest flakyTest : flakyTests) {
                        result.append("""

                                - Test name: `%s`
                                  Date and time: %s
                                  Failure message: `%s`
                                  Failure stacktrace:
                                ```
                                %s
                                ```
                                """.formatted(flakyTest.fullTestName(), flakyTest.dateTime(),
                                flakyTest.failureMessage(), flakyTest.failureStackTrace()));
                    }
                    result.append(System.lineSeparator());
                }
            }
        }
        return result.toString();
    }

    private String getFailureOverview(String[] args) {
        var overviewPath = baseDir.resolve(getRequiredArgument(OVERVIEW_FILE_KEY, args));
        if (Files.exists(overviewPath)) {
            return readFile(overviewPath);
        }
        throw new IllegalStateException("File '" + overviewPath + "' not found");
    }
}
