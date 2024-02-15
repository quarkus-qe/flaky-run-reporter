package io.quarkus.qe.reporter.flakyrun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.bot.build.reporting.model.BuildReport;
import io.quarkus.bot.build.reporting.model.ProjectReport;
import org.apache.maven.plugin.surefire.log.api.NullConsoleLogger;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.SurefireReportParser;
import org.codehaus.plexus.logging.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class FlakyRunReporter {
    public static final String FLAKY_RUN_REPORT = "flaky-run-report.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String BUILD_REPORT_JSON_FILENAME = "build-report.json";
    private static final String TARGET_DIR = "target";
    private static final Path MAVEN_SUREFIRE_REPORTS_PATH = Path.of(TARGET_DIR, "surefire-reports");
    private static final Path MAVEN_FAILSAFE_REPORTS_PATH = Path.of(TARGET_DIR, "failsafe-reports");
    private final Logger logger;

    FlakyRunReporter(Logger logger) {
        this.logger = logger;
    }

    void createFlakyRunReport() {
        createFlakyRunReport(buildReportToFlakyTests(getBuildReporter()));
    }

    private static List<FlakyTest> buildReportToFlakyTests(BuildReport buildReport) {
        return buildReport
                .getProjectReports()
                .stream()
                .flatMap(projectReport -> testDirsToFlakyTests(toTestDirs(projectReport), projectReport)).toList();
    }

    private void createFlakyRunReport(List<FlakyTest> flakyTests) {
        if (!flakyTests.isEmpty()) {
            try (FileOutputStream file = new FileOutputStream(Path.of(TARGET_DIR).resolve(FLAKY_RUN_REPORT).toFile())) {
                OBJECT_MAPPER.writeValue(file, flakyTests);
            } catch (Exception e) {
                logger.error("Unable to create the " + FLAKY_RUN_REPORT + " file", e);
            }
        }
    }

    private static BuildReport getBuildReporter() {
        var buildReportPath = Path.of(TARGET_DIR).resolve(BUILD_REPORT_JSON_FILENAME);
        if (Files.exists(buildReportPath)) {
            try {
                return OBJECT_MAPPER.readValue(buildReportPath.toFile(), BuildReport.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return new BuildReport();
    }

    private static List<File> toTestDirs(ProjectReport projectReport) {
        return Stream.of(Path.of(normalizeModuleName(projectReport.getBasedir())))
                .flatMap(baseDir -> Stream.of(baseDir.resolve(MAVEN_FAILSAFE_REPORTS_PATH),
                        baseDir.resolve(MAVEN_SUREFIRE_REPORTS_PATH)))
                .filter(Files::exists)
                .map(Path::toFile)
                .toList();
    }

    private static Stream<FlakyTest> testDirsToFlakyTests(List<File> testDirs, ProjectReport projectReport) {
        if (testDirs.isEmpty()) {
            return Stream.empty();
        }
        return new SurefireReportParser(testDirs, new NullConsoleLogger())
                .parseXMLReportFiles()
                .stream()
                .filter(r -> r.getNumberOfFlakes() > 0)
                .map(ReportTestSuite::getTestCases)
                .flatMap(Collection::stream)
                .filter(ReportTestCase::hasFlakes)
                .flatMap(reportTestCase -> FlakyTest.newInstances(reportTestCase, projectReport));
    }

    private static String normalizeModuleName(String moduleName) {
        return moduleName.replace('\\', '/');
    }
}
