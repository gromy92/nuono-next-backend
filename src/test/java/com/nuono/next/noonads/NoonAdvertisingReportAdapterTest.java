package com.nuono.next.noonads;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonReportDownloadedFile;
import com.nuono.next.noonpull.NoonReportProcessResult;
import com.nuono.next.noonpull.NoonReportPullRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonAdvertisingReportAdapterTest {

    @Test
    void importsCampaignAndQueryRowsFromDownloadedCsv() {
        RecordingImportRepository repository = new RecordingImportRepository();
        NoonAdvertisingReportAdapter adapter =
                new NoonAdvertisingReportAdapter(new NoonAdvertisingImportService(repository));

        NoonReportProcessResult result = adapter.process(new NoonReportDownloadedFile(
                NoonReportPullRequest.builder()
                        .ownerUserId(10002L)
                        .storeCode("STR69486-NSA")
                        .siteCode("SA")
                        .dataDomain(NoonPullDataDomain.NOON_ADVERTISING)
                        .reportType(NoonAdvertisingReportDescriptor.DEFAULT_REPORT_TYPE)
                        .dateFrom(LocalDate.of(2026, 7, 3))
                        .dateTo(LocalDate.of(2026, 7, 3))
                        .build(),
                "EXP-ADS-1",
                "noon-report-noon_advertising-1-abcdef12",
                "abcdef1234567890",
                csv().getBytes(StandardCharsets.UTF_8)
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED, result.getCode());
        assertEquals(2, result.getImportedCount());
        assertEquals(0, result.getExceptionCount());
        assertEquals(1, repository.insertedBatches.size());
        NoonAdvertisingReportBatch batch = repository.insertedBatches.get(0);
        assertEquals("noon_ads", batch.getSourceSystem());
        assertEquals("EXP-ADS-1", batch.getSourceName());
        assertEquals("abcdef1234567890", batch.getSourceDigestSha256());
        assertEquals(10002L, batch.getOwnerUserId());
        assertEquals(10002L, batch.getCreatedBy());
        assertEquals("PRJ69486", batch.getProjectCode());
        assertEquals("STR69486-NSA", batch.getStoreCode());
        assertEquals("SA", batch.getSiteCode());
        assertEquals(LocalDate.of(2026, 7, 3), batch.getReportDateFrom());
        assertEquals(LocalDate.of(2026, 7, 3), batch.getReportDateTo());
        assertEquals(1, batch.getCampaignRowCount());
        assertEquals(1, batch.getQueryRowCount());

        NoonAdvertisingCampaignFact campaign = repository.campaignFacts.get(0);
        assertEquals("C_ADS_001", campaign.getCampaignCode());
        assertEquals("Summer core exact", campaign.getCampaignName());
        assertEquals("ZADS001-1", campaign.getPrimaryAdSkuCode());
        assertEquals("PAPER001", campaign.getPrimaryPartnerSku());
        assertEquals("active", campaign.getCampaignStatus());
        assertEquals(1200L, campaign.getViews());
        assertEquals(80L, campaign.getClicks());
        assertEquals(12L, campaign.getOrdersCount());
        assertEquals(new BigDecimal("150.59"), campaign.getSpendAmount());
        assertEquals(new BigDecimal("450.10"), campaign.getAdRevenue());

        NoonAdvertisingQueryFact query = repository.queryFacts.get(0);
        assertEquals("C_ADS_001", query.getCampaignCode());
        assertEquals("ZADS001-1", query.getAdSkuCode());
        assertEquals("PAPER001", query.getPartnerSku());
        assertEquals("notebook", query.getQueryText());
        assertEquals("exact", query.getQueryKind());
        assertEquals(90L, query.getViews());
        assertEquals(10L, query.getClicks());
        assertEquals(2L, query.getOrdersCount());
        assertEquals(new BigDecimal("21.57"), query.getSpendAmount());
        assertEquals(new BigDecimal("82.30"), query.getAdRevenue());
    }

    @Test
    void importsHistoricalNormalizedCsvHeadersWithoutRowType() {
        RecordingImportRepository repository = new RecordingImportRepository();
        NoonAdvertisingReportAdapter adapter =
                new NoonAdvertisingReportAdapter(new NoonAdvertisingImportService(repository));

        NoonReportProcessResult campaignResult = adapter.process(new NoonReportDownloadedFile(
                request(),
                "campaign-metrics",
                "ads-campaigns",
                "campaign-digest",
                historicalCampaignCsv().getBytes(StandardCharsets.UTF_8)
        ));
        NoonReportProcessResult queryResult = adapter.process(new NoonReportDownloadedFile(
                request(),
                "query-metrics",
                "ads-queries",
                "query-digest",
                historicalQueryCsv().getBytes(StandardCharsets.UTF_8)
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED, campaignResult.getCode());
        assertEquals(NoonReportProcessResult.Code.SUCCEEDED, queryResult.getCode());
        assertEquals(1, repository.campaignFacts.size());
        assertEquals(1, repository.queryFacts.size());
        NoonAdvertisingCampaignFact campaign = repository.campaignFacts.get(0);
        assertEquals("C_U9Q0F611VL", campaign.getCampaignCode());
        assertEquals("ZD-一个黑色射灯", campaign.getCampaignName());
        assertEquals("ADG_732M6B3ETP", campaign.getAdgroupCode());
        assertEquals(LocalDate.of(2025, 10, 31), campaign.getCampaignStartDate());
        assertEquals(new BigDecimal("0.0274"), campaign.getCtrPercentage());
        assertEquals(new BigDecimal("0.0761"), campaign.getCvrPercentage());

        NoonAdvertisingQueryFact query = repository.queryFacts.get(0);
        assertEquals("C_U9Q0F611VL", query.getCampaignCode());
        assertEquals("ZBA4C707B499F0C0F5468Z-1", query.getAdSkuCode());
        assertEquals("اضاءة شجر", query.getQueryText());
        assertEquals("arabic_search_term", query.getQueryKind());
        assertEquals(new BigDecimal("0.3171"), query.getCtrPercentage());
        assertEquals(new BigDecimal("0.1538"), query.getCvrPercentage());
    }

    private NoonReportPullRequest request() {
        return NoonReportPullRequest.builder()
                .ownerUserId(10002L)
                .storeCode("STR69486-NSA")
                .siteCode("SA")
                .dataDomain(NoonPullDataDomain.NOON_ADVERTISING)
                .reportType(NoonAdvertisingReportDescriptor.DEFAULT_REPORT_TYPE)
                .dateFrom(LocalDate.of(2026, 7, 3))
                .dateTo(LocalDate.of(2026, 7, 3))
                .build();
    }

    private String csv() {
        return "row_type,project_code,campaign_code,campaign_name,ad_sku_code,partner_sku,query_text,query_kind,"
                + "campaign_status,views,clicks,orders_count,assisted_orders,atc_count,spend_amount,ad_revenue,"
                + "ctr_percentage,roas,cpc,cps,cvr_percentage\n"
                + "campaign,PRJ69486,C_ADS_001,Summer core exact,ZADS001-1,PAPER001,,,active,"
                + "1200,80,12,3,14,150.59,450.10,0.066667,2.988247,1.882375,12.549167,0.150000\n"
                + "query,PRJ69486,C_ADS_001,Summer core exact,ZADS001-1,PAPER001,notebook,exact,,"
                + "90,10,2,1,4,21.57,82.30,0.111111,3.815484,2.157000,10.785000,0.200000\n";
    }

    private String historicalCampaignCsv() {
        return "rank_by_spend,Campaign Name,campaignCode,match_status,candidate_count,match_score,status,qcStatus,"
                + "Views,Clicks,Orders,Assisted Orders,ATC,Spends,Revenue,CTR,ROAS,CPC,CPS,CVR,"
                + "adgroupCode,campaignStartDate,campaignEndDate\n"
                + "1,ZD-一个黑色射灯,C_U9Q0F611VL,api_campaign_metrics,1,,live,live,"
                + "19707,539,41,0,166,190.68,1570.15,2.74,8.23,0.35,4.65,7.61,"
                + "ADG_732M6B3ETP,2025-10-31,2099-12-31\n";
    }

    private String historicalQueryCsv() {
        return "rank,campaignCode,campaignName,sku,query,queryType,views,clicks,orders,assistedOrders,"
                + "atc,spend,revenue,ctr,roas,cpc,cps,cvr\n"
                + "1,C_U9Q0F611VL,ZD-一个黑色射灯,ZBA4C707B499F0C0F5468Z-1,اضاءة شجر,"
                + "arabic_search_term,82,26,4,0,12,10.92,163.25,31.71,14.95,0.42,2.73,15.38\n";
    }

    private static class RecordingImportRepository implements NoonAdvertisingImportRepository {
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
        public Long findReportBatchId(NoonAdvertisingReportBatch batch) {
            return null;
        }

        @Override
        public void insertReportBatch(NoonAdvertisingReportBatch batch) {
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
    }
}
