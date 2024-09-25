package io.quarkus.qe.reporter.flakyrun.mavenextension;

import io.quarkus.qe.reporter.flakyrun.reporter.FlakyRunReporter;
import io.quarkus.qe.reporter.flakyrun.reporter.Project;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import java.nio.file.Path;
import java.util.List;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "quarkus-qe-flaky-run-reporter")
public class FlakyRunReportingMavenExtension extends AbstractMavenLifecycleParticipant {

    @Requirement
    private Logger logger;

    @Override
    public void afterSessionEnd(MavenSession session) {
        logger.debug("Flaky run reporter started");

        var projects = getProjectsFromMvnSession(session);
        if (!projects.isEmpty()) {
            new FlakyRunReporter(logger).createReport(projects);
        } else {
            logger.info("No projects found in this Maven session, won't generate Flaky Run report");
        }
    }

    private static List<Project> getProjectsFromMvnSession(MavenSession session) {
        final Path rootPath = Path.of("").toAbsolutePath();

        // for multi-module projects, we don't inspect project root, because there are no tests
        // but for single-module project we should just check 'target' of that project
        boolean isNotMultiModuleProject = session.getResult().getTopologicallySortedProjects().size() == 1;

        return session.getResult().getTopologicallySortedProjects().stream()
                .map(p -> new Project(p.getName(), rootPath.relativize(p.getBasedir().toPath()))).filter(p -> {
                    var rootProject = !p.baseDir().toString().isEmpty();
                    return isNotMultiModuleProject || rootProject;
                }).toList();
    }
}
