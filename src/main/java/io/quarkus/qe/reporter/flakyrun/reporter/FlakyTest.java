package io.quarkus.qe.reporter.flakyrun.reporter;

import org.apache.maven.plugins.surefire.report.ReportTestCase;

import java.time.ZonedDateTime;
import java.util.stream.Stream;

public record FlakyTest(String projectName, String projectBaseDir, String fullTestName, String failureMessage,
        String failureType, String failureStackTrace, String dateTime) {

    static Stream<FlakyTest> newInstances(ReportTestCase reportTestCase, Project project) {
        final String now = ZonedDateTime.now().toString();
        Stream<FlakyTest> result = Stream.empty();
        if (!reportTestCase.getFlakyFailures().isEmpty()) {
            result = reportTestCase.getFlakyFailures().stream()
                    .map(s -> new FlakyTest(project.name(), project.baseDir().toString(), reportTestCase.getFullName(),
                            s.getMessage(), s.getType(), s.getStackTrace(), now));
        }
        if (!reportTestCase.getFlakyErrors().isEmpty()) {
            result = Stream.concat(result,
                    reportTestCase.getFlakyErrors().stream()
                            .map(s -> new FlakyTest(project.name(), project.baseDir().toString(),
                                    reportTestCase.getFullName(), s.getMessage(), s.getType(), s.getStackTrace(),
                                    now)));
        }
        return result;
    }

}
