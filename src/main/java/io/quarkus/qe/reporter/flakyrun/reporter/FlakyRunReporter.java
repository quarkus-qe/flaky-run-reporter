package io.quarkus.qe.reporter.flakyrun.reporter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
    private static final String TARGET_DIR = "target";
    private static final Path MAVEN_SUREFIRE_REPORTS_PATH = Path.of(TARGET_DIR, "surefire-reports");
    private static final Path MAVEN_FAILSAFE_REPORTS_PATH = Path.of(TARGET_DIR, "failsafe-reports");
    private final Logger logger;

    public FlakyRunReporter(Logger logger) {
        this.logger = logger;
    }

    public static List<FlakyTest> parseFlakyTestsReport(Path reportPath) {
        if (!Files.exists(reportPath)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(reportPath.toFile(), new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void createReport(List<Project> projects) {
        createFlakyRunReport(projectsToFlakyTests(projects));
    }

    private static List<FlakyTest> projectsToFlakyTests(List<Project> projects) {
        return projects.stream().flatMap(project -> testDirsToFlakyTests(toTestDirs(project), project)).toList();
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

    private static List<File> toTestDirs(Project project) {
        return Stream.of(project.baseDir()).flatMap(baseDir -> Stream.of(baseDir.resolve(MAVEN_FAILSAFE_REPORTS_PATH),
                baseDir.resolve(MAVEN_SUREFIRE_REPORTS_PATH))).filter(Files::exists).map(Path::toFile).toList();
    }

    private static Stream<FlakyTest> testDirsToFlakyTests(List<File> testDirs, Project project) {
        if (testDirs.isEmpty()) {
            return Stream.empty();
        }
        return new SurefireReportParser(testDirs, new NullConsoleLogger()).parseXMLReportFiles().stream()
                .filter(r -> r.getNumberOfFlakes() > 0).map(ReportTestSuite::getTestCases).flatMap(Collection::stream)
                .filter(ReportTestCase::hasFlakes)
                .flatMap(reportTestCase -> FlakyTest.newInstances(reportTestCase, project));
    }
}
