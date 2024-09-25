package io.quarkus.qe.reporter.flakyrun;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FlakyTest {

    private static final Path ALREADY_FAILED_FILE = Path.of("target").resolve("already-failed-test");

    @Test
    public void testFlaky() throws IOException {
        if (!Files.exists(ALREADY_FAILED_FILE)) {
            touch(ALREADY_FAILED_FILE);
            Assertions.fail("failing to test flakiness reporting");
        }
    }

    private static boolean touch(Path filePath) throws IOException {
        return filePath.toFile().createNewFile();
    }

}
