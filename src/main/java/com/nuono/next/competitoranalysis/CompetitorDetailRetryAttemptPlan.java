package com.nuono.next.competitoranalysis;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.util.StringUtils;

final class CompetitorDetailRetryAttemptPlan {
    private final List<CompetitorDetailRetryState> pendingStates;
    private final boolean retryableWithoutTarget;
    private final String fallbackErrorCode;
    private final String fallbackErrorMessage;
    private final int terminalFailureCount;
    private final String terminalErrorCode;
    private final String terminalErrorMessage;

    private CompetitorDetailRetryAttemptPlan(
            List<CompetitorDetailRetryState> pendingStates,
            boolean retryableWithoutTarget,
            String fallbackErrorCode,
            String fallbackErrorMessage,
            int terminalFailureCount,
            String terminalErrorCode,
            String terminalErrorMessage
    ) {
        this.pendingStates = pendingStates;
        this.retryableWithoutTarget = retryableWithoutTarget;
        this.fallbackErrorCode = fallbackErrorCode;
        this.fallbackErrorMessage = fallbackErrorMessage;
        this.terminalFailureCount = terminalFailureCount;
        this.terminalErrorCode = terminalErrorCode;
        this.terminalErrorMessage = terminalErrorMessage;
    }

    static CompetitorDetailRetryAttemptPlan create(
            CompetitorDetailRetryPayload current,
            CompetitorProductDetailRefreshResult result,
            String preferredErrorCode,
            String preferredErrorMessage,
            CompetitorDetailRetryPolicy policy,
            LocalDateTime failedAt,
            LocalDateTime sharedRiskHoldUntil
    ) {
        Map<String, CompetitorDetailRetryState> pending = new LinkedHashMap<>();
        for (CompetitorDetailRetryState state : current.getRetryStates()) {
            pending.put(state.identityKey(), state);
        }
        if (result != null) {
            for (CompetitorProductDetailTarget target : result.getSucceededTargets()) {
                pending.remove(target.identityKey());
            }
        }

        boolean targetlessRetry = false;
        int terminalCount = 0;
        String terminalCode = null;
        String terminalMessage = null;
        String fallbackCode = policy.isRetryable(preferredErrorCode)
                ? normalize(preferredErrorCode)
                : null;
        String fallbackMessage = fallbackCode == null ? null : normalize(preferredErrorMessage);
        Set<String> processedTargets = new LinkedHashSet<>();
        for (CompetitorProductDetailFailure failure : failures(result)) {
            if (failure == null) {
                continue;
            }
            CompetitorProductDetailTarget target = failure.getTarget();
            if (target == null) {
                if (policy.isRetryable(failure.getErrorCode())) {
                    targetlessRetry = true;
                    fallbackCode = firstNonBlank(fallbackCode, failure.getErrorCode());
                    fallbackMessage = firstNonBlank(
                            fallbackMessage,
                            failure.getErrorMessage()
                    );
                } else {
                    terminalCount++;
                    terminalCode = firstNonBlank(terminalCode, failure.getErrorCode());
                    terminalMessage = firstNonBlank(
                            terminalMessage,
                            failure.getErrorMessage()
                    );
                }
                continue;
            }
            if (!processedTargets.add(target.identityKey())) {
                continue;
            }
            CompetitorDetailRetryState previous = pending.remove(target.identityKey());
            if (!policy.isRetryable(failure.getErrorCode())) {
                terminalCount++;
                terminalCode = firstNonBlank(terminalCode, failure.getErrorCode());
                terminalMessage = firstNonBlank(
                        terminalMessage,
                        failure.getErrorMessage()
                );
                continue;
            }
            fallbackCode = firstNonBlank(fallbackCode, failure.getErrorCode());
            fallbackMessage = firstNonBlank(fallbackMessage, failure.getErrorMessage());
            Optional<CompetitorDetailRetryState> planned = policy.planTargetRetry(
                    previous,
                    target,
                    failure.getErrorCode(),
                    failure.getErrorMessage(),
                    failure.isDeferred(),
                    failedAt,
                    sharedRiskHoldUntil,
                    current.getMaxRetryAttempts()
            );
            if (planned.isPresent()) {
                CompetitorDetailRetryState next = planned.get();
                pending.put(next.identityKey(), next);
            } else {
                terminalCount++;
                terminalCode = firstNonBlank(terminalCode, failure.getErrorCode());
                terminalMessage = firstNonBlank(
                        terminalMessage,
                        failure.getErrorMessage()
                );
            }
        }
        List<CompetitorDetailRetryState> pendingStates =
                delayForSharedRiskHold(pending.values(), sharedRiskHoldUntil);
        return new CompetitorDetailRetryAttemptPlan(
                pendingStates,
                targetlessRetry,
                firstNonBlank(fallbackCode, "DETAIL_REFRESH_FAILED"),
                firstNonBlank(fallbackMessage, preferredErrorMessage, "竞品详情抓取失败。"),
                terminalCount,
                terminalCode,
                terminalMessage
        );
    }

    List<CompetitorDetailRetryState> getPendingStates() {
        return pendingStates;
    }

    boolean hasPendingStates() {
        return !pendingStates.isEmpty();
    }

    boolean hasRetryableWithoutTarget() {
        return retryableWithoutTarget;
    }

    String getFallbackErrorCode() {
        return fallbackErrorCode;
    }

    String getFallbackErrorMessage() {
        return fallbackErrorMessage;
    }

    int getTerminalFailureCount() {
        return terminalFailureCount;
    }

    String getTerminalErrorCode() {
        return terminalErrorCode;
    }

    String getTerminalErrorMessage() {
        return terminalErrorMessage;
    }

    private static List<CompetitorProductDetailFailure> failures(
            CompetitorProductDetailRefreshResult result
    ) {
        List<CompetitorProductDetailFailure> failures = new ArrayList<>();
        if (result != null) {
            failures.addAll(result.getFailures());
            failures.addAll(result.getDeferredFailures());
        }
        return failures;
    }

    private static List<CompetitorDetailRetryState> delayForSharedRiskHold(
            Iterable<CompetitorDetailRetryState> states,
            LocalDateTime sharedRiskHoldUntil
    ) {
        List<CompetitorDetailRetryState> delayed = new ArrayList<>();
        for (CompetitorDetailRetryState state : states) {
            delayed.add(state.delayedUntil(sharedRiskHoldUntil));
        }
        return delayed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
