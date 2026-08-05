package com.nuono.next.datapull.runtime;

import java.time.Duration;
import java.util.Objects;

/**
 * Typed, secret-free result returned by a provider Adapter.
 *
 * <p>Only a verified risk-control result can widen sharing beyond the exact
 * operation scope. A Retry-After value is accepted only for retryable provider
 * classifications.</p>
 */
public final class ProviderOutcome<T> {

    private final ProviderOutcomeType type;
    private final T value;
    private final Duration retryAfter;
    private final RiskShareLevel shareLevel;
    private final String sanitizedCode;

    private ProviderOutcome(
            ProviderOutcomeType type,
            T value,
            Duration retryAfter,
            RiskShareLevel shareLevel,
            String sanitizedCode
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.value = value;
        this.retryAfter = validateRetryAfter(type, retryAfter);
        this.shareLevel = validateShareLevel(type, shareLevel);
        this.sanitizedCode = SanitizedCode.require(sanitizedCode);
        if (type != ProviderOutcomeType.SUCCESS && value != null) {
            throw new IllegalArgumentException("only SUCCESS may carry a value");
        }
    }

    public static <T> ProviderOutcome<T> success(T value) {
        return new ProviderOutcome<>(
                ProviderOutcomeType.SUCCESS,
                value,
                null,
                RiskShareLevel.EXACT,
                "SUCCESS"
        );
    }

    public static <T> ProviderOutcome<T> notFound(String sanitizedCode) {
        return failure(ProviderOutcomeType.NOT_FOUND, sanitizedCode, null, RiskShareLevel.EXACT);
    }

    public static <T> ProviderOutcome<T> riskControl(String sanitizedCode) {
        return riskControl(sanitizedCode, null, RiskShareLevel.EXACT);
    }

    public static <T> ProviderOutcome<T> riskControl(
            String sanitizedCode,
            Duration retryAfter,
            RiskShareLevel shareLevel
    ) {
        return failure(ProviderOutcomeType.RISK_CONTROL, sanitizedCode, retryAfter, shareLevel);
    }

    public static <T> ProviderOutcome<T> transientFailure(String sanitizedCode) {
        return transientFailure(sanitizedCode, null);
    }

    public static <T> ProviderOutcome<T> transientFailure(String sanitizedCode, Duration retryAfter) {
        return failure(ProviderOutcomeType.TRANSIENT, sanitizedCode, retryAfter, RiskShareLevel.EXACT);
    }

    public static <T> ProviderOutcome<T> authRequired(String sanitizedCode) {
        return failure(ProviderOutcomeType.AUTH_REQUIRED, sanitizedCode, null, RiskShareLevel.EXACT);
    }

    public static <T> ProviderOutcome<T> contractError(String sanitizedCode) {
        return failure(ProviderOutcomeType.CONTRACT_ERROR, sanitizedCode, null, RiskShareLevel.EXACT);
    }

    public static <T> ProviderOutcome<T> unknownOutcome(String sanitizedCode) {
        return failure(ProviderOutcomeType.UNKNOWN_OUTCOME, sanitizedCode, null, RiskShareLevel.EXACT);
    }

    private static <T> ProviderOutcome<T> failure(
            ProviderOutcomeType type,
            String sanitizedCode,
            Duration retryAfter,
            RiskShareLevel shareLevel
    ) {
        return new ProviderOutcome<>(type, null, retryAfter, shareLevel, sanitizedCode);
    }

    private static Duration validateRetryAfter(ProviderOutcomeType type, Duration retryAfter) {
        if (retryAfter == null) {
            return null;
        }
        if (retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must not be negative");
        }
        if (type != ProviderOutcomeType.RISK_CONTROL && type != ProviderOutcomeType.TRANSIENT) {
            throw new IllegalArgumentException("retryAfter is only valid for retryable provider outcomes");
        }
        return retryAfter;
    }

    private static RiskShareLevel validateShareLevel(
            ProviderOutcomeType type,
            RiskShareLevel shareLevel
    ) {
        RiskShareLevel nonNull = Objects.requireNonNull(shareLevel, "shareLevel");
        if (type != ProviderOutcomeType.RISK_CONTROL && nonNull != RiskShareLevel.EXACT) {
            throw new IllegalArgumentException("only RISK_CONTROL may widen the risk share level");
        }
        return nonNull;
    }

    public ProviderOutcomeType getType() {
        return type;
    }

    public T getValue() {
        return value;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    public RiskShareLevel getShareLevel() {
        return shareLevel;
    }

    public String getSanitizedCode() {
        return sanitizedCode;
    }
}
