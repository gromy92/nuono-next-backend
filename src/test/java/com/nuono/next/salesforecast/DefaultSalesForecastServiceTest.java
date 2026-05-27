package com.nuono.next.salesforecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.operationsconfig.InMemoryOperationConfigTypedVersionRepository;
import com.nuono.next.operationsconfig.OperationConfigDefaultVersionCatalog;
import com.nuono.next.operationsconfig.OperationConfigTypedVersion;
import com.nuono.next.operationsconfig.OperationConfigVersionType;
import com.nuono.next.sales.DailySalesFact;
import com.nuono.next.sales.NoonSalesCsvImportService;
import com.nuono.next.sales.ProductLifecycleClassifier;
import com.nuono.next.sales.SalesActivityWindowRecord;
import com.nuono.next.sales.SalesActivityWindowRepository;
import com.nuono.next.sales.SalesActivityWindowScope;
import com.nuono.next.sales.SalesFactQuery;
import com.nuono.next.sales.SalesFactRepository;
import com.nuono.next.sales.SalesImportBatch;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultSalesForecastServiceTest {

    @Test
    void firstReadBuildsPersistsAndReturnsThirtyDayForecastRows() {
        InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        factRepository.facts.addAll(factsForGrowthProduct(LocalDate.of(2026, 5, 20)));
        InMemorySalesForecastRunRepository runRepository = new InMemorySalesForecastRunRepository();
        InMemorySalesForecastStockRepository stockRepository = new InMemorySalesForecastStockRepository();
        stockRepository.stocks.add(stock("PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1", 12));
        DefaultSalesForecastService service = service(factRepository, runRepository, stockRepository);

        SalesForecastOverviewView overview = service.getOverview(query());

        assertEquals("ready", overview.getState());
        assertEquals(LocalDate.of(2026, 5, 20), overview.getSourceDataDate());
        assertEquals("DEFAULT_V1", overview.getCalculationVersion());
        assertEquals("DEFAULT_V1", overview.getConfigVersion());
        assertEquals(1, overview.getRows().size());
        SalesForecastOverviewRow row = overview.getRows().get(0);
        assertEquals("PAPERSAYSB359", row.getPartnerSku());
        assertEquals("Z02AD5F198C0C2E813C30Z-1", row.getSku());
        assertEquals(21, row.getHistoryUnits7());
        assertEquals(60, row.getHistoryUnits30());
        assertEquals(90, row.getHistoryUnits60());
        assertEquals(90, row.getHistoryUnits90());
        assertEquals(106, row.getForecastUnits30());
        assertEquals(211, row.getForecastUnits60());
        assertEquals(316, row.getForecastUnits90());
        assertEquals("growth", row.getLifecycleCode());
        assertEquals("增长", row.getLifecycleLabel());
        assertEquals("DEFAULT_V1", row.getCalculationVersion());
        assertEquals("DEFAULT_V1", row.getConfigVersion());
        assertEquals(90, row.getDetail().getFeatureValues().getHistoryUnits60());
        assertEquals("2.3000", row.getDetail().getFactorBreakdown().getBaseDailySales().toPlainString());
        assertEquals("0.5000", row.getDetail().getFactorBreakdown().getRecentDailyTrendRate().toPlainString());
        assertEquals("1.2500", row.getDetail().getFactorBreakdown().getTrendFactor().toPlainString());
        assertEquals("1.2200", row.getDetail().getFactorBreakdown().getLifecycleFactor().toPlainString());
        assertEquals(211, row.getDetail().getFactorBreakdown().getForecastUnits60());
        assertEquals("DEFAULT_V1", row.getDetail().getCalculationVersion());
        assertEquals("DEFAULT_V1", row.getDetail().getConfigVersion());
        assertTrue(row.getDetail().getLifecycleExplanation().contains("增长"));
        assertEquals(1, runRepository.runs.size());
        assertEquals(1, runRepository.results.size());
    }

    @Test
    void firstReadIncludesStockConfidenceAndRiskLabels() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        factRepository.facts.addAll(productFacts(latestFactDate, "LOWSTOCK", "SKU-LOW", "Low stock product", day -> day <= 29 ? 2 : 0));
        factRepository.facts.addAll(productFacts(latestFactDate, "STOCKOUT", "SKU-ZERO", "Stockout product", day -> {
            if (day <= 13) {
                return 1;
            }
            if (day <= 27) {
                return 2;
            }
            return 0;
        }));
        factRepository.facts.addAll(productFacts(latestFactDate, "OVERSTOCK", "SKU-OVER", "Overstock product", day -> {
            if (day <= 13) {
                return day <= 6 ? 0 : 1;
            }
            if (day <= 27) {
                return 3;
            }
            return 0;
        }));
        factRepository.facts.addAll(productFacts(latestFactDate, "NEWSAMPLE", "SKU-NEW", "Low sample product", day -> day <= 9 ? 1 : -1));
        factRepository.facts.addAll(productFacts(latestFactDate, "MISSINGSTOCK", "SKU-MISS", "Missing stock product", day -> day <= 29 ? 1 : 0));

        InMemorySalesForecastStockRepository stockRepository = new InMemorySalesForecastStockRepository();
        stockRepository.stocks.add(stock("LOWSTOCK", "SKU-LOW", 6));
        stockRepository.stocks.add(stock("STOCKOUT", "SKU-ZERO", 0));
        stockRepository.stocks.add(stock("OVERSTOCK", "SKU-OVER", 240));
        stockRepository.stocks.add(stock("NEWSAMPLE", "SKU-NEW", 20));
        DefaultSalesForecastService service = service(
                factRepository,
                new InMemorySalesForecastRunRepository(),
                stockRepository
        );

        SalesForecastOverviewView overview = service.getOverview(query());

        SalesForecastOverviewRow lowStock = rowByPartnerSku(overview, "LOWSTOCK");
        assertEquals(6, lowStock.getCurrentStock());
        assertEquals("3.0", lowStock.getStockCoverDays().toPlainString());
        assertEquals("high", lowStock.getConfidenceLevel());
        assertRisk(lowStock, "replenishment_risk");

        SalesForecastOverviewRow stockout = rowByPartnerSku(overview, "STOCKOUT");
        assertEquals("stable", stockout.getLifecycleCode());
        assertRisk(stockout, "possible_stockout_distortion");
        assertTrue(stockout.getDataQualityWarnings().contains("possible_stockout_distortion"));
        assertEquals("low", stockout.getConfidenceLevel());

        SalesForecastOverviewRow overstock = rowByPartnerSku(overview, "OVERSTOCK");
        assertEquals("decline", overstock.getLifecycleCode());
        assertRisk(overstock, "overstock_risk");

        SalesForecastOverviewRow lowSample = rowByPartnerSku(overview, "NEWSAMPLE");
        assertEquals("low", lowSample.getConfidenceLevel());
        assertRisk(lowSample, "low_confidence");

        SalesForecastOverviewRow missingStock = rowByPartnerSku(overview, "MISSINGSTOCK");
        assertNull(missingStock.getCurrentStock());
        assertRisk(missingStock, "missing_stock_data");
    }

    @Test
    void marksStaleSalesDataWhenLatestFactDateIsOlderThanExpectedFreshness() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        factRepository.facts.addAll(factsForGrowthProduct(latestFactDate));
        InMemorySalesForecastStockRepository stockRepository = new InMemorySalesForecastStockRepository();
        stockRepository.stocks.add(stock("PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1", 30));
        DefaultSalesForecastService service = service(
                factRepository,
                new InMemorySalesForecastRunRepository(),
                stockRepository,
                Clock.fixed(Instant.parse("2026-05-25T00:00:00Z"), ZoneOffset.UTC)
        );

        SalesForecastOverviewRow row = service.getOverview(query()).getRows().get(0);

        assertTrue(row.getDataQualityWarnings().contains("stale_sales_data"));
        assertRisk(row, "stale_sales_data");
        assertEquals("medium", row.getConfidenceLevel());
    }

    @Test
    void repeatedReadForSameSourceDataDateReusesPersistedRun() {
        InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        factRepository.facts.addAll(factsForGrowthProduct(LocalDate.of(2026, 5, 20)));
        InMemorySalesForecastRunRepository runRepository = new InMemorySalesForecastRunRepository();
        InMemorySalesForecastStockRepository stockRepository = new InMemorySalesForecastStockRepository();
        stockRepository.stocks.add(stock("PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1", 12));
        DefaultSalesForecastService service = service(factRepository, runRepository, stockRepository);

        service.getOverview(query());
        SalesForecastOverviewView secondRead = service.getOverview(query());

        assertEquals("ready", secondRead.getState());
        assertEquals(1, runRepository.runs.size());
        assertEquals(1, runRepository.results.size());
        assertEquals(1, factRepository.listCalls);
    }

    @Test
    void returnsMissingSalesDataEmptyStateWhenStoreHasNoFacts() {
        DefaultSalesForecastService service = service(
                new InMemorySalesFactRepository(),
                new InMemorySalesForecastRunRepository(),
                new InMemorySalesForecastStockRepository()
        );

        SalesForecastOverviewView overview = service.getOverview(query());

        assertEquals("empty", overview.getState());
        assertEquals("missing_sales_data", overview.getEmptyState().getCode());
        assertEquals(0, overview.getRows().size());
        assertNull(overview.getSourceDataDate());
    }

    private DefaultSalesForecastService service(
            SalesFactRepository factRepository,
            SalesForecastRunRepository runRepository,
            SalesForecastStockRepository stockRepository
    ) {
        return service(
                factRepository,
                runRepository,
                stockRepository,
                new InMemorySalesActivityWindowRepository(),
                Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private DefaultSalesForecastService service(
            SalesFactRepository factRepository,
            SalesForecastRunRepository runRepository,
            SalesForecastStockRepository stockRepository,
            Clock clock
    ) {
        return service(
                factRepository,
                runRepository,
                stockRepository,
                new InMemorySalesActivityWindowRepository(),
                clock
        );
    }

    private DefaultSalesForecastService service(
            SalesFactRepository factRepository,
            SalesForecastRunRepository runRepository,
            SalesForecastStockRepository stockRepository,
            SalesActivityWindowRepository activityWindowRepository,
            Clock clock
    ) {
        return service(
                factRepository,
                runRepository,
                stockRepository,
                activityWindowRepository,
                clock,
                null
        );
    }

    private DefaultSalesForecastService service(
            SalesFactRepository factRepository,
            SalesForecastRunRepository runRepository,
            SalesForecastStockRepository stockRepository,
            SalesActivityWindowRepository activityWindowRepository,
            Clock clock,
            InMemoryOperationConfigTypedVersionRepository typedVersionRepository
    ) {
        return service(
                factRepository,
                runRepository,
                stockRepository,
                activityWindowRepository,
                new InMemorySalesForecastFollowUpRepository(),
                clock,
                typedVersionRepository
        );
    }

    private DefaultSalesForecastService service(
            SalesFactRepository factRepository,
            SalesForecastRunRepository runRepository,
            SalesForecastStockRepository stockRepository,
            SalesActivityWindowRepository activityWindowRepository,
            SalesForecastFollowUpRepository followUpRepository,
            Clock clock
    ) {
        return service(
                factRepository,
                runRepository,
                stockRepository,
                activityWindowRepository,
                followUpRepository,
                clock,
                null
        );
    }

    private DefaultSalesForecastService service(
            SalesFactRepository factRepository,
            SalesForecastRunRepository runRepository,
            SalesForecastStockRepository stockRepository,
            SalesActivityWindowRepository activityWindowRepository,
            SalesForecastFollowUpRepository followUpRepository,
            Clock clock,
            InMemoryOperationConfigTypedVersionRepository typedVersionRepository
    ) {
        return new DefaultSalesForecastService(
                factRepository,
                runRepository,
                stockRepository,
                activityWindowRepository,
                followUpRepository,
                new SalesForecastFeatureBuilder(new ProductLifecycleClassifier()),
                new DefaultSalesForecastEngine(),
                clock,
                typedVersionRepository
        );
    }

    private SalesForecastQuery query() {
        return new SalesForecastQuery(10002L, "STR245027-SAU", "SA");
    }

    private List<DailySalesFact> factsForGrowthProduct(LocalDate latestFactDate) {
        return productFacts(
                latestFactDate,
                "PAPERSAYSB359",
                "Z02AD5F198C0C2E813C30Z-1",
                "Paper notebook",
                this::unitsForOffset
        );
    }

    private List<DailySalesFact> productFacts(
            LocalDate latestFactDate,
            String partnerSku,
            String sku,
            String productTitle,
            DayUnits dayUnits
    ) {
        List<DailySalesFact> facts = new ArrayList<>();
        for (int dayOffset = 89; dayOffset >= 0; dayOffset--) {
            int netUnits = dayUnits.unitsForOffset(dayOffset);
            if (netUnits >= 0) {
                facts.add(fact(latestFactDate.minusDays(dayOffset), netUnits, partnerSku, sku, productTitle));
            }
        }
        return facts;
    }

    private int unitsForOffset(int dayOffset) {
        if (dayOffset <= 6) {
            return 3;
        }
        if (dayOffset <= 13) {
            return dayOffset == 13 ? 4 : 1;
        }
        if (dayOffset <= 27) {
            return dayOffset <= 21 ? 2 : 1;
        }
        if (dayOffset <= 29) {
            return dayOffset == 28 ? 3 : 4;
        }
        return dayOffset <= 59 ? 1 : 0;
    }

    private DailySalesFact fact(LocalDate factDate, int netUnits) {
        return fact(factDate, netUnits, "PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1", "Paper notebook");
    }

    private DailySalesFact fact(
            LocalDate factDate,
            int netUnits,
            String partnerSku,
            String sku,
            String productTitle
    ) {
        return new DailySalesFact(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                10001L,
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                factDate,
                partnerSku,
                sku,
                "Z02AD5F198C0C2E813C30Z",
                "SA",
                "SAR",
                productTitle,
                10,
                20,
                netUnits,
                netUnits,
                0,
                netUnits,
                BigDecimal.TEN,
                null,
                null,
                null
        );
    }

    private SalesForecastStockSnapshot stock(String partnerSku, String sku, int currentStock) {
        return new SalesForecastStockSnapshot(partnerSku, sku, currentStock);
    }

    private OperationConfigTypedVersion typedVersion(
            String versionNo,
            String displayName,
            OperationConfigVersionType configType
    ) {
        String sourceVersionNo = OperationConfigVersionType.BUSINESS_CALENDAR.equals(configType)
                ? OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO
                : OperationConfigDefaultVersionCatalog.DEFAULT_LIFECYCLE_VERSION_NO;
        return new OperationConfigTypedVersion(
                91001L,
                versionNo,
                displayName,
                configType.name(),
                "CURRENT",
                sourceVersionNo,
                "运营发布",
                "typed evidence fixture",
                1,
                "10002/STR245027-SAU/SA",
                "[]",
                10003L,
                10003L,
                LocalDateTime.of(2026, 5, 20, 9, 0),
                LocalDateTime.of(2026, 5, 20, 9, 0)
        );
    }

    private SalesForecastOverviewRow rowByPartnerSku(SalesForecastOverviewView overview, String partnerSku) {
        return overview.getRows().stream()
                .filter(row -> partnerSku.equals(row.getPartnerSku()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void activeActivityWindowFactorInfluencesForecastAndConfigVersion() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        factRepository.facts.addAll(factsForGrowthProduct(latestFactDate));
        InMemorySalesForecastStockRepository stockRepository = new InMemorySalesForecastStockRepository();
        stockRepository.stocks.add(stock("PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1", 30));
        InMemorySalesActivityWindowRepository activityRepository = new InMemorySalesActivityWindowRepository();
        activityRepository.records.add(activityWindow(40001L, "Ramadan Peak", new BigDecimal("1.20"), true, 2, latestFactDate.plusDays(5), latestFactDate.plusDays(20)));
        activityRepository.records.add(activityWindow(40002L, "Disabled campaign", new BigDecimal("1.50"), false, 9, latestFactDate.plusDays(1), latestFactDate.plusDays(2)));
        activityRepository.records.add(activityWindow(40003L, "Old campaign", new BigDecimal("1.60"), true, 10, latestFactDate.minusDays(20), latestFactDate.minusDays(10)));
        DefaultSalesForecastService service = service(
                factRepository,
                new InMemorySalesForecastRunRepository(),
                stockRepository,
                activityRepository,
                Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC)
        );

        SalesForecastOverviewRow row = service.getOverview(query()).getRows().get(0);

        assertEquals(127, row.getForecastUnits30());
        assertEquals("1.2000", row.getDetail().getFactorBreakdown().getFutureFactor().toPlainString());
        assertEquals("DEFAULT_V1-ACTIVITY-v2", row.getConfigVersion());
        assertEquals("DEFAULT_V1-ACTIVITY-v2", row.getDetail().getConfigVersion());
        assertTrue(row.getActivityWindowSummary().contains("40001"));
        assertTrue(row.getActivityWindowSummary().contains("Ramadan Peak"));
        assertTrue(row.getActivityExplanation().contains("Ramadan Peak"));
        assertTrue(!row.getActivityWindowSummary().contains("Disabled campaign"));
        assertTrue(!row.getActivityWindowSummary().contains("Old campaign"));
    }

    @Test
    void newActivityConfigCreatesFutureRunWithoutOverwritingExistingForecastEvidence() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        factRepository.facts.addAll(factsForGrowthProduct(latestFactDate));
        InMemorySalesForecastRunRepository runRepository = new InMemorySalesForecastRunRepository();
        InMemorySalesForecastStockRepository stockRepository = new InMemorySalesForecastStockRepository();
        stockRepository.stocks.add(stock("PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1", 30));
        InMemorySalesActivityWindowRepository activityRepository = new InMemorySalesActivityWindowRepository();
        DefaultSalesForecastService service = service(
                factRepository,
                runRepository,
                stockRepository,
                activityRepository,
                Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC)
        );

        SalesForecastOverviewView firstOverview = service.getOverview(query());
        activityRepository.records.add(activityWindow(40001L, "White Friday", new BigDecimal("1.30"), true, 7, latestFactDate.plusDays(10), latestFactDate.plusDays(20)));
        SalesForecastOverviewView secondOverview = service.getOverview(query());

        assertEquals(2, runRepository.runs.size());
        assertEquals("DEFAULT_V1", runRepository.results.get(firstOverview.getRunId()).get(0).getConfigVersion());
        assertEquals("DEFAULT_V1-ACTIVITY-v7", runRepository.results.get(secondOverview.getRunId()).get(0).getConfigVersion());
        assertTrue(runRepository.results.get(secondOverview.getRunId()).get(0).getActivityWindowSummary().contains("White Friday"));
    }

    @Test
    void operationsConfigBundleActivityWindowStampsForecastRunAndPreservesHistoricalEvidence() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        factRepository.facts.addAll(factsForGrowthProduct(latestFactDate));
        InMemorySalesForecastRunRepository runRepository = new InMemorySalesForecastRunRepository();
        InMemorySalesForecastStockRepository stockRepository = new InMemorySalesForecastStockRepository();
        stockRepository.stocks.add(stock("PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1", 30));
        InMemorySalesActivityWindowRepository activityRepository = new InMemorySalesActivityWindowRepository();
        activityRepository.records.add(operationConfigActivityWindow(
                8604001L,
                "Ramadan Suite",
                new BigDecimal("1.20"),
                86040L,
                "OPS_CONFIG_86040",
                latestFactDate.plusDays(5),
                latestFactDate.plusDays(20)
        ));
        DefaultSalesForecastService service = service(
                factRepository,
                runRepository,
                stockRepository,
                activityRepository,
                Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC)
        );

        SalesForecastOverviewView firstOverview = service.getOverview(query());
        activityRepository.records.clear();
        activityRepository.records.add(operationConfigActivityWindow(
                8604101L,
                "Eid Suite",
                new BigDecimal("1.30"),
                86041L,
                "OPS_CONFIG_86041",
                latestFactDate.plusDays(5),
                latestFactDate.plusDays(20)
        ));
        SalesForecastOverviewView secondOverview = service.getOverview(query());

        assertEquals(2, runRepository.runs.size());
        assertEquals("OPS_CONFIG_86040", runRepository.runs.get(firstOverview.getRunId()).getConfigVersion());
        assertEquals("OPS_CONFIG_86040", runRepository.results.get(firstOverview.getRunId()).get(0).getConfigVersion());
        assertEquals("OPS_CONFIG_86041", runRepository.runs.get(secondOverview.getRunId()).getConfigVersion());
        assertEquals("OPS_CONFIG_86041", runRepository.results.get(secondOverview.getRunId()).get(0).getConfigVersion());
        assertTrue(runRepository.results.get(secondOverview.getRunId()).get(0).getActivityWindowSummary().contains("OPS_CONFIG_86041"));
        assertTrue(!runRepository.results.get(firstOverview.getRunId()).get(0).getActivityWindowSummary().contains("OPS_CONFIG_86041"));
    }

    @Test
    void typedVersionEvidenceStampsForecastRunAndNewPublishDoesNotRewriteOldRunEvidence() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        factRepository.facts.addAll(factsForGrowthProduct(latestFactDate));
        InMemorySalesForecastRunRepository runRepository = new InMemorySalesForecastRunRepository();
        InMemorySalesForecastStockRepository stockRepository = new InMemorySalesForecastStockRepository();
        stockRepository.stocks.add(stock("PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1", 30));
        InMemoryOperationConfigTypedVersionRepository typedVersionRepository =
                new InMemoryOperationConfigTypedVersionRepository();
        typedVersionRepository.replaceWith(List.of(
                typedVersion(
                        "CALENDAR_CONFIG_2026_v1",
                        "2026 运营日历 v1",
                        OperationConfigVersionType.BUSINESS_CALENDAR
                ),
                typedVersion(
                        "LIFECYCLE_CONFIG_2026_v1",
                        "2026 生命周期 v1",
                        OperationConfigVersionType.PRODUCT_LIFECYCLE
                )
        ));
        DefaultSalesForecastService service = service(
                factRepository,
                runRepository,
                stockRepository,
                new InMemorySalesActivityWindowRepository(),
                Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC),
                typedVersionRepository
        );

        SalesForecastOverviewView firstOverview = service.getOverview(query());
        typedVersionRepository.replaceWith(List.of(
                typedVersion(
                        "CALENDAR_CONFIG_2026_v2",
                        "2026 运营日历 v2",
                        OperationConfigVersionType.BUSINESS_CALENDAR
                ),
                typedVersion(
                        "LIFECYCLE_CONFIG_2026_v2",
                        "2026 生命周期 v2",
                        OperationConfigVersionType.PRODUCT_LIFECYCLE
                )
        ));
        SalesForecastOverviewView secondOverview = service.getOverview(query());

        assertEquals(2, runRepository.runs.size());
        assertEquals("CALENDAR_CONFIG_2026_v1", runRepository.runs.get(firstOverview.getRunId()).getCalendarVersionNo());
        assertEquals("2026 运营日历 v1", runRepository.runs.get(firstOverview.getRunId()).getCalendarVersionName());
        assertEquals("LIFECYCLE_CONFIG_2026_v1", runRepository.runs.get(firstOverview.getRunId()).getLifecycleVersionNo());
        assertEquals("2026 生命周期 v1", runRepository.runs.get(firstOverview.getRunId()).getLifecycleVersionName());
        assertEquals("CALENDAR_CONFIG_2026_v2", runRepository.runs.get(secondOverview.getRunId()).getCalendarVersionNo());
        assertEquals("LIFECYCLE_CONFIG_2026_v2", runRepository.runs.get(secondOverview.getRunId()).getLifecycleVersionNo());
        assertTrue(runRepository.results.get(firstOverview.getRunId()).get(0).getFeatureSnapshotJson()
                .contains("\"calendarVersionNo\":\"CALENDAR_CONFIG_2026_v1\""));
        assertTrue(runRepository.results.get(firstOverview.getRunId()).get(0).getFeatureSnapshotJson()
                .contains("\"lifecycleVersionNo\":\"LIFECYCLE_CONFIG_2026_v1\""));
        assertTrue(!runRepository.results.get(firstOverview.getRunId()).get(0).getFeatureSnapshotJson()
                .contains("CALENDAR_CONFIG_2026_v2"));
    }

    @Test
    void followUpMarksStayScopedToForecastProducts() {
        InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        factRepository.facts.addAll(factsForGrowthProduct(LocalDate.of(2026, 5, 20)));
        InMemorySalesForecastRunRepository runRepository = new InMemorySalesForecastRunRepository();
        InMemorySalesForecastStockRepository stockRepository = new InMemorySalesForecastStockRepository();
        stockRepository.stocks.add(stock("PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1", 12));
        InMemorySalesForecastFollowUpRepository followUpRepository = new InMemorySalesForecastFollowUpRepository();
        DefaultSalesForecastService service = service(
                factRepository,
                runRepository,
                stockRepository,
                new InMemorySalesActivityWindowRepository(),
                followUpRepository,
                Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC)
        );

        SalesForecastFollowUpView marked = service.setFollowUp(new SalesForecastFollowUpCommand(
                10002L,
                "STR245027-SAU",
                "SA",
                "PAPERSAYSB359",
                "Z02AD5F198C0C2E813C30Z-1",
                true,
                10003L
        ));
        SalesForecastOverviewRow row = service.getOverview(query()).getRows().get(0);

        assertTrue(marked.isMarked());
        assertTrue(row.isFollowUpMarked());

        SalesForecastFollowUpView unmarked = service.setFollowUp(new SalesForecastFollowUpCommand(
                10002L,
                "STR245027-SAU",
                "SA",
                "PAPERSAYSB359",
                "Z02AD5F198C0C2E813C30Z-1",
                false,
                10003L
        ));
        SalesForecastOverviewRow updatedRow = service.getOverview(query()).getRows().get(0);

        assertTrue(!unmarked.isMarked());
        assertTrue(!updatedRow.isFollowUpMarked());
    }

    @Test
    void explicitRecalculateCreatesNewRunWithoutOverwritingExistingEvidence() {
        InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        factRepository.facts.addAll(factsForGrowthProduct(LocalDate.of(2026, 5, 20)));
        InMemorySalesForecastRunRepository runRepository = new InMemorySalesForecastRunRepository();
        InMemorySalesForecastStockRepository stockRepository = new InMemorySalesForecastStockRepository();
        stockRepository.stocks.add(stock("PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1", 12));
        DefaultSalesForecastService service = service(factRepository, runRepository, stockRepository);

        SalesForecastOverviewView firstOverview = service.getOverview(query());
        SalesForecastRunStatusView status = service.recalculate(query());

        assertEquals("succeeded", status.getStatus());
        assertEquals(2, runRepository.runs.size());
        assertEquals(2, runRepository.results.size());
        assertEquals(1, runRepository.results.get(firstOverview.getRunId()).size());
        assertEquals(1, runRepository.results.get(status.getRunId()).size());
    }

    @Test
    void explicitRecalculateReturnsReadableFailureWhenSalesFactsAreMissing() {
        DefaultSalesForecastService service = service(
                new InMemorySalesFactRepository(),
                new InMemorySalesForecastRunRepository(),
                new InMemorySalesForecastStockRepository()
        );

        SalesForecastRunStatusView status = service.recalculate(query());

        assertEquals("failed", status.getStatus());
        assertTrue(status.getFailureReason().contains("销量事实"));
    }

    @Test
    void exportUsesForecastFiltersWindowAndScope() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        factRepository.facts.addAll(factsForGrowthProduct(latestFactDate));
        factRepository.facts.addAll(productFacts(latestFactDate, "OVERSTOCK-PSKU", "SKU-OVER", "Overstock toy", day -> {
            if (day <= 13) {
                return day <= 6 ? 0 : 1;
            }
            if (day <= 27) {
                return 3;
            }
            return 0;
        }));
        InMemorySalesForecastStockRepository stockRepository = new InMemorySalesForecastStockRepository();
        stockRepository.stocks.add(stock("PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1", 12));
        stockRepository.stocks.add(stock("OVERSTOCK-PSKU", "SKU-OVER", 240));
        DefaultSalesForecastService service = service(
                factRepository,
                new InMemorySalesForecastRunRepository(),
                stockRepository
        );

        SalesForecastExportView export = service.exportCsv(
                query(),
                new SalesForecastExportQuery(60, "overstock", "decline", "risk", "all")
        );

        assertEquals("text/csv;charset=UTF-8", export.getContentType());
        assertTrue(export.getFilename().contains("sales-forecast"));
        assertTrue(export.getContent().contains("OVERSTOCK-PSKU"));
        assertTrue(export.getContent().contains("SKU-OVER"));
        assertTrue(export.getContent().contains(",60,"));
        assertTrue(!export.getContent().contains("PAPERSAYSB359"));
    }

    private void assertRisk(SalesForecastOverviewRow row, String riskCode) {
        assertTrue(
                row.getRiskLabels().stream().anyMatch(label -> riskCode.equals(label.getCode())),
                () -> "Expected risk " + riskCode + " in " + row.getRiskLabels()
        );
    }

    private SalesActivityWindowRecord activityWindow(
            Long id,
            String name,
            BigDecimal factor,
            boolean enabled,
            int versionNo,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        return new SalesActivityWindowRecord(
                id,
                10002L,
                "STR245027-SAU",
                "SA",
                name,
                "holiday",
                null,
                dateFrom,
                dateTo,
                factor,
                enabled,
                versionNo,
                10003L,
                10003L
        );
    }

    private SalesActivityWindowRecord operationConfigActivityWindow(
            Long id,
            String name,
            BigDecimal factor,
            Long bundleVersionId,
            String bundleVersionNo,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        return new SalesActivityWindowRecord(
                id,
                10002L,
                "STR245027-SAU",
                "SA",
                name,
                "holiday",
                null,
                dateFrom,
                dateTo,
                factor,
                true,
                1,
                10003L,
                10003L,
                bundleVersionId,
                bundleVersionNo,
                "operator",
                "运营发布"
        );
    }

    private static class InMemorySalesFactRepository implements SalesFactRepository {
        private final List<DailySalesFact> facts = new ArrayList<>();
        private int listCalls;

        @Override
        public long saveBatch(SalesImportBatch batch) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void upsert(DailySalesFact fact) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DailySalesFact> list(SalesFactQuery query) {
            listCalls++;
            List<DailySalesFact> matching = new ArrayList<>();
            for (DailySalesFact fact : facts) {
                if (fact.getOwnerUserId().equals(query.getOwnerUserId())
                        && fact.getStoreCode().equals(query.getStoreCode())
                        && fact.getSiteCode().equals(query.getSiteCode())
                        && !fact.getFactDate().isBefore(query.getDateFrom())
                        && !fact.getFactDate().isAfter(query.getDateTo())) {
                    matching.add(fact);
                }
            }
            return matching;
        }

        @Override
        public LocalDate findLatestFactDate(Long ownerUserId, String storeCode, String siteCode) {
            return facts.stream()
                    .filter(fact -> fact.getOwnerUserId().equals(ownerUserId)
                            && fact.getStoreCode().equals(storeCode)
                            && fact.getSiteCode().equals(siteCode))
                    .map(DailySalesFact::getFactDate)
                    .max(LocalDate::compareTo)
                    .orElse(null);
        }
    }

    private static class InMemorySalesForecastRunRepository implements SalesForecastRunRepository {
        private final Map<Long, SalesForecastRunRecord> runs = new LinkedHashMap<>();
        private final Map<Long, List<SalesForecastResultRecord>> results = new LinkedHashMap<>();
        private long nextRunId = 50001L;
        private long nextResultId = 60001L;

        @Override
        public SalesForecastRunRecord findLatestCompleted(SalesForecastQuery query) {
            return runs.values().stream()
                    .filter(run -> run.getOwnerUserId().equals(query.getOwnerUserId())
                            && run.getStoreCode().equals(query.getStoreCode())
                            && run.getSiteCode().equals(query.getSiteCode()))
                    .reduce((first, second) -> second)
                    .orElse(null);
        }

        @Override
        public SalesForecastRunRecord saveRun(SalesForecastRunRecord run) {
            SalesForecastRunRecord saved = run.withIdAndCalculatedAt(nextRunId++, LocalDateTime.of(2026, 5, 21, 9, 30));
            runs.put(saved.getId(), saved);
            return saved;
        }

        @Override
        public void saveResults(Long runId, List<SalesForecastResultRecord> records) {
            List<SalesForecastResultRecord> saved = new ArrayList<>();
            for (SalesForecastResultRecord record : records) {
                saved.add(record.withIdAndRunId(nextResultId++, runId));
            }
            results.put(runId, saved);
        }

        @Override
        public List<SalesForecastResultRecord> listResults(Long runId) {
            return results.getOrDefault(runId, List.of());
        }
    }

    private static class InMemorySalesForecastStockRepository implements SalesForecastStockRepository {
        private final List<SalesForecastStockSnapshot> stocks = new ArrayList<>();

        @Override
        public List<SalesForecastStockSnapshot> listCurrentStock(SalesForecastQuery query) {
            return stocks;
        }
    }

    private static class InMemorySalesActivityWindowRepository implements SalesActivityWindowRepository {
        private final List<SalesActivityWindowRecord> records = new ArrayList<>();

        @Override
        public SalesActivityWindowRecord save(SalesActivityWindowRecord record) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SalesActivityWindowRecord find(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setEnabled(Long id, boolean enabled, Long updatedBy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SalesActivityWindowRecord> listHistory(SalesActivityWindowScope scope) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SalesActivityWindowRecord> listActive(SalesActivityWindowScope scope) {
            List<SalesActivityWindowRecord> active = new ArrayList<>();
            for (SalesActivityWindowRecord record : records) {
                if (record.getOwnerUserId().equals(scope.getOwnerUserId())
                        && record.getStoreCode().equals(scope.getStoreCode())
                        && record.getSiteCode().equals(scope.getSiteCode())
                        && record.isEnabled()
                        && !record.getDateTo().isBefore(scope.getDateFrom())
                        && !record.getDateFrom().isAfter(scope.getDateTo())) {
                    active.add(record);
                }
            }
            return active;
        }
    }

    private static class InMemorySalesForecastFollowUpRepository implements SalesForecastFollowUpRepository {
        private final Map<String, SalesForecastFollowUpRecord> records = new LinkedHashMap<>();

        @Override
        public SalesForecastFollowUpRecord setMarked(SalesForecastFollowUpCommand command) {
            SalesForecastFollowUpRecord record = new SalesForecastFollowUpRecord(
                    command.getOwnerUserId(),
                    command.getStoreCode(),
                    command.getSiteCode(),
                    command.getPartnerSku(),
                    command.getSku(),
                    command.isMarked(),
                    command.getOperatorUserId()
            );
            records.put(
                    key(command.getOwnerUserId(), command.getStoreCode(), command.getSiteCode(), command.getPartnerSku(), command.getSku()),
                    record
            );
            return record;
        }

        @Override
        public List<SalesForecastFollowUpRecord> listMarked(SalesForecastQuery query) {
            List<SalesForecastFollowUpRecord> matching = new ArrayList<>();
            for (SalesForecastFollowUpRecord record : records.values()) {
                if (record.getOwnerUserId().equals(query.getOwnerUserId())
                        && record.getStoreCode().equals(query.getStoreCode())
                        && record.getSiteCode().equals(query.getSiteCode())
                        && record.isMarked()) {
                    matching.add(record);
                }
            }
            return matching;
        }

        private String key(Long ownerUserId, String storeCode, String siteCode, String partnerSku, String sku) {
            return ownerUserId + "|" + storeCode + "|" + siteCode + "|" + partnerSku + "|" + sku;
        }
    }

    @FunctionalInterface
    private interface DayUnits {
        int unitsForOffset(int dayOffset);
    }
}
