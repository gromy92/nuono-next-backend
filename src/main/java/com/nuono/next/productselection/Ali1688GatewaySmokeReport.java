package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

public final class Ali1688GatewaySmokeReport {

    private static final Set<String> SUCCESS_STATUSES = new LinkedHashSet<>(Arrays.asList("success", "partial_success"));
    private static final Set<String> ALLOWED_TYPED_GATEWAY_ERRORS = new LinkedHashSet<>(Arrays.asList(
            "login_required",
            "captcha_required",
            "rate_limited",
            "gateway_timeout",
            "no_candidates",
            "unexpected_response"
    ));
    private static final List<String> REQUIRED_DOWNSTREAM_SIDE_EFFECT_DELTAS = Collections.unmodifiableList(Arrays.asList(
            "procurement_candidate",
            "procurement_order",
            "procurement_demand_item",
            "procurement_auto_inquiry_task",
            "procurement_auto_inquiry_session",
            "procurement_auto_inquiry_event",
            "procurement_candidate_pool",
            "procurement_candidate_pool_item",
            "procurement_final_candidate"
    ));

    private final boolean passed;
    private final String outcome;
    private final String cleanupDecision;
    private final List<String> failures;
    private final Map<String, Long> downstreamSideEffectDeltas;
    private final Map<String, Object> outputRecord;

    private Ali1688GatewaySmokeReport(
            boolean passed,
            String outcome,
            String cleanupDecision,
            List<String> failures,
            Map<String, Long> downstreamSideEffectDeltas,
            Map<String, Object> outputRecord
    ) {
        this.passed = passed;
        this.outcome = outcome;
        this.cleanupDecision = cleanupDecision;
        this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        this.downstreamSideEffectDeltas = Collections.unmodifiableMap(new LinkedHashMap<>(downstreamSideEffectDeltas));
        this.outputRecord = Collections.unmodifiableMap(new LinkedHashMap<>(outputRecord));
    }

    public static Ali1688GatewaySmokeReport from(Snapshot snapshot) {
        Snapshot safeSnapshot = snapshot == null ? new Snapshot() : snapshot;
        List<String> failures = new ArrayList<>();
        String status = normalize(safeSnapshot.status);
        String gatewayErrorCode = normalize(safeSnapshot.gatewayErrorCode);
        Map<String, Long> sideEffectDeltas = normalizedSideEffectDeltas(safeSnapshot.downstreamSideEffectDeltas);

        if (safeSnapshot.sourceCollectionId == null) {
            failures.add("source collection ID is required");
        }
        if (safeSnapshot.taskId == null) {
            failures.add("task ID is required");
        }
        if (!StringUtils.hasText(status)) {
            failures.add("task status is required");
        }

        validateEvidence(safeSnapshot, failures);
        validateCleanupDecision(safeSnapshot.cleanupDecision, failures);
        validateGatewayPath(safeSnapshot, failures);
        validateSideEffectDeltas(sideEffectDeltas, failures);

        String outcome = resolveOutcome(status, gatewayErrorCode);
        if (SUCCESS_STATUSES.contains(status)) {
            validateSuccessfulCollection(safeSnapshot, failures);
        } else if ("typed_gateway_error".equals(outcome)) {
            if (!StringUtils.hasText(gatewayErrorCode)) {
                failures.add("typed gateway error code is required");
            }
        } else {
            failures.add("smoke status must be success, partial_success, or an allowed typed gateway error");
        }

        String cleanupDecision = StringUtils.hasText(safeSnapshot.cleanupDecision)
                ? safeSnapshot.cleanupDecision.trim()
                : "cleanup";
        Map<String, Object> outputRecord = outputRecord(safeSnapshot, outcome, cleanupDecision, failures, sideEffectDeltas);
        return new Ali1688GatewaySmokeReport(failures.isEmpty(), outcome, cleanupDecision, failures, sideEffectDeltas, outputRecord);
    }

    public boolean isPassed() {
        return passed;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getCleanupDecision() {
        return cleanupDecision;
    }

    public List<String> getFailures() {
        return failures;
    }

    public Map<String, Long> getDownstreamSideEffectDeltas() {
        return downstreamSideEffectDeltas;
    }

    public Map<String, Object> getOutputRecord() {
        return outputRecord;
    }

    public static List<String> requiredDownstreamSideEffectDeltaNames() {
        return REQUIRED_DOWNSTREAM_SIDE_EFFECT_DELTAS;
    }

    private static void validateEvidence(Snapshot snapshot, List<String> failures) {
        if (!snapshot.rawSnapshotPresent
                && !StringUtils.hasText(snapshot.providerTraceId)
                && !StringUtils.hasText(snapshot.diagnosticNote)) {
            failures.add("raw gateway snapshot, provider trace, or diagnostic note is required");
        }
    }

    private static void validateCleanupDecision(String cleanupDecision, List<String> failures) {
        if (!StringUtils.hasText(cleanupDecision)) {
            return;
        }
        String normalized = cleanupDecision.trim();
        if (!"cleanup".equals(normalized) && !normalized.startsWith("retain:")) {
            failures.add("cleanup decision must be cleanup or retain:<reason>");
        }
    }

    private static void validateSideEffectDeltas(Map<String, Long> sideEffectDeltas, List<String> failures) {
        for (String name : REQUIRED_DOWNSTREAM_SIDE_EFFECT_DELTAS) {
            if (!sideEffectDeltas.containsKey(name)) {
                failures.add("downstream side effect delta is required: " + name);
                continue;
            }
            Long delta = sideEffectDeltas.get(name);
            if (delta == null || delta.longValue() != 0L) {
                failures.add("downstream side effect delta must be 0: " + name + "=" + delta);
            }
        }
    }

    private static void validateSuccessfulCollection(Snapshot snapshot, List<String> failures) {
        int candidateCount = snapshot.candidateCount == null ? 0 : snapshot.candidateCount;
        int selectedRankCount = snapshot.selectedRankCount == null ? 0 : snapshot.selectedRankCount;
        if (candidateCount < 1 || candidateCount > 10) {
            failures.add("success or partial_success candidate count must be between 1 and 10");
        }
        if (selectedRankCount < 1 || selectedRankCount > Math.min(candidateCount, 5)) {
            failures.add("success or partial_success selected rank count must be between 1 and min(candidateCount, 5)");
        }
    }

    private static String resolveOutcome(String status, String gatewayErrorCode) {
        if (SUCCESS_STATUSES.contains(status)) {
            return status;
        }
        if ("failed".equals(status) && ALLOWED_TYPED_GATEWAY_ERRORS.contains(gatewayErrorCode)) {
            return "typed_gateway_error";
        }
        return StringUtils.hasText(status) ? status : "unknown";
    }

    private static Map<String, Long> normalizedSideEffectDeltas(Map<String, Long> source) {
        Map<String, Long> normalized = new LinkedHashMap<>();
        if (source != null) {
            normalized.putAll(source);
        }
        return normalized;
    }

    private static Map<String, Object> outputRecord(
            Snapshot snapshot,
            String outcome,
            String cleanupDecision,
            List<String> failures,
            Map<String, Long> sideEffectDeltas
    ) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("sourceCollectionId", snapshot.sourceCollectionId);
        output.put("taskId", snapshot.taskId);
        output.put("status", normalize(snapshot.status));
        output.put("outcome", outcome);
        output.put("candidateCount", snapshot.candidateCount);
        output.put("selectedRankCount", snapshot.selectedRankCount);
        output.put("gatewayErrorCode", normalize(snapshot.gatewayErrorCode));
        output.put("gatewayServiceKind", normalize(snapshot.gatewayServiceKind));
        output.put("usedPluginBridge", snapshot.usedPluginBridge);
        output.put("usedManualPayload", snapshot.usedManualPayload);
        output.put("rawSnapshotPresent", snapshot.rawSnapshotPresent);
        output.put("providerTraceId", snapshot.providerTraceId);
        output.put("diagnosticNote", snapshot.diagnosticNote);
        output.put("cleanupDecision", cleanupDecision);
        output.put("passed", failures.isEmpty());
        output.put("failures", new ArrayList<>(failures));
        output.put("downstreamSideEffectDeltas", new LinkedHashMap<>(sideEffectDeltas));
        return output;
    }

    private static void validateGatewayPath(Snapshot snapshot, List<String> failures) {
        if (!"system_browser_gateway".equals(normalize(snapshot.gatewayServiceKind))) {
            failures.add("gateway service kind must be system_browser_gateway");
        }
        if (snapshot.usedPluginBridge) {
            failures.add("system smoke must not use Chrome plugin bridge");
        }
        if (snapshot.usedManualPayload) {
            failures.add("system smoke must not use manual payload sending");
        }
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    public static final class Snapshot {
        private Long sourceCollectionId;
        private Long taskId;
        private String status;
        private String gatewayErrorCode;
        private String gatewayServiceKind;
        private boolean usedPluginBridge;
        private boolean usedManualPayload;
        private Integer candidateCount;
        private Integer selectedRankCount;
        private boolean rawSnapshotPresent;
        private String providerTraceId;
        private String diagnosticNote;
        private String cleanupDecision;
        private Map<String, Long> downstreamSideEffectDeltas = new LinkedHashMap<>();

        public void setSourceCollectionId(Long sourceCollectionId) {
            this.sourceCollectionId = sourceCollectionId;
        }

        public void setTaskId(Long taskId) {
            this.taskId = taskId;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public void setGatewayErrorCode(String gatewayErrorCode) {
            this.gatewayErrorCode = gatewayErrorCode;
        }

        public void setGatewayServiceKind(String gatewayServiceKind) {
            this.gatewayServiceKind = gatewayServiceKind;
        }

        public void setUsedPluginBridge(boolean usedPluginBridge) {
            this.usedPluginBridge = usedPluginBridge;
        }

        public void setUsedManualPayload(boolean usedManualPayload) {
            this.usedManualPayload = usedManualPayload;
        }

        public void setCandidateCount(Integer candidateCount) {
            this.candidateCount = candidateCount;
        }

        public void setSelectedRankCount(Integer selectedRankCount) {
            this.selectedRankCount = selectedRankCount;
        }

        public void setRawSnapshotPresent(boolean rawSnapshotPresent) {
            this.rawSnapshotPresent = rawSnapshotPresent;
        }

        public void setProviderTraceId(String providerTraceId) {
            this.providerTraceId = providerTraceId;
        }

        public void setDiagnosticNote(String diagnosticNote) {
            this.diagnosticNote = diagnosticNote;
        }

        public void setCleanupDecision(String cleanupDecision) {
            this.cleanupDecision = cleanupDecision;
        }

        public void setDownstreamSideEffectDeltas(Map<String, Long> downstreamSideEffectDeltas) {
            this.downstreamSideEffectDeltas = downstreamSideEffectDeltas == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(downstreamSideEffectDeltas);
        }
    }
}
