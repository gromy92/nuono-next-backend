package com.nuono.next.datapull.advertising;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.noonads.NoonAdvertisingCampaignFact;
import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdvertisingFactChunkPreparerTest {
    @Test
    void normalizedIdentityConflictsKeepFirstRowAndAccountLaterRows() {
        AdvertisingApplyCommand command = command();
        AdvertisingGenerationRow generation = generation();
        NoonAdvertisingCampaignFact laterCampaign = campaign();
        laterCampaign.setCampaignName("later campaign");
        NoonAdvertisingQueryFact firstQuery = query();
        firstQuery.setPartnerSku(" ZSKU-1 ");
        firstQuery.setAdSkuCode("");
        NoonAdvertisingQueryFact laterQuery = query();
        laterQuery.setPartnerSku("");
        laterQuery.setAdSkuCode("ZSKU-1");
        laterQuery.setSpendAmount(new BigDecimal("99.00"));

        AdvertisingFactChunk chunk = new AdvertisingFactChunkPreparer().prepare(
                command,
                generation,
                List.of(
                        raw(1, 0, 2, AdvertisingStagedFact.campaign(campaign())),
                        raw(1, 1, 2, AdvertisingStagedFact.campaign(laterCampaign)),
                        raw(2, 0, 3, AdvertisingStagedFact.queryPageProof(
                                new AdvertisingCampaignRef("C-LIVE-1", "Campaign"))),
                        raw(2, 1, 3, AdvertisingStagedFact.query(firstQuery)),
                        raw(2, 2, 3, AdvertisingStagedFact.query(laterQuery))
                ),
                Set.of()
        );

        assertEquals(5, chunk.getProcessedCount());
        assertEquals(1, chunk.getQueryPageProofCount());
        assertEquals(2, chunk.getSkippedIdentityCount());
        assertEquals(1, chunk.getCampaignSkippedIdentityCount());
        assertEquals(1, chunk.getCampaigns().size());
        assertEquals(1, chunk.getQueries().size());
        assertEquals("Campaign", chunk.getCampaigns().get(0).getCampaign().getCampaignName());
        assertEquals(new BigDecimal("2.00"),
                chunk.getQueries().get(0).getQuery().getSpendAmount());
        assertTrue(chunk.getQueries().get(0).getQuery().getPartnerSku().isEmpty());
        accountGeneration(generation);
        new AdvertisingGenerationGuard().requireCompleteAccounting(generation);
    }

    private AdvertisingApplyCommand command() {
        DataPullTask task = DataPullTask.queued(
                6001L, OperationCode.DP06, "noon-admanager", 307L, 108065L,
                "PRJ108065", null, "PRJ108065", "STR108065-NSA", "SA",
                "scope-sa", LocalDateTime.of(2026, 8, 1, 22, 30),
                "DP06:date-range:2026-08-01..2026-08-01", "ADS_ADVERTISER",
                LocalDateTime.of(2026, 8, 1, 22, 0)
        );
        return new AdvertisingApplyCommand(
                task.getId(), 7L, "worker-1", task.getScheduleSlot(),
                AdvertisingPullRequest.from(task), task.getBusinessWindowKey(),
                AdvertisingCampaignEnumerationAuthority.fromTwoPassObservation(
                        "1".repeat(64), 2L, true
                ),
                List.of(new AdvertisingCampaignRef("C-LIVE-1", "Campaign")),
                1
        );
    }

    private AdvertisingGenerationRow generation() {
        AdvertisingGenerationRow row = new AdvertisingGenerationRow();
        row.setStagedCampaignItemCount(2L);
        row.setCampaignBusinessSkippedItemCount(0L);
        row.setDeclaredCampaignCount(2L);
        row.setStagedItemCount(5L);
        row.setSourceItemCount(5L);
        row.setBusinessSkippedItemCount(0L);
        row.setActiveCampaignCount(1);
        row.setLastPage(2);
        row.setCursorPageNo(0);
        row.setCursorItemOrdinal(-1);
        row.setProcessedItemCount(0L);
        row.setCampaignFactCount(0L);
        row.setQueryFactCount(0L);
        row.setCampaignIdStart(210001L);
        row.setQueryIdStart(220001L);
        row.setBatchId(200001L);
        row.setDigestChainSha256("0".repeat(64));
        return row;
    }

    private void accountGeneration(AdvertisingGenerationRow row) {
        row.setProcessedItemCount(5L);
        row.setCampaignFactCount(1L);
        row.setQueryFactCount(1L);
        row.setIdentitySkippedItemCount(2L);
        row.setCampaignIdentitySkippedItemCount(1L);
        row.setQueryPageProofCount(1);
        row.setMatchedActiveCampaignCount(1);
    }

    private AdvertisingRawStageRow raw(
            int page, int ordinal, int count, AdvertisingStagedFact fact
    ) {
        AdvertisingStagedFactCodec codec = new AdvertisingStagedFactCodec();
        AdvertisingRawStageRow row = new AdvertisingRawStageRow();
        row.setTaskId(6001L);
        row.setPageNo(page);
        row.setItemOrdinal(ordinal);
        row.setPageItemCount(count);
        row.setStableIdentity(codec.stableIdentity(fact));
        row.setContentFingerprint(codec.stableContentFingerprint(fact));
        row.setPayload(codec.encode(fact));
        return row;
    }

    private NoonAdvertisingCampaignFact campaign() {
        NoonAdvertisingCampaignFact fact = new NoonAdvertisingCampaignFact();
        fact.setCampaignCode("C-LIVE-1");
        fact.setCampaignName("Campaign");
        fact.setCampaignStatus("live");
        fact.setSpendAmount(new BigDecimal("10.00"));
        return fact;
    }

    private NoonAdvertisingQueryFact query() {
        NoonAdvertisingQueryFact fact = new NoonAdvertisingQueryFact();
        fact.setCampaignCode("C-LIVE-1");
        fact.setCampaignName("Campaign");
        fact.setAdSkuCode("ZSKU-1");
        fact.setQueryText("paper towel");
        fact.setQueryKind("search_term");
        fact.setSpendAmount(new BigDecimal("2.00"));
        return fact;
    }
}
