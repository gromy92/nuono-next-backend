package com.nuono.next.replenishmentplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        when(forecastRunRepository.listResults(100L)).thenReturn(List.of(result("PSKU-001", "SKU-001", 900)));
        when(repository.listFbnSupermallStock(OWNER_USER_ID, STORE_CODE, SITE_CODE))
                .thenReturn(List.of(new StockRow(
                        "PSKU-001",
                        "SKU-001",
                        new BigDecimal("15"),
                        new BigDecimal("10"),
                        new BigDecimal("5")
                )));
        when(repository.listActiveInbound(OWNER_USER_ID, STORE_CODE, SITE_CODE))
                .thenReturn(List.of(
                        new InboundRow(
                                "PSKU-001",
                                1001L,
                                "BATCH-ETA",
                                "SEA",
                                "ACTIVE",
                                SOURCE_DATE.plusDays(20),
                                new BigDecimal("20")
                        ),
                        new InboundRow(
                                "PSKU-001",
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
        assertEquals(new BigDecimal("15"), row.getCurrentStockUnits());
        assertEquals(new BigDecimal("20"), row.getKnownInboundUnits());
        assertEquals(new BigDecimal("7"), row.getMissingEtaInboundQty());
        assertEquals(1, row.getMissingEtaBatchCount());
        assertEquals("BATCH-NO-ETA", row.getMissingEtaBatches().get(0).getBatchReferenceNo());
        assertEquals(new BigDecimal("300"), row.getSeaSuggestedUnits());
        assertTrue(row.getWarnings().contains("missing_eta_inbound_excluded"));

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
        assertTrue(row.getWarnings().contains("supermall_stock_fact_missing"));
    }

    @Test
    void partialStockFactsDeriveCurrentFromConfirmedSourcesButPreserveMissingSourceNull() {
        when(forecastRunRepository.findLatestCompleted(any())).thenReturn(run());
        when(forecastRunRepository.listResults(100L)).thenReturn(List.of(result("PSKU-PARTIAL", "SKU-PARTIAL", 90)));
        when(repository.listFbnSupermallStock(OWNER_USER_ID, STORE_CODE, SITE_CODE))
                .thenReturn(List.of(new StockRow(
                        "PSKU-PARTIAL",
                        "SKU-PARTIAL",
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
        assertTrue(row.getWarnings().contains("supermall_stock_fact_missing"));
        assertTrue(row.getWarnings().contains("stock_fact_missing"));
    }

    @Test
    void demandCurveUsesForecastUnits90DividedByNinetyForNonzeroSeaSuggestion() {
        when(forecastRunRepository.findLatestCompleted(any())).thenReturn(run());
        when(forecastRunRepository.listResults(100L)).thenReturn(List.of(result("PSKU-DEMAND", "SKU-DEMAND", 180)));
        when(repository.listFbnSupermallStock(eq(OWNER_USER_ID), eq(STORE_CODE), eq(SITE_CODE)))
                .thenReturn(List.of(new StockRow(
                        "PSKU-DEMAND",
                        "SKU-DEMAND",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )));
        when(repository.listActiveInbound(OWNER_USER_ID, STORE_CODE, SITE_CODE)).thenReturn(List.of());

        ReplenishmentPlanRecords.PlanOverviewView overview = service.getOverview(query());

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
                new BigDecimal("1"),
                new BigDecimal("1")
        )));
        when(repository.listFbnSupermallStock(eq(OWNER_USER_ID), eq(STORE_CODE), eq(SITE_CODE)))
                .thenReturn(List.of(new StockRow(
                        "PSKU-DETAIL",
                        "SKU-DETAIL",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )));
        when(repository.listActiveInbound(OWNER_USER_ID, STORE_CODE, SITE_CODE)).thenReturn(List.of());

        ReplenishmentPlanRecords.PlanOverviewView overview = service.getOverview(query());

        assertEquals(new BigDecimal("90"), overview.getRows().get(0).getSeaSuggestedUnits());
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
                BigDecimal.ONE,
                BigDecimal.ONE
        );
    }

    private static SalesForecastResultRecord resultWithFactors(
            String partnerSku,
            String sku,
            int forecastUnits90,
            BigDecimal baseDailySales,
            BigDecimal trendFactor,
            BigDecimal lifecycleFactor,
            BigDecimal futureFactor
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
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                0,
                0,
                forecastUnits90,
                null,
                null,
                "SALES_FORECAST_V1_4",
                "default",
                baseDailySales,
                BigDecimal.ONE,
                trendFactor,
                lifecycleFactor,
                futureFactor,
                null,
                "medium",
                "中",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
