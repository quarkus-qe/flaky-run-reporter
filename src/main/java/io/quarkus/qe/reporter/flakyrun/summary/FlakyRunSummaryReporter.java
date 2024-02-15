package io.quarkus.qe.reporter.flakyrun.summary;

public class FlakyRunSummaryReporter {
    private static final String DAY_RETENTION = "day-retention";
    private static final String MAX_FLAKES_PER_TEST = "max-flakes-per-test";
    private static final String EQUALS = "=";
    private final int dayRetention;
    private final int maxFlakesPerTest;

    public FlakyRunSummaryReporter(String[] args) {
        int dayRetention = 30;
        int maxFlakesPerTest = 50;
        for (String arg : args) {
            if (isArgument(DAY_RETENTION, arg)) {
                dayRetention = parseArgument(DAY_RETENTION, arg);
            }
            if (isArgument(MAX_FLAKES_PER_TEST, arg)) {
                maxFlakesPerTest = parseArgument(MAX_FLAKES_PER_TEST, arg);
            }
        }
        this.dayRetention = dayRetention;
        this.maxFlakesPerTest = maxFlakesPerTest;
    }

    public void createReport() {

    }

    private static boolean isArgument(String argumentKey, String argument) {
        return argument.startsWith(argumentKey + EQUALS);
    }

    private static int parseArgument(String argumentKey, String argument) {
        return Integer.parseInt(argument.substring((argumentKey + EQUALS).length()));
    }
}
