package com.nuono.next.datapull.advertising;

import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.authority;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.context;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.continueTask;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.job;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.queryReport;
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
    void providerAuthoritativeZeroIsTheOnlyZeroFactSuccess() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(new AdvertisingAdvertiser("ADV_108065")));
        provider.dashboards.add(ProviderOutcome.success(new AdvertisingDashboard(
                List.of(),
                List.of(),
                authority(0L)
        )));
        RecordingWriter writer = new RecordingWriter();
        Dp06AdvertisingJob job = job(provider, Dp06AdvertisingTestSupport.stageStore(), writer);
        DataPullTask task = task();

        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        AdvanceResult applied = job.advance(context(task));

        assertEquals(TaskState.SUCCEEDED, applied.getNextState());
        assertEquals(List.of("ADVERTISER", "DASHBOARD"), provider.calls);
        assertEquals(1, writer.commands.size());
        assertEquals(0L, writer.commands.get(0).getAuthority().getDeclaredCampaignCount());
        assertTrue(writer.commands.get(0).getActiveCampaigns().isEmpty());
    }

    @Test
    void contractErrorBeforeAuthorityStagesAndWritesNothing() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(new AdvertisingAdvertiser("ADV_108065")));
        provider.dashboards.add(ProviderOutcome.contractError("ADS_CAMPAIGN_AUTHORITY_MISSING"));
        CountingStageStore stage = new CountingStageStore();
        RecordingWriter writer = new RecordingWriter();
        Dp06AdvertisingJob job = job(provider, stage, writer);
        DataPullTask task = task();

        continueTask(task, job.advance(context(task)));
        AdvanceResult waiting = job.advance(context(task));

        assertEquals(TaskState.WAITING_BACKOFF, waiting.getNextState());
        assertEquals("ADS_CAMPAIGN_AUTHORITY_MISSING", waiting.getSanitizedCode());
        assertEquals(0, stage.stageCalls);
        assertTrue(writer.commands.isEmpty());
    }

    @Test
    void laterCampaignPageCannotChangeDashboardAuthority() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(new AdvertisingAdvertiser("ADV_108065")));
        provider.dashboards.add(ProviderOutcome.success(Dp06AdvertisingTestSupport.dashboard()));
        provider.queries("C-LIVE-1").add(ProviderOutcome.success(queryReport(List.of())));
        SnapshotStageStore<AdvertisingStagedFact> stage = Dp06AdvertisingTestSupport.stageStore();
        RecordingWriter writer = new RecordingWriter();
        Dp06AdvertisingJob job = job(provider, stage, writer);
        DataPullTask task = task();
        AdvertisingCheckpointCodec codec = new AdvertisingCheckpointCodec();

        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        AdvertisingCheckpoint original = codec.decode(task.getCheckpoint());
        AdvertisingCampaignEnumerationAuthority drift =
                AdvertisingCampaignEnumerationAuthority.fromProviderFields(
                        "different-generation",
                        original.getAuthority().getProviderAsOfUtc(),
                        original.getAuthority().getDeclaredCampaignCount(),
                        true
                );
        task.setCheckpoint(codec.encode(AdvertisingCheckpoint.restored(
                original.getPhase(),
                original.getAdvertiser(),
                original.getActiveCampaigns(),
                drift,
                original.getNextCampaignIndex(),
                original.getConsecutiveRetryAttempt()
        )));

        AdvanceResult resetQueued = job.advance(context(task));

        assertEquals(TaskState.QUEUED, resetQueued.getNextState());
        assertEquals(AdvertisingCheckpoint.Phase.RESET,
                codec.decode(resetQueued.getCheckpoint()).getPhase());
        continueTask(task, resetQueued);
        AdvanceResult waiting = job.advance(context(task));
        assertEquals(TaskState.WAITING_BACKOFF, waiting.getNextState());
        assertEquals("ADS_CONTAINER_RESTARTED", waiting.getSanitizedCode());
        assertEquals(1, writer.resetCalls);
        assertTrue(writer.commands.isEmpty());
    }

    @Test
    void legacyCampaignAndApplyCheckpointsResetBeforeAnyProviderOrFactCall() {
        for (AdvertisingCheckpoint.Phase phase : List.of(
                AdvertisingCheckpoint.Phase.CAMPAIGN_QUERY,
                AdvertisingCheckpoint.Phase.APPLY
        )) {
            ScriptedProvider provider = new ScriptedProvider();
            RejectingStageStore stage = new RejectingStageStore();
            RecordingWriter writer = new RecordingWriter();
            Dp06AdvertisingJob job = job(provider, stage, writer);
            DataPullTask task = task();
            task.setCheckpoint(legacyCheckpoint(phase));

            AdvanceResult resetQueued = job.advance(context(task));
            assertEquals(TaskState.QUEUED, resetQueued.getNextState());
            assertTrue(resetQueued.getCheckpoint().startsWith("v2|RESET|"));
            continueTask(task, resetQueued);
            AdvanceResult reset = job.advance(context(task));

            assertEquals(TaskState.WAITING_BACKOFF, reset.getNextState());
            assertEquals("ADS_CONTAINER_RESTARTED", reset.getSanitizedCode());
            assertTrue(reset.getCheckpoint().startsWith("v2|ADVERTISER|"));
            assertEquals(0, stage.clearCalls);
            assertEquals(1, writer.resetCalls);
            assertTrue(provider.calls.isEmpty());
            assertTrue(writer.commands.isEmpty());
        }
    }

    @Test
    void checkpointV2RoundTripsAuthorityWithoutMaterializingStageFacts() {
        AdvertisingCampaignEnumerationAuthority expected = authority(1L);
        AdvertisingCheckpoint checkpoint = AdvertisingCheckpoint.restored(
                AdvertisingCheckpoint.Phase.APPLY,
                new AdvertisingAdvertiser("ADV_108065"),
                List.of(),
                expected,
                0,
                0
        );
        AdvertisingCheckpointCodec codec = new AdvertisingCheckpointCodec();

        AdvertisingCheckpoint restored = codec.decode(codec.encode(checkpoint));
        assertEquals(expected, restored.getAuthority());
        assertTrue(restored.getActiveCampaigns().isEmpty());
    }

    private String legacyCheckpoint(AdvertisingCheckpoint.Phase phase) {
        int nextIndex = phase == AdvertisingCheckpoint.Phase.APPLY ? 1 : 0;
        return String.join(
                "|",
                "v1",
                phase.name(),
                "0",
                String.valueOf(nextIndex),
                encoded("ADV_108065"),
                "1",
                encoded("C-LIVE-1"),
                encoded("First")
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
        public SnapshotStageProof<AdvertisingStagedFact> proveComplete(long taskId, long fenceEpoch) {
            return SnapshotStageProof.incomplete("NO_STAGE_EXPECTED");
        }

        @Override
        public boolean clear(long taskId, long fenceEpoch) {
            return true;
        }
    }

}
