package io.quarkus.qe.reporter.flakyrun.reporter;

import java.nio.file.Path;

public record Project(String name, Path baseDir) {
}
