package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Typed retry metadata embedded in an existing competitor refresh task payload.
 *
 * <p>The original JSON document is retained so retry scheduling does not discard
 * watch-product, execution-mode, or batch checkpoint fields written by older code.</p>
 */
final class CompetitorDetailRetryPayload {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectNode original;
    private int retryAttempt;
    private int maxRetryAttempts;
    private LocalDateTime retryNotBefore;
    private Long rootRunId;
    private Long retryOfRunId;
    private List<CompetitorProductDetailTarget> failedDetailTargets;
    private String lastErrorCode;
    private String message;
    private int detailTargetTotal;
    private int detailRequestAttemptCount;
    private int detailSucceededCount;
    private int detailTerminalFailedCount;
    private String detailTerminalErrorCode;
    private String detailTerminalErrorMessage;

    private CompetitorDetailRetryPayload(ObjectNode original) {
        this.original = original == null ? JSON.createObjectNode() : original.deepCopy();
        this.retryAttempt = nonNegativeInt(this.original.get("retryAttempt"), 0);
        this.maxRetryAttempts = boundedMaxRetries(
                nonNegativeInt(
                        this.original.get("maxRetryAttempts"),
                        CompetitorDetailRetryPolicy.MAX_RETRY_ATTEMPTS
                )
        );
        this.retryNotBefore = localDateTime(this.original.get("retryNotBefore"));
        this.rootRunId = nullableLong(this.original.get("rootRunId"));
        this.retryOfRunId = nullableLong(this.original.get("retryOfRunId"));
        this.failedDetailTargets = targets(this.original.get("failedDetailTargets"));
        this.lastErrorCode = text(this.original.get("lastErrorCode"));
        this.message = text(this.original.get("message"));
        this.detailTargetTotal = nonNegativeInt(this.original.get("detailTargetTotal"), 0);
        this.detailRequestAttemptCount =
                nonNegativeInt(this.original.get("detailRequestAttemptCount"), 0);
        this.detailSucceededCount = nonNegativeInt(this.original.get("detailSucceededCount"), 0);
        this.detailTerminalFailedCount =
                nonNegativeInt(this.original.get("detailTerminalFailedCount"), 0);
        this.detailTerminalErrorCode = text(this.original.get("detailTerminalErrorCode"));
        this.detailTerminalErrorMessage = text(this.original.get("detailTerminalErrorMessage"));
    }

    static CompetitorDetailRetryPayload empty() {
        return new CompetitorDetailRetryPayload(JSON.createObjectNode());
    }

    static CompetitorDetailRetryPayload fromJson(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return empty();
        }
        try {
            JsonNode parsed = JSON.readTree(payloadJson);
            if (parsed == null || !parsed.isObject()) {
                throw new CompetitorDetailRetryPayloadException(
                        "Competitor detail retry payload must be a JSON object."
                );
            }
            return new CompetitorDetailRetryPayload((ObjectNode) parsed);
        } catch (JsonProcessingException exception) {
            throw new CompetitorDetailRetryPayloadException(
                    "Invalid competitor detail retry payload.",
                    exception
            );
        }
    }

    CompetitorDetailRetryPayload copy() {
        return new CompetitorDetailRetryPayload(toObjectNode());
    }

    String toJson() {
        try {
            return JSON.writeValueAsString(toObjectNode());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize competitor detail retry payload.", exception);
        }
    }

    boolean isReadyAt(LocalDateTime now) {
        return retryNotBefore == null || (now != null && !now.isBefore(retryNotBefore));
    }

    int getRetryAttempt() { return retryAttempt; }
    void setRetryAttempt(int value) { this.retryAttempt = Math.max(0, value); }
    int getMaxRetryAttempts() { return maxRetryAttempts; }
    void setMaxRetryAttempts(int value) { this.maxRetryAttempts = boundedMaxRetries(value); }
    LocalDateTime getRetryNotBefore() { return retryNotBefore; }
    void setRetryNotBefore(LocalDateTime value) { this.retryNotBefore = value; }
    Long getRootRunId() { return rootRunId; }
    void setRootRunId(Long value) { this.rootRunId = value; }
    Long getRetryOfRunId() { return retryOfRunId; }
    void setRetryOfRunId(Long value) { this.retryOfRunId = value; }
    List<CompetitorProductDetailTarget> getFailedDetailTargets() {
        return Collections.unmodifiableList(failedDetailTargets);
    }
    void setFailedDetailTargets(List<CompetitorProductDetailTarget> value) {
        this.failedDetailTargets = safeTargets(value);
    }
    String getLastErrorCode() { return lastErrorCode; }
    void setLastErrorCode(String value) { this.lastErrorCode = normalize(value); }
    String getMessage() { return message; }
    void setMessage(String value) { this.message = normalize(value); }
    int getDetailTargetTotal() { return detailTargetTotal; }
    void setDetailTargetTotal(int value) { this.detailTargetTotal = Math.max(0, value); }
    int getDetailRequestAttemptCount() { return detailRequestAttemptCount; }
    void setDetailRequestAttemptCount(int value) {
        this.detailRequestAttemptCount = Math.max(0, value);
    }
    int getDetailSucceededCount() { return detailSucceededCount; }
    void setDetailSucceededCount(int value) { this.detailSucceededCount = Math.max(0, value); }
    int getDetailTerminalFailedCount() { return detailTerminalFailedCount; }
    void setDetailTerminalFailedCount(int value) {
        this.detailTerminalFailedCount = Math.max(0, value);
    }
    String getDetailTerminalErrorCode() { return detailTerminalErrorCode; }
    void setDetailTerminalErrorCode(String value) {
        this.detailTerminalErrorCode = normalize(value);
    }
    String getDetailTerminalErrorMessage() { return detailTerminalErrorMessage; }
    void setDetailTerminalErrorMessage(String value) {
        this.detailTerminalErrorMessage = normalize(value);
    }

    private ObjectNode toObjectNode() {
        ObjectNode output = original.deepCopy();
        output.put("retryAttempt", retryAttempt);
        output.put("maxRetryAttempts", maxRetryAttempts);
        putDateTime(output, "retryNotBefore", retryNotBefore);
        putLong(output, "rootRunId", rootRunId);
        putLong(output, "retryOfRunId", retryOfRunId);
        ArrayNode targetArray = output.putArray("failedDetailTargets");
        for (CompetitorProductDetailTarget target : failedDetailTargets) {
            if (target != null) {
                targetArray.add(targetNode(target));
            }
        }
        putText(output, "lastErrorCode", lastErrorCode);
        putText(output, "message", message);
        output.put("detailTargetTotal", detailTargetTotal);
        output.put("detailRequestAttemptCount", detailRequestAttemptCount);
        output.put("detailSucceededCount", detailSucceededCount);
        output.put("detailTerminalFailedCount", detailTerminalFailedCount);
        putText(output, "detailTerminalErrorCode", detailTerminalErrorCode);
        putText(output, "detailTerminalErrorMessage", detailTerminalErrorMessage);
        return output;
    }

    private static List<CompetitorProductDetailTarget> targets(JsonNode value) {
        if (value == null || value.isNull()) {
            return new ArrayList<>();
        }
        if (!value.isArray()) {
            throw new CompetitorDetailRetryPayloadException(
                    "failedDetailTargets must be a JSON array."
            );
        }
        List<CompetitorProductDetailTarget> targets = new ArrayList<>();
        for (JsonNode item : value) {
            if (item == null || !item.isObject()) {
                throw new CompetitorDetailRetryPayloadException("Invalid failed detail target.");
            }
            CompetitorProductDetailTarget target = new CompetitorProductDetailTarget();
            target.setSubjectType(text(item.get("subjectType")));
            target.setCompetitorProductId(nullableLong(item.get("competitorProductId")));
            target.setNoonProductCode(text(item.get("noonProductCode")));
            target.setCanonicalUrl(text(item.get("canonicalUrl")));
            targets.add(target);
        }
        return safeTargets(targets);
    }

    private static ObjectNode targetNode(CompetitorProductDetailTarget target) {
        ObjectNode value = JSON.createObjectNode();
        putText(value, "subjectType", target.getSubjectType());
        putLong(value, "competitorProductId", target.getCompetitorProductId());
        putText(value, "noonProductCode", target.getNoonProductCode());
        putText(value, "canonicalUrl", target.getCanonicalUrl());
        return value;
    }

    private static List<CompetitorProductDetailTarget> safeTargets(
            List<CompetitorProductDetailTarget> value
    ) {
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<CompetitorProductDetailTarget> unique = new LinkedHashSet<>();
        for (CompetitorProductDetailTarget target : value) {
            if (target != null) {
                unique.add(target);
            }
        }
        return new ArrayList<>(unique);
    }

    private static int nonNegativeInt(JsonNode value, int fallback) {
        return value == null || value.isNull() ? fallback : Math.max(0, value.asInt(fallback));
    }

    private static int boundedMaxRetries(int value) {
        return Math.max(0, Math.min(value, CompetitorDetailRetryPolicy.MAX_RETRY_ATTEMPTS));
    }

    private static Long nullableLong(JsonNode value) {
        return value == null || value.isNull() ? null : value.asLong();
    }

    private static LocalDateTime localDateTime(JsonNode value) {
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException exception) {
            throw new CompetitorDetailRetryPayloadException(
                    "Invalid retryNotBefore value.",
                    exception
            );
        }
    }

    private static String text(JsonNode value) {
        return value == null || value.isNull() ? null : normalize(value.asText());
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static void putDateTime(ObjectNode target, String name, LocalDateTime value) {
        if (value == null) {
            target.remove(name);
        } else {
            target.put(name, value.toString());
        }
    }

    private static void putLong(ObjectNode target, String name, Long value) {
        if (value == null) {
            target.remove(name);
        } else {
            target.put(name, value);
        }
    }

    private static void putText(ObjectNode target, String name, String value) {
        if (value == null) {
            target.remove(name);
        } else {
            target.put(name, value);
        }
    }
}
