package io.quarkus.qe.reporter.flakyrun.summary;

import io.quarkus.qe.reporter.flakyrun.reporter.FlakyRunReporter;

public class FlakyRunSummaryReporter {
    public static final String FLAKY_SUMMARY_REPORT = "flaky-summary-report.json";
    private static final String DAY_RETENTION = "day-retention";
    private static final String MAX_FLAKES_PER_TEST = "max-flakes-per-test";
    private static final String SUMMARY_REPORT_PATH = "summary-report-path";
    private static final String NEW_BUILD_REPORT_PATH = "new-build-report-path";
    private static final String EQUALS = "=";
    private final int dayRetention;
    private final int maxFlakesPerTest;
    private final String newBuildReportPath;
    private final String summaryReportPath;

    public FlakyRunSummaryReporter(String[] args) {
        int dayRetention = 30;
        int maxFlakesPerTest = 50;
        String newBuildReportPath = FlakyRunReporter.FLAKY_RUN_REPORT;
        String summaryReportPath = FLAKY_SUMMARY_REPORT;
        for (String arg : args) {
            if (isArgument(DAY_RETENTION, arg)) {
                dayRetention = parseIntArgument(DAY_RETENTION, arg);
            }
            if (isArgument(MAX_FLAKES_PER_TEST, arg)) {
                maxFlakesPerTest = parseIntArgument(MAX_FLAKES_PER_TEST, arg);
            }
            if (isArgument(NEW_BUILD_REPORT_PATH, arg)) {
                newBuildReportPath = parseStringArgument(NEW_BUILD_REPORT_PATH, arg);
            }
            if (isArgument(SUMMARY_REPORT_PATH, arg)) {
                summaryReportPath = parseStringArgument(SUMMARY_REPORT_PATH, arg);
            }

        }
        this.dayRetention = dayRetention;
        this.maxFlakesPerTest = maxFlakesPerTest;
        this.newBuildReportPath = newBuildReportPath;
        this.summaryReportPath = summaryReportPath;
    }

    public void createReport() {

    }

    private static boolean isArgument(String argumentKey, String argument) {
        return argument.startsWith(argumentKey + EQUALS);
    }

    private static int parseIntArgument(String argumentKey, String argument) {
        return Integer.parseInt(argument.substring((argumentKey + EQUALS).length()));
    }

    private static String parseStringArgument(String argumentKey, String argument) {
        return argument.substring((argumentKey + EQUALS).length());
    }
}
