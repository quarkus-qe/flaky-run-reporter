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

## Generate GitHub PR comment content
You may want to transform a Flaky Run report into a GitHub comment.
There is a JBang script that can be used to generate content of the comment:

```bash
jbang trust add https://raw.githubusercontent.com/quarkus-qe/flaky-run-reporter
jbang https://raw.githubusercontent.com/quarkus-qe/flaky-run-reporter/main/jbang-scripts/GitHubPrCommentator.java overview-file=overview-file-name flaky-reports-file-prefix=flaky-run-report
```

Following script arguments are required:

| Argument name             | Argument description                                                                                                                                                                                                                                   |
|---------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| overview-file             | Overview file should contain list of jobs with flakes. Example content: `'PR - Linux - JVM build - Latest Version', 'PR - Linux - Native build - Latest Version', 'PR - Windows - JVM build - Latest Version'`.                                        |
| flaky-reports-file-prefix | This scrip can create one comment from many Flaky Run reports. This argument specifies common prefix of all report files. Last 3 words of the Flaky Run report file are matched with the jobs from overview above. Checkout code and test for details. |

Following environment variables are required:

| Environment variable name | Environment variable description                                                                                                                              |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| GH_REPO                   | GitHub project repository name. Expected format is `organization/project-name`, for example: `quarkus-qe/quarkus-test-framework`                              |
| WORKFLOW_ID               | GitHub action URL has a workflow id in the URL, current format is: `https://github.com/organization-name/project-name/actions/runs/workflow-id?pr=pr-number`. |

Funnily enough, this script expects PR number will be present in the file called `pr-number` placed in a directory where this script is executed.
