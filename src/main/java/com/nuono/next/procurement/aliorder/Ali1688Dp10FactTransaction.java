package com.nuono.next.procurement.aliorder;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.checkpoint.DataPullScopeProgressStore;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10RuntimeMapper;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10ApplyCommand;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10ApplySlice;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10BatchVerifier;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10BoundedStageStore;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10FactCommitGuard;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10FactAdvance;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10FactSegmentWriter;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10FactSegmentResult;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10FactWriter;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Executes one bounded verify/fact/page-marker/final-high-water transaction per advance. */
@Service
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class Ali1688Dp10FactTransaction implements Ali1688Dp10FactWriter {
    static final int MAX_FACT_ROWS_PER_ADVANCE = 20;

    private final Ali1688Dp10FactSegmentWriter factSegmentWriter;
    private final Ali1688Dp10BoundedStageStore stageStore;
    private final Ali1688Dp10FactCommitGuard commitGuard;

    public Ali1688Dp10FactTransaction(
            Ali1688Dp10RuntimeMapper runtimeMapper,
            Ali1688Dp10FactSegmentWriter factSegmentWriter,
            DataPullScopeProgressStore progressStore,
            Ali1688Dp10BoundedStageStore stageStore
    ) {
        this.factSegmentWriter = Objects.requireNonNull(factSegmentWriter, "factSegmentWriter");
        this.stageStore = Objects.requireNonNull(stageStore, "stageStore");
        this.commitGuard = new Ali1688Dp10FactCommitGuard(
                Objects.requireNonNull(runtimeMapper, "runtimeMapper"),
                Objects.requireNonNull(progressStore, "progressStore"));
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public Ali1688Dp10FactAdvance advance(Ali1688Dp10ApplyCommand command) {
        Ali1688Dp10ApplyCommand value = Objects.requireNonNull(command, "command");
        DataPullTask task = value.getTask();
        Ali1688HistoricalOrderAuthorizationRow authorization = commitGuard.lock(task, value);

        if ("DP10_VERIFY".equals(task.getStepCode())) {
            Ali1688Dp10BatchVerifier.Advance verified = stageStore.verifyNext(
                    task, value, value.getNowUtc());
            return verified == Ali1688Dp10BatchVerifier.Advance.COMPLETE
                    ? Ali1688Dp10FactAdvance.APPLYING
                    : Ali1688Dp10FactAdvance.VERIFYING;
        }
        if (!"DP10_APPLY".equals(task.getStepCode())) {
            throw new IllegalStateException("DP10_FACT_STEP_INVALID");
        }
        Ali1688Dp10ApplySlice slice = stageStore.nextApplySlice(
                task, value, value.getNowUtc()).orElse(null);
        if (slice != null) {
            Ali1688Dp10FactSegmentResult result = factSegmentWriter.applySegment(
                    task, authorization, slice, MAX_FACT_ROWS_PER_ADVANCE);
            if (result.isBusinessSkipped()) {
                stageStore.recordBusinessSkip(
                        task, slice, result.getBusinessSkipCode(), value.getNowUtc());
            } else {
                stageStore.recordAppliedSegment(
                        task, slice, result.getNextCursor(), value.getNowUtc());
            }
            return Ali1688Dp10FactAdvance.APPLYING;
        }
        if (stageStore.markNextPageApplied(task, value, value.getNowUtc())) {
            return Ali1688Dp10FactAdvance.APPLYING;
        }
        if (!stageStore.allApplied(task, value, value.getNowUtc())) {
            throw new IllegalStateException("DP10_STAGE_APPLY_INCOMPLETE");
        }
        commitGuard.commitHighWater(task, value);
        return Ali1688Dp10FactAdvance.COMPLETE;
    }
}
