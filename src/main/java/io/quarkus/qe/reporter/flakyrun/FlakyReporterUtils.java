package io.quarkus.qe.reporter.flakyrun;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FlakyReporterUtils {

    private static final String EQUALS = "=";

    private FlakyReporterUtils() {
    }

    public static boolean isArgument(String argumentKey, String argument) {
        return argument.startsWith(argumentKey + EQUALS);
    }

    public static int parseIntArgument(String argumentKey, String argument) {
        return Integer.parseInt(argument.substring((argumentKey + EQUALS).length()));
    }

    public static String parseStringArgument(String argumentKey, String argument) {
        return argument.substring((argumentKey + EQUALS).length());
    }

    public static String getRequiredArgument(String argumentKey, String[] arguments) {
        String argument = null;
        for (String a : arguments) {
            if (a != null && isArgument(argumentKey, a)) {
                argument = a;
            }
        }
        if (argument == null) {
            throw new IllegalArgumentException("Argument '" + argument + "' is missing");
        }
        return parseStringArgument(argumentKey, argument);
    }

    public static String readFile(Path overviewPath) {
        try {
            return Files.readString(overviewPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String[] createCommandArgs(String... args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Args must be even");
        }
        String[] result = new String[args.length / 2];
        for (int i = 0; i < result.length; i++) {
            int j = i * 2;
            result[i] = args[j] + EQUALS + args[j + 1];
        }
        return result;
    }
}
