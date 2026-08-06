package com.nuono.next.datapull.advertising;

import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.campaign;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.campaignPage;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.context;
import static com.nuono.next.datapull.advertising.Dp06AdvertisingTestSupport.continueTask;
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
    void performsExactlyOnePlusTwoPagesPlusActiveCampaignCalls() {
        ScriptedProvider provider = successfulProvider();
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task();

        AdvanceResult result = runUntilNotQueued(
                job(provider, stageStore(), writer), task, 20
        );

        assertEquals(TaskState.SUCCEEDED, result.getNextState());
        assertEquals(List.of(
                "ADVERTISER", "CAMPAIGNS:1", "CAMPAIGNS:1",
                "QUERY:C-LIVE-1", "QUERY:C-LIVE-2"
        ), provider.calls);
        assertEquals(5, provider.calls.size(), "DP06 call count must be 1+2P+C");
        assertEquals(1, writer.commands.size());
        AdvertisingApplyCommand command = writer.commands.get(0);
        assertEquals(1, command.getCampaignPageCount());
        assertEquals(2, command.getActiveCampaignCount());
        assertEquals(3L, command.getAuthority().getDeclaredCampaignCount());
        assertNull(command.getAuthority().getProviderAsOfUtc());
    }

    @Test
    void firstValidDuplicateCampaignIdentityWinsAndLaterConflictIsNotQueried() {
        com.nuono.next.noonads.NoonAdvertisingCampaignFact first =
                campaign("C-SAME", "live");
        first.setCampaignName("first valid");
        com.nuono.next.noonads.NoonAdvertisingCampaignFact later =
                campaign("C-SAME", "paused");
        later.setCampaignName("later conflict");
        AdvertisingCampaignPage page = campaignPage(
                1, 1, 2L, List.of(first, later)
        );
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(
                new AdvertisingAdvertiser("ADV_108065")
        ));
        provider.campaignPages.add(ProviderOutcome.success(page));
        provider.campaignPages.add(ProviderOutcome.success(page));
        provider.queries("C-SAME").add(ProviderOutcome.success(queryReport(List.of())));
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task();

        AdvanceResult result = runUntilNotQueued(
                job(provider, stageStore(), writer), task, 20
        );

        assertEquals(TaskState.SUCCEEDED, result.getNextState());
        assertEquals(List.of(
                "ADVERTISER", "CAMPAIGNS:1", "CAMPAIGNS:1", "QUERY:C-SAME"
        ), provider.calls);
        assertEquals("first valid",
                writer.commands.get(0).getActiveCampaigns().get(0).getCampaignName());
    }

    @Test
    void oneCampaignFailureBacksOffAtThatCampaignWithoutApplyingPartialFacts() {
        ScriptedProvider provider = successfulProvider();
        provider.queries("C-LIVE-2").clear();
        provider.queries("C-LIVE-2").add(ProviderOutcome.transientFailure("ADS_TIMEOUT"));
        provider.queries("C-LIVE-2").add(ProviderOutcome.success(queryReport(List.of(
                query("C-LIVE-2", "second", "2.00")
        ))));
        RecordingWriter writer = new RecordingWriter();
        Dp06AdvertisingJob job = job(provider, stageStore(), writer);
        DataPullTask task = task();

        AdvanceResult waiting = runUntilNotQueued(job, task, 20);
        assertEquals(TaskState.WAITING_BACKOFF, waiting.getNextState());
        assertEquals("ADS_TIMEOUT", waiting.getSanitizedCode());
        assertTrue(writer.commands.isEmpty());

        continueTask(task, waiting);
        AdvanceResult applied = runUntilNotQueued(job, task, 5);
        assertEquals(TaskState.SUCCEEDED, applied.getNextState());
        assertEquals(1, writer.commands.size());
        assertEquals(2, provider.calls.stream()
                .filter("QUERY:C-LIVE-2"::equals).count());
    }

    private ScriptedProvider successfulProvider() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.advertisers.add(ProviderOutcome.success(
                new AdvertisingAdvertiser("ADV_108065")
        ));
        provider.campaignPages.add(ProviderOutcome.success(campaignPage()));
        provider.campaignPages.add(ProviderOutcome.success(campaignPage()));
        provider.queries("C-LIVE-1").add(ProviderOutcome.success(queryReport(List.of(
                query("C-LIVE-1", "paper towel", "1.00")
        ))));
        provider.queries("C-LIVE-2").add(ProviderOutcome.success(queryReport(List.of(
                query("C-LIVE-2", "kitchen roll", "2.00")
        ))));
        return provider;
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
}
