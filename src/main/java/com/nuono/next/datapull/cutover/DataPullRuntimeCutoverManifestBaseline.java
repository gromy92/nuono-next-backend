package com.nuono.next.datapull.cutover;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.runtime.OperationCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Validated immutable boundary baseline used by the stopped-JVM source-cohort recheck. */
final class DataPullRuntimeCutoverManifestBaseline {
    private final String cohortSha;
    private final Map<OperationCode, Map<String, LocalDateTime>> boundaries;

    private DataPullRuntimeCutoverManifestBaseline(
            String cohortSha,
            Map<OperationCode, Map<String, LocalDateTime>> boundaries
    ) {
        this.cohortSha = cohortSha;
        this.boundaries = boundaries;
    }

    static DataPullRuntimeCutoverManifestBaseline load(
            ObjectMapper mapper,
            Path path,
            String commit,
            String jarSha,
            String cutoverKey
    ) throws Exception {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("DP_CUTOVER_BASELINE_INVALID");
        }
        JsonNode root = mapper.readTree(path.toFile());
        if (!DataPullRuntimeCutoverManifest.SCHEMA.equals(text(root, "schema"))
                || !DataPullRuntimeCutoverManifest.TYPE.equals(text(root, "type"))
                || !commit.equals(text(root, "manifestCommit"))
                || !jarSha.equals(text(root, "candidateJarSha256"))
                || !cutoverKey.equals(text(root, "cutoverKey"))
                || !DataPullRuntimeCutoverManifest.BOUNDARY_KIND.equals(
                        text(root, "boundaryPolicy")
                )) {
            throw new IllegalStateException("DP_CUTOVER_BASELINE_BINDING_MISMATCH");
        }
        String expected = text(root, "cohortSha256");
        if (!expected.matches("[0-9a-f]{64}")
                || !expected.equals(DataPullRuntimeCutoverManifest.canonicalSha256(
                        mapper, root.get("operations")
                ))) {
            throw new IllegalStateException("DP_CUTOVER_BASELINE_DIGEST_MISMATCH");
        }
        EnumMap<OperationCode, Map<String, LocalDateTime>> values =
                new EnumMap<>(OperationCode.class);
        for (JsonNode operation : root.withArray("operations")) {
            OperationCode code = OperationCode.valueOf(text(operation, "operationCode"));
            Map<String, LocalDateTime> scopes = new HashMap<>();
            for (JsonNode scope : operation.withArray("scopes")) {
                LocalDateTime previous = scopes.put(
                        text(scope, "scopeKey"), time(scope, "reconcileAfterUtc")
                );
                if (previous != null) {
                    throw new IllegalStateException("DP_CUTOVER_BASELINE_DUPLICATE_SCOPE");
                }
            }
            if (values.putIfAbsent(code, Map.copyOf(scopes)) != null) {
                throw new IllegalStateException("DP_CUTOVER_BASELINE_DUPLICATE_OPERATION");
            }
        }
        if (values.size() != OperationCode.values().length) {
            throw new IllegalStateException("DP_CUTOVER_BASELINE_OPERATION_GAP");
        }
        return new DataPullRuntimeCutoverManifestBaseline(expected, Map.copyOf(values));
    }

    LocalDateTime boundary(OperationCode operation, String scopeKey) {
        LocalDateTime value = boundaries.getOrDefault(operation, Map.of()).get(scopeKey);
        if (value == null) {
            throw new IllegalStateException("DP_CUTOVER_SCOPE_COHORT_DRIFT");
        }
        return value;
    }

    void requireSameCohort(String actual) {
        if (!cohortSha.equals(actual)) {
            throw new IllegalStateException("DP_CUTOVER_SOURCE_COHORT_DRIFT");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException("DP_CUTOVER_BASELINE_FIELD_INVALID:" + field);
        }
        return value.textValue();
    }

    private static LocalDateTime time(JsonNode node, String field) {
        String value = text(node, field);
        if (!value.endsWith("Z")) {
            throw new IllegalStateException("DP_CUTOVER_TIME_INVALID");
        }
        return LocalDateTime.parse(value.substring(0, value.length() - 1));
    }
}
