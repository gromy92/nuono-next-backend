package com.nuono.next.salesforecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.operationsconfig.OperationConfigTypedVersion;
import com.nuono.next.operationsconfig.OperationConfigTypedVersionRepository;
import com.nuono.next.operationsconfig.OperationConfigVersionType;
import com.nuono.next.sales.DailySalesFact;
import com.nuono.next.sales.NoonSalesCsvImportService;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultSalesForecastServiceTest {

    @Test
    void forecastUsesTypedBusinessCalendarWithoutMultiplyingLegacyActivityWindow() {
        LocalDate latestFactDate = LocalDate.of(2026, 6, 23);
        RecordingSalesForecastRunRepository runRepository = new RecordingSalesForecastRunRepository();
        DefaultSalesForecastService service = new DefaultSalesForecastService(
                new FixedSalesFactRepository(latestFactDate),
                runRepository,
                query -> List.of(new SalesForecastStockSnapshot(
                        "PAPERSAY001",
                        "SKU-1",
                        100,
                        "PAPERSAY",
                        "copy_multipurpose_paper",
                        "paper"
                )),
                new LegacyActivityWindowRepository(),
                new NoopFollowUpRepository(),
                new SalesForecastFeatureBuilder(),
                new DefaultSalesForecastEngine(),
                Clock.fixed(Instant.parse("2026-06-29T00:00:00Z"), ZoneOffset.UTC),
                typedCalendarRepository()
        );

        SalesForecastOverviewView overview = service.getOverview(new SalesForecastQuery(
                10002L,
                "STR108065-NSA",
                "SA"
        ));

        assertEquals("ready", overview.getState());
        assertEquals(1, runRepository.savedResults.size());
        SalesForecastResultRecord result = runRepository.savedResults.get(0);
        assertEquals(31, result.getForecastUnits30());
        assertEquals("1.0167", result.getFutureFactor().setScale(4).toPlainString());
        assertTrue(result.getShortReason().contains("日历因子30/60/90天：1.0167"));
        assertFalse(result.getFeatureSnapshotJson().contains("lifecycle"));
    }

    @Test
    void forecastNormalizesHistoricalCalendarFactorsBeforePredicting() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 30);
        List<DailySalesFact> facts = List.of(
                fact(latestFactDate.minusDays(6), 10),
                fact(latestFactDate.minusDays(5), 10),
                fact(latestFactDate.minusDays(4), 7),
                fact(latestFactDate.minusDays(3), 7),
                fact(latestFactDate.minusDays(2), 7),
                fact(latestFactDate.minusDays(1), 7),
                fact(latestFactDate, 11)
        );
        RecordingSalesForecastRunRepository runRepository = new RecordingSalesForecastRunRepository();
        DefaultSalesForecastService service = new DefaultSalesForecastService(
                new ListedSalesFactRepository(latestFactDate, facts),
                runRepository,
                query -> List.of(new SalesForecastStockSnapshot(
                        10002L,
                        "STR108065-NSA",
                        "SA",
                        "PAPERSAY001",
                        "SKU-1",
                        100,
                        "PAPERSAY",
                        "copy_multipurpose_paper",
                        "paper",
                        "Paper"
                )),
                new LegacyActivityWindowRepository(),
                new NoopFollowUpRepository(),
                new SalesForecastFeatureBuilder(),
                new DefaultSalesForecastEngine(),
                Clock.fixed(Instant.parse("2026-06-29T00:00:00Z"), ZoneOffset.UTC),
                overlapCalendarRepository()
        );

        SalesForecastOverviewView overview = service.getOverview(new SalesForecastQuery(
                10002L,
                "STR108065-NSA",
                "SA"
        ));

        assertEquals("ready", overview.getState());
        assertEquals(1, runRepository.savedResults.size());
        SalesForecastResultRecord result = runRepository.savedResults.get(0);
        assertEquals(59, result.getHistoryUnits7());
        assertEquals(123, result.getForecastUnits30());
        assertTrue(result.getFeatureSnapshotJson().contains("\"adjustedHistoryUnits7\":\"70.0000\""));
        assertTrue(result.getFeatureSnapshotJson().contains("\"historyCalendarFactorImpacts\""));
    }

    @Test
    void forecastImputesHistoricalStockoutDemandFromHistoricalStockFacts() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 30);
        List<DailySalesFact> facts = new ArrayList<>();
        List<SalesForecastHistoricalStockSnapshot> historicalStocks = new ArrayList<>();
        for (int dayOffset = 59; dayOffset >= 30; dayOffset--) {
            LocalDate factDate = latestFactDate.minusDays(dayOffset);
            facts.add(fact(factDate, 10));
            historicalStocks.add(new SalesForecastHistoricalStockSnapshot(
                    10002L,
                    "STR108065-NSA",
                    "SA",
                    "PAPERSAY001",
                    factDate,
                    20
            ));
        }
        for (int dayOffset = 29; dayOffset >= 0; dayOffset--) {
            LocalDate factDate = latestFactDate.minusDays(dayOffset);
            facts.add(fact(factDate, 0));
            historicalStocks.add(new SalesForecastHistoricalStockSnapshot(
                    10002L,
                    "STR108065-NSA",
                    "SA",
                    "PAPERSAY001",
                    factDate,
                    0
            ));
        }
        RecordingSalesForecastRunRepository runRepository = new RecordingSalesForecastRunRepository();
        DefaultSalesForecastService service = new DefaultSalesForecastService(
                new ListedSalesFactRepository(latestFactDate, facts),
                runRepository,
                query -> List.of(new SalesForecastStockSnapshot(
                        10002L,
                        "STR108065-NSA",
                        "SA",
                        "PAPERSAY001",
                        "SKU-1",
                        0,
                        "PAPERSAY",
                        "copy_multipurpose_paper",
                        "paper",
                        "Paper"
                )),
                new LegacyActivityWindowRepository(),
                new NoopFollowUpRepository(),
                new SalesForecastFeatureBuilder(),
                new DefaultSalesForecastEngine(),
                Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC),
                typedCalendarRepository(),
                null,
                (query, dateFrom, dateTo, partnerSkus) -> historicalStocks
        );

        SalesForecastOverviewView overview = service.getOverview(new SalesForecastQuery(
                10002L,
                "STR108065-NSA",
                "SA"
        ));

        assertEquals("ready", overview.getState());
        SalesForecastResultRecord result = runRepository.savedResults.get(0);
        assertEquals(0, result.getHistoryUnits30());
        assertEquals(300, result.getHistoryUnits60());
        assertEquals(306, result.getForecastUnits30());
        assertTrue(result.getWarningCodes().contains("historical_stockout_imputed"));
        assertTrue(result.getFeatureSnapshotJson().contains("\"adjustedHistoryUnits30\":\"300.0000\""));
    }

    @Test
    void forecastCoversCurrentProductsWithoutSalesTrainingData() {
        LocalDate latestFactDate = LocalDate.of(2026, 6, 23);
        RecordingSalesForecastRunRepository runRepository = new RecordingSalesForecastRunRepository();
        DefaultSalesForecastService service = new DefaultSalesForecastService(
                new FixedSalesFactRepository(latestFactDate),
                runRepository,
                query -> List.of(
                        new SalesForecastStockSnapshot(
                                10002L,
                                "STR108065-NSA",
                                "SA",
                                "PAPERSAY001",
                                "SKU-1",
                                100,
                                "PAPERSAY",
                                "copy_multipurpose_paper",
                                "paper",
                                "Paper"
                        ),
                        new SalesForecastStockSnapshot(
                                10002L,
                                "STR108065-NSA",
                                "SA",
                                "PAPERSAY-COLD",
                                "SKU-COLD",
                                12,
                                "PAPERSAY",
                                "gift_wrap_paper",
                                "paper",
                                "New active product"
                        )
                ),
                new LegacyActivityWindowRepository(),
                new NoopFollowUpRepository(),
                new SalesForecastFeatureBuilder(),
                new DefaultSalesForecastEngine(),
                Clock.fixed(Instant.parse("2026-06-29T00:00:00Z"), ZoneOffset.UTC),
                typedCalendarRepository()
        );

        SalesForecastOverviewView overview = service.getOverview(new SalesForecastQuery(
                10002L,
                "STR108065-NSA",
                "SA"
        ));

        assertEquals("ready", overview.getState());
        assertEquals(2, runRepository.savedResults.size());
        SalesForecastResultRecord coldStart = result(runRepository.savedResults, "PAPERSAY-COLD");
        assertEquals(10002L, coldStart.getOwnerUserId());
        assertEquals("STR108065-NSA", coldStart.getStoreCode());
        assertEquals("SA", coldStart.getSiteCode());
        assertEquals(0, coldStart.getHistoryUnits30());
        assertEquals(0, coldStart.getObservedDays());
        assertEquals(4, coldStart.getForecastUnits30());
        assertEquals(7, coldStart.getForecastUnits60());
        assertEquals(10, coldStart.getForecastUnits90());
        assertEquals("low", coldStart.getConfidenceLevel());
        assertTrue(coldStart.getWarningCodes().contains("no_sales_training_data"));
        assertTrue(coldStart.getRiskCodes().contains("no_sales_training_data"));
        assertTrue(coldStart.getRiskCodes().contains("low_confidence"));
        assertTrue(coldStart.getShortReason().contains("低样本同类目兜底"));
        assertTrue(coldStart.getFeatureSnapshotJson().contains("\"lowSampleDailyFloor\":\"0.1000\""));
    }

    @Test
    void currentStockDoesNotAffectSalesForecastConfidenceOrRisk() {
        LocalDate latestFactDate = LocalDate.of(2026, 6, 23);
        RecordingSalesForecastRunRepository runRepository = new RecordingSalesForecastRunRepository();
        DefaultSalesForecastService service = new DefaultSalesForecastService(
                new FixedSalesFactRepository(latestFactDate),
                runRepository,
                query -> List.of(new SalesForecastStockSnapshot(
                        10002L,
                        "STR108065-NSA",
                        "SA",
                        "PAPERSAY001",
                        "SKU-1",
                        0,
                        "PAPERSAY",
                        "copy_multipurpose_paper",
                        "paper",
                        "Paper"
                )),
                new LegacyActivityWindowRepository(),
                new NoopFollowUpRepository(),
                new SalesForecastFeatureBuilder(),
                new DefaultSalesForecastEngine(),
                Clock.fixed(Instant.parse("2026-06-25T00:00:00Z"), ZoneOffset.UTC),
                typedCalendarRepository()
        );

        SalesForecastOverviewView overview = service.getOverview(new SalesForecastQuery(
                10002L,
                "STR108065-NSA",
                "SA"
        ));

        assertEquals("ready", overview.getState());
        assertEquals(1, runRepository.savedResults.size());
        SalesForecastResultRecord result = runRepository.savedResults.get(0);
        assertEquals(31, result.getForecastUnits30());
        assertEquals("high", result.getConfidenceLevel());
        assertFalse(result.getWarningCodes().contains("missing_stock_data"));
        assertFalse(result.getWarningCodes().contains("possible_stockout_distortion"));
        assertFalse(result.getRiskCodes().contains("replenishment_risk"));
        assertFalse(result.getRiskCodes().contains("overstock_risk"));
    }

    @Test
    void exportCsvDoesNotExposeOrFilterByLifecycle() {
        LocalDate latestFactDate = LocalDate.of(2026, 6, 23);
        DefaultSalesForecastService service = new DefaultSalesForecastService(
                new FixedSalesFactRepository(latestFactDate),
                new RecordingSalesForecastRunRepository(),
                query -> List.of(new SalesForecastStockSnapshot(
                        "PAPERSAY001",
                        "SKU-1",
                        100,
                        "PAPERSAY",
                        "copy_multipurpose_paper",
                        "paper"
                )),
                new LegacyActivityWindowRepository(),
                new NoopFollowUpRepository(),
                new SalesForecastFeatureBuilder(),
                new DefaultSalesForecastEngine(),
                Clock.fixed(Instant.parse("2026-06-29T00:00:00Z"), ZoneOffset.UTC),
                typedCalendarRepository()
        );

        SalesForecastExportView export = service.exportCsv(
                new SalesForecastQuery(10002L, "STR108065-NSA", "SA"),
                new SalesForecastExportQuery(30, "", "all", "all")
        );

        String[] lines = export.getContent().split("\\R");
        assertFalse(lines[0].contains("lifecycle"));
        assertTrue(export.getContent().contains("PAPERSAY001"));
    }

    @Test
    void overviewReusesCurrentCalendarRunWhenOnlyLifecycleVersionDiffers() {
        LocalDate latestFactDate = LocalDate.of(2026, 6, 23);
        RecordingSalesForecastRunRepository runRepository = new RecordingSalesForecastRunRepository();
        runRepository.existingRun = new SalesForecastRunRecord(
                90001L,
                10002L,
                "STR108065-NSA",
                "SA",
                latestFactDate,
                DefaultSalesForecastEngine.CALCULATION_VERSION,
                "CALENDAR_FACTOR_CURRENT",
                "CALENDAR_FACTOR_CURRENT",
                "站点类目日历因子",
                "运营配置",
                "OLD_LIFECYCLE_CONFIG",
                "旧生命周期配置",
                "历史兼容",
                "succeeded",
                1,
                LocalDateTime.of(2026, 6, 28, 0, 0)
        );
        runRepository.existingResults = List.of(resultRecord(latestFactDate));
        DefaultSalesForecastService service = new DefaultSalesForecastService(
                new FixedSalesFactRepository(latestFactDate),
                runRepository,
                query -> List.of(),
                new LegacyActivityWindowRepository(),
                new NoopFollowUpRepository(),
                new SalesForecastFeatureBuilder(),
                new DefaultSalesForecastEngine(),
                Clock.fixed(Instant.parse("2026-06-29T00:00:00Z"), ZoneOffset.UTC),
                typedCalendarRepository()
        );

        SalesForecastOverviewView overview = service.getOverview(new SalesForecastQuery(
                10002L,
                "STR108065-NSA",
                "SA"
        ));

        assertEquals(90001L, overview.getRunId());
        assertEquals(0, runRepository.saveRunCount);
    }

    @Test
    void overviewRecalculatesWhenExistingRunUsesOldCalculationVersion() {
        LocalDate latestFactDate = LocalDate.of(2026, 6, 23);
        RecordingSalesForecastRunRepository runRepository = new RecordingSalesForecastRunRepository();
        runRepository.existingRun = new SalesForecastRunRecord(
                90002L,
                10002L,
                "STR108065-NSA",
                "SA",
                latestFactDate,
                "SALES_FORECAST_V1",
                "CALENDAR_FACTOR_CURRENT",
                "CALENDAR_FACTOR_CURRENT",
                "站点类目日历因子",
                "运营配置",
                "OLD_LIFECYCLE_CONFIG",
                "旧生命周期配置",
                "历史兼容",
                "succeeded",
                1,
                LocalDateTime.of(2026, 6, 28, 0, 0)
        );
        runRepository.existingResults = List.of(resultRecord(latestFactDate));
        DefaultSalesForecastService service = new DefaultSalesForecastService(
                new FixedSalesFactRepository(latestFactDate),
                runRepository,
                query -> List.of(new SalesForecastStockSnapshot(
                        "PAPERSAY001",
                        "SKU-1",
                        100,
                        "PAPERSAY",
                        "copy_multipurpose_paper",
                        "paper"
                )),
                new LegacyActivityWindowRepository(),
                new NoopFollowUpRepository(),
                new SalesForecastFeatureBuilder(),
                new DefaultSalesForecastEngine(),
                Clock.fixed(Instant.parse("2026-06-29T00:00:00Z"), ZoneOffset.UTC),
                typedCalendarRepository()
        );

        SalesForecastOverviewView overview = service.getOverview(new SalesForecastQuery(
                10002L,
                "STR108065-NSA",
                "SA"
        ));

        assertEquals(70001L, overview.getRunId());
        assertEquals(DefaultSalesForecastEngine.CALCULATION_VERSION, overview.getCalculationVersion());
        assertEquals(1, runRepository.saveRunCount);
    }

    private OperationConfigTypedVersionRepository typedCalendarRepository() {
        OperationConfigTypedVersion version = new OperationConfigTypedVersion(
                88024L,
                "CALENDAR_FACTOR_CURRENT",
                "站点类目日历因子",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "CURRENT",
                null,
                "运营配置",
                "站点类目因子",
                1,
                "10002/STR108065-NSA/SA",
                "[{\"groupName\":\"活动\",\"itemName\":\"SA paper 单日活动\",\"cadence\":\"年度\",\"valueType\":\"日期范围/系数\",\"defaultValue\":\"2026-06-24 ~ 2026-06-24 / 1.50\",\"resultShape\":\"site:SA|family:paper\",\"note\":\"高置信\"}]",
                0L,
                0L,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 1, 0, 0)
        );
        return new OperationConfigTypedVersionRepository() {
            @Override
            public Long nextVersionId() {
                return 88025L;
            }

            @Override
            public void insert(OperationConfigTypedVersion version) {
            }

            @Override
            public List<OperationConfigTypedVersion> listVersions() {
                return List.of(version);
            }

            @Override
            public Optional<OperationConfigTypedVersion> findByVersionNo(String versionNo) {
                return "CALENDAR_FACTOR_CURRENT".equals(versionNo) ? Optional.of(version) : Optional.empty();
            }

            @Override
            public void update(OperationConfigTypedVersion version) {
            }

            @Override
            public void deleteByVersionNo(String versionNo) {
            }
        };
    }

    private OperationConfigTypedVersionRepository overlapCalendarRepository() {
        OperationConfigTypedVersion version = new OperationConfigTypedVersion(
                88026L,
                "CALENDAR_FACTOR_OVERLAP",
                "重叠日历因子",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "CURRENT",
                null,
                "运营配置",
                "重叠日历因子",
                2,
                "10002/STR108065-NSA/SA",
                "["
                        + "{\"groupName\":\"薪酬日\",\"itemName\":\"薪酬日\",\"cadence\":\"月度\",\"valueType\":\"日期范围/系数\",\"defaultValue\":\"2026-05-26 ~ 2026-05-30 / 1.10\",\"resultShape\":\"site:SA\",\"note\":\"稳定测算\"},"
                        + "{\"groupName\":\"节日\",\"itemName\":\"古尔邦节\",\"cadence\":\"年度\",\"valueType\":\"日期范围/系数\",\"defaultValue\":\"2026-05-26 ~ 2026-05-29 / 0.70\",\"resultShape\":\"site:SA\",\"note\":\"历史测算\"}"
                        + "]",
                0L,
                0L,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 1, 0, 0)
        );
        return new OperationConfigTypedVersionRepository() {
            @Override
            public Long nextVersionId() {
                return 88027L;
            }

            @Override
            public void insert(OperationConfigTypedVersion version) {
            }

            @Override
            public List<OperationConfigTypedVersion> listVersions() {
                return List.of(version);
            }

            @Override
            public Optional<OperationConfigTypedVersion> findByVersionNo(String versionNo) {
                return "CALENDAR_FACTOR_OVERLAP".equals(versionNo) ? Optional.of(version) : Optional.empty();
            }

            @Override
            public void update(OperationConfigTypedVersion version) {
            }

            @Override
            public void deleteByVersionNo(String versionNo) {
            }
        };
    }

    private SalesForecastResultRecord resultRecord(LocalDate latestFactDate) {
        return new SalesForecastResultRecord(
                1L,
                90001L,
                10002L,
                "STR108065-NSA",
                "SA",
                "PAPERSAY001",
                "SKU-1",
                "Paper",
                latestFactDate,
                7,
                30,
                60,
                90,
                90,
                100,
                new BigDecimal("100.0"),
                31,
                62,
                93,
                "SALES_FORECAST_V1",
                "CALENDAR_FACTOR_CURRENT",
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE,
                "medium",
                "中",
                "样本满足",
                "",
                "",
                "",
                "",
                "日历因子30/60/90天：1.0000 / 1.0000 / 1.0000",
                "{}"
        );
    }

    private SalesForecastResultRecord result(List<SalesForecastResultRecord> results, String partnerSku) {
        return results.stream()
                .filter(result -> partnerSku.equals(result.getPartnerSku()))
                .findFirst()
                .orElseThrow();
    }

    private DailySalesFact fact(LocalDate factDate, int netUnits) {
        return new DailySalesFact(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                1L,
                10002L,
                108065L,
                "STR108065-NSA",
                "SA",
                factDate,
                "PAPERSAY001",
                "SKU-1",
                "SKU-1",
                "SA",
                "SAR",
                "Paper",
                10,
                10,
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

    private static class ListedSalesFactRepository implements SalesFactRepository {
        private final LocalDate latestFactDate;
        private final List<DailySalesFact> facts;

        private ListedSalesFactRepository(LocalDate latestFactDate, List<DailySalesFact> facts) {
            this.latestFactDate = latestFactDate;
            this.facts = List.copyOf(facts);
        }

        @Override
        public long saveBatch(SalesImportBatch batch) {
            return 0;
        }

        @Override
        public void upsert(DailySalesFact fact) {
        }

        @Override
        public List<DailySalesFact> list(SalesFactQuery query) {
            return facts;
        }

        @Override
        public LocalDate findLatestFactDate(Long ownerUserId, String storeCode, String siteCode) {
            return latestFactDate;
        }
    }

    private static class FixedSalesFactRepository implements SalesFactRepository {
        private final LocalDate latestFactDate;

        private FixedSalesFactRepository(LocalDate latestFactDate) {
            this.latestFactDate = latestFactDate;
        }

        @Override
        public long saveBatch(SalesImportBatch batch) {
            return 0;
        }

        @Override
        public void upsert(DailySalesFact fact) {
        }

        @Override
        public List<DailySalesFact> list(SalesFactQuery query) {
            List<DailySalesFact> facts = new ArrayList<>();
            for (int dayOffset = 89; dayOffset >= 0; dayOffset--) {
                facts.add(new DailySalesFact(
                        NoonSalesCsvImportService.SOURCE_SYSTEM,
                        1L,
                        10002L,
                        108065L,
                        "STR108065-NSA",
                        "SA",
                        latestFactDate.minusDays(dayOffset),
                        "PAPERSAY001",
                        "SKU-1",
                        "SKU-1",
                        "SA",
                        "SAR",
                        "Paper",
                        10,
                        10,
                        1,
                        1,
                        0,
                        1,
                        BigDecimal.TEN,
                        null,
                        null,
                        null
                ));
            }
            return facts;
        }

        @Override
        public LocalDate findLatestFactDate(Long ownerUserId, String storeCode, String siteCode) {
            return latestFactDate;
        }
    }

    private static class RecordingSalesForecastRunRepository implements SalesForecastRunRepository {
        private final List<SalesForecastResultRecord> savedResults = new ArrayList<>();
        private SalesForecastRunRecord existingRun;
        private List<SalesForecastResultRecord> existingResults = List.of();
        private int saveRunCount;

        @Override
        public SalesForecastRunRecord findLatestCompleted(SalesForecastQuery query) {
            return existingRun;
        }

        @Override
        public SalesForecastRunRecord saveRun(SalesForecastRunRecord run) {
            saveRunCount++;
            return run.withIdAndCalculatedAt(70001L, LocalDateTime.of(2026, 6, 29, 0, 0));
        }

        @Override
        public void saveResults(Long runId, List<SalesForecastResultRecord> records) {
            savedResults.clear();
            savedResults.addAll(records);
        }

        @Override
        public List<SalesForecastResultRecord> listResults(Long runId) {
            if (existingRun != null && existingRun.getId().equals(runId)) {
                return existingResults;
            }
            return savedResults;
        }
    }

    private static class LegacyActivityWindowRepository implements SalesActivityWindowRepository {
        @Override
        public SalesActivityWindowRecord save(SalesActivityWindowRecord record) {
            return record;
        }

        @Override
        public SalesActivityWindowRecord find(Long id) {
            return null;
        }

        @Override
        public void setEnabled(Long id, boolean enabled, Long updatedBy) {
        }

        @Override
        public List<SalesActivityWindowRecord> listHistory(SalesActivityWindowScope scope) {
            return List.of();
        }

        @Override
        public List<SalesActivityWindowRecord> listActive(SalesActivityWindowScope scope) {
            return List.of(new SalesActivityWindowRecord(
                    40001L,
                    scope.getOwnerUserId(),
                    scope.getStoreCode(),
                    scope.getSiteCode(),
                    "旧黑五活动窗口",
                    "holiday",
                    "all",
                    scope.getDateFrom(),
                    scope.getDateTo(),
                    new BigDecimal("2.0000"),
                    true,
                    1,
                    0L,
                    0L
            ));
        }
    }

    private static class NoopFollowUpRepository implements SalesForecastFollowUpRepository {
        @Override
        public SalesForecastFollowUpRecord setMarked(SalesForecastFollowUpCommand command) {
            return null;
        }

        @Override
        public List<SalesForecastFollowUpRecord> listMarked(SalesForecastQuery query) {
            return List.of();
        }
    }
}
