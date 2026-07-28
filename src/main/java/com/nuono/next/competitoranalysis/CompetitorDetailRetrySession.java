package com.nuono.next.competitoranalysis;

import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.system.task.OperationalTask;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

final class CompetitorDetailRetrySession {
    private static final String RETRY_WAITING = "DETAIL_RETRY_WAITING";

    private final CompetitorRefreshTaskFactory taskFactory;
    private final OperationalTask task;
    private final Long runId;
    private final Long watchProductId;
    private final Clock clock;
    private final CompetitorDetailRetryPolicy policy = new CompetitorDetailRetryPolicy();
    private CompetitorDetailRetryPayload payload;

    CompetitorDetailRetrySession(
            CompetitorRefreshTaskFactory taskFactory,
            OperationalTask task,
            Long runId,
            Long watchProductId,
            List<CompetitorProductDetailTarget> initialTargets,
            Clock clock
    ) {
        this.taskFactory = taskFactory;
        this.task = task;
        this.runId = runId;
        this.watchProductId = watchProductId;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.payload = CompetitorDetailRetryPayload.fromJson(
                task == null ? null : task.getPayloadJson()
        );
        requireIdentity();
        if (!payload.isInitialized()) {
            initialize(initialTargets);
        } else {
            recoverOrSeal();
        }
    }

    List<CompetitorProductDetailTarget> readyTargets() {
        return payload.getReadyTargetsAt(LocalDateTime.now(clock));
    }

    boolean isComplete() {
        return payload.isInitialized() && payload.getRetryStates().isEmpty();
    }

    void beginRequest(CompetitorProductDetailTarget target) {
        checkpoint(CompetitorDetailRetryRequestLedger.begin(
                payload, target, runId
        ));
    }

    String payloadAfterSuccess(CompetitorProductDetailTarget target) {
        if (!requirePending(target).isRequestInFlight()) {
            throw new CompetitorDetailRetryPayloadException(
                    "Detail request success has no durable reservation."
            );
        }
        CompetitorDetailRetryPayload next = payload.copy();
        next.removeRetryState(target);
        next.setDetailSucceededCount(next.getDetailSucceededCount() + 1);
        return next.toJson();
    }

    void successCommitted(String payloadJson) {
        accept(payloadJson);
    }

    void recordFailure(
            CompetitorProductDetailTarget target,
            String errorCode,
            String errorMessage,
            boolean requested
    ) {
        checkpoint(CompetitorDetailRetryRequestLedger.failure(
                payload,
                target,
                errorCode,
                errorMessage,
                requested,
                runId,
                LocalDateTime.now(clock),
                policy
        ));
    }

    boolean requeue(
            NoonRiskBackoffHold riskHold,
            String errorCode,
            String errorMessage
    ) {
        if (payload.getRetryStates().isEmpty()) {
            return false;
        }
        CompetitorDetailRetryPayload waiting = payload.copy();
        if (riskHold != null) {
            LocalDateTime holdUntil = riskHold.getBlockedUntil();
            if (holdUntil == null) {
                holdUntil = LocalDateTime.now(clock).plusMinutes(2);
            }
            waiting.delayRetryStatesUntil(holdUntil);
        }
        waiting.setRootRunId(firstNonNull(waiting.getRootRunId(), runId));
        waiting.setRetryOfRunId(runId);
        waiting.setLastErrorCode(firstNonBlank(
                errorCode, waiting.getLastErrorCode(), RETRY_WAITING
        ));
        waiting.setMessage(firstNonBlank(errorMessage, waiting.getMessage()));
        String payloadJson = waiting.toJson();
        taskFactory.requeueDetailRetry(
                task.getId(),
                runId,
                payloadJson,
                waiting.getLastErrorCode(),
                retryMessage(waiting, riskHold)
        );
        accept(payloadJson);
        return true;
    }

    void applyCumulative(CompetitorProductDetailRefreshResult result) {
        if (result == null) {
            return;
        }
        result.useCumulativeCounts(
                payload.getDetailTargetTotal(),
                payload.getDetailRequestAttemptCount(),
                payload.getDetailSucceededCount(),
                payload.getDetailTerminalFailedCount(),
                payload.getDetailTerminalErrorCode(),
                payload.getDetailTerminalErrorMessage()
        );
    }

    private void initialize(List<CompetitorProductDetailTarget> initialTargets) {
        List<CompetitorDetailRetryState> states = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);
        if (initialTargets != null) {
            for (CompetitorProductDetailTarget target : initialTargets) {
                states.add(new CompetitorDetailRetryState(target, 0, now, null, null));
            }
        }
        payload.setRootRunId(runId);
        payload.setRetryOfRunId(runId);
        payload.setDetailTargetTotal(states.size());
        payload.setRetryStates(states);
        checkpoint(payload);
    }

    private void sealMigratedPayload() {
        payload.setRootRunId(firstNonNull(payload.getRootRunId(), runId));
        payload.setRetryOfRunId(firstNonNull(payload.getRetryOfRunId(), runId));
        checkpoint(payload);
    }

    private void recoverOrSeal() {
        CompetitorDetailRetryPayload recovered =
                CompetitorDetailRetryRequestLedger.recoverInFlight(
                        payload, LocalDateTime.now(clock), policy
                );
        if (recovered == null) {
            sealMigratedPayload();
        } else {
            checkpoint(recovered);
        }
    }

    private void checkpoint(CompetitorDetailRetryPayload next) {
        String payloadJson = next.toJson();
        taskFactory.executionFinalizer().checkpointDetailRetry(
                task.getId(), runId, watchProductId, payloadJson
        );
        payload = next;
        task.setPayloadJson(payloadJson);
    }

    private void accept(String payloadJson) {
        payload = CompetitorDetailRetryPayload.fromJson(payloadJson);
        task.setPayloadJson(payloadJson);
    }

    private CompetitorDetailRetryState requirePending(
            CompetitorProductDetailTarget target
    ) {
        CompetitorDetailRetryState state = payload.state(target);
        if (state == null) {
            throw new CompetitorDetailRetryPayloadException(
                    "Detail retry target is not pending."
            );
        }
        return state;
    }

    private void requireIdentity() {
        if (task == null || task.getId() == null || runId == null
                || watchProductId == null
                || !CompetitorRefreshRecoveryPayload.matchesIdentity(
                        task,
                        watchProductId,
                        CompetitorRefreshExecutionMode.SCHEDULED_DETAIL
                )) {
            throw new CompetitorDetailRetryPayloadException(
                    "Scheduled detail retry identity is incomplete."
            );
        }
    }

    private String retryMessage(
            CompetitorDetailRetryPayload waiting,
            NoonRiskBackoffHold riskHold
    ) {
        if (riskHold != null) {
            return "竞品详情抓取等待共享风控冷却，计划于 "
                    + waiting.getRetryNotBefore() + " 后继续。";
        }
        return "竞品详情抓取失败，已进入退避重试，计划于 "
                + waiting.getRetryNotBefore() + " 后重试。";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static Long firstNonNull(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
