package com.nuono.next.datapull.cutover;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.DataPullScopeBindingDigest;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchor;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchorEvidence;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchorManifest;
import com.nuono.next.datapull.schedule.DataPullScopeAdmission;
import com.nuono.next.datapull.scope.DataPullScopeBindingCandidate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Canonical zero-write release evidence for the complete cutover cohort. */
final class DataPullRuntimeCutoverManifest {

    static final String SCHEMA = "nuono.dp-runtime-cutover-manifest/v1";
    static final String TYPE = "DP_RUNTIME_CUTOVER_MANIFEST";
    static final String BOUNDARY_KIND = "SAFE_PREDECESSOR_OR_FALLBACK_BOUNDARY";
    private static final String BOUNDARY_PROOF_VERSION = "DP_CUTOVER_SAFE_BOUNDARY_V2";
    private static final int VALID_MINUTES = 30;
    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    ObjectNode build(
            String commit,
            String jarSha256,
            DataPullRuntimeCutoverSourceCohort cohort,
            Path baselinePath
    ) throws Exception {
        String cutoverKey = "dp-runtime-" + commit;
        DataPullRuntimeCutoverManifestBaseline baseline = baselinePath == null
                ? null : DataPullRuntimeCutoverManifestBaseline.load(
                        mapper, baselinePath, commit, jarSha256, cutoverKey
                );
        LocalDateTime observed = cohort.getObservedAtUtc().truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime fallback = observed.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                .toLocalDate().minusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai"))
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        ArrayNode operations = mapper.createArrayNode();
        for (OperationCode operation : OperationCode.values()) {
            operations.add(operation(
                    cohort.getJobs().require(operation),
                    cohort.bindings(operation),
                    cutoverKey,
                    commit,
                    jarSha256,
                    observed,
                    fallback,
                    cohort,
                    baseline
            ));
        }
        String cohortSha = canonicalSha256(mapper, operations);
        if (baseline != null) baseline.requireSameCohort(cohortSha);

        ObjectNode root = mapper.createObjectNode();
        root.put("schema", SCHEMA);
        root.put("type", TYPE);
        root.put("manifestCommit", commit);
        root.put("candidateJarSha256", jarSha256);
        root.put("cutoverKey", cutoverKey);
        root.put("sourceObservedAtUtc", utc(observed));
        root.put("generatedAtUtc", utc(observed));
        root.put("expiresAtUtc", utc(observed.plusMinutes(VALID_MINUTES)));
        root.put("boundaryPolicy", BOUNDARY_KIND);
        root.put("operationCount", OperationCode.values().length);
        root.put("cohortSha256", cohortSha);
        root.set("operations", operations);
        return root;
    }

    private ObjectNode operation(
            DataPullJob job,
            List<DataPullScopeBindingCandidate> bindings,
            String cutoverKey,
            String commit,
            String jarSha,
            LocalDateTime observed,
            LocalDateTime fallback,
            DataPullRuntimeCutoverSourceCohort cohort,
            DataPullRuntimeCutoverManifestBaseline baseline
    ) {
        OperationCode operation = job.operationCode();
        List<DataPullScope> scopes = new ArrayList<>(job.listScopes());
        scopes.sort(Comparator.comparing(DataPullScope::getStableScopeKey));
        Map<String, DataPullScopeBindingCandidate> bindingsByScope = bindings(bindings);
        boolean dp08 = operation == OperationCode.DP08A || operation == OperationCode.DP08B;
        if ((dp08 && bindingsByScope.size() != scopes.size())
                || (!dp08 && !bindingsByScope.isEmpty())) {
            throw new IllegalStateException("DP_CUTOVER_BINDING_COHORT_MISMATCH:" + operation);
        }
        ArrayNode scopeNodes = mapper.createArrayNode();
        List<DataPullScheduleAnchor> anchors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DataPullScope scope : scopes) {
            if (!seen.add(scope.getStableScopeKey())) {
                throw new IllegalStateException("DP_CUTOVER_DUPLICATE_SCOPE:" + operation);
            }
            LocalDateTime boundary = baseline == null
                    ? cohort.reconcileAfter(operation, scope.getStableScopeKey(), fallback)
                    : baseline.boundary(operation, scope.getStableScopeKey());
            DataPullScopeAdmission admission = DataPullScopeAdmission.cutoverExisting(
                    scope, cutoverKey, observed
            );
            String boundaryProof = boundaryProof(
                    commit, jarSha, cutoverKey, operation, scope, boundary
            );
            String anchorEvidence = DataPullScheduleAnchorEvidence.cutoverSha256(
                    operation, admission, boundary, BOUNDARY_KIND, boundaryProof
            );
            anchors.add(DataPullScheduleAnchor.cutover(
                    operation, admission, boundary, observed, anchorEvidence
            ));
            scopeNodes.add(scope(
                    scope, admission, boundary, boundaryProof, anchorEvidence,
                    bindingsByScope.remove(scope.getStableScopeKey())
            ));
        }
        if (!bindingsByScope.isEmpty()) {
            throw new IllegalStateException("DP_CUTOVER_ORPHAN_BINDING:" + operation);
        }
        ObjectNode result = mapper.createObjectNode();
        result.put("operationCode", operation.name());
        result.put("expectedScopeCount", scopes.size());
        result.put("anchorManifestSha256", DataPullScheduleAnchorManifest.sha256(
                operation, cutoverKey, anchors
        ));
        result.set("scopes", scopeNodes);
        return result;
    }

    private ObjectNode scope(
            DataPullScope scope,
            DataPullScopeAdmission admission,
            LocalDateTime boundary,
            String boundaryProof,
            String anchorEvidence,
            DataPullScopeBindingCandidate binding
    ) {
        ObjectNode node = mapper.createObjectNode();
        node.put("scopeKey", scope.getStableScopeKey());
        node.put("scopeNamespace", scope.getNamespace());
        node.put("ownerUserId", scope.getOwnerUserId());
        nullable(node, "logicalStoreId", scope.getLogicalStoreId());
        node.put("accountKey", scope.getAccountKey());
        nullable(node, "egressKey", scope.getEgressKey());
        nullable(node, "projectCode", scope.getProjectCode());
        nullable(node, "storeCode", scope.getStoreCode());
        nullable(node, "siteCode", scope.getSiteCode());
        node.put("sourceBindingSha256", admission.getSourceBindingSha256());
        node.put("reconcileAfterUtc", utc(boundary));
        node.put("boundaryKind", BOUNDARY_KIND);
        node.put("boundaryEvidenceSha256", boundaryProof);
        node.put("anchorEvidenceSha256", anchorEvidence);
        if (binding == null) {
            node.putNull("binding");
        } else {
            ObjectNode value = node.putObject("binding");
            value.put("bindingId", binding.getBindingId());
            value.put("payloadType", binding.getPayloadType());
            value.put("payloadSha256", binding.getPayloadSha256());
            value.put("payload", binding.getPayload());
            value.put("effectiveFromUtc", utc(binding.getEffectiveFromUtc()));
        }
        if (!DataPullScopeBindingDigest.sha256(scope).equals(
                node.get("sourceBindingSha256").asText()
        )) throw new IllegalStateException("DP_CUTOVER_SCOPE_DIGEST_DRIFT");
        return node;
    }

    private static Map<String, DataPullScopeBindingCandidate> bindings(
            List<DataPullScopeBindingCandidate> bindings
    ) {
        Map<String, DataPullScopeBindingCandidate> result = new HashMap<>();
        for (DataPullScopeBindingCandidate binding : bindings) {
            if (result.putIfAbsent(binding.getScopeKey(), binding) != null) {
                throw new IllegalStateException("DP_CUTOVER_DUPLICATE_BINDING");
            }
        }
        return result;
    }

    private static String boundaryProof(
            String commit, String jarSha, String cutoverKey,
            OperationCode operation, DataPullScope scope, LocalDateTime boundary
    ) {
        return digest(List.of(
                BOUNDARY_PROOF_VERSION, commit, jarSha, cutoverKey,
                operation.name(), scope.getStableScopeKey(),
                DataPullScopeBindingDigest.sha256(scope), utc(boundary)
        ));
    }

    private static String digest(List<String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return hex(digest.digest());
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    static String canonicalSha256(ObjectMapper mapper, JsonNode value) throws Exception {
        Object generic = mapper.convertValue(value, Object.class);
        return hex(MessageDigest.getInstance("SHA-256").digest(
                mapper.writeValueAsBytes(generic)
        ));
    }

    private static String utc(LocalDateTime value) {
        return UTC_MILLIS.format(value.truncatedTo(ChronoUnit.MILLIS));
    }

    private static void nullable(ObjectNode node, String name, Object value) {
        if (value == null) node.putNull(name);
        else if (value instanceof Long) node.put(name, (Long) value);
        else node.put(name, String.valueOf(value));
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

}
