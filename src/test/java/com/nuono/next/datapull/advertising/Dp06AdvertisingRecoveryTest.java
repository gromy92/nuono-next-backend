package com.nuono.next.datapull.advertising;

import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.campaignPage;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.context;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.continueTask;
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
    void authWaitsAndThenEqualEmptyPassesCanApply() {
        ScriptedProvider provider = emptyProvider();
        provider.advertisers.addFirst(ProviderOutcome.authRequired("ADS_AUTH_REQUIRED"));
        RecordingWriter writer = new RecordingWriter();
        Dp06AdvertisingJob job = job(
                provider, Dp06AdvertisingTestSupport.stageStore(), writer
        );
        DataPullTask task = task();

        AdvanceResult auth = job.advance(context(task));
        assertEquals(TaskState.WAITING_AUTH, auth.getNextState());
        continueTask(task, auth);
        AdvanceResult applied = runUntilNotQueued(job, task, 15);

        assertEquals(TaskState.SUCCEEDED, applied.getNextState());
        assertEquals(1, writer.commands.size());
        assertEquals(List.of(
                "ADVERTISER", "ADVERTISER", "CAMPAIGNS:1", "CAMPAIGNS:1"
        ), provider.calls);
    }

    @Test
    void providerContractAndNotFoundWaitWithoutLosingCampaignCursor() {
        assertWaitingProviderOutcome(
                ProviderOutcome.contractError("ADS_CAMPAIGN_PAGE_PARSE_FAILED"),
                "ADS_CAMPAIGN_PAGE_PARSE_FAILED",
                TaskState.WAITING_BACKOFF
        );
        assertWaitingProviderOutcome(
                ProviderOutcome.notFound("ADS_CAMPAIGN_PAGE_NOT_FOUND"),
                "ADS_CAMPAIGN_PAGE_NOT_FOUND",
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
                0,
                0,
                null,
                Integer.MAX_VALUE
        )));

        AdvanceResult result = job(
                provider,
                Dp06AdvertisingTestSupport.stageStore(),
                new RecordingWriter()
        ).advance(context(task));

        assertEquals(TaskState.WAITING_BACKOFF, result.getNextState());
        assertEquals(Integer.MAX_VALUE,
                codec.decode(result.getCheckpoint()).getConsecutiveRetryAttempt());
    }

    @Test
    void immutableApplyContractFailureWaitsWithoutDiscardingVerifiedStage() {
        ScriptedProvider provider = emptyProvider();
        AdvertisingFactWriter rejectingWriter = writerReturning(
                AdvertisingFactWriter.ApplyResult.CONTRACT_ERROR,
                AdvertisingFactWriter.ResetResult.CLEARED
        );
        DataPullTask task = task();
        Dp06AdvertisingJob job = job(
                provider, Dp06AdvertisingTestSupport.stageStore(), rejectingWriter
        );

        advanceUntilPhase(job, task, AdvertisingCheckpoint.Phase.APPLY, 15);
        String checkpoint = task.getCheckpoint();
        AdvanceResult result = job.advance(context(task));

        assertEquals(TaskState.WAITING_REMOTE, result.getNextState());
        assertEquals("ADS_APPLY_CONTRACT_ERROR", result.getSanitizedCode());
        assertEquals(checkpoint, result.getCheckpoint());
    }

    @Test
    void staleApplyFenceWaitsForCurrentOwnerInsteadOfTerminatingTask() {
        ScriptedProvider provider = emptyProvider();
        DataPullTask task = task();
        Dp06AdvertisingJob job = job(
                provider,
                Dp06AdvertisingTestSupport.stageStore(),
                writerReturning(
                        AdvertisingFactWriter.ApplyResult.STALE_FENCE,
                        AdvertisingFactWriter.ResetResult.STALE_FENCE
                )
        );

        advanceUntilPhase(job, task, AdvertisingCheckpoint.Phase.APPLY, 15);
        AdvanceResult result = job.advance(context(task));

        assertEquals(TaskState.WAITING_REMOTE, result.getNextState());
        assertEquals("ADS_APPLY_STALE_FENCE", result.getSanitizedCode());
        assertEquals(AdvertisingCheckpoint.Phase.APPLY,
                new AdvertisingCheckpointCodec().decode(result.getCheckpoint()).getPhase());
    }

    @Test
    void structuralStageConflictUsesBoundedResetBeforeRestartingAtAdvertiser() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(
                new AdvertisingAdvertiser("ADV_108065")
        ));
        provider.campaignPages.add(ProviderOutcome.success(campaignPage()));
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
        assertEquals(1, writer.resetCalls);
        assertTrue(writer.commands.isEmpty());
    }

    private ScriptedProvider emptyProvider() {
        AdvertisingCampaignPage empty = campaignPage(1, 1, 0L, List.of());
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(
                new AdvertisingAdvertiser("ADV_108065")
        ));
        provider.campaignPages.add(ProviderOutcome.success(empty));
        provider.campaignPages.add(ProviderOutcome.success(empty));
        return provider;
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
            ProviderOutcome<AdvertisingCampaignPage> outcome,
            String expectedCode,
            TaskState expectedState
    ) {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(
                new AdvertisingAdvertiser("ADV_108065")
        ));
        provider.campaignPages.add(outcome);
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task();
        Dp06AdvertisingJob job = job(
                provider, Dp06AdvertisingTestSupport.stageStore(), writer
        );
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

    private AdvanceResult runUntilNotQueued(
            Dp06AdvertisingJob job,
            DataPullTask task,
            int maxSteps
    ) {
        for (int step = 0; step < maxSteps; step++) {
            AdvanceResult result = job.advance(context(task));
            if (result.getNextState() != TaskState.QUEUED) return result;
            continueTask(task, result);
        }
        throw new AssertionError("DP06 did not leave QUEUED within bounded steps");
    }

    private void advanceUntilPhase(
            Dp06AdvertisingJob job,
            DataPullTask task,
            AdvertisingCheckpoint.Phase expected,
            int maxSteps
    ) {
        AdvertisingCheckpointCodec codec = new AdvertisingCheckpointCodec();
        for (int step = 0; step < maxSteps; step++) {
            AdvanceResult result = job.advance(context(task));
            continueTask(task, result);
            if (codec.decode(task.getCheckpoint()).getPhase() == expected) return;
        }
        throw new AssertionError("DP06 did not reach " + expected);
    }
}
