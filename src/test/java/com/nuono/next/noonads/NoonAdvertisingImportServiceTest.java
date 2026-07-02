package com.nuono.next.noonads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonAdvertisingImportServiceTest {

    @Test
    void importsNormalizedReportRowsWithBatchScopeAndQueryHash() {
        RecordingNoonAdvertisingImportRepository repository = new RecordingNoonAdvertisingImportRepository();
        NoonAdvertisingImportService service = new NoonAdvertisingImportService(repository);

        NoonAdvertisingImportResult result = service.importReport(new NoonAdvertisingReportImportCommand(
                10002L,
                10003L,
                "PRJ69486",
                "STR69486-NSA",
                "SA",
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 6, 25),
                "campaign-query-normalized.json",
                "sha256-sample",
                "manual normalized import",
                List.of(campaignFact()),
                List.of(queryFact())
        ));

        assertEquals(200001L, result.getBatchId());
        assertEquals(1, result.getCampaignRowCount());
        assertEquals(1, result.getQueryRowCount());
        assertEquals(200001L, repository.insertedBatch.getId());
        assertEquals("noon_ads", repository.insertedBatch.getSourceSystem());
        assertEquals("campaign-query-normalized.json", repository.insertedBatch.getSourceName());
        assertEquals("sha256-sample", repository.insertedBatch.getSourceDigestSha256());
        assertEquals(10002L, repository.insertedBatch.getOwnerUserId());
        assertEquals(10003L, repository.insertedBatch.getCreatedBy());
        assertEquals("PRJ69486", repository.insertedBatch.getProjectCode());
        assertEquals("STR69486-NSA", repository.insertedBatch.getStoreCode());
        assertEquals("SA", repository.insertedBatch.getSiteCode());
        assertEquals(LocalDate.of(2026, 5, 26), repository.insertedBatch.getReportDateFrom());
        assertEquals(LocalDate.of(2026, 6, 25), repository.insertedBatch.getReportDateTo());
        assertEquals("imported", repository.insertedBatch.getStatus());
        assertEquals(1, repository.insertedBatch.getCampaignRowCount());
        assertEquals(1, repository.insertedBatch.getQueryRowCount());

        NoonAdvertisingCampaignFact campaign = repository.campaignFacts.get(0);
        assertEquals(210001L, campaign.getId());
        assertEquals(200001L, campaign.getBatchId());
        assertEquals("PRJ69486", campaign.getProjectCode());
        assertEquals("C_U9Q0F611VL", campaign.getCampaignCode());
        assertEquals(new BigDecimal("150.59"), campaign.getSpendAmount());

        NoonAdvertisingQueryFact query = repository.queryFacts.get(0);
        assertEquals(220001L, query.getId());
        assertEquals(200001L, query.getBatchId());
        assertEquals("C_NBN9Z09F58", query.getCampaignCode());
        assertEquals("ZDD-EXTERNAL-001", query.getAdSkuCode());
        assertEquals("SGGR001", query.getPartnerSku());
        assertEquals("STR69486-NSA|SA|SGGR001", query.getProductIdentityKey());
        assertEquals("STR69486-NSA|SA|SGGR001", query.getAdvertisingIdentityKey());
        assertEquals("دبدوب", query.getQueryText());
        assertEquals("broad", query.getQueryKind());
        assertNotNull(query.getQueryHash());
        assertEquals(64, query.getQueryHash().length());
    }

    @Test
    void reimportingSameDigestReusesExistingBatch() {
        RecordingNoonAdvertisingImportRepository repository = new RecordingNoonAdvertisingImportRepository();
        NoonAdvertisingImportService service = new NoonAdvertisingImportService(repository);
        NoonAdvertisingReportImportCommand command = new NoonAdvertisingReportImportCommand(
                10002L,
                10003L,
                "PRJ69486",
                "STR69486-NSA",
                "SA",
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 6, 25),
                "campaign-query-normalized.json",
                "sha256-sample",
                "manual normalized import",
                List.of(campaignFact()),
                List.of(queryFact())
        );

        NoonAdvertisingImportResult first = service.importReport(command);
        NoonAdvertisingImportResult second = service.importReport(command);

        assertEquals(first.getBatchId(), second.getBatchId());
        assertEquals(1, repository.insertedBatches.size());
        assertEquals(2, repository.campaignFacts.size());
        assertEquals(2, repository.queryFacts.size());
        assertEquals(first.getBatchId(), repository.campaignFacts.get(1).getBatchId());
        assertEquals(first.getBatchId(), repository.queryFacts.get(1).getBatchId());
    }

    @Test
    void treatsNoonExternalSkuCodeAsAdSkuCodeWhenSubmittedAsPartnerSku() {
        RecordingNoonAdvertisingImportRepository repository = new RecordingNoonAdvertisingImportRepository();
        NoonAdvertisingImportService service = new NoonAdvertisingImportService(repository);
        NoonAdvertisingQueryFact query = queryFact();
        query.setAdSkuCode(null);
        query.setPartnerSku("ZDD2976030B3514036193Z-1");

        service.importReport(new NoonAdvertisingReportImportCommand(
                10002L,
                10003L,
                "PRJ69486",
                "STR69486-NSA",
                "SA",
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 6, 25),
                "campaign-query-normalized.json",
                "sha256-sample",
                "manual normalized import",
                List.of(campaignFact()),
                List.of(query)
        ));

        NoonAdvertisingQueryFact imported = repository.queryFacts.get(0);
        assertEquals("ZDD2976030B3514036193Z-1", imported.getAdSkuCode());
        assertEquals("", imported.getPartnerSku());
        assertEquals("", imported.getProductIdentityKey());
        assertEquals("STR69486-NSA|SA|ADSKU|ZDD2976030B3514036193Z-1", imported.getAdvertisingIdentityKey());
    }

    private NoonAdvertisingCampaignFact campaignFact() {
        NoonAdvertisingCampaignFact fact = new NoonAdvertisingCampaignFact();
        fact.setCampaignCode("C_U9Q0F611VL");
        fact.setCampaignName("Brand towels exact");
        fact.setCampaignStatus("active");
        fact.setViews(1000);
        fact.setClicks(80);
        fact.setOrdersCount(35);
        fact.setSpendAmount(new BigDecimal("150.59"));
        fact.setAdRevenue(new BigDecimal("1335.10"));
        fact.setZeroOrderSpendAmount(new BigDecimal("120.40"));
        fact.setZeroOrderSpendShare(new BigDecimal("0.799500"));
        return fact;
    }

    private NoonAdvertisingQueryFact queryFact() {
        NoonAdvertisingQueryFact fact = new NoonAdvertisingQueryFact();
        fact.setCampaignCode("C_NBN9Z09F58");
        fact.setCampaignName("Search broad");
        fact.setAdSkuCode("ZDD-EXTERNAL-001");
        fact.setPartnerSku("SGGR001");
        fact.setQueryText("دبدوب");
        fact.setQueryKind("broad");
        fact.setSpendAmount(new BigDecimal("21.57"));
        fact.setOrdersCount(0);
        fact.setAdRevenue(BigDecimal.ZERO);
        return fact;
    }

    private static class RecordingNoonAdvertisingImportRepository implements NoonAdvertisingImportRepository {
        private NoonAdvertisingReportBatch insertedBatch;
        private final List<NoonAdvertisingReportBatch> insertedBatches = new ArrayList<>();
        private final List<NoonAdvertisingCampaignFact> campaignFacts = new ArrayList<>();
        private final List<NoonAdvertisingQueryFact> queryFacts = new ArrayList<>();

        @Override
        public Long nextReportBatchId() {
            return 200001L + insertedBatches.size();
        }

        @Override
        public Long nextCampaignFactId() {
            return 210001L + campaignFacts.size();
        }

        @Override
        public Long nextQueryFactId() {
            return 220001L + queryFacts.size();
        }

        @Override
        public void insertReportBatch(NoonAdvertisingReportBatch batch) {
            insertedBatch = batch;
            insertedBatches.add(batch);
        }

        @Override
        public void upsertCampaignFact(NoonAdvertisingCampaignFact fact) {
            campaignFacts.add(fact);
        }

        @Override
        public void upsertQueryFact(NoonAdvertisingQueryFact fact) {
            queryFacts.add(fact);
        }

        @Override
        public Long findReportBatchId(NoonAdvertisingReportBatch batch) {
            return insertedBatches.stream()
                    .filter(candidate -> candidate.getSourceSystem().equals(batch.getSourceSystem()))
                    .filter(candidate -> candidate.getOwnerUserId().equals(batch.getOwnerUserId()))
                    .filter(candidate -> candidate.getProjectCode().equals(batch.getProjectCode()))
                    .filter(candidate -> candidate.getStoreCode().equals(batch.getStoreCode()))
                    .filter(candidate -> candidate.getSiteCode().equals(batch.getSiteCode()))
                    .filter(candidate -> candidate.getReportDateFrom().equals(batch.getReportDateFrom()))
                    .filter(candidate -> candidate.getReportDateTo().equals(batch.getReportDateTo()))
                    .filter(candidate -> candidate.getSourceDigestSha256().equals(batch.getSourceDigestSha256()))
                    .map(NoonAdvertisingReportBatch::getId)
                    .findFirst()
                    .orElse(null);
        }
    }
}
