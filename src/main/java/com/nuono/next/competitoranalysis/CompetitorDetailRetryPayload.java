package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
    private List<CompetitorDetailRetryState> retryStates;
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
        this.retryNotBefore = CompetitorDetailRetryStateJson.dateTime(
                this.original.get("retryNotBefore")
        );
        this.rootRunId = nullableLong(this.original.get("rootRunId"));
        this.retryOfRunId = nullableLong(this.original.get("retryOfRunId"));
        this.lastErrorCode = text(this.original.get("lastErrorCode"));
        this.message = text(this.original.get("message"));
        this.retryStates = CompetitorDetailRetryStateJson.read(
                this.original,
                retryAttempt,
                retryNotBefore,
                lastErrorCode,
                message
        );
        if (!retryStates.isEmpty()) {
            refreshRetrySummary();
        }
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
        if (!retryStates.isEmpty()) {
            for (CompetitorDetailRetryState state : retryStates) {
                if (state.isReadyAt(now)) {
                    return true;
                }
            }
            return false;
        }
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
        List<CompetitorProductDetailTarget> targets = new ArrayList<>();
        for (CompetitorDetailRetryState state : retryStates) {
            targets.add(state.getTarget());
        }
        return Collections.unmodifiableList(targets);
    }
    void setFailedDetailTargets(List<CompetitorProductDetailTarget> value) {
        List<CompetitorDetailRetryState> states = new ArrayList<>();
        if (value != null) {
            for (CompetitorProductDetailTarget target : value) {
                if (target != null) {
                    states.add(new CompetitorDetailRetryState(
                            target,
                            retryAttempt,
                            retryNotBefore,
                            lastErrorCode,
                            message
                    ));
                }
            }
        }
        setRetryStates(states);
    }
    List<CompetitorDetailRetryState> getRetryStates() {
        return Collections.unmodifiableList(new ArrayList<>(retryStates));
    }
    void setRetryStates(List<CompetitorDetailRetryState> value) {
        retryStates = CompetitorDetailRetryStateJson.unique(value);
        refreshRetrySummary();
    }
    List<CompetitorProductDetailTarget> getReadyTargetsAt(LocalDateTime now) {
        List<CompetitorProductDetailTarget> targets = new ArrayList<>();
        for (CompetitorDetailRetryState state : retryStates) {
            if (state.isReadyAt(now)) {
                targets.add(state.getTarget());
            }
        }
        return Collections.unmodifiableList(targets);
    }
    void delayRetryStatesUntil(LocalDateTime holdUntil) {
        if (retryStates.isEmpty()) {
            if (holdUntil != null
                    && (retryNotBefore == null || holdUntil.isAfter(retryNotBefore))) {
                retryNotBefore = holdUntil;
            }
            return;
        }
        List<CompetitorDetailRetryState> delayed = new ArrayList<>();
        for (CompetitorDetailRetryState state : retryStates) {
            delayed.add(state.delayedUntil(holdUntil));
        }
        setRetryStates(delayed);
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
        CompetitorDetailRetryStateJson.write(output, retryStates);
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

    private void refreshRetrySummary() {
        if (retryStates.isEmpty()) {
            retryAttempt = 0;
            retryNotBefore = null;
            return;
        }
        CompetitorDetailRetryState earliest = null;
        retryAttempt = 0;
        for (CompetitorDetailRetryState state : retryStates) {
            retryAttempt = Math.max(retryAttempt, state.getRetryAttempt());
            if (earliest == null
                    || earlier(state.getRetryNotBefore(), earliest.getRetryNotBefore())) {
                earliest = state;
            }
        }
        retryNotBefore = earliest == null ? null : earliest.getRetryNotBefore();
        if (earliest != null) {
            lastErrorCode = earliest.getErrorCode();
            message = earliest.getErrorMessage();
        }
    }

    private boolean earlier(LocalDateTime candidate, LocalDateTime current) {
        return candidate == null || (current != null && candidate.isBefore(current));
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
