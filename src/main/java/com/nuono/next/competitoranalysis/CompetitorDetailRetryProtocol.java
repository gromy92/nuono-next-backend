package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.List;

final class CompetitorDetailRetryProtocol {
    private static final int PREVIOUS_SCHEMA_VERSION = 2;
    private static final int PROJECTION_VERSION = 1;
    private static final String SCHEMA_FIELD = "detailRetrySchemaVersion";
    private static final String PROJECTION_FIELD = "detailRetryProjectionVersion";
    private static final String STATE_CHECKSUM_FIELD = "detailRetryStateChecksum";
    private static final String LEGACY_CHECKSUM_FIELD =
            "detailRetryLegacyProjectionChecksum";
    private static final String[] TARGETED_FIELDS = {
        "retryAttempt",
        "maxRetryAttempts",
        "rootRunId",
        "retryOfRunId",
        CompetitorDetailRetryStateJson.STATE_FIELD,
        CompetitorDetailRetryStateJson.LEGACY_TARGET_FIELD,
        "lastErrorCode",
        "message",
        "detailTargetTotal",
        "detailRequestAttemptCount",
        "detailSucceededCount",
        "detailTerminalFailedCount",
        "detailTerminalErrorCode",
        "detailTerminalErrorMessage",
        SCHEMA_FIELD,
        PROJECTION_FIELD,
        CompetitorDetailRetrySealedProtocol.PHASE_FIELD,
        STATE_CHECKSUM_FIELD,
        LEGACY_CHECKSUM_FIELD,
        CompetitorDetailRetrySealedProtocol.ENVELOPE_CHECKSUM_FIELD
    };
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
        CompetitorDetailRetrySealedProtocol.PHASE_FIELD,
        STATE_CHECKSUM_FIELD,
        LEGACY_CHECKSUM_FIELD,
        CompetitorDetailRetrySealedProtocol.ENVELOPE_CHECKSUM_FIELD
    };

    private CompetitorDetailRetryProtocol() {
    }

    static boolean hasTargetedMetadata(ObjectNode payload) {
        for (String field : TARGETED_FIELDS) {
            if (payload.has(field)) {
                return true;
            }
        }
        java.util.Iterator<String> fields = payload.fieldNames();
        while (fields.hasNext()) {
            if (fields.next().startsWith("detailRetry")) {
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
        if (hasUnknownRetryField(payload)) {
            throw invalid("Unknown detail retry protocol field.");
        }
        if (!hasTargetedMetadata(payload)) {
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
        if (hasMarker) {
            if (!hasCompleteMarker(payload)) {
                throw invalid("Incomplete detail retry protocol marker.");
            }
            return readCurrent(payload, retryAttempt, maximumAttempts, retryNotBefore);
        }
        if (!hasLegacy) {
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
            int maximumAttempts,
            boolean initialized
    ) {
        if (!initialized) {
            clear(payload);
            return;
        }
        CompetitorDetailRetrySealedProtocol.write(payload, states, maximumAttempts);
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
        int schema = CompetitorDetailRetryJsonSupport.requiredInt(
                payload,
                SCHEMA_FIELD,
                PREVIOUS_SCHEMA_VERSION,
                CompetitorDetailRetrySealedProtocol.SCHEMA_VERSION
        );
        int projection = CompetitorDetailRetryJsonSupport.requiredInt(
                payload,
                PROJECTION_FIELD,
                PROJECTION_VERSION,
                PROJECTION_VERSION
        );
        if (projection != PROJECTION_VERSION) {
            throw invalid("Unsupported detail retry payload protocol.");
        }
        if (schema == CompetitorDetailRetrySealedProtocol.SCHEMA_VERSION) {
            return CompetitorDetailRetrySealedProtocol.read(
                    payload, retryAttempt, maximumAttempts, retryNotBefore
            );
        }
        if (payload.has(CompetitorDetailRetrySealedProtocol.PHASE_FIELD)
                || payload.has(
                        CompetitorDetailRetrySealedProtocol.ENVELOPE_CHECKSUM_FIELD
                )
                || CompetitorDetailRetryStateJson.hasRequestReservation(
                        payload.get(CompetitorDetailRetryStateJson.STATE_FIELD)
                )) {
            throw invalid("Mixed detail retry protocol markers are not allowed.");
        }
        requireLegacySummary(payload, retryAttempt, maximumAttempts, retryNotBefore);
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
        if (!expectedState.equals(
                CompetitorDetailRetrySealedProtocol.previousStateChecksum(
                        states, maximumAttempts
                )
        )
                || !expectedLegacy.equals(CompetitorDetailRetrySealedProtocol.legacyChecksum(
                        CompetitorDetailRetryStateJson.targets(states),
                        projectedAttempt,
                        projectedWake
                ))
                || !expectedLegacy.equals(CompetitorDetailRetrySealedProtocol.legacyChecksum(
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
                || payload.has(LEGACY_CHECKSUM_FIELD)
                || payload.has(CompetitorDetailRetrySealedProtocol.PHASE_FIELD)
                || payload.has(CompetitorDetailRetrySealedProtocol.ENVELOPE_CHECKSUM_FIELD);
    }

    private static boolean hasUnknownRetryField(ObjectNode payload) {
        java.util.Iterator<String> fields = payload.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (field.startsWith("detailRetry") && !isTargetedField(field)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTargetedField(String candidate) {
        for (String field : TARGETED_FIELDS) {
            if (field.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCompleteMarker(ObjectNode payload) {
        return payload.hasNonNull(SCHEMA_FIELD)
                && payload.hasNonNull(PROJECTION_FIELD)
                && payload.hasNonNull(STATE_CHECKSUM_FIELD)
                && payload.hasNonNull(LEGACY_CHECKSUM_FIELD)
                && (payload.path(SCHEMA_FIELD).asInt() == PREVIOUS_SCHEMA_VERSION
                || CompetitorDetailRetrySealedProtocol.hasCompleteMarker(payload));
    }

    private static String requiredChecksum(ObjectNode payload, String field) {
        String value = CompetitorDetailRetryJsonSupport.requiredText(payload, field);
        if (!value.matches("[0-9a-f]{64}")) {
            throw invalid(field + " must be a SHA-256 checksum.");
        }
        return value;
    }

    private static CompetitorDetailRetryPayloadException invalid(String message) {
        return CompetitorDetailRetryJsonSupport.invalid(message);
    }
}
