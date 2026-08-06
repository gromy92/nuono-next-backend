package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskStore;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** Executes exactly one job advance and commits it through the claimed fence. */
public class RuntimeExecutor {

    private static final String UNTYPED_HANDLER_RETRY_CODE = "DP_HANDLER_UNTYPED_FAILURE";
    private static final String ADVANCE_TIMEOUT_CODE = "DP_ADVANCE_DEADLINE_EXCEEDED";
    private static final Duration ADVANCE_TIMEOUT_RETRY_DELAY = Duration.ofMinutes(1);

    private final DataPullJobRegistry jobRegistry;
    private final RuntimeTransitionCommitter transitionCommitter;
    private final Clock clock;
    private final Duration totalBudget;
    private final Duration jobBudget;
    private final Duration transitionBudget;
    private final DataPullRuntimeStopSignal stopSignal;
    private final Runnable beforeJobStart;

    public RuntimeExecutor(DataPullJobRegistry jobRegistry, DataPullTaskStore store) {
        this(jobRegistry, store, requiredBackoffRecorder(), Clock.systemUTC());
    }

    public RuntimeExecutor(DataPullJobRegistry jobRegistry, DataPullTaskStore store, Clock clock) {
        this(jobRegistry, store, requiredBackoffRecorder(), clock);
    }

    public RuntimeExecutor(
            DataPullJobRegistry jobRegistry,
            DataPullTaskStore store,
            BackoffHoldRecorder backoffHoldRecorder
    ) {
        this(jobRegistry, store, backoffHoldRecorder, Clock.systemUTC());
    }

    public RuntimeExecutor(
            DataPullJobRegistry jobRegistry,
            DataPullTaskStore store,
            BackoffHoldRecorder backoffHoldRecorder,
            Clock clock
    ) {
        this(
                jobRegistry,
                new RuntimeTransitionCommitter(
                        Objects.requireNonNull(store, "store"),
                        Objects.requireNonNull(backoffHoldRecorder, "backoffHoldRecorder")
                ),
                clock
        );
    }

    public RuntimeExecutor(
            DataPullJobRegistry jobRegistry,
            RuntimeTransitionCommitter transitionCommitter,
            Clock clock
    ) {
        this(jobRegistry, transitionCommitter, clock, new DataPullRuntimeStopSignal());
    }

    RuntimeExecutor(
            DataPullJobRegistry jobRegistry,
            RuntimeTransitionCommitter transitionCommitter,
            Clock clock,
            DataPullRuntimeStopSignal stopSignal
    ) {
        this(
                jobRegistry,
                transitionCommitter,
                clock,
                Duration.ofSeconds(DataPullRuntimeProperties.ADVANCE_BUDGET_SECONDS),
                Duration.ofSeconds(DataPullRuntimeProperties.JOB_BUDGET_SECONDS),
                Duration.ofSeconds(DataPullRuntimeProperties.TRANSITION_BUDGET_SECONDS),
                Objects.requireNonNull(stopSignal, "stopSignal")
        );
    }

    RuntimeExecutor(
            DataPullJobRegistry jobRegistry,
            RuntimeTransitionCommitter transitionCommitter,
            Clock clock,
            Duration totalBudget,
            Duration jobBudget,
            Duration transitionBudget
    ) {
        this(
                jobRegistry, transitionCommitter, clock, totalBudget, jobBudget,
                transitionBudget, new DataPullRuntimeStopSignal()
        );
    }

    RuntimeExecutor(
            DataPullJobRegistry jobRegistry,
            RuntimeTransitionCommitter transitionCommitter,
            Clock clock,
            Duration totalBudget,
            Duration jobBudget,
            Duration transitionBudget,
            DataPullRuntimeStopSignal stopSignal
    ) {
        this(
                jobRegistry, transitionCommitter, clock, totalBudget, jobBudget,
                transitionBudget, stopSignal, () -> { }
        );
    }

    RuntimeExecutor(
            DataPullJobRegistry jobRegistry,
            RuntimeTransitionCommitter transitionCommitter,
            Clock clock,
            Duration totalBudget,
            Duration jobBudget,
            Duration transitionBudget,
            DataPullRuntimeStopSignal stopSignal,
            Runnable beforeJobStart
    ) {
        this.jobRegistry = Objects.requireNonNull(jobRegistry, "jobRegistry");
        this.transitionCommitter = Objects.requireNonNull(
                transitionCommitter,
                "transitionCommitter"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
        this.totalBudget = requirePositive(totalBudget, "totalBudget");
        this.jobBudget = requirePositive(jobBudget, "jobBudget");
        this.transitionBudget = requirePositive(transitionBudget, "transitionBudget");
        this.stopSignal = Objects.requireNonNull(stopSignal, "stopSignal");
        this.beforeJobStart = Objects.requireNonNull(beforeJobStart, "beforeJobStart");
        if (this.jobBudget.plus(this.transitionBudget).compareTo(this.totalBudget) >= 0) {
            throw new IllegalArgumentException("DP deadline budgets require a positive reserve");
        }
    }

    public boolean execute(DataPullTask claimedTask, LocalDateTime nowUtc) {
        DataPullTask task = requireLiveClaim(claimedTask, nowUtc);
        if (stopSignal.isStopping()) return releaseUnstarted(task);
        DataPullJob job = jobRegistry.require(task.getOperationCode());
        long totalDeadline = DataPullAdvanceDeadline.deadlineAfter(totalBudget);
        AdvanceResult result = null;
        boolean releaseBeforeStart = false;
        long jobDeadline = DataPullAdvanceDeadline.earlier(
                totalDeadline,
                DataPullAdvanceDeadline.deadlineAfter(jobBudget)
        );
        try (DataPullAdvanceDeadline deadline =
                     DataPullAdvanceDeadline.openUntil(jobDeadline, stopSignal)) {
            beforeJobStart.run();
            if (stopSignal.isStopping() || deadline.isExpired()) {
                releaseBeforeStart = true;
            } else {
                try {
                    result = Objects.requireNonNull(
                            job.advance(new ExecutionContext(task, nowUtc)),
                            "job advance result"
                    );
                } catch (RuntimeException unexpected) {
                    result = deadline.isExpired()
                            ? advanceTimeout(task)
                            : untypedHandlerRetry(task);
                }
                if (deadline.isExpired()) result = advanceTimeout(task);
            }
        }
        if (releaseBeforeStart) return releaseUnstarted(task);
        if (stopSignal.isStopping()) return false;
        LocalDateTime completedAtUtc = completionTimeUtc(nowUtc);
        long transitionDeadline = DataPullAdvanceDeadline.earlier(
                totalDeadline,
                DataPullAdvanceDeadline.deadlineAfter(transitionBudget)
        );
        try (DataPullAdvanceDeadline deadline =
                     DataPullAdvanceDeadline.openUntil(transitionDeadline, stopSignal)) {
            if (stopSignal.isStopping() || deadline.isExpired()) return false;
            try {
                return transitionCommitter.commit(task, result, completedAtUtc);
            } catch (RuntimeException transitionFailure) {
                if (deadline.isExpired()) return false;
                throw transitionFailure;
            }
        }
    }

    DataPullRuntimeStopSignal stopSignal() {
        return stopSignal;
    }

    private boolean releaseUnstarted(DataPullTask task) {
        boolean interrupted = Thread.interrupted();
        try (DataPullAdvanceDeadline deadline = DataPullAdvanceDeadline.open(transitionBudget)) {
            try {
                transitionCommitter.releaseUnstartedClaim(
                        task, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
                return false;
            } catch (RuntimeException releaseFailure) {
                if (deadline.isExpired()) return false;
                throw releaseFailure;
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private LocalDateTime completionTimeUtc(LocalDateTime startedAtUtc) {
        LocalDateTime completedAtUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (completedAtUtc.isBefore(startedAtUtc)) {
            throw new IllegalStateException("runtime clock moved before the execution start");
        }
        return completedAtUtc;
    }

    private AdvanceResult untypedHandlerRetry(DataPullTask task) {
        return AdvanceResult.waitingRemote(
                task.getStepCode(),
                task.getRemoteHandle(),
                task.getCheckpoint(),
                ADVANCE_TIMEOUT_RETRY_DELAY,
                UNTYPED_HANDLER_RETRY_CODE
        );
    }

    private AdvanceResult advanceTimeout(DataPullTask task) {
        return AdvanceResult.waitingRemote(
                task.getStepCode(),
                task.getRemoteHandle(),
                task.getCheckpoint(),
                ADVANCE_TIMEOUT_RETRY_DELAY,
                ADVANCE_TIMEOUT_CODE
        );
    }

    private DataPullTask requireLiveClaim(DataPullTask task, LocalDateTime nowUtc) {
        DataPullTask nonNullTask = Objects.requireNonNull(task, "claimedTask");
        LocalDateTime now = Objects.requireNonNull(nowUtc, "nowUtc");
        if (nonNullTask.getState() != TaskState.RUNNING
                || nonNullTask.getLeaseOwner() == null
                || nonNullTask.getLeaseUntil() == null
                || !nonNullTask.getLeaseUntil().isAfter(now)
                || nonNullTask.getFenceEpoch() == null
                || nonNullTask.getFenceEpoch() <= 0L
                || nonNullTask.getVersion() == null) {
            throw new IllegalStateException("runtime execution requires a live claimed task epoch");
        }
        return nonNullTask;
    }

    private static BackoffHoldRecorder requiredBackoffRecorder() {
        return (shareLevel, identity, blockedUntil, code, observedAt) -> {
            throw new IllegalStateException(
                    "durable backoff hold recorder is required for a WAITING_BACKOFF result"
            );
        };
    }

    private static Duration requirePositive(Duration value, String name) {
        Duration nonNull = Objects.requireNonNull(value, name);
        if (nonNull.isZero() || nonNull.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return nonNull;
    }
}
