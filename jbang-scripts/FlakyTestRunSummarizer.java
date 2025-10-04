//usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS io.quarkus.qe:flaky-run-reporter:0.1.7

import io.quarkus.qe.reporter.flakyrun.summary.FlakyRunSummaryReporter;

public class FlakyTestRunSummarizer {
    public static void main(String... args) {
        try {
            new FlakyRunSummaryReporter(args).createReport();
            System.exit(0);
        } catch (Exception e) {
            System.exit(1);
        }
    }
}
