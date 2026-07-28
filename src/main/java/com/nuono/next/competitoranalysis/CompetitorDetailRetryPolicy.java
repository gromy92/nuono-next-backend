package com.nuono.next.competitoranalysis;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.util.StringUtils;

final class CompetitorDetailRetryPolicy {
    static final int MAX_RETRY_ATTEMPTS = 4;
    private static final String INVALID_NOON_PRODUCT_CODE = "INVALID_NOON_PRODUCT_CODE";
    private static final String DETAIL_TARGET_STALE = "DETAIL_TARGET_STALE";
    private static final String PUBLIC_DETAIL_NOT_FOUND = "PUBLIC_DETAIL_NOT_FOUND";

    private static final Set<String> RISK_ERROR_CODES = Set.of(
            "RATE_LIMITED",
            "BLOCKED_BY_RISK_CONTROL",
            "CAPTCHA_REQUIRED"
    );

    Optional<CompetitorDetailRetryPayload> planNextRetry(
            CompetitorDetailRetryPayload current,
            Long failedRunId,
            List<CompetitorProductDetailTarget> failedDetailTargets,
            String errorCode,
            String errorMessage,
            LocalDateTime failedAt,
            LocalDateTime sharedRiskHoldUntil
    ) {
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt is required.");
        }
        if (!isRetryable(errorCode)) {
            return Optional.empty();
        }
        CompetitorDetailRetryPayload next =
                current == null ? CompetitorDetailRetryPayload.empty() : current.copy();
        int maximum = Math.min(MAX_RETRY_ATTEMPTS, Math.max(0, next.getMaxRetryAttempts()));
        int nextAttempt = next.getRetryAttempt() + 1;
        if (nextAttempt > maximum) {
            return Optional.empty();
        }

        LocalDateTime retryNotBefore =
                failedAt.plus(backoffForFailure(errorCode, nextAttempt));
        if (isRiskFailure(errorCode)
                && sharedRiskHoldUntil != null
                && sharedRiskHoldUntil.isAfter(retryNotBefore)) {
            retryNotBefore = sharedRiskHoldUntil;
        }

        next.setRetryAttempt(nextAttempt);
        next.setMaxRetryAttempts(maximum);
        next.setRetryNotBefore(retryNotBefore);
        next.setRootRunId(firstNonNull(next.getRootRunId(), next.getRetryOfRunId(), failedRunId));
        next.setRetryOfRunId(failedRunId);
        next.setFailedDetailTargets(failedDetailTargets);
        next.setLastErrorCode(errorCode);
        next.setMessage(errorMessage);
        return Optional.of(next);
    }

    Duration backoffForAttempt(int retryAttempt) {
        if (retryAttempt < 1 || retryAttempt > MAX_RETRY_ATTEMPTS) {
            throw new IllegalArgumentException("retryAttempt must be between 1 and 4.");
        }
        return Duration.ofMinutes(2L << (retryAttempt - 1));
    }

    Duration backoffForFailure(String errorCode, int retryAttempt) {
        if (!hasErrorCode(errorCode, PUBLIC_DETAIL_NOT_FOUND)) {
            return backoffForAttempt(retryAttempt);
        }
        switch (retryAttempt) {
            case 1:
                return Duration.ofMinutes(30);
            case 2:
                return Duration.ofHours(6);
            case 3:
            case 4:
                return Duration.ofHours(24);
            default:
                throw new IllegalArgumentException("retryAttempt must be between 1 and 4.");
        }
    }

    boolean isRiskFailure(String errorCode) {
        if (!StringUtils.hasText(errorCode)) {
            return false;
        }
        return RISK_ERROR_CODES.contains(errorCode.trim().toUpperCase(Locale.ROOT));
    }

    boolean isRetryable(String errorCode) {
        return !hasErrorCode(errorCode, INVALID_NOON_PRODUCT_CODE)
                && !hasErrorCode(errorCode, DETAIL_TARGET_STALE);
    }

    private boolean hasErrorCode(String actual, String expected) {
        return StringUtils.hasText(actual)
                && expected.equals(actual.trim().toUpperCase(Locale.ROOT));
    }

    private Long firstNonNull(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
