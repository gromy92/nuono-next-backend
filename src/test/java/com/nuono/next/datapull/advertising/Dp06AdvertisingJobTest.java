package com.nuono.next.datapull.advertising;

import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.context;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.campaign;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.continueTask;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.dashboard;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.job;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.query;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.queryReport;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.stageStore;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.task;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.RecordingWriter;
import com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.ScriptedProvider;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.datapull.snapshot.SnapshotStageStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class Dp06AdvertisingJobTest {

    @Test
    void stagedCodecRemainsDeterministicForNullableDatesAndUnicode() {
        AdvertisingStagedFactCodec codec = new AdvertisingStagedFactCodec();
        com.nuono.next.noonads.NoonAdvertisingCampaignFact campaign =
                campaign("C-LIVE-1", "live");
        campaign.setCampaignName("مناديل 厨房");
        campaign.setCampaignStartDate(null);
        AdvertisingStagedFact original = AdvertisingStagedFact.campaign(campaign);

        String payload = codec.encode(original);
        AdvertisingStagedFact decoded = codec.decode(payload);

        assertEquals(payload, codec.encode(decoded));
        assertEquals(codec.stableIdentity(original), codec.stableIdentity(decoded));
        assertEquals(codec.stableContentFingerprint(original),
                codec.stableContentFingerprint(decoded));
        assertEquals("مناديل 厨房", decoded.getCampaignFact().getCampaignName());
        assertNull(decoded.getCampaignFact().getCampaignStartDate());
        assertEquals(76, codec.stableIdentity(original).length());
        decoded.getCampaignFact().setSpendAmount(new java.math.BigDecimal("11.00"));
        assertNotEquals(codec.stableContentFingerprint(original),
                codec.stableContentFingerprint(decoded));
    }

    @Test
    void queryStageIdentityStaysBoundedAtMaximumPersistableFieldWidths() {
        com.nuono.next.noonads.NoonAdvertisingQueryFact fact =
                new com.nuono.next.noonads.NoonAdvertisingQueryFact();
        fact.setCampaignCode("C".repeat(120));
        fact.setPartnerSku("P".repeat(160));
        fact.setAdSkuCode("S".repeat(160));
        fact.setQueryText("Q".repeat(1000));
        fact.setQueryKind("K".repeat(40));

        String identity = new AdvertisingStagedFactCodec().stableIdentity(
                AdvertisingStagedFact.query(fact)
        );

        assertEquals(73, identity.length());
        assertTrue(identity.startsWith("query:v1:"));
    }

    @Test
    void keepsRawDashboardCountButCallsEachActiveIdentityOnlyOnce() {
        AdvertisingDashboard dashboard = new AdvertisingDashboard(
                List.of(
                        campaign("C-LIVE-1", "live"),
                        campaign("C-LIVE-1", "active"),
                        campaign("C-LIVE-2", "live")
                ),
                List.of(
                        new AdvertisingCampaignRef("C-LIVE-1", "First"),
                        new AdvertisingCampaignRef("C-LIVE-1", "Conflicting later row"),
                        new AdvertisingCampaignRef("C-LIVE-2", "Second")
                ),
                Dp06AdvertisingTestSupport.authority(3L)
        );

        assertEquals(3, dashboard.getCampaignFacts().size());
        assertEquals(2, dashboard.getActiveCampaigns().size());
        assertEquals("First", dashboard.getActiveCampaigns().get(0).getCampaignName());

        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(new AdvertisingAdvertiser("ADV_108065")));
        provider.dashboards.add(ProviderOutcome.success(dashboard));
        provider.queries("C-LIVE-1").add(ProviderOutcome.success(queryReport(List.of())));
        provider.queries("C-LIVE-2").add(ProviderOutcome.success(queryReport(List.of())));
        RecordingWriter writer = new RecordingWriter();
        Dp06AdvertisingJob job = job(provider, stageStore(), writer);
        DataPullTask task = task();

        for (int step = 0; step < 4; step++) {
            continueTask(task, job.advance(context(task)));
        }
        AdvanceResult applied = job.advance(context(task));

        assertEquals(TaskState.SUCCEEDED, applied.getNextState());
        assertEquals(
                List.of("ADVERTISER", "DASHBOARD", "QUERY:C-LIVE-1", "QUERY:C-LIVE-2"),
                provider.calls
        );
        assertEquals(2, writer.commands.get(0).getActiveCampaignCount());
        assertEquals(3L, writer.commands.get(0).getAuthority().getDeclaredCampaignCount());
    }

    @Test
    void callsAdvertiserDashboardAndEveryActiveCampaignThenAppliesOnce() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(new AdvertisingAdvertiser("ADV_108065")));
        provider.dashboards.add(ProviderOutcome.success(dashboard()));
        provider.queries("C-LIVE-1").add(ProviderOutcome.success(queryReport(List.of(
                query("C-LIVE-1", "paper towel", "1.00"),
                query("C-LIVE-1", "paper towel", "99.00")
        ))));
        provider.queries("C-LIVE-2").add(ProviderOutcome.success(queryReport(List.of(
                query("C-LIVE-2", "kitchen roll", "2.00")
        ))));
        RecordingWriter writer = new RecordingWriter();
        SnapshotStageStore<AdvertisingStagedFact> stage = stageStore();
        DataPullTask task = task();

        Dp06AdvertisingJob firstProcess = job(provider, stage, writer);
        continueTask(task, firstProcess.advance(context(task)));
        continueTask(task, firstProcess.advance(context(task)));
        Dp06AdvertisingJob restarted = job(provider, stage, writer);
        continueTask(task, restarted.advance(context(task)));
        continueTask(task, restarted.advance(context(task)));
        assertTrue(writer.commands.isEmpty());
        AdvanceResult applied = restarted.advance(context(task));

        assertEquals(TaskState.SUCCEEDED, applied.getNextState());
        assertEquals(
                List.of("ADVERTISER", "DASHBOARD", "QUERY:C-LIVE-1", "QUERY:C-LIVE-2"),
                provider.calls
        );
        assertEquals(4, provider.calls.size(), "successful DP-06 must perform exactly 2+C calls");
        assertEquals(1, writer.commands.size());
        AdvertisingApplyCommand command = writer.commands.get(0);
        assertEquals(2, command.getActiveCampaigns().size());
        assertEquals(3L, command.getAuthority().getDeclaredCampaignCount());
        assertEquals("DP06:date-range:2026-08-01..2026-08-01", command.getBusinessWindowKey());
    }

    @Test
    void campaignFailureWaitsAtThatCampaignAndNeverAppliesPartialFacts() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(new AdvertisingAdvertiser("ADV_108065")));
        provider.dashboards.add(ProviderOutcome.success(dashboard()));
        provider.queries("C-LIVE-1").add(ProviderOutcome.success(queryReport(List.of(
                query("C-LIVE-1", "first", "1.00")
        ))));
        provider.queries("C-LIVE-2").add(ProviderOutcome.transientFailure("ADS_TIMEOUT"));
        provider.queries("C-LIVE-2").add(ProviderOutcome.success(queryReport(List.of(
                query("C-LIVE-2", "second", "2.00")
        ))));
        RecordingWriter writer = new RecordingWriter();
        Dp06AdvertisingJob job = job(provider, stageStore(), writer);
        DataPullTask task = task();

        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        AdvanceResult waiting = job.advance(context(task));

        assertEquals(TaskState.WAITING_BACKOFF, waiting.getNextState());
        assertEquals("ADS_TIMEOUT", waiting.getSanitizedCode());
        assertTrue(writer.commands.isEmpty());
        continueTask(task, waiting);
        continueTask(task, job.advance(context(task)));
        AdvanceResult applied = job.advance(context(task));
        assertEquals(TaskState.SUCCEEDED, applied.getNextState());
        assertEquals(1, writer.commands.size());
        assertEquals(
                List.of(
                        "ADVERTISER",
                        "DASHBOARD",
                        "QUERY:C-LIVE-1",
                        "QUERY:C-LIVE-2",
                        "QUERY:C-LIVE-2"
                ),
                provider.calls
        );
    }
}
