package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompetitorDetailRetryRollingRollbackTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void oldWriterChangedSubsetFailsClosedBecauseRemovedTargetOutcomeIsUnknown() throws Exception {
        ObjectNode rewritten = markedPayload(
                state("SELF", null, "ZSELF001", 1, "2026-07-28T02:02:00"),
                state("COMPETITOR", 88002L, "ZFAIL002", 1, "2026-07-28T03:00:00")
        );
        rewritten.put("retryAttempt", 2);
        rewritten.put("retryNotBefore", "2026-07-28T03:06:00");
        rewritten.put("lastErrorCode", "DETAIL_REFRESH_FAILED");
        ArrayNode targets = rewritten.putArray("failedDetailTargets");
        targets.add(targetJson("COMPETITOR", 88002L, "ZFAIL002"));

        assertThrows(
                CompetitorDetailRetryPayloadException.class,
                () -> CompetitorDetailRetryPayload.fromJson(
                        JSON.writeValueAsString(rewritten)
                )
        );
    }

    @Test
    void oldWriterSameIdentityWithAdvancedAttemptAndWakeFailsChecksumValidation() throws Exception {
        ObjectNode rewritten = markedPayload(
                state("SELF", null, "ZSELF001", 1, "2026-07-28T02:02:00")
        );
        rewritten.put("retryAttempt", 2);
        rewritten.put("retryNotBefore", "2026-07-28T02:06:00");

        assertThrows(
                CompetitorDetailRetryPayloadException.class,
                () -> CompetitorDetailRetryPayload.fromJson(
                        JSON.writeValueAsString(rewritten)
                )
        );
    }

    @Test
    void oldWriterEmptyTargetsFailsClosedInsteadOfFallingBackToStaleModern() throws Exception {
        ObjectNode rewritten = markedPayload(
                state("SELF", null, "ZSELF001", 1, "2026-07-28T02:02:00")
        );
        rewritten.put("retryAttempt", 2);
        rewritten.put("retryNotBefore", "2026-07-28T02:06:00");
        rewritten.putArray("failedDetailTargets");

        assertThrows(
                CompetitorDetailRetryPayloadException.class,
                () -> CompetitorDetailRetryPayload.fromJson(
                        JSON.writeValueAsString(rewritten)
                )
        );
    }

    @Test
    void pureOldAndPureModernLegalPayloadsRemainAccepted() throws Exception {
        CompetitorDetailRetryPayload legacy = CompetitorDetailRetryPayload.fromJson(
                "{\"retryAttempt\":1,"
                        + "\"maxRetryAttempts\":4,"
                        + "\"retryNotBefore\":\"2026-07-28T02:02:00\","
                        + "\"failedDetailTargets\":[{"
                        + "\"subjectType\":\"SELF\","
                        + "\"noonProductCode\":\"ZSELF001\"}]}"
        );
        CompetitorDetailRetryPayload modern = CompetitorDetailRetryPayload.fromJson(
                markedPayload(
                        state(
                                "COMPETITOR",
                                88002L,
                                "ZFAIL002",
                                2,
                                "2026-07-28T02:06:00"
                        )
                ).toString()
        );

        assertEquals(1, legacy.getRetryStates().size());
        assertEquals(1, modern.getRetryStates().size());
        assertEquals(2, modern.getRetryAttempt());
    }

    @Test
    void newWriterPersistsSchemaAndAllIntegrityChecks() throws Exception {
        JsonNode persisted = JSON.readTree(
                CompetitorDetailRetryPayload.fromJson(
                        "{\"retryAttempt\":1,"
                                + "\"maxRetryAttempts\":4,"
                                + "\"retryNotBefore\":\"2026-07-28T02:02:00\","
                                + "\"failedDetailTargets\":[{"
                                + "\"subjectType\":\"SELF\","
                                + "\"noonProductCode\":\"ZSELF001\"}]}"
                ).toJson()
        );

        assertEquals(3, persisted.path("detailRetrySchemaVersion").asInt());
        assertEquals(1, persisted.path("detailRetryProjectionVersion").asInt());
        assertTrue(persisted.path("detailRetryStateChecksum").asText().length() >= 32);
        assertTrue(persisted.path("detailRetryLegacyProjectionChecksum").asText().length() >= 32);
        assertTrue(persisted.path("detailRetryEnvelopeChecksum").asText().length() >= 32);
    }

    @Test
    void validVersionTwoPayloadStillMigratesToSealedVersionThree()
            throws Exception {
        CompetitorDetailRetryState state = state(
                "SELF", null, "ZSELF001", 1, "2026-07-28T02:02:00"
        );
        ObjectNode versionTwo = markedPayload(state);
        versionTwo.put("detailRetrySchemaVersion", 2);
        versionTwo.remove("detailRetryPhase");
        versionTwo.remove("detailRetryEnvelopeChecksum");
        for (JsonNode item : versionTwo.withArray("detailRetryStates")) {
            ((ObjectNode) item).remove("requestInFlight");
        }
        versionTwo.put(
                "detailRetryStateChecksum",
                CompetitorDetailRetrySealedProtocol.previousStateChecksum(
                        List.of(state), 4
                )
        );

        JsonNode migrated = JSON.readTree(
                CompetitorDetailRetryPayload.fromJson(
                        versionTwo.toString()
                ).toJson()
        );

        assertEquals(3, migrated.path("detailRetrySchemaVersion").asInt());
        assertTrue(migrated.hasNonNull("detailRetryEnvelopeChecksum"));
        assertEquals(1, migrated.path("detailRetryStates").size());
    }

    @Test
    void legacyWakeProjectionUsesLatestStateWhileNewReadinessUsesEarliestState() throws Exception {
        CompetitorDetailRetryPayload payload = CompetitorDetailRetryPayload.empty();
        payload.setRetryStates(Arrays.asList(
                state("SELF", null, "ZSELF001", 1, "2026-07-28T02:02:00"),
                state("COMPETITOR", 88002L, "ZFAIL002", 2, "2026-07-28T03:00:00")
        ));

        JsonNode persisted = JSON.readTree(payload.toJson());

        assertEquals(2, persisted.path("retryAttempt").asInt());
        assertEquals("2026-07-28T03:00:00", persisted.path("retryNotBefore").asText());
        assertTrue(payload.isReadyAt(LocalDateTime.parse("2026-07-28T02:02:00")));
        assertEquals(1, payload.getReadyTargetsAt(
                LocalDateTime.parse("2026-07-28T02:02:00")
        ).size());
    }

    private static ObjectNode markedPayload(CompetitorDetailRetryState... states)
            throws Exception {
        CompetitorDetailRetryPayload payload = CompetitorDetailRetryPayload.empty();
        payload.setRetryStates(Arrays.asList(states));
        return (ObjectNode) JSON.readTree(payload.toJson());
    }

    private static CompetitorDetailRetryState state(
            String subjectType,
            Long competitorProductId,
            String noonProductCode,
            int attempt,
            String notBefore
    ) {
        CompetitorProductDetailTarget target = CompetitorProductDetailTarget.SELF.equals(subjectType)
                ? CompetitorProductDetailTarget.self(noonProductCode)
                : CompetitorProductDetailTarget.competitor(
                        competitorProductId,
                        noonProductCode,
                        null
                );
        return new CompetitorDetailRetryState(
                target,
                attempt,
                LocalDateTime.parse(notBefore),
                null,
                null
        );
    }

    private static ObjectNode targetJson(
            String subjectType,
            Long competitorProductId,
            String noonProductCode
    ) {
        ObjectNode target = JSON.createObjectNode();
        target.put("subjectType", subjectType);
        if (competitorProductId != null) {
            target.put("competitorProductId", competitorProductId);
        }
        target.put("noonProductCode", noonProductCode);
        return target;
    }
}
