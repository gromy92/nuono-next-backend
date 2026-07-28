package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.system.task.OperationalTask;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.util.StringUtils;

final class CompetitorRefreshRecoveryPayload {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> STALE_IDENTITY_FIELDS =
            List.of("taskId", "runId", "naturalKey");

    private CompetitorRefreshRecoveryPayload() {
    }

    static String fresh(
            Long watchProductId,
            int keywordTotal,
            CompetitorRefreshExecutionMode mode,
            String batchKey
    ) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("keywordTotal", Math.max(0, keywordTotal));
        applyIdentity(payload, watchProductId, mode, batchKey);
        return write(payload);
    }

    static String replacement(
            OperationalTask staleTask,
            Long watchProductId,
            int keywordTotal,
            CompetitorRefreshExecutionMode mode,
            String batchKey
    ) {
        ObjectNode payload = object(staleTask == null ? null : staleTask.getPayloadJson());
        JsonNode existingKeywordTotal = payload.get("keywordTotal");
        if (existingKeywordTotal == null
                || !existingKeywordTotal.isIntegralNumber()
                || existingKeywordTotal.asInt() < 0) {
            payload.put("keywordTotal", Math.max(0, keywordTotal));
        }
        payload.remove(STALE_IDENTITY_FIELDS);
        applyIdentity(payload, watchProductId, mode, batchKey);
        return write(payload);
    }

    static String batchKey(OperationalTask task) {
        ObjectNode payload = objectOrNull(task == null ? null : task.getPayloadJson());
        JsonNode value = payload == null ? null : payload.get("batchKey");
        return value != null && value.isTextual() && StringUtils.hasText(value.asText())
                ? value.asText().trim()
                : null;
    }

    static boolean isReady(OperationalTask task, LocalDateTime now) {
        String payloadJson = task == null ? null : task.getPayloadJson();
        if (!StringUtils.hasText(payloadJson)) {
            return true;
        }
        ObjectNode payload = objectOrNull(payloadJson);
        if (payload == null) {
            return false;
        }
        try {
            JsonNode states = payload.get("detailRetryStates");
            if (states != null && !states.isNull()) {
                if (!states.isArray()) {
                    return false;
                }
                if (!states.isEmpty()) {
                    for (JsonNode state : states) {
                        if (state != null
                                && state.isObject()
                                && readyAt(state.get("retryNotBefore"), now)) {
                            return true;
                        }
                    }
                    return false;
                }
            }
            return readyAt(payload.get("retryNotBefore"), now);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static void applyIdentity(
            ObjectNode payload,
            Long watchProductId,
            CompetitorRefreshExecutionMode mode,
            String batchKey
    ) {
        CompetitorRefreshExecutionMode safeMode =
                mode == null ? CompetitorRefreshExecutionMode.FULL_MANUAL : mode;
        if (watchProductId == null) {
            payload.remove("watchProductId");
        } else {
            payload.put("watchProductId", watchProductId);
        }
        payload.put("triggerMode", safeMode.triggerMode());
        payload.put("executionMode", safeMode.taskKey());
        payload.put("rankRefresh", safeMode.runsRank());
        payload.put("detailRefresh", safeMode.runsDetail());
        if (StringUtils.hasText(batchKey)) {
            payload.put("batchKey", batchKey.trim());
        } else {
            payload.remove("batchKey");
        }
    }

    private static boolean readyAt(JsonNode value, LocalDateTime now) {
        if (value == null || value.isNull() || !StringUtils.hasText(value.asText())) {
            return true;
        }
        LocalDateTime notBefore = LocalDateTime.parse(value.asText().trim());
        return now != null && !now.isBefore(notBefore);
    }

    private static ObjectNode object(String payloadJson) {
        ObjectNode value = objectOrNull(payloadJson);
        return value == null ? JSON.createObjectNode() : value;
    }

    private static ObjectNode objectOrNull(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return JSON.createObjectNode();
        }
        try {
            JsonNode value = JSON.readTree(payloadJson);
            return value != null && value.isObject() ? ((ObjectNode) value).deepCopy() : null;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static String write(ObjectNode payload) {
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize competitor refresh payload.", exception);
        }
    }
}
