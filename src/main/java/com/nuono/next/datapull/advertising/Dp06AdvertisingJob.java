package com.nuono.next.datapull.advertising;

import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.DataPullScopeProvider;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.datapull.snapshot.SnapshotStageStore;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * DP-06 bounded runtime: advertiser (1), dashboard (1), then one call for every active campaign.
 * Facts remain fenced in staging until all 2+C calls have closed and one atomic apply succeeds.
 */
public final class Dp06AdvertisingJob implements DataPullJob {
    public static final String INITIAL_STEP = "ADS_ADVERTISER";

    private final String providerChannel;
    private final DataPullScopeProvider scopeProvider;
    private final AdvertisingCheckpointCodec checkpointCodec = new AdvertisingCheckpointCodec();
    private final AdvertisingProviderSteps providerSteps;
    private final AdvertisingStageCoordinator stageCoordinator;
    private final AdvertisingJobTransitions transitions;

    public Dp06AdvertisingJob(
            String providerChannel,
            DataPullScopeProvider scopeProvider,
            AdvertisingProvider provider,
            SnapshotStageStore<AdvertisingStagedFact> stageStore,
            AdvertisingFactWriter factWriter,
            ProviderWaitTransition providerWaitTransition,
            Duration localRetryDelay
    ) {
        this.providerChannel = AdvertisingAdvertiser.requireIdentity(
                providerChannel,
                "providerChannel"
        );
        this.scopeProvider = Objects.requireNonNull(scopeProvider, "scopeProvider");
        this.transitions = new AdvertisingJobTransitions(
                providerWaitTransition,
                localRetryDelay,
                checkpointCodec
        );
        this.stageCoordinator = new AdvertisingStageCoordinator(
                stageStore,
                factWriter,
                transitions
        );
        this.providerSteps = new AdvertisingProviderSteps(
                provider,
                stageCoordinator,
                transitions
        );
    }

    @Override
    public OperationCode operationCode() {
        return OperationCode.DP06;
    }

    @Override
    public String providerChannel() {
        return providerChannel;
    }

    @Override
    public String initialStep() {
        return INITIAL_STEP;
    }

    @Override
    public List<DataPullScope> listScopes() {
        return List.copyOf(Objects.requireNonNull(scopeProvider.listScopes(), "DP-06 scopes"));
    }

    @Override
    public AdvanceResult advance(ExecutionContext context) {
        DataPullTask task = context == null ? null : context.getTask();
        String taskError = validateTask(task);
        if (taskError != null) {
            return AdvanceResult.failed(task == null ? null : task.getCheckpoint(), taskError);
        }

        AdvertisingPullRequest request;
        AdvertisingCheckpoint checkpoint;
        try {
            request = AdvertisingPullRequest.from(task);
            checkpoint = checkpointCodec.decode(task.getCheckpoint());
        } catch (AdvertisingCheckpointCodec.AuthoritylessCheckpointException legacy) {
            return stageCoordinator.resetAuthoritylessCheckpoint(task);
        } catch (RuntimeException invalidCheckpoint) {
            return AdvanceResult.failed(task.getCheckpoint(), "ADS_TASK_CHECKPOINT_INVALID");
        }

        switch (checkpoint.getPhase()) {
            case ADVERTISER:
                return providerSteps.resolveAdvertiser(task, request, checkpoint);
            case DASHBOARD:
                return providerSteps.fetchDashboard(task, request, checkpoint);
            case CAMPAIGN_QUERY:
                return providerSteps.fetchCampaign(task, request, checkpoint);
            case APPLY:
                return stageCoordinator.apply(task, request, checkpoint);
            case RESET:
                return stageCoordinator.reset(task);
            default:
                return transitions.failure(checkpoint, "ADS_PHASE_UNSUPPORTED");
        }
    }

    private String validateTask(DataPullTask task) {
        if (task == null) {
            return "ADS_TASK_REQUIRED";
        }
        if (task.getOperationCode() != OperationCode.DP06) {
            return "ADS_OPERATION_MISMATCH";
        }
        if (!providerChannel.equals(task.getProviderChannel())) {
            return "ADS_PROVIDER_CHANNEL_MISMATCH";
        }
        if (task.getState() != TaskState.RUNNING) {
            return "ADS_TASK_NOT_RUNNING";
        }
        if (task.getId() == null || task.getId() <= 0L
                || task.getFenceEpoch() == null || task.getFenceEpoch() <= 0L
                || task.getLeaseOwner() == null) {
            return "ADS_TASK_FENCE_INVALID";
        }
        return null;
    }

}
