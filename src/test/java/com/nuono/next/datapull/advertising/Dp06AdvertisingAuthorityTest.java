package com.nuono.next.datapull.advertising;

import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.authority;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.campaign;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.campaignPage;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.context;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.continueTask;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.job;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.task;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.RecordingWriter;
import com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.ScriptedProvider;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.datapull.snapshot.SnapshotPage;
import com.nuono.next.datapull.snapshot.SnapshotStageProof;
import com.nuono.next.datapull.snapshot.SnapshotStageResult;
import com.nuono.next.datapull.snapshot.SnapshotStageStore;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class Dp06AdvertisingAuthorityTest {

    @Test
    void equalTwoPassEmptyEnumerationIsTheOnlyZeroFactSuccess() {
        AdvertisingCampaignPage empty = campaignPage(1, 1, 0L, List.of());
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(
                new AdvertisingAdvertiser("ADV_108065")
        ));
        provider.campaignPages.add(ProviderOutcome.success(empty));
        provider.campaignPages.add(ProviderOutcome.success(empty));
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task();

        AdvanceResult applied = runUntilNotQueued(
                job(provider, Dp06AdvertisingTestSupport.stageStore(), writer), task, 15
        );

        assertEquals(TaskState.SUCCEEDED, applied.getNextState());
        assertEquals(List.of("ADVERTISER", "CAMPAIGNS:1", "CAMPAIGNS:1"),
                provider.calls);
        assertEquals(1, writer.commands.size());
        assertEquals(0L, writer.commands.get(0).getAuthority().getDeclaredCampaignCount());
        assertNull(writer.commands.get(0).getAuthority().getProviderAsOfUtc());
        assertTrue(writer.commands.get(0).getActiveCampaigns().isEmpty());
    }

    @Test
    void providerContractErrorBeforeFirstPageStagesAndWritesNothing() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(
                new AdvertisingAdvertiser("ADV_108065")
        ));
        provider.campaignPages.add(ProviderOutcome.contractError(
                "ADS_CAMPAIGN_PAGE_CONTAINER_INVALID"
        ));
        CountingStageStore stage = new CountingStageStore();
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task();
        Dp06AdvertisingJob job = job(provider, stage, writer);
        continueTask(task, job.advance(context(task)));

        AdvanceResult waiting = job.advance(context(task));

        assertEquals(TaskState.WAITING_BACKOFF, waiting.getNextState());
        assertEquals("ADS_CAMPAIGN_PAGE_CONTAINER_INVALID", waiting.getSanitizedCode());
        assertEquals(0, stage.stageCalls);
        assertTrue(writer.commands.isEmpty());
    }

    @Test
    void changedSecondPassQueuesBoundedResetAndNeverApplies() {
        AdvertisingCampaignPage first = campaignPage(
                1, 1, 1L, List.of(campaign("C-LIVE-1", "live"))
        );
        AdvertisingCampaignPage changed = campaignPage(
                1, 1, 1L, List.of(campaign("C-LIVE-1", "paused"))
        );
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(
                new AdvertisingAdvertiser("ADV_108065")
        ));
        provider.campaignPages.add(ProviderOutcome.success(first));
        provider.campaignPages.add(ProviderOutcome.success(changed));
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task();
        Dp06AdvertisingJob job = job(
                provider, Dp06AdvertisingTestSupport.stageStore(), writer
        );

        AdvanceResult reset = runUntilPhase(job, task, AdvertisingCheckpoint.Phase.RESET, 12);

        assertEquals(TaskState.QUEUED, reset.getNextState());
        assertTrue(writer.commands.isEmpty());
        continueTask(task, reset);
        AdvanceResult waiting = job.advance(context(task));
        assertEquals(TaskState.WAITING_BACKOFF, waiting.getNextState());
        assertEquals("ADS_CONTAINER_RESTARTED", waiting.getSanitizedCode());
        assertEquals(1, writer.resetCalls);
    }

    @Test
    void legacyCheckpointResetsBeforeAnyProviderOrFactCall() {
        ScriptedProvider provider = new ScriptedProvider();
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task();
        task.setCheckpoint(legacyCheckpoint());
        Dp06AdvertisingJob job = job(
                provider, Dp06AdvertisingTestSupport.stageStore(), writer
        );

        AdvanceResult resetQueued = job.advance(context(task));

        assertEquals(TaskState.QUEUED, resetQueued.getNextState());
        assertTrue(resetQueued.getCheckpoint().startsWith("v3|RESET|"));
        continueTask(task, resetQueued);
        AdvanceResult reset = job.advance(context(task));
        assertEquals(TaskState.WAITING_BACKOFF, reset.getNextState());
        assertTrue(reset.getCheckpoint().startsWith("v3|ADVERTISER|"));
        assertEquals(1, writer.resetCalls);
        assertTrue(provider.calls.isEmpty());
        assertTrue(writer.commands.isEmpty());
    }

    @Test
    void checkpointV3RoundTripsTwoPassAuthorityWithoutStagePayloads() {
        AdvertisingCampaignEnumerationAuthority expected = authority(1L);
        AdvertisingCheckpoint checkpoint = AdvertisingCheckpoint.restored(
                AdvertisingCheckpoint.Phase.APPLY,
                new AdvertisingAdvertiser("ADV_108065"),
                List.of(new AdvertisingCampaignObservation(
                        new AdvertisingCampaignRef("C-PAUSED", "Paused"), false
                )),
                expected,
                0,
                1,
                0,
                1L,
                0
        );
        AdvertisingCheckpointCodec codec = new AdvertisingCheckpointCodec();

        AdvertisingCheckpoint restored = codec.decode(codec.encode(checkpoint));

        assertEquals(expected, restored.getAuthority());
        assertEquals(1, restored.getCampaignPageCount());
        assertTrue(restored.activeCampaigns().isEmpty());
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

    private AdvanceResult runUntilPhase(
            Dp06AdvertisingJob job,
            DataPullTask task,
            AdvertisingCheckpoint.Phase phase,
            int maxSteps
    ) {
        AdvertisingCheckpointCodec codec = new AdvertisingCheckpointCodec();
        for (int step = 0; step < maxSteps; step++) {
            AdvanceResult result = job.advance(context(task));
            if (codec.decode(result.getCheckpoint()).getPhase() == phase) return result;
            continueTask(task, result);
        }
        throw new AssertionError("DP06 did not reach " + phase);
    }

    private String legacyCheckpoint() {
        return String.join(
                "|", "v2", "CAMPAIGN_QUERY", "0", "0",
                encoded("ADV_108065"), "1", encoded("C-LIVE-1"), encoded("First")
        );
    }

    private String encoded(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static final class CountingStageStore
            implements SnapshotStageStore<AdvertisingStagedFact> {
        private int stageCalls;

        @Override
        public SnapshotStageResult stagePage(
                long taskId,
                long fenceEpoch,
                SnapshotPage<AdvertisingStagedFact> page
        ) {
            stageCalls++;
            return SnapshotStageResult.staged(null, 1);
        }

        @Override
        public SnapshotStageProof<AdvertisingStagedFact> proveComplete(
                long taskId,
                long fenceEpoch
        ) {
            return SnapshotStageProof.incomplete("NO_STAGE_EXPECTED");
        }

        @Override
        public boolean clear(long taskId, long fenceEpoch) {
            return true;
        }
    }
}
