package com.nuono.next.datapull.advertising;

import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.authority;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.campaign;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.context;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.continueTask;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.dashboard;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.job;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.task;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.RecordingWriter;
import com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.RejectingStageStore;
import com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.ScriptedProvider;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.TaskState;
import java.util.List;
import org.junit.jupiter.api.Test;

class Dp06AdvertisingRecoveryTest {

    @Test
    void authWaitsAndZeroActiveCampaignsStillApplyAfterTwoCalls() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.authRequired("ADS_AUTH_REQUIRED"));
        provider.advertisers.add(ProviderOutcome.success(new AdvertisingAdvertiser("ADV_108065")));
        provider.dashboards.add(ProviderOutcome.success(new AdvertisingDashboard(
                List.of(campaign("C-PAUSED", "paused")),
                List.of(),
                authority(1L)
        )));
        RecordingWriter writer = new RecordingWriter();
        Dp06AdvertisingJob job = job(provider, Dp06AdvertisingTestSupport.stageStore(), writer);
        DataPullTask task = task();

        AdvanceResult auth = job.advance(context(task));
        assertEquals(TaskState.WAITING_AUTH, auth.getNextState());
        continueTask(task, auth);
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        AdvanceResult applied = job.advance(context(task));

        assertEquals(TaskState.SUCCEEDED, applied.getNextState());
        assertEquals(1, writer.commands.size());
        assertEquals(
                List.of("ADVERTISER", "ADVERTISER", "DASHBOARD"),
                provider.calls
        );
    }

    @Test
    void providerContractAndNotFoundWaitWithoutLosingCheckpoint() {
        assertWaitingProviderOutcome(
                ProviderOutcome.contractError("ADS_DASHBOARD_PARSE_FAILED"),
                "ADS_DASHBOARD_PARSE_FAILED",
                TaskState.WAITING_BACKOFF
        );
        assertWaitingProviderOutcome(
                ProviderOutcome.notFound("ADS_DASHBOARD_NOT_FOUND"),
                "ADS_DASHBOARD_NOT_FOUND",
                TaskState.WAITING_REMOTE
        );
    }

    @Test
    void riskAtSaturatedAttemptStillBacksOffInsteadOfTerminating() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.riskControl("ADS_RISK_CONTROL"));
        DataPullTask task = task();
        AdvertisingCheckpointCodec codec = new AdvertisingCheckpointCodec();
        task.setCheckpoint(codec.encode(AdvertisingCheckpoint.restored(
                AdvertisingCheckpoint.Phase.ADVERTISER,
                null,
                List.of(),
                null,
                0,
                Integer.MAX_VALUE
        )));

        AdvanceResult result = job(
                provider,
                Dp06AdvertisingTestSupport.stageStore(),
                new RecordingWriter()
        ).advance(context(task));

        assertEquals(TaskState.WAITING_BACKOFF, result.getNextState());
        assertEquals(
                Integer.MAX_VALUE,
                codec.decode(result.getCheckpoint()).getConsecutiveRetryAttempt()
        );
    }

    @Test
    void immutableApplyContractFailureWaitsWithoutDiscardingTheStage() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(new AdvertisingAdvertiser("ADV_108065")));
        provider.dashboards.add(ProviderOutcome.success(new AdvertisingDashboard(
                List.of(),
                List.of(),
                authority(0L)
        )));
        AdvertisingFactWriter rejectingWriter = new AdvertisingFactWriter() {
            @Override
            public ApplyResult applyComplete(AdvertisingApplyCommand command) {
                return ApplyResult.CONTRACT_ERROR;
            }

            @Override
            public ResetResult reset(long taskId, long fenceEpoch, String leaseOwner) {
                return ResetResult.CLEARED;
            }
        };
        DataPullTask task = task();
        Dp06AdvertisingJob job = job(
                provider,
                Dp06AdvertisingTestSupport.stageStore(),
                rejectingWriter
        );
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        String checkpoint = task.getCheckpoint();

        AdvanceResult result = job.advance(context(task));

        assertEquals(TaskState.WAITING_REMOTE, result.getNextState());
        assertEquals("ADS_APPLY_CONTRACT_ERROR", result.getSanitizedCode());
        assertEquals(checkpoint, result.getCheckpoint());
    }

    @Test
    void staleApplyFenceWaitsForTheCurrentOwnerInsteadOfTerminatingTheTask() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(new AdvertisingAdvertiser("ADV_108065")));
        provider.dashboards.add(ProviderOutcome.success(new AdvertisingDashboard(
                List.of(), List.of(), authority(0L)
        )));
        AdvertisingFactWriter staleWriter = writerReturning(
                AdvertisingFactWriter.ApplyResult.STALE_FENCE,
                AdvertisingFactWriter.ResetResult.STALE_FENCE
        );
        DataPullTask task = task();
        Dp06AdvertisingJob job = job(
                provider,
                Dp06AdvertisingTestSupport.stageStore(),
                staleWriter
        );
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));

        AdvanceResult result = job.advance(context(task));

        assertEquals(TaskState.WAITING_REMOTE, result.getNextState());
        assertEquals("ADS_APPLY_STALE_FENCE", result.getSanitizedCode());
        assertEquals(AdvertisingCheckpoint.Phase.APPLY,
                new AdvertisingCheckpointCodec().decode(result.getCheckpoint()).getPhase());
    }

    @Test
    void structuralStageConflictUsesBoundedResetBeforeRestartingAtAdvertiser() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(new AdvertisingAdvertiser("ADV_108065")));
        provider.dashboards.add(ProviderOutcome.success(dashboard()));
        RejectingStageStore stage = new RejectingStageStore();
        RecordingWriter writer = new RecordingWriter();
        Dp06AdvertisingJob job = job(provider, stage, writer);
        DataPullTask task = task();

        continueTask(task, job.advance(context(task)));
        AdvanceResult resetQueued = job.advance(context(task));
        assertEquals(TaskState.QUEUED, resetQueued.getNextState());
        assertEquals("ADS_RESET", resetQueued.getStepCode());
        continueTask(task, resetQueued);
        AdvanceResult restarted = job.advance(context(task));

        assertEquals(TaskState.WAITING_BACKOFF, restarted.getNextState());
        assertEquals("ADS_ADVERTISER", restarted.getStepCode());
        assertEquals("ADS_CONTAINER_RESTARTED", restarted.getSanitizedCode());
        assertEquals(0, stage.clearCalls);
        assertEquals(1, writer.resetCalls);
        assertTrue(writer.commands.isEmpty());
    }

    @Test
    void staleResetFenceWaitsForTheCurrentOwnerInsteadOfTerminatingTheTask() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(new AdvertisingAdvertiser("ADV_108065")));
        provider.dashboards.add(ProviderOutcome.success(dashboard()));
        Dp06AdvertisingJob job = job(
                provider,
                new RejectingStageStore(),
                writerReturning(
                        AdvertisingFactWriter.ApplyResult.APPLIED,
                        AdvertisingFactWriter.ResetResult.STALE_FENCE
                )
        );
        DataPullTask task = task();
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));

        AdvanceResult result = job.advance(context(task));

        assertEquals(TaskState.WAITING_REMOTE, result.getNextState());
        assertEquals("ADS_STAGE_RESET_STALE_FENCE", result.getSanitizedCode());
        assertEquals(AdvertisingCheckpoint.Phase.RESET,
                new AdvertisingCheckpointCodec().decode(result.getCheckpoint()).getPhase());
    }

    private AdvertisingFactWriter writerReturning(
            AdvertisingFactWriter.ApplyResult apply,
            AdvertisingFactWriter.ResetResult reset
    ) {
        return new AdvertisingFactWriter() {
            @Override
            public ApplyResult applyComplete(AdvertisingApplyCommand command) {
                return apply;
            }

            @Override
            public ResetResult reset(long taskId, long fenceEpoch, String leaseOwner) {
                return reset;
            }
        };
    }

    private void assertWaitingProviderOutcome(
            ProviderOutcome<AdvertisingDashboard> outcome,
            String expectedCode,
            TaskState expectedState
    ) {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(new AdvertisingAdvertiser("ADV_108065")));
        provider.dashboards.add(outcome);
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task();
        Dp06AdvertisingJob job = job(provider, Dp06AdvertisingTestSupport.stageStore(), writer);
        continueTask(task, job.advance(context(task)));
        String checkpoint = task.getCheckpoint();

        AdvanceResult result = job.advance(context(task));

        assertEquals(expectedState, result.getNextState());
        assertEquals(expectedCode, result.getSanitizedCode());
        assertEquals(
                new AdvertisingCheckpointCodec().decode(checkpoint).getPhase(),
                new AdvertisingCheckpointCodec().decode(result.getCheckpoint()).getPhase()
        );
        assertTrue(writer.commands.isEmpty());
    }
}
