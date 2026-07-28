package com.nuono.next.competitoranalysis;

import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.system.task.OperationalTask;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.util.StringUtils;

final class CompetitorDetailRetryCoordinator {
    private static final String RETRY_WAITING = "DETAIL_RETRY_WAITING";

    private final CompetitorRefreshTaskFactory taskFactory;
    private final Clock clock;
    private final CompetitorDetailRetryPolicy policy = new CompetitorDetailRetryPolicy();

    CompetitorDetailRetryCoordinator(CompetitorRefreshTaskFactory taskFactory, Clock clock) {
        this.taskFactory = taskFactory;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    boolean isReady(OperationalTask task) {
        try {
            return payload(task).isReadyAt(LocalDateTime.now(clock));
        } catch (CompetitorDetailRetryPayloadException exception) {
            taskFactory.failInvalidDetailRetryPayload(task.getId());
            return false;
        }
    }

    boolean isRetry(OperationalTask task) {
        CompetitorDetailRetryPayload payload = payload(task);
        return payload.getRetryAttempt() > 0 || !payload.getFailedDetailTargets().isEmpty();
    }

    List<CompetitorProductDetailTarget> retryTargets(OperationalTask task) {
        return payload(task).getFailedDetailTargets();
    }

    boolean scheduleFailure(
            OperationalTask task,
            Long runId,
            CompetitorProductDetailRefreshResult result,
            String errorCode,
            String errorMessage,
            NoonRiskBackoffHold riskHold
    ) {
        CompetitorDetailRetryPayload current = payload(task);
        RetrySelection retry = retrySelection(result, errorCode, errorMessage);
        if (!retry.retryable) {
            return false;
        }
        Optional<CompetitorDetailRetryPayload> planned = policy.planNextRetry(
                current,
                runId,
                retry.targets,
                retry.errorCode,
                retry.errorMessage,
                LocalDateTime.now(clock),
                riskHold == null ? null : riskHold.getBlockedUntil()
        );
        if (planned.isEmpty()) {
            return false;
        }
        CompetitorDetailRetryPayload next = planned.get();
        int succeededThisAttempt = result == null ? 0 : result.getSucceededCount();
        int retryTargetCount = retry.targets.size();
        int observedTargetTotal = result == null
                ? 0
                : Math.max(
                        result.getAttemptedCount() + result.getDeferredCount(),
                        succeededThisAttempt + retryTargetCount
        );
        next.setDetailTargetTotal(Math.max(current.getDetailTargetTotal(), observedTargetTotal));
        next.setDetailRequestAttemptCount(
                current.getDetailRequestAttemptCount() + (result == null ? 0 : result.getRequestAttemptCount())
        );
        next.setDetailSucceededCount(current.getDetailSucceededCount() + succeededThisAttempt);
        next.setDetailTerminalFailedCount(
                current.getDetailTerminalFailedCount() + retry.terminalFailureCount
        );
        next.setDetailTerminalErrorCode(firstNonBlank(
                current.getDetailTerminalErrorCode(),
                retry.terminalErrorCode
        ));
        next.setDetailTerminalErrorMessage(firstNonBlank(
                current.getDetailTerminalErrorMessage(),
                retry.terminalErrorMessage
        ));
        String message = retryMessage(next);
        if (!taskFactory.requeueDetailRetry(
                task.getId(),
                runId,
                next.toJson(),
                firstNonBlank(retry.errorCode, RETRY_WAITING),
                message
        )) {
            throw new IllegalStateException("Competitor detail retry transition conflict: " + task.getId());
        }
        return true;
    }

    void parkForRiskHold(
            OperationalTask task,
            Long runId,
            NoonRiskBackoffHold hold,
            String message
    ) {
        CompetitorDetailRetryPayload waiting = payload(task).copy();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime notBefore = hold == null ? now.plusMinutes(2) : hold.getBlockedUntil();
        if (waiting.getRetryNotBefore() != null
                && (notBefore == null || waiting.getRetryNotBefore().isAfter(notBefore))) {
            notBefore = waiting.getRetryNotBefore();
        }
        waiting.setRetryNotBefore(notBefore);
        waiting.setRootRunId(waiting.getRootRunId() == null ? runId : waiting.getRootRunId());
        waiting.setRetryOfRunId(runId);
        waiting.setLastErrorCode("COMPETITOR_RISK_BACKOFF");
        waiting.setMessage(message);
        String waitingMessage = "竞品详情抓取等待共享风控冷却，计划于 "
                + notBefore
                + " 后继续。";
        if (!taskFactory.requeueDetailRetry(
                task.getId(),
                runId,
                waiting.toJson(),
                "COMPETITOR_RISK_BACKOFF",
                waitingMessage
        )) {
            throw new IllegalStateException("Competitor detail risk hold transition conflict: " + task.getId());
        }
    }

    void addPriorCounts(
            OperationalTask task,
            CompetitorProductDetailRefreshResult result
    ) {
        if (result == null) {
            return;
        }
        CompetitorDetailRetryPayload payload = payload(task);
        result.addPriorCounts(
                payload.getDetailTargetTotal(),
                payload.getDetailSucceededCount(),
                payload.getDetailRequestAttemptCount()
        );
        result.addPriorTerminalFailures(
                payload.getDetailTerminalFailedCount(),
                payload.getDetailTerminalErrorCode(),
                payload.getDetailTerminalErrorMessage()
        );
    }

    private CompetitorDetailRetryPayload payload(OperationalTask task) {
        return CompetitorDetailRetryPayload.fromJson(task == null ? null : task.getPayloadJson());
    }

    private RetrySelection retrySelection(
            CompetitorProductDetailRefreshResult result,
            String preferredErrorCode,
            String preferredErrorMessage
    ) {
        Map<String, CompetitorProductDetailTarget> targets = new LinkedHashMap<>();
        String selectedCode = StringUtils.hasText(preferredErrorCode)
                && policy.isRetryable(preferredErrorCode)
                ? preferredErrorCode
                : null;
        String selectedMessage = selectedCode == null ? null : preferredErrorMessage;
        boolean retryable = false;
        int terminalFailureCount = 0;
        String terminalErrorCode = null;
        String terminalErrorMessage = null;
        List<CompetitorProductDetailFailure> failures = new ArrayList<>();
        if (result != null) {
            failures.addAll(result.getFailures());
            failures.addAll(result.getDeferredFailures());
        }
        for (CompetitorProductDetailFailure failure : failures) {
            if (failure == null) {
                continue;
            }
            if (!policy.isRetryable(failure.getErrorCode())) {
                terminalFailureCount++;
                terminalErrorCode = firstNonBlank(terminalErrorCode, failure.getErrorCode());
                terminalErrorMessage = firstNonBlank(
                        terminalErrorMessage,
                        failure.getErrorMessage()
                );
                continue;
            }
            retryable = true;
            if (!StringUtils.hasText(selectedCode)) {
                selectedCode = failure.getErrorCode();
                selectedMessage = failure.getErrorMessage();
            }
            CompetitorProductDetailTarget target = failure.getTarget();
            if (target != null) {
                targets.putIfAbsent(target.identityKey(), target);
            }
        }
        return new RetrySelection(
                retryable,
                new ArrayList<>(targets.values()),
                firstNonBlank(selectedCode, "DETAIL_REFRESH_FAILED"),
                firstNonBlank(selectedMessage, preferredErrorMessage, "竞品详情抓取失败。"),
                terminalFailureCount,
                terminalErrorCode,
                terminalErrorMessage
        );
    }

    private String retryMessage(CompetitorDetailRetryPayload payload) {
        return "竞品详情抓取失败，已进入第 "
                + payload.getRetryAttempt()
                + "/"
                + payload.getMaxRetryAttempts()
                + " 次退避重试，计划于 "
                + payload.getRetryNotBefore()
                + " 后重试。";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static final class RetrySelection {
        private final boolean retryable;
        private final List<CompetitorProductDetailTarget> targets;
        private final String errorCode;
        private final String errorMessage;
        private final int terminalFailureCount;
        private final String terminalErrorCode;
        private final String terminalErrorMessage;

        private RetrySelection(
                boolean retryable,
                List<CompetitorProductDetailTarget> targets,
                String errorCode,
                String errorMessage,
                int terminalFailureCount,
                String terminalErrorCode,
                String terminalErrorMessage
        ) {
            this.retryable = retryable;
            this.targets = targets;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.terminalFailureCount = terminalFailureCount;
            this.terminalErrorCode = terminalErrorCode;
            this.terminalErrorMessage = terminalErrorMessage;
        }
    }
}
