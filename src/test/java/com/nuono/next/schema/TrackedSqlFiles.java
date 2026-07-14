package com.nuono.next.schema;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

final class TrackedSqlFiles {
    private TrackedSqlFiles() {
    }

    static List<Path> initSqlFiles() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "git",
                "ls-files",
                "--cached",
                "--others",
                "--exclude-standard",
                "--",
                "src/main/resources/db/init"
        )
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        assertTrue(exitCode == 0, "git ls-files failed: " + output);
        return output.lines()
                .filter(path -> path.endsWith(".sql"))
                .map(Path::of)
                .collect(Collectors.toList());
    }
}
