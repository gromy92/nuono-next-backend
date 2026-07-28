package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
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
        assertEquals(0, persisted.path("retryAttempt").asInt());
        assertEquals(4, persisted.path("maxRetryAttempts").asInt());
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
                + "},{"
                + "\"subjectType\":\"competitor\","
                + "\"competitorProductId\":190999,"
                + "\"noonProductCode\":\"zfail001\","
                + "\"canonicalUrl\":\"https://www.noon.com/duplicate/ZFAIL001\""
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
        CompetitorDetailRetryPayload payload = CompetitorDetailRetryPayload.empty();
        payload.setRetryNotBefore(LocalDateTime.parse("2026-07-28T02:02:00"));

        assertFalse(payload.isReadyAt(LocalDateTime.parse("2026-07-28T02:01:59")));
        assertTrue(payload.isReadyAt(LocalDateTime.parse("2026-07-28T02:02:00")));
        assertTrue(payload.isReadyAt(LocalDateTime.parse("2026-07-28T02:02:01")));
    }

    @Test
    void riskHoldDelaysReadyTargetWithoutShorteningLaterTarget() throws Exception {
        CompetitorDetailRetryPayload payload = CompetitorDetailRetryPayload.fromJson(
                "{\"detailRetryStates\":[{"
                        + "\"subjectType\":\"SELF\","
                        + "\"noonProductCode\":\"ZREADY\","
                        + "\"retryAttempt\":1,"
                        + "\"retryNotBefore\":\"2026-07-28T02:02:00\""
                        + "},{"
                        + "\"subjectType\":\"COMPETITOR\","
                        + "\"competitorProductId\":88003,"
                        + "\"noonProductCode\":\"ZLATER\","
                        + "\"retryAttempt\":1,"
                        + "\"retryNotBefore\":\"2026-07-28T03:00:00\""
                        + "}]}"
        );

        payload.delayRetryStatesUntil(LocalDateTime.parse("2026-07-28T02:11:00"));

        JsonNode persisted = JSON.readTree(payload.toJson());
        assertEquals("2026-07-28T02:11:00", persisted.path("retryNotBefore").asText());
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
}
