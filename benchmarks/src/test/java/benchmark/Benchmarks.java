package benchmark;

import java.util.List;

public final class Benchmarks {
    private Benchmarks() {
    }

    public static void main(String[] args) {
        for (String backend : List.of("gl", "gl-preserve", "vulkan")) {
            runFork(backend);
        }
    }

    private static void runFork(String backend) {
        ProcessBuilder builder = new ProcessBuilder(
                javaBin(),
                "--enable-native-access=ALL-UNNAMED",
                "-cp",
                System.getProperty("java.class.path"),
                SingleBenchmarkMain.class.getName(),
                backend
        );
        builder.inheritIO();

        try {
            int exitCode = builder.start().waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Benchmark failed: " + backend);
            }
        } catch (Exception exception) {
            throw new RuntimeException("Unable to run " + backend + " benchmark", exception);
        }
    }

    private static String javaBin() {
        String separator = System.getProperty("file.separator");
        return System.getProperty("java.home") + separator + "bin" + separator + "java";
    }
}
