# Quarkus QE Flaky Run Reporter
This is Maven extensions that accepts SureFire / FailSafe reports and creates flaky run reports.

## Generate flaky run report
Add Maven extension to your POM file:

```xml
<build>
    <extensions>
        <extension>
            <groupId>io.quarkus.qe</groupId>
            <artifactId>flaky-run-reporter</artifactId>
            <version>${flaky-run-reporter.version}</version>
        </extension>
    </extensions>
</build>
```

## Generate summary of multiple flaky run reports
You may want to summarize past flaky run reports into one report:

```bash
jbang trust add https://raw.githubusercontent.com/quarkus-qe/flaky-run-reporter
jbang https://raw.githubusercontent.com/quarkus-qe/flaky-run-reporter/main/jbang-scripts/FlakyTestRunSummarizer.java day-retention=30 new-flaky-report-path=target/flaky-run-report.json
```
Following script arguments are supported:

| Argument name                | Argument description                               | Default value               |
|------------------------------|----------------------------------------------------|-----------------------------|
| day-retention                | Max days flaky results are kept                    | 30                          |
| max-flakes-per-test          | Max flaky results per one flaky test               | 50                          |
| previous-summary-report-path | Path to a previous summary report                  | ./flaky-summary-report.json |
| new-flaky-report-path        | Path to the latest flaky report added to a summary | ./flaky-run-report.json     |
| flaky-report-ci-job-name     | Jenkins job name or GitHub action name             | \<\<empty>>                 |
| flaky-report-ci-build-number | Jenkins job or GitHub action build number          | \<\<empty>>                 |
| new-summary-report-path      | Jenkins job or GitHub action build number          | ./flaky-summary-report.json |

Please note that all script arguments are optional.
The JBang script requires new flaky report to exist, as the whole point of the script is to add new report to a summary.