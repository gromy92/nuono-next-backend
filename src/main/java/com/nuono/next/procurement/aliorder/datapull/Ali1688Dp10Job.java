package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.checkpoint.DataPullScopeProgress;
import com.nuono.next.datapull.checkpoint.DataPullScopeProgressStore;
import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** DP-10 registration and checkpoint decoder; step behavior lives behind one executor interface. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class Ali1688Dp10Job implements DataPullJob {
    public static final String INITIAL_STEP = "DP10_LOAD_PROGRESS";
    static final String LIST_STEP = "DP10_LIST";
    static final String SEAL_STEP = "DP10_SEAL";
    static final String DETAIL_STEP = "DP10_DETAIL";
    static final String VERIFY_STEP = "DP10_VERIFY";
    static final String APPLY_STEP = "DP10_APPLY";
    static final String CLEANUP_STEP = "DP10_CLEANUP";

    private final Ali1688Dp10ScopeSource scopeSource;
    private final DataPullScopeProgressStore progressStore;
    private final Ali1688Dp10CheckpointCodec checkpointCodec;
    private final Ali1688Dp10FailurePolicy failurePolicy;
    private final Ali1688Dp10StepExecutor stepExecutor;
    private final int listPageSize;

    public Ali1688Dp10Job(
            Ali1688Dp10ScopeSource scopeSource,
            Ali1688HistoricalOrderProvider provider,
            Ali1688Dp10PageStageStore stageStore,
            Ali1688Dp10StageCleanup stageCleanup,
            Ali1688Dp10FactWriter factWriter,
            DataPullScopeProgressStore progressStore,
            ProviderWaitTransition providerWaitTransition,
            ObjectMapper objectMapper
    ) {
        this.scopeSource = Objects.requireNonNull(scopeSource, "scopeSource");
        this.progressStore = Objects.requireNonNull(progressStore, "progressStore");
        this.checkpointCodec = new Ali1688Dp10CheckpointCodec(objectMapper);
        this.failurePolicy = new Ali1688Dp10FailurePolicy(providerWaitTransition);
        this.listPageSize = Ali1688Dp10ListPageContract.requireSupported(
                Objects.requireNonNull(provider, "provider").listPageSize());
        this.stepExecutor = new Ali1688Dp10StepExecutor(
                scopeSource,
                provider,
                Objects.requireNonNull(stageStore, "stageStore"),
                Objects.requireNonNull(stageCleanup, "stageCleanup"),
                Objects.requireNonNull(factWriter, "factWriter"),
                progressStore,
                checkpointCodec,
                failurePolicy
        );
    }

    @Override public OperationCode operationCode() { return OperationCode.DP10; }
    @Override public String providerChannel() { return Ali1688Dp10ScopeIdentity.PROVIDER_CHANNEL; }
    @Override public String initialStep() { return INITIAL_STEP; }
    @Override public List<DataPullScope> listScopes() { return scopeSource.listScopes(); }

    @Override
    public AdvanceResult advance(ExecutionContext context) {
        DataPullTask task = context == null ? null : context.getTask();
        LocalDateTime nowUtc = context == null ? null : context.getNowUtc();
        String invalid = validateTask(task, nowUtc);
        if (invalid != null) {
            return AdvanceResult.failed(task == null ? null : task.getCheckpoint(), invalid);
        }
        if (task.getCheckpoint() == null) return initialize(task, nowUtc);
        Ali1688Dp10Checkpoint checkpoint;
        try {
            checkpoint = checkpointCodec.decode(task.getCheckpoint());
        } catch (RuntimeException invalidCheckpoint) {
            return AdvanceResult.failed(task.getCheckpoint(), "DP10_CHECKPOINT_INVALID");
        }
        try {
            return stepExecutor.advance(task, nowUtc, checkpoint);
        } catch (RuntimeException systemFailure) {
            return failurePolicy.transientFailure(
                    task,
                    task.getStepCode(),
                    task.getCheckpoint(),
                    "DP10_EXECUTION_UNKNOWN"
            );
        }
    }

    private AdvanceResult initialize(DataPullTask task, LocalDateTime nowUtc) {
        try {
            DataPullScopeProgress progress = progressStore.getOrCreate(
                    operationCode(),
                    task.getScopeKey(),
                    nowUtc
            );
            return AdvanceResult.queued(
                    LIST_STEP,
                    null,
                    checkpointCodec.encode(Ali1688Dp10Checkpoint.initial(
                            progress,
                            nowUtc,
                            listPageSize
                    ))
            );
        } catch (RuntimeException failure) {
            return failurePolicy.transientFailure(
                    task,
                    INITIAL_STEP,
                    null,
                    "DP10_PROGRESS_LOAD_UNKNOWN"
            );
        }
    }

    private String validateTask(DataPullTask task, LocalDateTime nowUtc) {
        if (task == null || nowUtc == null) return "DP10_TASK_REQUIRED";
        if (task.getOperationCode() != operationCode()
                || !providerChannel().equals(task.getProviderChannel())
                || task.getOwnerUserId() == null
                || task.getOwnerUserId() <= 0L
                || task.getScopeKey() == null
                || task.getScopeKey().isBlank()
                || !Ali1688Dp10ScopeIdentity.isAccountKey(task.getAccountKey())) {
            return "DP10_TASK_SCOPE_INVALID";
        }
        return List.of(
                INITIAL_STEP,
                LIST_STEP,
                SEAL_STEP,
                DETAIL_STEP,
                VERIFY_STEP,
                APPLY_STEP,
                CLEANUP_STEP
        ).contains(task.getStepCode())
                ? null
                : "DP10_TASK_STEP_INVALID";
    }
}
