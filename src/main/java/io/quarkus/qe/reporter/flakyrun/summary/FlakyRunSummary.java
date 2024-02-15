package io.quarkus.qe.reporter.flakyrun.summary;

import java.util.List;

public record FlakyRunSummary(String projectName, String projectBaseDir, String testFullName,
        List<FlakyRunSummaryItem> flakes) {

    public record FlakyRunSummaryItem(String failureMessage, String failureType, String failureStackTrace,
            String dateTime) {
    }
}
