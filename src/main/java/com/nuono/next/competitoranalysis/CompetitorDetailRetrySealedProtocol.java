package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

final class CompetitorDetailRetrySealedProtocol {
    static final int SCHEMA_VERSION = 3;
    static final String PHASE_FIELD = "detailRetryPhase";
    static final String ENVELOPE_CHECKSUM_FIELD = "detailRetryEnvelopeChecksum";
    private static final String STATE_CHECKSUM_FIELD = "detailRetryStateChecksum";
    private static final String LEGACY_CHECKSUM_FIELD =
            "detailRetryLegacyProjectionChecksum";

    private CompetitorDetailRetrySealedProtocol() {
    }

    static boolean hasCompleteMarker(ObjectNode payload) {
        return payload.path("detailRetrySchemaVersion").asInt() == SCHEMA_VERSION
                && payload.hasNonNull(PHASE_FIELD)
                && payload.hasNonNull(ENVELOPE_CHECKSUM_FIELD);
    }

    static void write(
            ObjectNode payload,
            List<CompetitorDetailRetryState> states,
            int maximumAttempts
    ) {
        List<CompetitorDetailRetryState> safeStates =
                states == null ? Collections.emptyList() : states;
        int retryAttempt = maximumAttempt(safeStates);
        if (retryAttempt > maximumAttempts) {
            throw invalid("Detail retry state exceeds maxRetryAttempts.");
        }
        LocalDateTime wake = latestWake(safeStates);
        boolean active = !safeStates.isEmpty();
        if (active) {
            CompetitorDetailRetryStateJson.writeArrays(payload, safeStates);
            payload.put("retryAttempt", retryAttempt);
            CompetitorDetailRetryJsonSupport.putDateTime(
                    payload, "retryNotBefore", wake
            );
        } else {
            payload.remove(CompetitorDetailRetryStateJson.STATE_FIELD);
            payload.remove(CompetitorDetailRetryStateJson.LEGACY_TARGET_FIELD);
            payload.remove("retryAttempt");
            payload.remove("retryNotBefore");
        }
        payload.put("detailRetrySchemaVersion", SCHEMA_VERSION);
        payload.put("detailRetryProjectionVersion", 1);
        payload.put(PHASE_FIELD, active ? "ACTIVE" : "COMPLETE");
        payload.put(STATE_CHECKSUM_FIELD, stateChecksum(safeStates, maximumAttempts));
        payload.put(
                LEGACY_CHECKSUM_FIELD,
                legacyChecksum(targets(safeStates), retryAttempt, wake)
        );
        payload.put(ENVELOPE_CHECKSUM_FIELD, envelopeChecksum(payload));
    }

    static List<CompetitorDetailRetryState> read(
            ObjectNode payload,
            int retryAttempt,
            int maximumAttempts,
            LocalDateTime retryNotBefore
    ) {
        String phase = CompetitorDetailRetryJsonSupport.requiredText(payload, PHASE_FIELD);
        if (!"ACTIVE".equals(phase) && !"COMPLETE".equals(phase)) {
            throw invalid("Invalid detail retry phase.");
        }
        requireCounts(payload);
        if (!requiredChecksum(payload, ENVELOPE_CHECKSUM_FIELD)
                .equals(envelopeChecksum(payload))) {
            throw invalid("Detail retry envelope integrity check failed.");
        }
        if ("COMPLETE".equals(phase)) {
            requireCompleteShape(payload);
            verifyChecksums(payload, Collections.emptyList(), maximumAttempts, 0, null);
            return Collections.emptyList();
        }
        requireActiveSummary(payload, retryAttempt, maximumAttempts, retryNotBefore);
        List<CompetitorDetailRetryState> states =
                CompetitorDetailRetryStateJson.readStates(
                        payload.get(CompetitorDetailRetryStateJson.STATE_FIELD),
                        maximumAttempts
                );
        List<CompetitorProductDetailTarget> legacyTargets =
                CompetitorDetailRetryStateJson.readTargets(
                        payload.get(CompetitorDetailRetryStateJson.LEGACY_TARGET_FIELD)
                );
        int projectedAttempt = maximumAttempt(states);
        LocalDateTime projectedWake = latestWake(states);
        if (retryAttempt != projectedAttempt || !projectedWake.equals(retryNotBefore)) {
            throw invalid("Detail retry legacy summary does not match target states.");
        }
        verifyChecksums(payload, states, maximumAttempts, projectedAttempt, projectedWake);
        if (!requiredChecksum(payload, LEGACY_CHECKSUM_FIELD).equals(
                legacyChecksum(legacyTargets, retryAttempt, retryNotBefore)
        )) {
            throw invalid("Detail retry legacy projection integrity check failed.");
        }
        return states;
    }

    static String stateChecksum(
            List<CompetitorDetailRetryState> states,
            int maximumAttempts
    ) {
        String canonical = states == null || states.isEmpty()
                ? ""
                : CompetitorDetailRetryStateJson.sealedStateCanonical(states);
        return sha256(CompetitorDetailRetryJsonSupport.part(maximumAttempts) + canonical);
    }

    static String previousStateChecksum(
            List<CompetitorDetailRetryState> states,
            int maximumAttempts
    ) {
        String canonical = states == null || states.isEmpty()
                ? ""
                : CompetitorDetailRetryStateJson.stateCanonical(states);
        return sha256(CompetitorDetailRetryJsonSupport.part(maximumAttempts) + canonical);
    }

    static String legacyChecksum(
            List<CompetitorProductDetailTarget> targets,
            int attempt,
            LocalDateTime notBefore
    ) {
        String canonical = targets == null || targets.isEmpty()
                ? CompetitorDetailRetryJsonSupport.part(attempt)
                + CompetitorDetailRetryJsonSupport.part(notBefore)
                : CompetitorDetailRetryStateJson.legacyCanonical(
                        targets, attempt, notBefore
                );
        return sha256(canonical);
    }

    private static void requireCompleteShape(ObjectNode payload) {
        if (payload.has(CompetitorDetailRetryStateJson.STATE_FIELD)
                || payload.has(CompetitorDetailRetryStateJson.LEGACY_TARGET_FIELD)
                || payload.has("retryAttempt")
                || payload.has("retryNotBefore")) {
            throw invalid("Completed detail retry payload still has pending targets.");
        }
    }

    private static void requireActiveSummary(
            ObjectNode payload,
            int retryAttempt,
            int maximumAttempts,
            LocalDateTime retryNotBefore
    ) {
        if (!payload.hasNonNull("retryAttempt")
                || !payload.hasNonNull("maxRetryAttempts")
                || !payload.hasNonNull("retryNotBefore")
                || retryNotBefore == null
                || retryAttempt < 0
                || retryAttempt > maximumAttempts) {
            throw invalid("Detail retry payload requires a valid attempt and wake.");
        }
    }

    private static void requireCounts(ObjectNode payload) {
        int total = requiredCount(payload, "detailTargetTotal");
        int succeeded = requiredCount(payload, "detailSucceededCount");
        int terminal = requiredCount(payload, "detailTerminalFailedCount");
        requiredCount(payload, "detailRequestAttemptCount");
        int pending = payload.path(CompetitorDetailRetryStateJson.STATE_FIELD).size();
        if (succeeded + terminal + pending > total) {
            throw invalid("Detail retry counters exceed target total.");
        }
    }

    private static int requiredCount(ObjectNode payload, String field) {
        return CompetitorDetailRetryJsonSupport.requiredInt(
                payload, field, 0, Integer.MAX_VALUE
        );
    }

    private static void verifyChecksums(
            ObjectNode payload,
            List<CompetitorDetailRetryState> states,
            int maximumAttempts,
            int attempt,
            LocalDateTime wake
    ) {
        if (!requiredChecksum(payload, STATE_CHECKSUM_FIELD)
                .equals(stateChecksum(states, maximumAttempts))
                || !requiredChecksum(payload, LEGACY_CHECKSUM_FIELD)
                .equals(legacyChecksum(targets(states), attempt, wake))) {
            throw invalid("Detail retry payload integrity check failed.");
        }
    }

    private static String requiredChecksum(ObjectNode payload, String field) {
        String value = CompetitorDetailRetryJsonSupport.requiredText(payload, field);
        if (!value.matches("[0-9a-f]{64}")) {
            throw invalid(field + " must be a SHA-256 checksum.");
        }
        return value;
    }

    private static String envelopeChecksum(ObjectNode payload) {
        String[] fields = {
            "detailRetrySchemaVersion", "detailRetryProjectionVersion",
            "watchProductId", "triggerMode", "executionMode", "batchKey",
            "rootRunId", "retryOfRunId", "maxRetryAttempts",
            "detailTargetTotal", "detailRequestAttemptCount",
            "detailSucceededCount", "detailTerminalFailedCount",
            "detailTerminalErrorCode", "detailTerminalErrorMessage",
            "lastErrorCode", "message", PHASE_FIELD,
            STATE_CHECKSUM_FIELD, LEGACY_CHECKSUM_FIELD
        };
        StringBuilder canonical = new StringBuilder();
        for (String field : fields) {
            JsonNode value = payload.get(field);
            canonical.append(CompetitorDetailRetryJsonSupport.part(field));
            canonical.append(CompetitorDetailRetryJsonSupport.part(
                    value == null || value.isNull() ? null : value.toString()
            ));
        }
        return sha256(canonical.toString());
    }

    private static List<CompetitorProductDetailTarget> targets(
            List<CompetitorDetailRetryState> states
    ) {
        return states == null || states.isEmpty()
                ? Collections.emptyList()
                : CompetitorDetailRetryStateJson.targets(states);
    }

    private static int maximumAttempt(List<CompetitorDetailRetryState> states) {
        return states == null || states.isEmpty()
                ? 0
                : CompetitorDetailRetryStateJson.maximumAttempt(states);
    }

    private static LocalDateTime latestWake(List<CompetitorDetailRetryState> states) {
        return states == null || states.isEmpty()
                ? null
                : CompetitorDetailRetryStateJson.latestWake(states);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder output = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                output.append(String.format("%02x", item & 0xff));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static CompetitorDetailRetryPayloadException invalid(String message) {
        return CompetitorDetailRetryJsonSupport.invalid(message);
    }
}
