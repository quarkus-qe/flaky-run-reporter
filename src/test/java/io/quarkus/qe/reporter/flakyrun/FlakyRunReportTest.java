package io.quarkus.qe.reporter.flakyrun;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.apache.maven.shared.invoker.SystemOutHandler;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.Assertions;
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

import static io.quarkus.qe.reporter.flakyrun.reporter.FlakyRunReporter.FLAKY_RUN_REPORT;
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
        invoker.setMavenHome(getMavenHome());
        try (var request = mvnCleanTestRequest()) {
            invoker.execute(request.delegate);
            var runLogs = request.outputStream.toString();
            assertTrue(runLogs.contains("Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Flakes: 1"));
            assertTrue(runLogs.contains("Run 1: FlakyTest.testFlaky:18 failing to test flakiness reporting"));
            assertTrue(runLogs.contains("Run 2: PASS"));
            System.out.println(runLogs);
        }

        assertGeneratedFlakyRunReport();
    }

    private static void assertGeneratedFlakyRunReport() throws IOException {
        var flakyRunReport = new File(TARGET_FLAKY_TEST_DIR, "target/" + FLAKY_RUN_REPORT);
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

    private static void assertReportContent(String reportContent, String expectedPortion) {
        assertTrue(reportContent.contains(expectedPortion), reportContent);
    }

    private static File getMavenHome() {
        var mvnHome = System.getenv("MAVEN_HOME");
        Objects.requireNonNull(mvnHome, "Environment variable 'MAVEN_HOME' is required");
        return new File(mvnHome);
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
