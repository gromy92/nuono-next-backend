package com.nuono.next.replenishmentplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.replenishmentplan.ReplenishmentPlanRepository.InboundRow;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRepository.StockRow;
import com.nuono.next.salesforecast.SalesForecastQuery;
import com.nuono.next.salesforecast.SalesForecastResultRecord;
import com.nuono.next.salesforecast.SalesForecastRunRecord;
import com.nuono.next.salesforecast.SalesForecastRunRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultReplenishmentPlanServiceTest {

    private static final Long OWNER_USER_ID = 307L;
    private static final String STORE_CODE = "STR108065-NSA";
    private static final String SITE_CODE = "SA";
    private static final LocalDate SOURCE_DATE = LocalDate.of(2026, 7, 6);

    @Mock
    private SalesForecastRunRepository forecastRunRepository;
    @Mock
    private ReplenishmentPlanRepository repository;
    @Mock
    private ReplenishmentPlanConfigResolver configResolver;

    private DefaultReplenishmentPlanService service;

    @BeforeEach
    void setUp() {
        service = new DefaultReplenishmentPlanService(
                forecastRunRepository,
                repository,
                configResolver,
                new ReplenishmentPlanCalculator(),
                Clock.fixed(Instant.parse("2026-07-07T00:00:00Z"), ZoneOffset.UTC)
        );
        when(configResolver.resolve(OWNER_USER_ID, STORE_CODE, SITE_CODE))
                .thenReturn(ReplenishmentPlanConfig.defaultBasicV1());
    }

    @Test
    void readyForecastMapsStockEtaInboundAndMissingEtaByCanonicalPartnerSku() {
        when(forecastRunRepository.findLatestCompleted(any())).thenReturn(run());
        when(forecastRunRepository.listResults(100L)).thenReturn(List.of(resultWithEvidence("PSKU-001", "SKU-001", 900)));
        when(repository.listFbnSupermallStock(OWNER_USER_ID, STORE_CODE, SITE_CODE))
                .thenReturn(List.of(new StockRow(
                        " psku-001 ",
                        "SKU-001",
                        "https://f.nooncdn.com/p/eff639f2df2651369082d90705ccc7ca|pzsku/Z930F2C6D839E70FE2653Z/45/1769591448/41897da8-e52a-4e7a-b654-13fb35dfdcd6.jpg",
                        LocalDate.of(2026, 3, 12),
                        new BigDecimal("15"),
                        new BigDecimal("10"),
                        new BigDecimal("5")
                )));
        when(repository.listActiveInbound(OWNER_USER_ID, STORE_CODE, SITE_CODE))
                .thenReturn(List.of(
                        new InboundRow(
                                "psku-001",
                                1001L,
                                "BATCH-ETA",
                                "SEA",
                                "ACTIVE",
                                SOURCE_DATE.plusDays(20),
                                new BigDecimal("20")
                        ),
                        new InboundRow(
                                " PSKU-001 ",
                                1002L,
                                "BATCH-NO-ETA",
                                "SEA",
                                "ACTIVE",
                                null,
                                new BigDecimal("7")
                        ),
                        new InboundRow(
                                "PSKU-OTHER",
                                1003L,
                                "BATCH-OTHER",
                                "SEA",
                                "ACTIVE",
                                SOURCE_DATE.plusDays(20),
                                new BigDecimal("99")
                        )
                ));

        ReplenishmentPlanRecords.PlanOverviewView overview = service.getOverview(query());

        assertEquals("ready", overview.getState());
        assertEquals(SOURCE_DATE, overview.getAnchorDate());
        assertEquals(1, overview.getRows().size());
        ReplenishmentPlanRecords.PlanItemView row = overview.getRows().get(0);
        assertEquals("PSKU-001", row.getPartnerSku());
        assertEquals(
                "https://f.nooncdn.com/p/eff639f2df2651369082d90705ccc7ca%7Cpzsku/Z930F2C6D839E70FE2653Z/45/1769591448/41897da8-e52a-4e7a-b654-13fb35dfdcd6.jpg",
                row.getImageUrl()
        );
        assertEquals(LocalDate.of(2026, 3, 12), row.getListingAt());
        assertEquals(new BigDecimal("10"), row.getCurrentStockUnits());
        assertEquals(new BigDecimal("10"), row.getFbnStockUnits());
        assertEquals(new BigDecimal("20"), row.getKnownInboundUnits());
        assertEquals(new BigDecimal("7"), row.getMissingEtaInboundQty());
        assertEquals(1, row.getMissingEtaBatchCount());
        assertEquals("BATCH-NO-ETA", row.getMissingEtaBatches().get(0).getBatchReferenceNo());
        assertEquals(new BigDecimal("150"), row.getSeaCalculatedUnits());
        assertEquals(new BigDecimal("150"), row.getSeaSuggestedUnits());
        assertTrue(row.getWarnings().contains("missing_eta_inbound_excluded"));
        assertEquals(SOURCE_DATE, row.getLatestFactDate());
        assertEquals(67, row.getObservedDays());
        assertEquals(11, row.getHistoryUnits7());
        assertEquals(42, row.getHistoryUnits30());
        assertEquals(73, row.getHistoryUnits60());
        assertEquals(105, row.getHistoryUnits90());
        assertEquals(new BigDecimal("15.5000"), row.getAdjustedHistoryUnits7());
        assertEquals(new BigDecimal("50.0000"), row.getAdjustedHistoryUnits30());
        assertEquals(new BigDecimal("88.0000"), row.getAdjustedHistoryUnits60());
        assertEquals(new BigDecimal("120.0000"), row.getAdjustedHistoryUnits90());
        assertEquals(300, row.getForecastUnits30());
        assertEquals(600, row.getForecastUnits60());
        assertEquals(900, row.getForecastUnits90());
        assertEquals("高", row.getConfidenceLabel());
        assertEquals("近 30 天稳定出单", row.getShortReason());
        assertEquals(SOURCE_DATE.plusDays(20), row.getNearestInboundEtaDate());

        ArgumentCaptor<SalesForecastQuery> forecastQuery = ArgumentCaptor.forClass(SalesForecastQuery.class);
        verify(forecastRunRepository).findLatestCompleted(forecastQuery.capture());
        assertEquals(OWNER_USER_ID, forecastQuery.getValue().getOwnerUserId());
        assertEquals(STORE_CODE, forecastQuery.getValue().getStoreCode());
        assertEquals(SITE_CODE, forecastQuery.getValue().getSiteCode());
        verify(forecastRunRepository).listResults(100L);
        verify(forecastRunRepository, never()).saveRun(any());
        verify(forecastRunRepository, never()).saveResults(any(), any());
    }

    @Test
    void missingForecastRunReturnsEmptyOverviewWithoutReadingRepositoriesOrWritingForecast() {
        when(forecastRunRepository.findLatestCompleted(any())).thenReturn(null);

        ReplenishmentPlanRecords.PlanOverviewView overview = service.getOverview(query());

        assertEquals("empty", overview.getState());
        assertEquals(LocalDate.of(2026, 7, 7), overview.getAnchorDate());
        assertTrue(overview.getRows().isEmpty());
        verify(forecastRunRepository, never()).listResults(any());
        verify(forecastRunRepository, never()).saveRun(any());
        verify(forecastRunRepository, never()).saveResults(any(), any());
        verify(repository, never()).listFbnSupermallStock(any(), any(), any());
        verify(repository, never()).listActiveInbound(any(), any(), any());
    }

    @Test
    void emptyForecastResultsReturnEmptyOverviewWithoutReadingRepositories() {
        when(forecastRunRepository.findLatestCompleted(any())).thenReturn(run());
        when(forecastRunRepository.listResults(100L)).thenReturn(List.of());

        ReplenishmentPlanRecords.PlanOverviewView overview = service.getOverview(query());

        assertEquals("empty", overview.getState());
        assertEquals(SOURCE_DATE, overview.getAnchorDate());
        assertTrue(overview.getRows().isEmpty());
        verify(repository, never()).listFbnSupermallStock(any(), any(), any());
        verify(repository, never()).listActiveInbound(any(), any(), any());
    }

    @Test
    void nullForecastResultsReturnEmptyOverviewWithoutReadingRepositories() {
        when(forecastRunRepository.findLatestCompleted(any())).thenReturn(run());
        when(forecastRunRepository.listResults(100L)).thenReturn(null);

        ReplenishmentPlanRecords.PlanOverviewView overview = service.getOverview(query());

        assertEquals("empty", overview.getState());
        assertEquals(SOURCE_DATE, overview.getAnchorDate());
        assertTrue(overview.getRows().isEmpty());
        verify(repository, never()).listFbnSupermallStock(any(), any(), any());
        verify(repository, never()).listActiveInbound(any(), any(), any());
    }

    @Test
    void missingStockFactsPreserveNullFieldsAndAddWarnings() {
        when(forecastRunRepository.findLatestCompleted(any())).thenReturn(run());
        when(forecastRunRepository.listResults(100L)).thenReturn(List.of(result("PSKU-MISSING", "SKU-MISSING", 90)));
        when(repository.listFbnSupermallStock(OWNER_USER_ID, STORE_CODE, SITE_CODE)).thenReturn(List.of());
        when(repository.listActiveInbound(OWNER_USER_ID, STORE_CODE, SITE_CODE)).thenReturn(List.of());

        ReplenishmentPlanRecords.PlanOverviewView overview = service.getOverview(query());

        ReplenishmentPlanRecords.PlanItemView row = overview.getRows().get(0);
        assertNull(row.getCurrentStockUnits());
        assertNull(row.getFbnStockUnits());
        assertNull(row.getSupermallStockUnits());
        assertTrue(row.getWarnings().contains("stock_fact_missing"));
        assertTrue(row.getWarnings().contains("fbn_stock_fact_missing"));
        assertFalse(row.getWarnings().contains("supermall_stock_fact_missing"));
    }

    @Test
    void partialStockFactsUseFbnAsCurrentStockAndIgnoreMissingSupermall() {
        when(forecastRunRepository.findLatestCompleted(any())).thenReturn(run());
        when(forecastRunRepository.listResults(100L)).thenReturn(List.of(result("PSKU-PARTIAL", "SKU-PARTIAL", 90)));
        when(repository.listFbnSupermallStock(OWNER_USER_ID, STORE_CODE, SITE_CODE))
                .thenReturn(List.of(new StockRow(
                        "PSKU-PARTIAL",
                        "SKU-PARTIAL",
                        null,
                        null,
                        new BigDecimal("8"),
                        null
                )));
        when(repository.listActiveInbound(OWNER_USER_ID, STORE_CODE, SITE_CODE)).thenReturn(List.of());

        ReplenishmentPlanRecords.PlanOverviewView overview = service.getOverview(query());

        ReplenishmentPlanRecords.PlanItemView row = overview.getRows().get(0);
        assertEquals(new BigDecimal("8"), row.getCurrentStockUnits());
        assertEquals(new BigDecimal("8"), row.getFbnStockUnits());
        assertNull(row.getSupermallStockUnits());
        assertFalse(row.getWarnings().contains("supermall_stock_fact_missing"));
        assertFalse(row.getWarnings().contains("stock_fact_missing"));
    }

    @Test
    void currentDateControlsPastEtaExclusionAndRecentEtaReviewFlag() {
        DefaultReplenishmentPlanService serviceOnJuly8 = new DefaultReplenishmentPlanService(
                forecastRunRepository,
                repository,
                configResolver,
                new ReplenishmentPlanCalculator(),
                Clock.fixed(Instant.parse("2026-07-08T00:00:00Z"), ZoneOffset.UTC)
        );
        when(forecastRunRepository.findLatestCompleted(any())).thenReturn(run());
        when(forecastRunRepository.listResults(100L)).thenReturn(List.of(result("PSKU-ETA", "SKU-ETA", 90)));
        when(repository.listFbnSupermallStock(OWNER_USER_ID, STORE_CODE, SITE_CODE)).thenReturn(List.of());
        when(repository.listActiveInbound(OWNER_USER_ID, STORE_CODE, SITE_CODE))
                .thenReturn(List.of(
                        new InboundRow(
                                "PSKU-ETA",
                                2001L,
                                "BATCH-OLD-ETA",
                                "SEA",
                                "ACTIVE",
                                LocalDate.of(2026, 7, 3),
                                new BigDecimal("20")
                        ),
                        new InboundRow(
                                "PSKU-ETA",
                                2002L,
                                "BATCH-RECENT-ETA",
                                "SEA",
                                "ACTIVE",
                                LocalDate.of(2026, 7, 7),
                                new BigDecimal("10")
                        ),
                        new InboundRow(
                                "PSKU-ETA",
                                2003L,
                                "BATCH-FUTURE-ETA",
                                "SEA",
                                "ACTIVE",
                                LocalDate.of(2026, 7, 10),
                                new BigDecimal("30")
                        )
                ));

        ReplenishmentPlanRecords.PlanOverviewView overview = serviceOnJuly8.getOverview(query());

        ReplenishmentPlanRecords.PlanItemView row = overview.getRows().get(0);
        assertEquals(SOURCE_DATE, overview.getAnchorDate());
        assertEquals(new BigDecimal("40"), row.getKnownInboundUnits());
        assertEquals(LocalDate.of(2026, 7, 7), row.getNearestInboundEtaDate());
        assertEquals(2, row.getInboundBatches().size());
        ReplenishmentPlanRecords.InboundBatch recentEta = inboundBatch(row, "BATCH-RECENT-ETA");
        assertTrue(recentEta.isEtaReviewRequired());
        assertTrue(recentEta.isCoverageIncluded());
        ReplenishmentPlanRecords.InboundBatch futureEta = inboundBatch(row, "BATCH-FUTURE-ETA");
        assertFalse(futureEta.isEtaReviewRequired());
        assertTrue(futureEta.isCoverageIncluded());
        assertTrue(row.getWarnings().contains("past_eta_inbound_excluded"));
        assertTrue(row.getWarnings().contains("recent_eta_review_required"));
    }

    @Test
    void persistedDailyForecastsAreMappedFromForecastDateToCurrentPlanDate() {
        DefaultReplenishmentPlanService serviceOnJuly8 = new DefaultReplenishmentPlanService(
                forecastRunRepository,
                repository,
                configResolver,
                new ReplenishmentPlanCalculator(),
                Clock.fixed(Instant.parse("2026-07-08T00:00:00Z"), ZoneOffset.UTC)
        );
        when(forecastRunRepository.findLatestCompleted(any())).thenReturn(run());
        when(forecastRunRepository.listResults(100L)).thenReturn(List.of(resultWithShiftedDailyForecasts(
                "PSKU-SHIFTED",
                "SKU-SHIFTED"
        )));
        when(repository.listFbnSupermallStock(eq(OWNER_USER_ID), eq(STORE_CODE), eq(SITE_CODE)))
                .thenReturn(List.of(new StockRow(
                        "PSKU-SHIFTED",
                        "SKU-SHIFTED",
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )));
        when(repository.listActiveInbound(OWNER_USER_ID, STORE_CODE, SITE_CODE)).thenReturn(List.of());

        ReplenishmentPlanRecords.PlanOverviewView overview = serviceOnJuly8.getOverview(query());

        ReplenishmentPlanRecords.PlanItemView row = overview.getRows().get(0);
        assertEquals(SOURCE_DATE, overview.getAnchorDate());
        assertEquals(new BigDecimal("9"), row.getAirWindowForecastUnits());
        assertEquals(new BigDecimal("9"), row.getAirCalculatedUnits());
        assertEquals(new BigDecimal("10"), row.getAirSuggestedUnits());
        assertEquals(new BigDecimal("7"), row.getSeaWindowForecastUnits());
        assertEquals(new BigDecimal("0"), row.getSeaCalculatedUnits());
        assertEquals(new BigDecimal("0"), row.getSeaSuggestedUnits());
        assertEquals(new BigDecimal("51"), row.getForecastUnits100());
    }

    @Test
    void demandCurveUsesForecastUnits90DividedByNinetyForNonzeroSeaSuggestion() {
        when(forecastRunRepository.findLatestCompleted(any())).thenReturn(run());
        when(forecastRunRepository.listResults(100L)).thenReturn(List.of(result("PSKU-DEMAND", "SKU-DEMAND", 180)));
        when(repository.listFbnSupermallStock(eq(OWNER_USER_ID), eq(STORE_CODE), eq(SITE_CODE)))
                .thenReturn(List.of(new StockRow(
                        "PSKU-DEMAND",
                        "SKU-DEMAND",
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )));
        when(repository.listActiveInbound(OWNER_USER_ID, STORE_CODE, SITE_CODE)).thenReturn(List.of());

        ReplenishmentPlanRecords.PlanOverviewView overview = service.getOverview(query());

        assertEquals(new BigDecimal("30"), overview.getRows().get(0).getSeaCalculatedUnits());
        assertEquals(new BigDecimal("30"), overview.getRows().get(0).getSeaSuggestedUnits());
    }

    @Test
    void demandCurveUsesPersistedDailyForecastsBeforeNinetyDayAverage() {
        when(forecastRunRepository.findLatestCompleted(any())).thenReturn(run());
        when(forecastRunRepository.listResults(100L)).thenReturn(List.of(resultWithDailyForecasts(
                "PSKU-DAILY",
                "SKU-DAILY",
                900
        )));
        when(repository.listFbnSupermallStock(eq(OWNER_USER_ID), eq(STORE_CODE), eq(SITE_CODE)))
                .thenReturn(List.of(new StockRow(
                        "PSKU-DAILY",
                        "SKU-DAILY",
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )));
        when(repository.listActiveInbound(OWNER_USER_ID, STORE_CODE, SITE_CODE)).thenReturn(List.of());

        ReplenishmentPlanRecords.PlanOverviewView overview = service.getOverview(query());

        assertEquals(new BigDecimal("58"), overview.getRows().get(0).getSeaCalculatedUnits());
        assertEquals(new BigDecimal("60"), overview.getRows().get(0).getSeaSuggestedUnits());
    }

    @Test
    void demandCurveFallsBackToDetailFactorBreakdownWhenForecastUnits90IsMissing() {
        when(forecastRunRepository.findLatestCompleted(any())).thenReturn(run());
        when(forecastRunRepository.listResults(100L)).thenReturn(List.of(resultWithFactors(
                "PSKU-DETAIL",
                "SKU-DETAIL",
                0,
                new BigDecimal("1.5"),
                new BigDecimal("2"),
                new BigDecimal("1")
        )));
        when(repository.listFbnSupermallStock(eq(OWNER_USER_ID), eq(STORE_CODE), eq(SITE_CODE)))
                .thenReturn(List.of(new StockRow(
                        "PSKU-DETAIL",
                        "SKU-DETAIL",
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )));
        when(repository.listActiveInbound(OWNER_USER_ID, STORE_CODE, SITE_CODE)).thenReturn(List.of());

        ReplenishmentPlanRecords.PlanOverviewView overview = service.getOverview(query());

        assertEquals(new BigDecimal("45"), overview.getRows().get(0).getSeaCalculatedUnits());
        assertEquals(new BigDecimal("50"), overview.getRows().get(0).getSeaSuggestedUnits());
    }

    private static ReplenishmentPlanRecords.PlanQuery query() {
        return new ReplenishmentPlanRecords.PlanQuery(OWNER_USER_ID, STORE_CODE, SITE_CODE);
    }

    private static SalesForecastRunRecord run() {
        return new SalesForecastRunRecord(
                100L,
                OWNER_USER_ID,
                STORE_CODE,
                SITE_CODE,
                SOURCE_DATE,
                "SALES_FORECAST_V1_4",
                "default",
                "succeeded",
                1,
                LocalDateTime.of(2026, 7, 6, 12, 0)
        );
    }

    private static SalesForecastResultRecord result(String partnerSku, String sku, int forecastUnits90) {
        return resultWithFactors(
                partnerSku,
                sku,
                forecastUnits90,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE
        );
    }

    private static SalesForecastResultRecord resultWithEvidence(String partnerSku, String sku, int forecastUnits90) {
        return resultRecord(
                partnerSku,
                sku,
                forecastUnits90,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE,
                11,
                42,
                73,
                105,
                67,
                "high",
                "高",
                "近 30 天稳定出单",
                "{\"adjustedHistoryUnits7\":\"15.5000\","
                        + "\"adjustedHistoryUnits30\":\"50.0000\","
                        + "\"adjustedHistoryUnits60\":\"88.0000\","
                        + "\"adjustedHistoryUnits90\":\"120.0000\"}"
        );
    }

    private static SalesForecastResultRecord resultWithFactors(
            String partnerSku,
            String sku,
            int forecastUnits90,
            BigDecimal baseDailySales,
            BigDecimal trendFactor,
            BigDecimal futureFactor
    ) {
        return resultRecord(
                partnerSku,
                sku,
                forecastUnits90,
                baseDailySales,
                trendFactor,
                futureFactor,
                0,
                0,
                0,
                0,
                0,
                "medium",
                "中",
                null
        );
    }

    private static SalesForecastResultRecord resultWithDailyForecasts(String partnerSku, String sku, int forecastUnits90) {
        return resultRecord(
                partnerSku,
                sku,
                forecastUnits90,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE,
                0,
                0,
                0,
                0,
                90,
                "high",
                "高",
                null,
                dailyForecastsJson()
        );
    }

    private static SalesForecastResultRecord resultWithShiftedDailyForecasts(String partnerSku, String sku) {
        return resultRecord(
                partnerSku,
                sku,
                0,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE,
                0,
                0,
                0,
                0,
                90,
                "high",
                "高",
                null,
                shiftedDailyForecastsJson()
        );
    }

    private static SalesForecastResultRecord resultRecord(
            String partnerSku,
            String sku,
            int forecastUnits90,
            BigDecimal baseDailySales,
            BigDecimal trendFactor,
            BigDecimal futureFactor,
            int historyUnits7,
            int historyUnits30,
            int historyUnits60,
            int historyUnits90,
            int observedDays,
            String confidenceLevel,
            String confidenceLabel,
            String shortReason
    ) {
        return resultRecord(
                partnerSku,
                sku,
                forecastUnits90,
                baseDailySales,
                trendFactor,
                futureFactor,
                historyUnits7,
                historyUnits30,
                historyUnits60,
                historyUnits90,
                observedDays,
                confidenceLevel,
                confidenceLabel,
                shortReason,
                null
        );
    }

    private static SalesForecastResultRecord resultRecord(
            String partnerSku,
            String sku,
            int forecastUnits90,
            BigDecimal baseDailySales,
            BigDecimal trendFactor,
            BigDecimal futureFactor,
            int historyUnits7,
            int historyUnits30,
            int historyUnits60,
            int historyUnits90,
            int observedDays,
            String confidenceLevel,
            String confidenceLabel,
            String shortReason,
            String featureSnapshotJson
    ) {
        return new SalesForecastResultRecord(
                1000L,
                100L,
                OWNER_USER_ID,
                STORE_CODE,
                SITE_CODE,
                partnerSku,
                sku,
                "Product " + partnerSku,
                SOURCE_DATE,
                historyUnits7,
                historyUnits30,
                historyUnits60,
                historyUnits90,
                observedDays,
                null,
                null,
                forecastUnits90 / 3,
                forecastUnits90 * 2 / 3,
                forecastUnits90,
                "SALES_FORECAST_V1_4",
                "default",
                baseDailySales,
                BigDecimal.ONE,
                trendFactor,
                futureFactor,
                confidenceLevel,
                confidenceLabel,
                null,
                null,
                null,
                null,
                null,
                shortReason,
                featureSnapshotJson
        );
    }

    private static String dailyForecastsJson() {
        StringBuilder json = new StringBuilder("{\"dailyForecasts\":[");
        for (int day = 1; day <= 100; day++) {
            if (day > 1) {
                json.append(',');
            }
            BigDecimal forecastUnits = day >= 70 && day < 100 ? new BigDecimal("2") : BigDecimal.ZERO;
            json.append('{')
                    .append("\"dayIndex\":").append(day).append(',')
                    .append("\"forecastDate\":\"").append(SOURCE_DATE.plusDays(day)).append("\",")
                    .append("\"calendarFactor\":\"1.0000\",")
                    .append("\"forecastUnits\":\"").append(forecastUnits.toPlainString()).append("\"")
                    .append('}');
        }
        return json.append("]}").toString();
    }

    private static String shiftedDailyForecastsJson() {
        return "{\"dailyForecasts\":["
                + "{\"dayIndex\":12,\"forecastDate\":\"2026-07-18\",\"calendarFactor\":\"1.0000\",\"forecastUnits\":\"5\"},"
                + "{\"dayIndex\":28,\"forecastDate\":\"2026-08-03\",\"calendarFactor\":\"1.0000\",\"forecastUnits\":\"9\"},"
                + "{\"dayIndex\":70,\"forecastDate\":\"2026-09-14\",\"calendarFactor\":\"1.0000\",\"forecastUnits\":\"30\"},"
                + "{\"dayIndex\":101,\"forecastDate\":\"2026-10-15\",\"calendarFactor\":\"1.0000\",\"forecastUnits\":\"7\"}"
                + "]}";
    }

    private static ReplenishmentPlanRecords.InboundBatch inboundBatch(
            ReplenishmentPlanRecords.PlanItemView row,
            String batchReferenceNo
    ) {
        return row.getInboundBatches().stream()
                .filter(batch -> batchReferenceNo.equals(batch.getBatchReferenceNo()))
                .findFirst()
                .orElseThrow();
    }
}
