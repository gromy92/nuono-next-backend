package com.nuono.next.datapull.advertising;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.snapshot.SnapshotPage;
import com.nuono.next.datapull.snapshot.SnapshotStageResult;
import com.nuono.next.datapull.snapshot.SnapshotStageStore;
import java.util.Objects;

/** Owns the DP-06 complete-container proof, fenced apply, and stage cleanup invariant. */
final class AdvertisingStageCoordinator {

    private final SnapshotStageStore<AdvertisingStagedFact> stageStore;
    private final AdvertisingFactWriter factWriter;
    private final AdvertisingJobTransitions transitions;

    AdvertisingStageCoordinator(
            SnapshotStageStore<AdvertisingStagedFact> stageStore,
            AdvertisingFactWriter factWriter,
            AdvertisingJobTransitions transitions
    ) {
        this.stageStore = Objects.requireNonNull(stageStore, "stageStore");
        this.factWriter = Objects.requireNonNull(factWriter, "factWriter");
        this.transitions = Objects.requireNonNull(transitions, "transitions");
    }

    AdvanceResult stage(
            DataPullTask task,
            AdvertisingCheckpoint checkpoint,
            SnapshotPage<AdvertisingStagedFact> page
    ) {
        SnapshotStageResult result;
        try {
            result = Objects.requireNonNull(
                    stageStore.stagePage(task.getId(), task.getFenceEpoch(), page),
                    "stage result"
            );
        } catch (RuntimeException unknownStageResult) {
            return transitions.waitingLocal(checkpoint, "ADS_STAGE_OUTCOME_UNKNOWN");
        }
        return result.isAccepted()
                ? null
                : restart(task, checkpoint, result.getSanitizedCode());
    }

    AdvanceResult apply(
            DataPullTask task,
            AdvertisingPullRequest request,
            AdvertisingCheckpoint checkpoint
    ) {
        AdvertisingFactWriter.ApplyResult result = applyFacts(task, request, checkpoint);
        if (result == null) {
            return transitions.waitingLocal(checkpoint, "ADS_APPLY_OUTCOME_UNKNOWN");
        }
        if (result == AdvertisingFactWriter.ApplyResult.STALE_FENCE) {
            return transitions.waitingLocal(checkpoint, "ADS_APPLY_STALE_FENCE");
        }
        if (result == AdvertisingFactWriter.ApplyResult.MORE_WORK) {
            return transitions.queued(checkpoint);
        }
        if (result == AdvertisingFactWriter.ApplyResult.CONTRACT_ERROR) {
            return transitions.waitingLocal(checkpoint, "ADS_APPLY_CONTRACT_ERROR");
        }
        if (result != AdvertisingFactWriter.ApplyResult.APPLIED
                && result != AdvertisingFactWriter.ApplyResult.ALREADY_APPLIED) {
            return transitions.waitingLocal(checkpoint, "ADS_APPLY_OUTCOME_UNKNOWN");
        }
        return AdvanceResult.succeeded();
    }

    private AdvertisingFactWriter.ApplyResult applyFacts(
            DataPullTask task,
            AdvertisingPullRequest request,
            AdvertisingCheckpoint checkpoint
    ) {
        try {
            return Objects.requireNonNull(
                    factWriter.applyComplete(new AdvertisingApplyCommand(
                            task.getId(),
                            task.getFenceEpoch(),
                            task.getLeaseOwner(),
                            task.getScheduleSlot(),
                            request,
                            task.getBusinessWindowKey(),
                            checkpoint.getAuthority(),
                            checkpoint.getActiveCampaigns()
                    )),
                    "apply result"
            );
        } catch (NoonAdvertisingFactWriter.AdvertisingApplyLeaseExpiredException staleLease) {
            return AdvertisingFactWriter.ApplyResult.STALE_FENCE;
        } catch (NoonAdvertisingFactWriter.AdvertisingApplyContractException contractFailure) {
            return AdvertisingFactWriter.ApplyResult.CONTRACT_ERROR;
        } catch (RuntimeException unknownApplyResult) {
            return null;
        }
    }

    private AdvanceResult restart(
            DataPullTask task,
            AdvertisingCheckpoint checkpoint,
            String code
    ) {
        return transitions.queued(AdvertisingCheckpoint.resetting());
    }

    AdvanceResult reset(DataPullTask task) {
        AdvertisingCheckpoint resetting = AdvertisingCheckpoint.resetting();
        AdvertisingFactWriter.ResetResult result;
        try {
            result = factWriter.reset(
                    task.getId(), task.getFenceEpoch(), task.getLeaseOwner()
            );
        } catch (NoonAdvertisingFactWriter.AdvertisingApplyLeaseExpiredException stale) {
            result = AdvertisingFactWriter.ResetResult.STALE_FENCE;
        } catch (RuntimeException unknown) {
            return transitions.waitingLocal(resetting, "ADS_STAGE_RESET_UNKNOWN");
        }
        if (result == AdvertisingFactWriter.ResetResult.MORE_WORK) {
            return transitions.queued(resetting);
        }
        if (result == AdvertisingFactWriter.ResetResult.STALE_FENCE) {
            return transitions.waitingLocal(resetting, "ADS_STAGE_RESET_STALE_FENCE");
        }
        return transitions.waitForProvider(
                task,
                AdvertisingCheckpoint.initial(),
                ProviderOutcome.transientFailure("ADS_CONTAINER_RESTARTED")
        );
    }

    AdvanceResult resetAuthoritylessCheckpoint(DataPullTask task) {
        return restart(
                task,
                AdvertisingCheckpoint.initial(),
                "ADS_AUTHORITY_CHECKPOINT_UPGRADE_REQUIRED"
        );
    }
}
