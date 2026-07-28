package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;

final class CompetitorDetailRetryProtocol {
    private static final int SCHEMA_VERSION = 2;
    private static final int PROJECTION_VERSION = 1;
    private static final String SCHEMA_FIELD = "detailRetrySchemaVersion";
    private static final String PROJECTION_FIELD = "detailRetryProjectionVersion";
    private static final String STATE_CHECKSUM_FIELD = "detailRetryStateChecksum";
    private static final String LEGACY_CHECKSUM_FIELD =
            "detailRetryLegacyProjectionChecksum";
    private static final String[] RETRY_FIELDS = {
        "retryAttempt",
        "maxRetryAttempts",
        "retryNotBefore",
        "rootRunId",
        "retryOfRunId",
        CompetitorDetailRetryStateJson.STATE_FIELD,
        CompetitorDetailRetryStateJson.LEGACY_TARGET_FIELD,
        "lastErrorCode",
        "message",
        SCHEMA_FIELD,
        PROJECTION_FIELD,
        STATE_CHECKSUM_FIELD,
        LEGACY_CHECKSUM_FIELD
    };

    private CompetitorDetailRetryProtocol() {
    }

    static boolean hasRetryMetadata(ObjectNode payload) {
        for (String field : RETRY_FIELDS) {
            if (payload.has(field)) {
                return true;
            }
        }
        return false;
    }

    static List<CompetitorDetailRetryState> read(
            ObjectNode payload,
            int retryAttempt,
            int maximumAttempts,
            LocalDateTime retryNotBefore,
            String errorCode,
            String errorMessage
    ) {
        if (!hasRetryMetadata(payload)) {
            return java.util.Collections.emptyList();
        }
        boolean hasStates = payload.has(CompetitorDetailRetryStateJson.STATE_FIELD);
        boolean hasLegacy = payload.has(CompetitorDetailRetryStateJson.LEGACY_TARGET_FIELD);
        boolean hasMarker = hasAnyMarker(payload);
        if (hasStates) {
            if (!hasMarker || !hasLegacy || !hasCompleteMarker(payload)) {
                throw invalid("Incomplete or unversioned detail retry state payload.");
            }
            return readCurrent(
                    payload,
                    retryAttempt,
                    maximumAttempts,
                    retryNotBefore
            );
        }
        if (hasMarker || !hasLegacy) {
            throw invalid("Invalid detail retry payload shape.");
        }
        requireLegacySummary(payload, retryAttempt, maximumAttempts, retryNotBefore);
        return CompetitorDetailRetryStateJson.migrateLegacy(
                CompetitorDetailRetryStateJson.readTargets(
                        payload.get(CompetitorDetailRetryStateJson.LEGACY_TARGET_FIELD)
                ),
                retryAttempt,
                retryNotBefore,
                errorCode,
                errorMessage
        );
    }

    static void write(
            ObjectNode payload,
            List<CompetitorDetailRetryState> states,
            int maximumAttempts
    ) {
        if (CompetitorDetailRetryStateJson.maximumAttempt(states) > maximumAttempts) {
            throw invalid("Detail retry state exceeds maxRetryAttempts.");
        }
        CompetitorDetailRetryStateJson.writeArrays(payload, states);
        int retryAttempt = CompetitorDetailRetryStateJson.maximumAttempt(states);
        LocalDateTime legacyWake = CompetitorDetailRetryStateJson.latestWake(states);
        payload.put("retryAttempt", retryAttempt);
        CompetitorDetailRetryJsonSupport.putDateTime(
                payload,
                "retryNotBefore",
                legacyWake
        );
        payload.put(SCHEMA_FIELD, SCHEMA_VERSION);
        payload.put(PROJECTION_FIELD, PROJECTION_VERSION);
        payload.put(
                STATE_CHECKSUM_FIELD,
                stateChecksum(states, maximumAttempts)
        );
        payload.put(
                LEGACY_CHECKSUM_FIELD,
                legacyChecksum(
                        CompetitorDetailRetryStateJson.targets(states),
                        retryAttempt,
                        legacyWake
                )
        );
    }

    static void clear(ObjectNode payload) {
        for (String field : RETRY_FIELDS) {
            payload.remove(field);
        }
    }

    private static List<CompetitorDetailRetryState> readCurrent(
            ObjectNode payload,
            int retryAttempt,
            int maximumAttempts,
            LocalDateTime retryNotBefore
    ) {
        requireLegacySummary(payload, retryAttempt, maximumAttempts, retryNotBefore);
        int schema = CompetitorDetailRetryJsonSupport.requiredInt(
                payload,
                SCHEMA_FIELD,
                SCHEMA_VERSION,
                SCHEMA_VERSION
        );
        int projection = CompetitorDetailRetryJsonSupport.requiredInt(
                payload,
                PROJECTION_FIELD,
                PROJECTION_VERSION,
                PROJECTION_VERSION
        );
        if (schema != SCHEMA_VERSION || projection != PROJECTION_VERSION) {
            throw invalid("Unsupported detail retry payload protocol.");
        }
        List<CompetitorDetailRetryState> states =
                CompetitorDetailRetryStateJson.readStates(
                        payload.get(CompetitorDetailRetryStateJson.STATE_FIELD),
                        maximumAttempts
                );
        List<CompetitorProductDetailTarget> legacyTargets =
                CompetitorDetailRetryStateJson.readTargets(
                        payload.get(CompetitorDetailRetryStateJson.LEGACY_TARGET_FIELD)
                );
        int projectedAttempt = CompetitorDetailRetryStateJson.maximumAttempt(states);
        LocalDateTime projectedWake = CompetitorDetailRetryStateJson.latestWake(states);
        if (retryAttempt != projectedAttempt || !projectedWake.equals(retryNotBefore)) {
            throw invalid("Detail retry legacy summary does not match target states.");
        }
        String expectedState = requiredChecksum(payload, STATE_CHECKSUM_FIELD);
        String expectedLegacy = requiredChecksum(payload, LEGACY_CHECKSUM_FIELD);
        if (!expectedState.equals(stateChecksum(states, maximumAttempts))
                || !expectedLegacy.equals(legacyChecksum(
                        CompetitorDetailRetryStateJson.targets(states),
                        projectedAttempt,
                        projectedWake
                ))
                || !expectedLegacy.equals(legacyChecksum(
                        legacyTargets,
                        retryAttempt,
                        retryNotBefore
                ))) {
            throw invalid("Detail retry payload integrity check failed.");
        }
        return states;
    }

    private static void requireLegacySummary(
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

    private static boolean hasAnyMarker(ObjectNode payload) {
        return payload.has(SCHEMA_FIELD)
                || payload.has(PROJECTION_FIELD)
                || payload.has(STATE_CHECKSUM_FIELD)
                || payload.has(LEGACY_CHECKSUM_FIELD);
    }

    private static boolean hasCompleteMarker(ObjectNode payload) {
        return payload.hasNonNull(SCHEMA_FIELD)
                && payload.hasNonNull(PROJECTION_FIELD)
                && payload.hasNonNull(STATE_CHECKSUM_FIELD)
                && payload.hasNonNull(LEGACY_CHECKSUM_FIELD);
    }

    private static String requiredChecksum(ObjectNode payload, String field) {
        String value = CompetitorDetailRetryJsonSupport.requiredText(payload, field);
        if (!value.matches("[0-9a-f]{64}")) {
            throw invalid(field + " must be a SHA-256 checksum.");
        }
        return value;
    }

    private static String stateChecksum(
            List<CompetitorDetailRetryState> states,
            int maximumAttempts
    ) {
        return sha256(
                CompetitorDetailRetryJsonSupport.part(maximumAttempts)
                        + CompetitorDetailRetryStateJson.stateCanonical(states)
        );
    }

    private static String legacyChecksum(
            List<CompetitorProductDetailTarget> targets,
            int attempt,
            LocalDateTime notBefore
    ) {
        return sha256(
                CompetitorDetailRetryStateJson.legacyCanonical(
                        targets,
                        attempt,
                        notBefore
                )
        );
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
