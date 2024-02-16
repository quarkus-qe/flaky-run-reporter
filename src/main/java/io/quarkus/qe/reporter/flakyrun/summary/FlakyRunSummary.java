package io.quarkus.qe.reporter.flakyrun.summary;

import java.time.ZonedDateTime;
import java.util.List;

public record FlakyRunSummary(List<FlakyRunProjectSummary> flakyProjects) {
    public record FlakyRunProjectSummary(String projectName, String projectBaseDir,
            List<FlakyRunTestSummary> flakeTests) {
    }

    public record FlakyRunTestSummary(String fullTestName, List<FlakyRunFlake> flakes) {
    }

    public record FlakyRunFlake(String failureMessage, String failureType, String failureStackTrace, String dateTime,
            String ciJobName, String ciBuildNumber) implements Comparable<FlakyRunFlake> {
        @Override
        public int compareTo(FlakyRunFlake that) {
            ZonedDateTime thatDateTime = ZonedDateTime.parse(that.dateTime());
            ZonedDateTime thisDateTime = ZonedDateTime.parse(dateTime());
            return thisDateTime.compareTo(thatDateTime);
        }
    }
}
