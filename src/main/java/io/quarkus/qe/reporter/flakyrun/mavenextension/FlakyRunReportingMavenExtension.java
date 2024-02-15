package io.quarkus.qe.reporter.flakyrun.mavenextension;

import io.quarkus.qe.reporter.flakyrun.reporter.FlakyRunReporter;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "quarkus-qe-flaky-run-reporter")
public class FlakyRunReportingMavenExtension extends AbstractMavenLifecycleParticipant {

    @Requirement
    private Logger logger;

    @Override
    public void afterSessionEnd(MavenSession session) {
        new FlakyRunReporter(logger).createFlakyRunReport();
    }
}
