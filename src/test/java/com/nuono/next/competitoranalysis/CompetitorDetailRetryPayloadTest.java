package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CompetitorDetailRetryPayloadTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void legacyPayloadIsImmediatelyReadyAndKeepsExistingTaskFields() throws Exception {
        String legacy = "{"
                + "\"watchProductId\":180123,"
                + "\"triggerMode\":\"SCHEDULED_DETAIL_MONITOR\","
                + "\"executionMode\":\"scheduledDetail\","
                + "\"rankRefresh\":false,"
                + "\"detailRefresh\":true,"
                + "\"batchKey\":\"detail:2026-07-28\""
                + "}";

        CompetitorDetailRetryPayload payload = CompetitorDetailRetryPayload.fromJson(legacy);

        assertEquals(0, payload.getRetryAttempt());
        assertEquals(4, payload.getMaxRetryAttempts());
        assertNull(payload.getRetryNotBefore());
        assertTrue(payload.isReadyAt(LocalDateTime.parse("2026-07-28T02:00:00")));

        JsonNode persisted = JSON.readTree(payload.toJson());
        assertEquals(180123L, persisted.path("watchProductId").asLong());
        assertEquals("SCHEDULED_DETAIL_MONITOR", persisted.path("triggerMode").asText());
        assertEquals("scheduledDetail", persisted.path("executionMode").asText());
        assertFalse(persisted.path("rankRefresh").asBoolean());
        assertTrue(persisted.path("detailRefresh").asBoolean());
        assertEquals("detail:2026-07-28", persisted.path("batchKey").asText());
        assertFalse(persisted.has("retryAttempt"));
        assertFalse(persisted.has("maxRetryAttempts"));
        assertFalse(persisted.has("detailRetryStates"));
    }

    @Test
    void retryMetadataAndTypedTargetsSurviveJsonRoundTrip() throws Exception {
        String value = "{"
                + "\"watchProductId\":180123,"
                + "\"retryAttempt\":2,"
                + "\"maxRetryAttempts\":4,"
                + "\"retryNotBefore\":\"2026-07-28T02:06:00\","
                + "\"rootRunId\":220100,"
                + "\"retryOfRunId\":220101,"
                + "\"failedDetailTargets\":[{"
                + "\"subjectType\":\"COMPETITOR\","
                + "\"competitorProductId\":190123,"
                + "\"noonProductCode\":\"ZFAIL001\","
                + "\"canonicalUrl\":\"https://www.noon.com/item/ZFAIL001\""
                + "}],"
                + "\"lastErrorCode\":\"PROVIDER_UNAVAILABLE\","
                + "\"message\":\"Noon detail timed out\","
                + "\"detailTargetTotal\":5,"
                + "\"detailSucceededCount\":4"
                + "}";

        CompetitorDetailRetryPayload payload = CompetitorDetailRetryPayload.fromJson(value);
        CompetitorDetailRetryPayload restored =
                CompetitorDetailRetryPayload.fromJson(payload.toJson());

        assertEquals(2, restored.getRetryAttempt());
        assertEquals(4, restored.getMaxRetryAttempts());
        assertEquals(LocalDateTime.parse("2026-07-28T02:06:00"), restored.getRetryNotBefore());
        assertEquals(220100L, restored.getRootRunId());
        assertEquals(220101L, restored.getRetryOfRunId());
        assertEquals(1, restored.getFailedDetailTargets().size());
        assertEquals("PROVIDER_UNAVAILABLE", restored.getLastErrorCode());
        assertEquals("Noon detail timed out", restored.getMessage());
        assertEquals(5, restored.getDetailTargetTotal());
        assertEquals(4, restored.getDetailSucceededCount());

        JsonNode persisted = JSON.readTree(restored.toJson());
        assertEquals("ZFAIL001", persisted.path("failedDetailTargets").path(0)
                .path("noonProductCode").asText());
        assertEquals(1, persisted.path("detailRetryStates").size());
        assertEquals(2, persisted.path("detailRetryStates").path(0)
                .path("retryAttempt").asInt());
        assertEquals("2026-07-28T02:06:00", persisted.path("detailRetryStates").path(0)
                .path("retryNotBefore").asText());
        assertEquals("PROVIDER_UNAVAILABLE", persisted.path("detailRetryStates").path(0)
                .path("errorCode").asText());
    }

    @Test
    void readinessUsesInclusiveRetryBoundary() {
        CompetitorDetailRetryPayload payload = CompetitorDetailRetryPayload.fromJson(
                "{\"retryAttempt\":1,"
                        + "\"maxRetryAttempts\":4,"
                        + "\"retryNotBefore\":\"2026-07-28T02:02:00\","
                        + "\"failedDetailTargets\":[{"
                        + "\"subjectType\":\"SELF\","
                        + "\"noonProductCode\":\"ZSELF001\"}]}"
        );

        assertFalse(payload.isReadyAt(LocalDateTime.parse("2026-07-28T02:01:59")));
        assertTrue(payload.isReadyAt(LocalDateTime.parse("2026-07-28T02:02:00")));
        assertTrue(payload.isReadyAt(LocalDateTime.parse("2026-07-28T02:02:01")));
    }

    @Test
    void riskHoldDelaysReadyTargetWithoutShorteningLaterTarget() throws Exception {
        CompetitorDetailRetryPayload payload = CompetitorDetailRetryPayload.empty();
        payload.setRetryStates(Arrays.asList(
                state(
                        CompetitorProductDetailTarget.self("ZREADY01"),
                        "2026-07-28T02:02:00"
                ),
                state(
                        CompetitorProductDetailTarget.competitor(
                                88003L,
                                "ZLATER01",
                                null
                        ),
                        "2026-07-28T03:00:00"
                )
        ));

        payload.delayRetryStatesUntil(LocalDateTime.parse("2026-07-28T02:11:00"));

        JsonNode persisted = JSON.readTree(payload.toJson());
        assertEquals("2026-07-28T03:00:00", persisted.path("retryNotBefore").asText());
        assertEquals("2026-07-28T02:11:00", persisted.path("detailRetryStates").path(0)
                .path("retryNotBefore").asText());
        assertEquals("2026-07-28T03:00:00", persisted.path("detailRetryStates").path(1)
                .path("retryNotBefore").asText());
    }

    @Test
    void malformedRetryTimestampFailsClosed() {
        assertThrows(
                CompetitorDetailRetryPayloadException.class,
                () -> CompetitorDetailRetryPayload.fromJson(
                        "{\"retryNotBefore\":\"not-a-date\"}"
                )
        );
    }

    @Test
    void trailingJsonFailsClosed() {
        assertThrows(
                CompetitorDetailRetryPayloadException.class,
                () -> CompetitorDetailRetryPayload.fromJson(
                        "{\"watchProductId\":180123}{}"
                )
        );
    }

    @Test
    void sealedIdentityAndCountersCannotBeRewrittenWithoutDetection()
            throws Exception {
        ObjectNode sealed = (ObjectNode) JSON.readTree(
                CompetitorDetailRetryPayload.fromJson(
                        "{\"watchProductId\":180123,"
                                + "\"triggerMode\":\"SCHEDULED_DETAIL_MONITOR\","
                                + "\"executionMode\":\"detail\","
                                + "\"rankRefresh\":false,"
                                + "\"detailRefresh\":true,"
                                + "\"batchKey\":\"detail:2026-07-28\","
                                + "\"retryAttempt\":1,"
                                + "\"maxRetryAttempts\":4,"
                                + "\"retryNotBefore\":\"2026-07-28T02:02:00\","
                                + "\"rootRunId\":220123,"
                                + "\"retryOfRunId\":220123,"
                                + "\"failedDetailTargets\":[{"
                                + "\"subjectType\":\"SELF\","
                                + "\"noonProductCode\":\"ZSELF001\"}]}"
                ).toJson()
        );

        assertMutationRejected(sealed, "rootRunId", 220999L);
        assertMutationRejected(sealed, "watchProductId", 180999L);
        assertMutationRejected(sealed, "detailTargetTotal", 9L);
        assertTextMutationRejected(
                sealed, "batchKey", "detail:2026-07-29"
        );
    }

    @Test
    void sealedPayloadRejectsPartialLegacySchemaDowngrade() throws Exception {
        ObjectNode sealed = (ObjectNode) JSON.readTree(
                CompetitorDetailRetryPayload.fromJson(
                        "{\"retryAttempt\":1,"
                                + "\"maxRetryAttempts\":4,"
                                + "\"retryNotBefore\":\"2026-07-28T02:02:00\","
                                + "\"failedDetailTargets\":[{"
                                + "\"subjectType\":\"SELF\","
                                + "\"noonProductCode\":\"ZSELF001\"}]}"
                ).toJson()
        );
        sealed.put("detailRetrySchemaVersion", 2);
        sealed.put("detailSucceededCount", 1);

        assertThrows(
                CompetitorDetailRetryPayloadException.class,
                () -> CompetitorDetailRetryPayload.fromJson(sealed.toString())
        );
    }

    private static void assertMutationRejected(
            ObjectNode sealed,
            String field,
            long value
    ) {
        ObjectNode changed = sealed.deepCopy();
        changed.put(field, value);
        assertThrows(
                CompetitorDetailRetryPayloadException.class,
                () -> CompetitorDetailRetryPayload.fromJson(changed.toString())
        );
    }

    private static void assertTextMutationRejected(
            ObjectNode sealed,
            String field,
            String value
    ) {
        ObjectNode changed = sealed.deepCopy();
        changed.put(field, value);
        assertThrows(
                CompetitorDetailRetryPayloadException.class,
                () -> CompetitorDetailRetryPayload.fromJson(changed.toString())
        );
    }

    private static CompetitorDetailRetryState state(
            CompetitorProductDetailTarget target,
            String wake
    ) {
        return new CompetitorDetailRetryState(
                target,
                1,
                LocalDateTime.parse(wake),
                null,
                null
        );
    }
}
