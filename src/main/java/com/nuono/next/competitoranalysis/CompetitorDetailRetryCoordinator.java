package com.nuono.next.competitoranalysis;

import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.system.task.OperationalTask;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
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
        return payload.getRetryAttempt() > 0 || !payload.getRetryStates().isEmpty();
    }

    List<CompetitorProductDetailTarget> retryTargets(OperationalTask task) {
        return payload(task).getReadyTargetsAt(LocalDateTime.now(clock));
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
        LocalDateTime failedAt = LocalDateTime.now(clock);
        LocalDateTime holdUntil = riskHold == null ? null : riskHold.getBlockedUntil();
        CompetitorDetailRetryAttemptPlan attemptPlan =
                CompetitorDetailRetryAttemptPlan.create(
                        current,
                        result,
                        errorCode,
                        errorMessage,
                        policy,
                        failedAt,
                        holdUntil
                );
        CompetitorDetailRetryPayload next;
        if (attemptPlan.hasPendingStates()) {
            next = current.copy();
            next.setRetryStates(attemptPlan.getPendingStates());
            next.setRootRunId(firstNonNull(
                    next.getRootRunId(),
                    next.getRetryOfRunId(),
                    runId
            ));
            next.setRetryOfRunId(runId);
        } else if (attemptPlan.hasRetryableWithoutTarget()) {
            Optional<CompetitorDetailRetryPayload> planned = policy.planNextRetry(
                    current,
                    runId,
                    List.of(),
                    attemptPlan.getFallbackErrorCode(),
                    attemptPlan.getFallbackErrorMessage(),
                    failedAt,
                    holdUntil
            );
            if (planned.isEmpty()) {
                return false;
            }
            next = planned.get();
        } else {
            return false;
        }
        int succeededThisAttempt = result == null ? 0 : result.getSucceededCount();
        int retryTargetCount = attemptPlan.getPendingStates().size();
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
                current.getDetailTerminalFailedCount()
                        + attemptPlan.getTerminalFailureCount()
        );
        next.setDetailTerminalErrorCode(firstNonBlank(
                current.getDetailTerminalErrorCode(),
                attemptPlan.getTerminalErrorCode()
        ));
        next.setDetailTerminalErrorMessage(firstNonBlank(
                current.getDetailTerminalErrorMessage(),
                attemptPlan.getTerminalErrorMessage()
        ));
        String message = retryMessage(next);
        if (!taskFactory.requeueDetailRetry(
                task.getId(),
                runId,
                next.toJson(),
                firstNonBlank(
                        riskHold == null ? null : errorCode,
                        next.getLastErrorCode(),
                        attemptPlan.getFallbackErrorCode(),
                        RETRY_WAITING
                ),
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
        if (notBefore == null) {
            notBefore = now.plusMinutes(2);
        }
        waiting.delayRetryStatesUntil(notBefore);
        notBefore = waiting.getRetryNotBefore();
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

    private Long firstNonNull(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
