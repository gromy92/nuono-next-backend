package com.nuono.next.datapull.cutover;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Candidate-Jar one-shot: one read-only DB snapshot, no Spring/Web/scheduler lifecycle. */
public final class DataPullRuntimeCutoverManifestCommand {

    public static final String COMMAND = "dp-runtime-cutover-manifest";
    private static final Set<String> REQUIRED = Set.of(
            "--env-file", "--candidate-jar", "--manifest-commit",
            "--expected-jar-sha256", "--evidence-file"
    );
    private static final String BASELINE = "--baseline-manifest";

    private DataPullRuntimeCutoverManifestCommand() {
    }

    public static boolean handles(String[] args) {
        return args != null && args.length > 0 && COMMAND.equals(args[0]);
    }

    public static int run(String[] args) {
        Path output = null;
        boolean created = false;
        try {
            Map<String, String> options = parse(args);
            String commit = requireHex(
                    options.get("--manifest-commit"), 40, "DP_CUTOVER_COMMIT_INVALID"
            );
            String jarSha = requireHex(
                    options.get("--expected-jar-sha256"), 64, "DP_CUTOVER_JAR_SHA_INVALID"
            );
            Path jar = path(options, "--candidate-jar");
            if (!jarSha.equals(sha256(jar))) {
                throw new IllegalStateException("DP_CUTOVER_JAR_SHA_MISMATCH");
            }
            Path envPath = path(options, "--env-file");
            output = path(options, "--evidence-file");
            Path baseline = options.containsKey(BASELINE) ? path(options, BASELINE) : null;
            if (Files.exists(output) || !Files.isDirectory(output.getParent())) {
                throw new IllegalArgumentException("DP_CUTOVER_EVIDENCE_TARGET_INVALID");
            }
            DataPullRuntimeCutoverManifestEnvironment environment =
                    DataPullRuntimeCutoverManifestEnvironment.load(envPath);
            String cutoverKey = "dp-runtime-" + commit;
            DataPullRuntimeCutoverSourceCohort cohort =
                    new DataPullRuntimeCutoverManifestDatabase().read(
                            environment, cutoverKey
                    );
            ObjectNode evidence = new DataPullRuntimeCutoverManifest().build(
                    commit, jarSha, cohort, baseline
            );
            Files.createFile(output);
            created = true;
            setOwnerOnly(output);
            byte[] bytes = new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(evidence);
            Files.write(
                    output, bytes, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            System.out.println("DP_RUNTIME_CUTOVER_MANIFEST=PASS");
            return 0;
        } catch (Exception failure) {
            if (created && output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (Exception ignored) {
                    // The command remains failed; the release verifier rejects any partial file.
                }
            }
            System.err.println("DP_RUNTIME_CUTOVER_MANIFEST=FAIL");
            return 23;
        }
    }

    static Map<String, String> parse(String[] args) {
        if (!handles(args) || args.length < 11 || args.length > 13 || args.length % 2 == 0) {
            throw new IllegalArgumentException("DP_CUTOVER_ARGUMENTS_INVALID");
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        for (int index = 1; index < args.length; index += 2) {
            String option = args[index];
            String value = args[index + 1];
            if ((!REQUIRED.contains(option) && !BASELINE.equals(option))
                    || parsed.containsKey(option) || value == null || value.isBlank()
                    || value.startsWith("--")) {
                throw new IllegalArgumentException("DP_CUTOVER_ARGUMENTS_INVALID");
            }
            parsed.put(option, value);
        }
        if (!parsed.keySet().containsAll(REQUIRED)
                || parsed.size() != REQUIRED.size() + (parsed.containsKey(BASELINE) ? 1 : 0)) {
            throw new IllegalArgumentException("DP_CUTOVER_ARGUMENTS_INVALID");
        }
        return Map.copyOf(parsed);
    }

    private static Path path(Map<String, String> values, String option) {
        return Path.of(values.get(option)).toAbsolutePath().normalize();
    }

    private static String requireHex(String value, int length, String code) {
        if (value == null || !value.matches("[0-9a-f]{" + length + "}")) {
            throw new IllegalArgumentException(code);
        }
        return value;
    }

    private static String sha256(Path path) throws Exception {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("DP_CUTOVER_JAR_INVALID");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private static void setOwnerOnly(Path path) throws Exception {
        Files.setPosixFilePermissions(path, Set.of(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
        ));
    }
}
