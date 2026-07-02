package com.nuono.next.noonads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonAdvertisingAnalyticsServiceTest {

    @Test
    void buildsDashboardFromAdAndNaturalSalesFacts() {
        RecordingNoonAdvertisingRepository repository = new RecordingNoonAdvertisingRepository();
        repository.adSummary = adSummary();
        repository.salesSummary = new NoonAdvertisingSalesSummaryView(
                2120,
                new BigDecimal("85828.86")
        );
        repository.campaignRows = List.of(campaignRow());
        repository.productRows = List.of(productRow());
        repository.zeroOrderQueries = List.of(zeroOrderQuery());
        repository.winningQueries = List.of(winningQuery());
        repository.dataStatus = new NoonAdvertisingDataStatus(
                1,
                1,
                2,
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 6, 25),
                true
        );
        NoonAdvertisingAnalyticsService service = new NoonAdvertisingAnalyticsService(repository);

        NoonAdvertisingDashboardView view = service.dashboard(defaultQuery());

        assertEquals(10002L, repository.lastQuery.getOwnerUserId());
        assertEquals("PRJ69486", repository.lastQuery.getProjectCode());
        assertEquals("STR69486-NSA", repository.lastQuery.getStoreCode());
        assertEquals("SA", repository.lastQuery.getSiteCode());
        assertEquals(LocalDate.of(2026, 5, 26), repository.lastQuery.getDateFrom());
        assertEquals(LocalDate.of(2026, 6, 25), repository.lastQuery.getDateTo());
        assertEquals(new BigDecimal("5006.61"), view.getAdSummary().getSpendAmount());
        assertEquals(new BigDecimal("34862.09"), view.getAdSummary().getAdRevenue());
        assertEquals(822, view.getAdSummary().getOrdersCount());
        assertEquals(new BigDecimal("4185.29"), view.getAdSummary().getZeroOrderSpendAmount());
        assertEquals(new BigDecimal("0.836000"), view.getAdSummary().getZeroOrderSpendShare());
        assertEquals(2120, view.getSalesSummary().getNetUnits());
        assertEquals(new BigDecimal("85828.86"), view.getSalesSummary().getRevenueShipped());
        assertEquals(new BigDecimal("0.058332"), view.getSalesSummary().getAdSpendShareOfSales());
        assertEquals("C_U9Q0F611VL", view.getCampaignRows().get(0).getCampaignCode());
        assertEquals(new BigDecimal("0.799500"), view.getCampaignRows().get(0).getZeroOrderSpendShare());
        assertEquals("ZDD-SAMPLE-001", view.getProductRows().get(0).getPartnerSku());
        assertEquals("STR69486-NSA|SA|ZDD-SAMPLE-001", view.getProductRows().get(0).getProductIdentityKey());
        assertEquals("https://f.nooncdn.com/p/pzsku/ZDD-SAMPLE-001/45/main.jpg", view.getProductRows().get(0).getImageUrl());
        assertEquals(2, view.getProductRows().get(0).getQueryCount());
        assertEquals(new BigDecimal("0.420000"), view.getProductRows().get(0).getZeroOrderSpendShare());
        assertEquals("ZDD-SAMPLE-001", view.getProductDiagnostics().get(0).getPartnerSku());
        assertEquals("STR69486-NSA|SA|ZDD-SAMPLE-001", view.getProductDiagnostics().get(0).getProductIdentityKey());
        assertEquals(NoonAdvertisingProductDiagnosisType.STRUCTURE_REVIEW, view.getProductDiagnostics().get(0).getDiagnosisType());
        assertEquals("结构待整理", view.getProductDiagnostics().get(0).getDiagnosisLabel());
        assertEquals(1, view.getProductDiagnostics().get(0).getCoreCampaignCount());
        assertEquals(0, view.getProductDiagnostics().get(0).getExplorationCampaignCount());
        assertFalse(view.getProductDiagnostics().get(0).isRankDataAvailable());
        assertEquals(NoonAdvertisingPlanType.CORE, view.getCampaignDiagnostics().get(0).getPlanType());
        assertEquals(NoonAdvertisingPlanTypeConfidence.RULE, view.getCampaignDiagnostics().get(0).getPlanTypeConfidence());
        assertEquals("C_NBN9Z09F58", view.getZeroOrderQueries().get(0).getCampaignCode());
        assertEquals(new BigDecimal("21.57"), view.getZeroOrderQueries().get(0).getSpendAmount());
        assertEquals("C_FEMENDHZ5T", view.getWinningQueries().get(0).getCampaignCode());
        assertEquals(new BigDecimal("19.64"), view.getWinningQueries().get(0).getRoas());
        assertEquals(1, view.getDataStatus().getBatchCount());
        assertTrue(view.getDataStatus().isDataAvailable());
    }

    @Test
    void returnsStableEmptyDashboardWhenRepositoryHasNoRows() {
        NoonAdvertisingAnalyticsService service = new NoonAdvertisingAnalyticsService(new RecordingNoonAdvertisingRepository());

        NoonAdvertisingDashboardView view = service.dashboard(defaultQuery());

        assertEquals(BigDecimal.ZERO, view.getAdSummary().getSpendAmount());
        assertEquals(BigDecimal.ZERO, view.getAdSummary().getAdRevenue());
        assertEquals(0, view.getAdSummary().getOrdersCount());
        assertEquals(BigDecimal.ZERO, view.getSalesSummary().getRevenueShipped());
        assertEquals(BigDecimal.ZERO, view.getSalesSummary().getAdSpendShareOfSales());
        assertTrue(view.getCampaignRows().isEmpty());
        assertTrue(view.getProductRows().isEmpty());
        assertTrue(view.getProductDiagnostics().isEmpty());
        assertTrue(view.getCampaignDiagnostics().isEmpty());
        assertTrue(view.getZeroOrderQueries().isEmpty());
        assertTrue(view.getWinningQueries().isEmpty());
        assertEquals(0, view.getDataStatus().getBatchCount());
        assertEquals(0, view.getDataStatus().getCampaignRowCount());
        assertEquals(0, view.getDataStatus().getQueryRowCount());
    }

    @Test
    void returnsLatestImportedReportWindowForScope() {
        RecordingNoonAdvertisingRepository repository = new RecordingNoonAdvertisingRepository();
        repository.latestReportWindow = new NoonAdvertisingLatestReportWindowView(
                true,
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 6, 25)
        );
        NoonAdvertisingAnalyticsService service = new NoonAdvertisingAnalyticsService(repository);

        NoonAdvertisingLatestReportWindowView view = service.latestReportWindow(defaultScopeQuery());

        assertEquals(10002L, repository.lastScopeQuery.getOwnerUserId());
        assertEquals("PRJ69486", repository.lastScopeQuery.getProjectCode());
        assertEquals("STR69486-NSA", repository.lastScopeQuery.getStoreCode());
        assertEquals("SA", repository.lastScopeQuery.getSiteCode());
        assertTrue(view.isDataAvailable());
        assertEquals(LocalDate.of(2026, 5, 26), view.getDateFrom());
        assertEquals(LocalDate.of(2026, 6, 25), view.getDateTo());
    }

    @Test
    void returnsEmptyLatestReportWindowWhenNoBatchExists() {
        NoonAdvertisingAnalyticsService service = new NoonAdvertisingAnalyticsService(new RecordingNoonAdvertisingRepository());

        NoonAdvertisingLatestReportWindowView view = service.latestReportWindow(defaultScopeQuery());

        assertFalse(view.isDataAvailable());
        assertEquals(null, view.getDateFrom());
        assertEquals(null, view.getDateTo());
    }

    @Test
    void productRowNormalizesNoonHashedPzskuImageUrl() {
        NoonAdvertisingProductRow row = new NoonAdvertisingProductRow();

        row.setImageUrl("https://f.nooncdn.com/eff639f2df2651369082d90705ccc7ca|pzsku/Z763CC536AE30FF658259Z/45/1768543884/5af868d5-4bfa-418b-a671-436dc3e1b9e2");

        assertEquals(
                "https://f.nooncdn.com/p/eff639f2df2651369082d90705ccc7ca%7Cpzsku/Z763CC536AE30FF658259Z/45/1768543884/5af868d5-4bfa-418b-a671-436dc3e1b9e2.jpg",
                row.getImageUrl()
        );
    }

    private NoonAdvertisingDashboardQuery defaultQuery() {
        return new NoonAdvertisingDashboardQuery(
                10002L,
                "PRJ69486",
                "STR69486-NSA",
                "SA",
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 6, 25)
        );
    }

    private NoonAdvertisingScopeQuery defaultScopeQuery() {
        return new NoonAdvertisingScopeQuery(
                10002L,
                "PRJ69486",
                "STR69486-NSA",
                "SA"
        );
    }

    private NoonAdvertisingSummaryView adSummary() {
        NoonAdvertisingSummaryView summary = new NoonAdvertisingSummaryView();
        summary.setCampaignCount(148);
        summary.setQueryCount(218571);
        summary.setViews(100000);
        summary.setClicks(3200);
        summary.setOrdersCount(822);
        summary.setAtcCount(1100);
        summary.setSpendAmount(new BigDecimal("5006.61"));
        summary.setAdRevenue(new BigDecimal("34862.09"));
        summary.setRoas(new BigDecimal("6.962000"));
        summary.setZeroOrderSpendAmount(new BigDecimal("4185.29"));
        summary.setZeroOrderSpendShare(new BigDecimal("0.836000"));
        return summary;
    }

    private NoonAdvertisingCampaignRow campaignRow() {
        NoonAdvertisingCampaignRow row = new NoonAdvertisingCampaignRow();
        row.setCampaignCode("C_U9Q0F611VL");
        row.setCampaignName("Brand towels exact");
        row.setStoreCode("STR69486-NSA");
        row.setSiteCode("SA");
        row.setPrimaryPartnerSku("ZDD-SAMPLE-001");
        row.setSpendAmount(new BigDecimal("150.59"));
        row.setOrdersCount(35);
        row.setAdRevenue(new BigDecimal("1335.10"));
        row.setZeroOrderSpendAmount(new BigDecimal("120.40"));
        row.setZeroOrderSpendShare(new BigDecimal("0.799500"));
        return row;
    }

    private NoonAdvertisingQueryRow zeroOrderQuery() {
        NoonAdvertisingQueryRow row = new NoonAdvertisingQueryRow();
        row.setCampaignCode("C_NBN9Z09F58");
        row.setCampaignName("Search broad");
        row.setStoreCode("STR69486-NSA");
        row.setSiteCode("SA");
        row.setPartnerSku("ZDD-SAMPLE-001");
        row.setQueryText("دبدوب");
        row.setQueryKind("broad");
        row.setSpendAmount(new BigDecimal("21.57"));
        row.setOrdersCount(0);
        row.setAdRevenue(BigDecimal.ZERO);
        return row;
    }

    private NoonAdvertisingProductRow productRow() {
        NoonAdvertisingProductRow row = new NoonAdvertisingProductRow();
        row.setStoreCode("STR69486-NSA");
        row.setSiteCode("SA");
        row.setPartnerSku("ZDD-SAMPLE-001");
        row.setImageUrl("https://f.nooncdn.com/p/pzsku/ZDD-SAMPLE-001/45/main.jpg");
        row.setCampaignCount(1);
        row.setQueryCount(2);
        row.setViews(800);
        row.setClicks(40);
        row.setOrdersCount(5);
        row.setSpendAmount(new BigDecimal("50.00"));
        row.setAdRevenue(new BigDecimal("250.00"));
        row.setRoas(new BigDecimal("5.00"));
        row.setZeroOrderSpendAmount(new BigDecimal("21.00"));
        row.setZeroOrderSpendShare(new BigDecimal("0.420000"));
        return row;
    }

    private NoonAdvertisingQueryRow winningQuery() {
        NoonAdvertisingQueryRow row = new NoonAdvertisingQueryRow();
        row.setCampaignCode("C_FEMENDHZ5T");
        row.setCampaignName("Search exact");
        row.setStoreCode("STR69486-NSA");
        row.setSiteCode("SA");
        row.setPartnerSku("ZDD-SAMPLE-002");
        row.setQueryText("مناشف");
        row.setQueryKind("exact");
        row.setSpendAmount(new BigDecimal("25.08"));
        row.setOrdersCount(13);
        row.setAdRevenue(new BigDecimal("492.50"));
        row.setRoas(new BigDecimal("19.64"));
        return row;
    }

    private static class RecordingNoonAdvertisingRepository implements NoonAdvertisingRepository {
        private NoonAdvertisingDashboardQuery lastQuery;
        private NoonAdvertisingScopeQuery lastScopeQuery;
        private NoonAdvertisingSummaryView adSummary;
        private NoonAdvertisingSalesSummaryView salesSummary;
        private NoonAdvertisingDataStatus dataStatus;
        private NoonAdvertisingLatestReportWindowView latestReportWindow;
        private List<NoonAdvertisingCampaignRow> campaignRows;
        private List<NoonAdvertisingProductRow> productRows;
        private List<NoonAdvertisingQueryRow> zeroOrderQueries;
        private List<NoonAdvertisingQueryRow> winningQueries;

        @Override
        public NoonAdvertisingSummaryView selectAdSummary(NoonAdvertisingDashboardQuery query) {
            this.lastQuery = query;
            return adSummary;
        }

        @Override
        public NoonAdvertisingSalesSummaryView selectSalesSummary(NoonAdvertisingDashboardQuery query) {
            this.lastQuery = query;
            return salesSummary;
        }

        @Override
        public NoonAdvertisingDataStatus selectDataStatus(NoonAdvertisingDashboardQuery query) {
            this.lastQuery = query;
            return dataStatus;
        }

        @Override
        public List<NoonAdvertisingCampaignRow> selectCampaignRows(NoonAdvertisingDashboardQuery query) {
            this.lastQuery = query;
            return campaignRows;
        }

        @Override
        public List<NoonAdvertisingProductRow> selectProductRows(NoonAdvertisingDashboardQuery query) {
            this.lastQuery = query;
            return productRows;
        }

        @Override
        public List<NoonAdvertisingQueryRow> selectZeroOrderQueryRows(NoonAdvertisingDashboardQuery query) {
            this.lastQuery = query;
            return zeroOrderQueries;
        }

        @Override
        public List<NoonAdvertisingQueryRow> selectWinningQueryRows(NoonAdvertisingDashboardQuery query) {
            this.lastQuery = query;
            return winningQueries;
        }

        @Override
        public NoonAdvertisingLatestReportWindowView selectLatestReportWindow(NoonAdvertisingScopeQuery query) {
            this.lastScopeQuery = query;
            return latestReportWindow;
        }
    }
}
