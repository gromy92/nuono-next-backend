package com.nuono.next.competitoranalysis;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.util.StringUtils;

final class CompetitorDetailRetryPolicy {
    static final int MAX_RETRY_ATTEMPTS = 4;
    private static final String INVALID_NOON_PRODUCT_CODE = "INVALID_NOON_PRODUCT_CODE";
    private static final String DETAIL_TARGET_STALE = "DETAIL_TARGET_STALE";
    private static final String LIST_PRODUCT_NOT_FOUND = "LIST_PRODUCT_NOT_FOUND";
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
        if (failedDetailTargets == null || failedDetailTargets.isEmpty()) {
            return Optional.empty();
        }
        List<CompetitorDetailRetryState> plannedStates = new ArrayList<>();
        for (CompetitorProductDetailTarget target : failedDetailTargets) {
            CompetitorDetailRetryState state = findState(next, target);
            if (state == null && next.getRetryAttempt() > 0) {
                state = new CompetitorDetailRetryState(
                        target,
                        next.getRetryAttempt(),
                        next.getRetryNotBefore(),
                        next.getLastErrorCode(),
                        next.getMessage()
                );
            }
            Optional<CompetitorDetailRetryState> planned = planTargetRetry(
                    state,
                    target,
                    errorCode,
                    errorMessage,
                    false,
                    failedAt,
                    sharedRiskHoldUntil,
                    maximum
            );
            planned.ifPresent(plannedStates::add);
        }
        if (plannedStates.isEmpty()) {
            return Optional.empty();
        }

        next.setMaxRetryAttempts(maximum);
        next.setRootRunId(firstNonNull(next.getRootRunId(), next.getRetryOfRunId(), failedRunId));
        next.setRetryOfRunId(failedRunId);
        next.setRetryStates(plannedStates);
        return Optional.of(next);
    }

    Optional<CompetitorDetailRetryState> planTargetRetry(
            CompetitorDetailRetryState current,
            CompetitorProductDetailTarget target,
            String errorCode,
            String errorMessage,
            boolean deferred,
            LocalDateTime failedAt,
            LocalDateTime sharedRiskHoldUntil,
            int maximumAttempts
    ) {
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt is required.");
        }
        if (target == null || !isRetryable(errorCode)) {
            return Optional.empty();
        }
        int maximum = Math.min(MAX_RETRY_ATTEMPTS, Math.max(0, maximumAttempts));
        int priorAttempt = current == null ? 0 : current.getRetryAttempt();
        int nextAttempt = deferred ? priorAttempt : priorAttempt + 1;
        if (!deferred && nextAttempt > maximum) {
            return Optional.empty();
        }
        int delayAttempt = Math.max(
                1,
                Math.min(MAX_RETRY_ATTEMPTS, deferred ? priorAttempt + 1 : nextAttempt)
        );
        LocalDateTime retryNotBefore =
                failedAt.plus(backoffForFailure(errorCode, delayAttempt));
        if (deferred
                && current != null
                && current.getRetryNotBefore() != null
                && current.getRetryNotBefore().isAfter(retryNotBefore)) {
            retryNotBefore = current.getRetryNotBefore();
        }
        if (isRiskFailure(errorCode)
                && sharedRiskHoldUntil != null
                && sharedRiskHoldUntil.isAfter(retryNotBefore)) {
            retryNotBefore = sharedRiskHoldUntil;
        }
        return Optional.of(new CompetitorDetailRetryState(
                target,
                nextAttempt,
                retryNotBefore,
                errorCode,
                errorMessage
        ));
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
                && !hasErrorCode(errorCode, DETAIL_TARGET_STALE)
                && !hasErrorCode(errorCode, LIST_PRODUCT_NOT_FOUND);
    }

    private boolean hasErrorCode(String actual, String expected) {
        return StringUtils.hasText(actual)
                && expected.equals(actual.trim().toUpperCase(Locale.ROOT));
    }

    private CompetitorDetailRetryState findState(
            CompetitorDetailRetryPayload payload,
            CompetitorProductDetailTarget target
    ) {
        if (payload == null || target == null) {
            return null;
        }
        for (CompetitorDetailRetryState state : payload.getRetryStates()) {
            if (target.identityKey().equals(state.identityKey())) {
                return state;
            }
        }
        return null;
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
