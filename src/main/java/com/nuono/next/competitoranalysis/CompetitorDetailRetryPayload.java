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
        this.retryAttempt = CompetitorDetailRetryJsonSupport.optionalInt(
                this.original,
                "retryAttempt",
                0,
                0,
                CompetitorDetailRetryPolicy.MAX_RETRY_ATTEMPTS
        );
        this.maxRetryAttempts = CompetitorDetailRetryJsonSupport.optionalInt(
                this.original,
                "maxRetryAttempts",
                CompetitorDetailRetryPolicy.MAX_RETRY_ATTEMPTS,
                1,
                CompetitorDetailRetryPolicy.MAX_RETRY_ATTEMPTS
        );
        this.retryNotBefore = CompetitorDetailRetryJsonSupport.optionalDateTime(
                this.original,
                "retryNotBefore"
        );
        this.rootRunId = CompetitorDetailRetryJsonSupport.optionalPositiveLong(
                this.original,
                "rootRunId"
        );
        this.retryOfRunId = CompetitorDetailRetryJsonSupport.optionalPositiveLong(
                this.original,
                "retryOfRunId"
        );
        this.lastErrorCode = CompetitorDetailRetryJsonSupport.optionalText(
                this.original,
                "lastErrorCode"
        );
        this.message = CompetitorDetailRetryJsonSupport.optionalText(
                this.original,
                "message"
        );
        this.retryStates = CompetitorDetailRetryProtocol.read(
                this.original,
                retryAttempt,
                maxRetryAttempts,
                retryNotBefore,
                lastErrorCode,
                message
        );
        if (!retryStates.isEmpty()) {
            refreshRetrySummary();
        }
        this.detailTargetTotal = count("detailTargetTotal");
        this.detailRequestAttemptCount = count("detailRequestAttemptCount");
        this.detailSucceededCount = count("detailSucceededCount");
        this.detailTerminalFailedCount = count("detailTerminalFailedCount");
        this.detailTerminalErrorCode = CompetitorDetailRetryJsonSupport.optionalText(
                this.original,
                "detailTerminalErrorCode"
        );
        this.detailTerminalErrorMessage = CompetitorDetailRetryJsonSupport.optionalText(
                this.original,
                "detailTerminalErrorMessage"
        );
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
        CompetitorDetailRetryPayload copy = new CompetitorDetailRetryPayload(original);
        copy.retryAttempt = retryAttempt;
        copy.maxRetryAttempts = maxRetryAttempts;
        copy.retryNotBefore = retryNotBefore;
        copy.rootRunId = rootRunId;
        copy.retryOfRunId = retryOfRunId;
        copy.retryStates = CompetitorDetailRetryStateJson.unique(retryStates);
        copy.lastErrorCode = lastErrorCode;
        copy.message = message;
        copy.detailTargetTotal = detailTargetTotal;
        copy.detailRequestAttemptCount = detailRequestAttemptCount;
        copy.detailSucceededCount = detailSucceededCount;
        copy.detailTerminalFailedCount = detailTerminalFailedCount;
        copy.detailTerminalErrorCode = detailTerminalErrorCode;
        copy.detailTerminalErrorMessage = detailTerminalErrorMessage;
        return copy;
    }
    String toJson() {
        try {
            return JSON.writeValueAsString(toObjectNode());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize competitor detail retry payload.", exception);
        }
    }

    boolean isReadyAt(LocalDateTime now) {
        for (CompetitorDetailRetryState state : retryStates) {
            if (state.isReadyAt(now)) {
                return true;
            }
        }
        return retryStates.isEmpty();
    }
    int getRetryAttempt() { return retryAttempt; }
    void setRetryAttempt(int value) {
        this.retryAttempt = Math.max(
                0,
                Math.min(value, CompetitorDetailRetryPolicy.MAX_RETRY_ATTEMPTS)
        );
    }
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
            throw new IllegalStateException("Cannot park a targetless detail retry.");
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
        if (retryStates.isEmpty()) {
            CompetitorDetailRetryProtocol.clear(output);
        } else {
            output.put("maxRetryAttempts", maxRetryAttempts);
            CompetitorDetailRetryJsonSupport.putLong(output, "rootRunId", rootRunId);
            CompetitorDetailRetryJsonSupport.putLong(output, "retryOfRunId", retryOfRunId);
            CompetitorDetailRetryJsonSupport.putText(
                    output,
                    "lastErrorCode",
                    lastErrorCode
            );
            CompetitorDetailRetryJsonSupport.putText(output, "message", message);
            CompetitorDetailRetryProtocol.write(output, retryStates, maxRetryAttempts);
        }
        output.put("detailTargetTotal", detailTargetTotal);
        output.put("detailRequestAttemptCount", detailRequestAttemptCount);
        output.put("detailSucceededCount", detailSucceededCount);
        output.put("detailTerminalFailedCount", detailTerminalFailedCount);
        CompetitorDetailRetryJsonSupport.putText(
                output,
                "detailTerminalErrorCode",
                detailTerminalErrorCode
        );
        CompetitorDetailRetryJsonSupport.putText(
                output,
                "detailTerminalErrorMessage",
                detailTerminalErrorMessage
        );
        return output;
    }

    private void refreshRetrySummary() {
        if (retryStates.isEmpty()) {
            retryAttempt = 0;
            retryNotBefore = null;
            return;
        }
        retryAttempt = CompetitorDetailRetryStateJson.maximumAttempt(retryStates);
        retryNotBefore = CompetitorDetailRetryStateJson.earliestWake(retryStates);
        CompetitorDetailRetryState earliest = retryStates.stream()
                .min(java.util.Comparator.comparing(
                        CompetitorDetailRetryState::getRetryNotBefore
                ))
                .orElse(null);
        if (earliest != null) {
            lastErrorCode = earliest.getErrorCode();
            message = earliest.getErrorMessage();
        }
    }

    private int count(String field) {
        return CompetitorDetailRetryJsonSupport.optionalInt(
                original,
                field,
                0,
                0,
                Integer.MAX_VALUE
        );
    }

    private static int boundedMaxRetries(int value) {
        return Math.max(
                1,
                Math.min(value, CompetitorDetailRetryPolicy.MAX_RETRY_ATTEMPTS)
        );
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
