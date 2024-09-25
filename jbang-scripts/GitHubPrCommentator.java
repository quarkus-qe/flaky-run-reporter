//usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS io.quarkus.qe:flaky-run-reporter:0.1.2.Beta1

import io.quarkus.qe.reporter.flakyrun.commentator.CreateGhPrComment;

public class GitHubPrCommentator {
    public static void main(String... args) {
        try {
            new CreateGhPrComment(args).printToStdOut();
            System.exit(0);
        } catch (Exception e) {
            System.exit(1);
        }
    }
}
