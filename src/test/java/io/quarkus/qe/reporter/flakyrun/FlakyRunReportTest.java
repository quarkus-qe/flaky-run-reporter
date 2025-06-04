package io.quarkus.qe.reporter.flakyrun;

import io.quarkus.qe.reporter.flakyrun.commentator.CreateGhPrComment;
import io.quarkus.qe.reporter.flakyrun.summary.FlakyRunSummaryReporter;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.apache.maven.shared.invoker.SystemOutHandler;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import static io.quarkus.qe.reporter.flakyrun.FlakyReporterUtils.createCommandArgs;
import static io.quarkus.qe.reporter.flakyrun.FlakyReporterUtils.readFile;
import static io.quarkus.qe.reporter.flakyrun.commentator.CreateGhPrComment.FLAKY_REPORTS_FILE_PREFIX_KEY;
import static io.quarkus.qe.reporter.flakyrun.commentator.CreateGhPrComment.OVERVIEW_FILE_KEY;
import static io.quarkus.qe.reporter.flakyrun.reporter.FlakyRunReporter.FLAKY_RUN_REPORT;
import static io.quarkus.qe.reporter.flakyrun.summary.FlakyRunSummaryReporter.CI_BUILD_NUMBER;
import static io.quarkus.qe.reporter.flakyrun.summary.FlakyRunSummaryReporter.DAY_RETENTION;
import static io.quarkus.qe.reporter.flakyrun.summary.FlakyRunSummaryReporter.FLAKY_SUMMARY_REPORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FlakyRunReportTest {

    private static final File TEMPLATE_FLAKY_TEST_DIR = new File("src/test/resources-flaky-test");
    private static final File TARGET_FLAKY_TEST_DIR = new File("target/flaky-test");

    // HINT: always build this project first and then run tests
    // otherwise in my experience, you are testing previous version
    // maybe because result of the build is only stored to the local .m2 after all tests are run as well
    @Test
    public void test() throws IOException, MavenInvocationException {
        var invoker = new DefaultInvoker();
        FileUtils.copyDirectoryStructure(TEMPLATE_FLAKY_TEST_DIR, TARGET_FLAKY_TEST_DIR);
        invoker.setWorkingDirectory(TARGET_FLAKY_TEST_DIR);
        invoker.setMavenExecutable(getMavenWrapper());
        try (var request = mvnCleanTestRequest()) {
            invoker.execute(request.delegate);
            var runLogs = request.outputStream.toString();
            assertTrue(runLogs.contains("Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Flakes: 1"));
            assertTrue(runLogs.contains("Run 1: FlakyTest.testFlaky:18 failing to test flakiness reporting"));
            assertTrue(runLogs.contains("Run 2: PASS"));
            System.out.println(runLogs);
        }

        assertGeneratedFlakyRunReport();
        assertFlakyRunSummary();
        assertGitHubPrCommentator();
    }

    private static void assertGitHubPrCommentator() throws IOException {
        var testTarget = getFlakyRunReportFile().toPath().getParent();
        System.setProperty(CreateGhPrComment.TEST_BASE_DIR, testTarget.toString());

        // prepare PR Number
        var prNumberFile = new File("src/test/resources/pr-number");
        var prNumberFileInTestTarget = new File(TARGET_FLAKY_TEST_DIR, "target/" + prNumberFile.getName());
        FileUtils.copyFile(prNumberFile, prNumberFileInTestTarget);
        assertEquals("8888", readFile(prNumberFileInTestTarget.toPath()));

        // prepare overview
        var overview = new File("src/test/resources/overview_file.txt");
        var overviewInTestTarget = new File(TARGET_FLAKY_TEST_DIR, "target/" + overview.getName());
        FileUtils.copyFile(overview, overviewInTestTarget);
        assertFalse(readFile(overviewInTestTarget.toPath()).isBlank());

        // prepare flaky run reports
        var expectedReportPrefix = "flaky-run-report-";
        var newFlakyRunReportFile1 = testTarget.resolve(expectedReportPrefix + "linux-jvm-latest.json").toFile();
        var newFlakyRunReportFile2 = testTarget.resolve(expectedReportPrefix + "linux-native-latest.json").toFile();
        var newFlakyRunReportFile3 = testTarget.resolve(expectedReportPrefix + "windows-jvm-latest.json").toFile();
        var newFlakyRunReportFile4 = testTarget.resolve(expectedReportPrefix + "linux-build-jvm-latest.json").toFile();
        var newFlakyRunReportFile5 = testTarget.resolve(expectedReportPrefix + "windows-build-jvm-latest.json")
                .toFile();
        var newFlakyRunReportFile6 = testTarget.resolve(expectedReportPrefix + "linux-build-native-latest.json")
                .toFile();
        FileUtils.copyFile(getFlakyRunReportFile(), newFlakyRunReportFile1);
        FileUtils.copyFile(getFlakyRunReportFile(), newFlakyRunReportFile2);
        FileUtils.copyFile(getFlakyRunReportFile(), newFlakyRunReportFile3);
        FileUtils.copyFile(getFlakyRunReportFile(), newFlakyRunReportFile4);
        FileUtils.copyFile(getFlakyRunReportFile(), newFlakyRunReportFile5);
        FileUtils.copyFile(getFlakyRunReportFile(), newFlakyRunReportFile6);

        // prepare comment
        var args = createCommandArgs(OVERVIEW_FILE_KEY, overview.getName(), FLAKY_REPORTS_FILE_PREFIX_KEY,
                expectedReportPrefix);
        var commentator = new CreateGhPrComment(args, "quarkus-qe/quarkus-test-suite", "1234567890");
        var comment = commentator.getComment();

        // assert comment
        assertTrue(comment.contains("Following jobs contain at least one flaky test"), comment);
        assertTrue(comment.contains(" * PR - Windows - JVM build - Latest Version"), comment);
        assertTrue(comment.contains(" * PR - Linux - Native build - Latest Version"), comment);
        assertTrue(comment.contains(" * PR - Linux - JVM build - Latest Version"), comment);
        assertTrue(comment.contains(" * Linux JVM"), comment);
        assertTrue(comment.contains(" * Windows JVM"), comment);
        assertTrue(comment.contains(" * Linux Native"), comment);
        assertTrue(comment.contains(
                "Run summary: https://github.com/quarkus-qe/quarkus-test-suite/actions/runs/1234567890?pr=8888"),
                comment);
        assertTrue(comment.contains("**`io.quarkus.qe.reporter.flakyrun.FlakyTest.testFlaky`**"), comment);
        assertTrue(comment.contains(" - Failure message: `failing to test flakiness reporting`"), comment);
        assertTrue(comment.contains("   - PR - Windows - JVM build - Latest Version"), comment);
        assertTrue(comment.contains("   - PR - Linux - JVM build - Latest Version"), comment);
        assertTrue(comment.contains("   - PR - Linux - Native build - Latest Version"), comment);
        assertTrue(comment.contains("   - Linux JVM"), comment);
        assertTrue(comment.contains("   - Windows JVM"), comment);
        assertTrue(comment.contains("   - Linux Native"), comment);
        assertTrue(comment.contains("org.opentest4j.AssertionFailedError: failing to test flakiness reporting"),
                comment);
    }

    private static void assertFlakyRunSummary() throws IOException {
        // use old summary I downloaded from Jenkins, if format changes, it needs to change as well
        var oldSummary = new File("src/test/resources/flaky-summary-report.json");
        var summaryTarget = new File(TARGET_FLAKY_TEST_DIR, "target/" + FLAKY_SUMMARY_REPORT);
        FileUtils.copyFile(oldSummary, summaryTarget);
        var previousValue = Files.readString(summaryTarget.toPath());
        assertTrue(previousValue.contains("PicocliDevIT.verifyGreetingCommandOutputsExpectedMessage"), previousValue);
        assertFalse(previousValue.contains("FlakyTest.testFlaky"), previousValue);

        System.setProperty(FlakyRunSummaryReporter.TEST_BASE_DIR, getFlakyRunReportFile().getParent());
        // making it maximal day retention because the old message needs to be valid for this test to pass
        var expectedBuildNumber = "987654321";
        new FlakyRunSummaryReporter(
                createCommandArgs(CI_BUILD_NUMBER, expectedBuildNumber, DAY_RETENTION, Integer.MAX_VALUE + ""))
                        .createReport();

        // now assert the old summary and new flaky run report were merged
        var newValue = Files.readString(summaryTarget.toPath());
        assertTrue(newValue.contains("PicocliDevIT.verifyGreetingCommandOutputsExpectedMessage"), newValue);
        assertTrue(newValue.contains("FlakyTest.testFlaky"), newValue);
        assertTrue(newValue.contains(expectedBuildNumber), newValue);
    }

    private static void assertGeneratedFlakyRunReport() throws IOException {
        var flakyRunReport = getFlakyRunReportFile();
        assertTrue(flakyRunReport.exists(),
                "Flaky Run report wasn't generated, '%s' does not exist".formatted(flakyRunReport));
        var reportContent = Files.readString(flakyRunReport.toPath());
        assertReportContent(reportContent, "\"projectName\" : \"Flaky Run Reporter - Failing Test\"");
        assertReportContent(reportContent,
                "\"fullTestName\" : \"io.quarkus.qe.reporter.flakyrun.FlakyTest.testFlaky\"");
        assertReportContent(reportContent, "\"failureType\" : \"org.opentest4j.AssertionFailedError\"");
        assertReportContent(reportContent, "failureStackTrace");
        assertReportContent(reportContent, "FlakyTest.java:18"); // part of the stacktrace
        assertReportContent(reportContent, "dateTime");
    }

    private static File getFlakyRunReportFile() {
        return new File(TARGET_FLAKY_TEST_DIR, "target/" + FLAKY_RUN_REPORT);
    }

    private static void assertReportContent(String reportContent, String expectedPortion) {
        assertTrue(reportContent.contains(expectedPortion), reportContent);
    }

    private static File getMavenWrapper() {
        var mvnHome = System.getProperty("basedir");
        Objects.requireNonNull(mvnHome, "System property 'basedir' is required");
        if (!mvnHome.endsWith(File.separator)) {
            mvnHome += File.separator;
        }
        return new File(mvnHome + "mvnw");
    }

    private static CloseableRequest mvnCleanTestRequest() {
        DefaultInvocationRequest request = new DefaultInvocationRequest();
        request.setGoals(List.of("clean", "verify"));
        request.setShowErrors(true);
        request.setDebug(false);
        request.setBaseDirectory(TARGET_FLAKY_TEST_DIR);
        request.setPomFile(new File(TARGET_FLAKY_TEST_DIR, "pom.xml"));
        request.setShellEnvironmentInherited(true);
        request.setProperties(getProperties());
        var outputStream = new ByteArrayOutputStream();
        var inputStream = new PrintStream(outputStream);
        request.setOutputHandler(new PrintStreamHandler(inputStream, true));
        request.setErrorHandler(new SystemOutHandler());
        return new CloseableRequest(request, outputStream, inputStream);
    }

    private static Properties getProperties() {
        var properties = new Properties();
        properties.setProperty("junit.jupiter.version", System.getProperty("junit.jupiter.version"));
        properties.setProperty("maven.surefire.version", System.getProperty("maven.surefire.version"));
        properties.setProperty("flaky-run-reporter.version", System.getProperty("flaky-run-reporter.version"));
        return properties;
    }

    private record CloseableRequest(InvocationRequest delegate, ByteArrayOutputStream outputStream,
            PrintStream printStream) implements Closeable {

        @Override
        public void close() throws IOException {
            printStream.close();
            outputStream.close();
        }
    }
}
