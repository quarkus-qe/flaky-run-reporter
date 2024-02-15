package io.quarkus.qe.reporter.flakyrun;

import io.quarkus.bot.build.reporting.model.ProjectReport;
import org.apache.maven.plugins.surefire.report.ReportTestCase;

import java.time.ZonedDateTime;
import java.util.stream.Stream;

public record FlakyTest(String projectName, String projectBaseDir, String fullTestName, String failureMessage,
                        String failureType, String failureStackTrace, String dateTime) {

    static Stream<FlakyTest> newInstances(ReportTestCase reportTestCase, ProjectReport projectReport) {
        Stream<FlakyTest> result = Stream.empty();
        ZonedDateTime now = ZonedDateTime.now();
        if (!reportTestCase.getFlakyFailures().isEmpty()) {
            result = reportTestCase.getFlakyFailures()
                    .stream()
                    .map(s -> new FlakyTest(projectReport.getName(), projectReport.getBasedir(),
                            reportTestCase.getFullName(), s.getMessage(), s.getType(), s.getStackTrace(), now.toString()));
        }
        if (!reportTestCase.getFlakyErrors().isEmpty()) {
            result = Stream.concat(result, reportTestCase.getFlakyErrors()
                    .stream()
                    .map(s -> new FlakyTest(projectReport.getName(), projectReport.getBasedir(),
                            reportTestCase.getFullName(), s.getMessage(), s.getType(), s.getStackTrace(), now.toString())));
        }
        return result;
    }

}
