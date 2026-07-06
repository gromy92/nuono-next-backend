package com.nuono.next.salesforecast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.sales.DailySalesFact;
import com.nuono.next.sales.NoonSalesCsvImportService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalesForecastFeatureBuilderTest {

    @Test
    void buildsProductSnapshotsFromDailySalesFactsWithoutLifecycleState() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder();

        List<SalesForecastFeatureSnapshot> snapshots = builder.build(factsForGrowthProduct(latestFactDate), latestFactDate);

        assertEquals(1, snapshots.size());
        SalesForecastFeatureSnapshot snapshot = snapshots.get(0);
        assertEquals(10002L, snapshot.getOwnerUserId());
        assertEquals("STR245027-SAU", snapshot.getStoreCode());
        assertEquals("SA", snapshot.getSiteCode());
        assertEquals("PAPERSAYSB359", snapshot.getPartnerSku());
        assertEquals("Z02AD5F198C0C2E813C30Z-1", snapshot.getSku());
        assertEquals("Paper notebook", snapshot.getProductTitle());
        assertEquals(latestFactDate, snapshot.getLatestFactDate());
        assertEquals(21, snapshot.getHistoryUnits7());
        assertEquals(60, snapshot.getHistoryUnits30());
        assertEquals(90, snapshot.getHistoryUnits60());
        assertEquals(90, snapshot.getHistoryUnits90());
        assertEquals(90, snapshot.getObservedDays());
        assertEquals(List.of(), snapshot.getWarningCodes());
    }

    @Test
    void groupsForecastFeaturesByPartnerSkuWhenExternalSkuChanges() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder();
        List<DailySalesFact> facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "Z-OLD-1", 2),
                fact(LocalDate.of(2026, 5, 19), "Z-OLD-1", 3),
                fact(LocalDate.of(2026, 5, 20), "Z-NEW-1", 5)
        );

        List<SalesForecastFeatureSnapshot> snapshots = builder.build(facts, latestFactDate);

        assertEquals(1, snapshots.size());
        SalesForecastFeatureSnapshot snapshot = snapshots.get(0);
        assertEquals("PAPERSAYSB359", snapshot.getPartnerSku());
        assertEquals("Z-NEW-1", snapshot.getSku());
        assertEquals(10, snapshot.getHistoryUnits7());
        assertEquals(10, snapshot.getHistoryUnits30());
    }

    @Test
    void keepsSamePartnerSkuSeparatedAcrossStoresAndSites() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder();
        List<DailySalesFact> facts = List.of(
                scopedFact("STR108065-NSA", "SA", LocalDate.of(2026, 5, 20), "PAPERSAYS065", "Z-SA-1", 2),
                scopedFact("STR108065-NAE", "AE", LocalDate.of(2026, 5, 20), "PAPERSAYS065", "Z-AE-1", 3),
                scopedFact("STR69486-NSA", "SA", LocalDate.of(2026, 5, 20), "PAPERSAYS065", "Z-OTHER-1", 5)
        );

        List<SalesForecastFeatureSnapshot> snapshots = builder.build(facts, latestFactDate);

        assertEquals(3, snapshots.size());
        assertEquals(2, snapshot(snapshots, "STR108065-NSA", "SA").getHistoryUnits30());
        assertEquals(3, snapshot(snapshots, "STR108065-NAE", "AE").getHistoryUnits30());
        assertEquals(5, snapshot(snapshots, "STR69486-NSA", "SA").getHistoryUnits30());
    }

    @Test
    void excludesFactsWithoutBusinessPskuFromForecastFeatures() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder();
        List<DailySalesFact> facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "-", "Z-MISSING-1", 2),
                fact(LocalDate.of(2026, 5, 19), "-", "Z-MISSING-2", 3),
                fact(LocalDate.of(2026, 5, 20), "PAPERSAYSB359", "Z-VALID-1", 5)
        );

        List<SalesForecastFeatureSnapshot> snapshots = builder.build(facts, latestFactDate);

        assertEquals(1, snapshots.size());
        assertEquals("PAPERSAYSB359", snapshots.get(0).getPartnerSku());
        assertEquals(5, snapshots.get(0).getHistoryUnits30());
    }

    @Test
    void carriesProductDimensionFieldsForCalendarFactorMatching() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder();

        SalesForecastFeatureSnapshot snapshot = builder.build(
                factsForGrowthProduct(latestFactDate),
                latestFactDate,
                List.of(new SalesForecastStockSnapshot(
                        "PAPERSAYSB359",
                        "Z02AD5F198C0C2E813C30Z-1",
                        12,
                        "PAPERSAY",
                        "copy_multipurpose_paper",
                        "paper"
                ))
        ).get(0);

        assertEquals("PAPERSAY", snapshot.getBrand());
        assertEquals("copy_multipurpose_paper", snapshot.getProductFulltype());
        assertEquals("paper", snapshot.getProductFamily());
        assertEquals(List.of(), snapshot.getWarningCodes());
    }

    @Test
    void usesProductDimensionTitleWhenLatestSalesFactTitleIsBlank() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder();

        SalesForecastFeatureSnapshot snapshot = builder.build(
                List.of(fact("PAPERSAYS065", "ZEA2BC495A97B0328CF53Z-1", " ", latestFactDate, 5)),
                latestFactDate,
                List.of(new SalesForecastStockSnapshot(
                        "PAPERSAYS065",
                        "ZEA2BC495A97B0328CF53Z-1",
                        198,
                        "PAPERSAY",
                        "stationery-paper-copy_multipurpose_paper",
                        "stationery",
                        "A4白色不干胶打印纸"
                ))
        ).get(0);

        assertEquals("A4白色不干胶打印纸", snapshot.getProductTitle());
    }

    @Test
    void collapsesHistoricalSkuChangesToOnePartnerSkuSnapshot() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder();

        List<SalesForecastFeatureSnapshot> snapshots = builder.build(
                List.of(
                        fact("PAPERSAYS167", "SKU-OLD", "Old title", latestFactDate.minusDays(1), 5),
                        fact("PAPERSAYS167", "SKU-NEW", "New title", latestFactDate, 7)
                ),
                latestFactDate,
                List.of(
                        new SalesForecastStockSnapshot("PAPERSAYS167", "SKU-OLD", 4, "PAPERSAY", null, null),
                        new SalesForecastStockSnapshot("PAPERSAYS167", "SKU-NEW", 6, null, "copy_multipurpose_paper", "paper")
                )
        );

        assertEquals(1, snapshots.size());
        SalesForecastFeatureSnapshot snapshot = snapshots.get(0);
        assertEquals("PAPERSAYS167", snapshot.getPartnerSku());
        assertEquals("SKU-NEW", snapshot.getSku());
        assertEquals("New title", snapshot.getProductTitle());
        assertEquals(12, snapshot.getHistoryUnits7());
        assertEquals(12, snapshot.getHistoryUnits30());
        assertEquals(12, snapshot.getHistoryUnits60());
        assertEquals(12, snapshot.getHistoryUnits90());
        assertEquals(10, snapshot.getCurrentStock());
        assertEquals(new BigDecimal("25.0"), snapshot.getStockCoverDays());
        assertEquals("PAPERSAY", snapshot.getBrand());
        assertEquals("copy_multipurpose_paper", snapshot.getProductFulltype());
        assertEquals("paper", snapshot.getProductFamily());
    }

    @Test
    void includesCurrentProductsWithoutSalesTrainingData() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder();

        List<SalesForecastFeatureSnapshot> snapshots = builder.build(
                List.of(fact("PAPERSAYS065", "ZEA2BC495A97B0328CF53Z-1", "Paper", latestFactDate, 5)),
                latestFactDate,
                List.of(
                        new SalesForecastStockSnapshot(
                                10002L,
                                "STR108065-NSA",
                                "SA",
                                "PAPERSAYS065",
                                "ZEA2BC495A97B0328CF53Z-1",
                                198,
                                "PAPERSAY",
                                "copy_multipurpose_paper",
                                "paper",
                                "A4 paper"
                        ),
                        new SalesForecastStockSnapshot(
                                10002L,
                                "STR108065-NSA",
                                "SA",
                                "PAPERSAYSB400",
                                "Z-COLD-1",
                                24,
                                "PAPERSAY",
                                "gift_wrap_paper",
                                "paper",
                                "New active listing"
                        )
                )
        );

        assertEquals(2, snapshots.size());
        SalesForecastFeatureSnapshot coldStart = snapshots.stream()
                .filter(snapshot -> "PAPERSAYSB400".equals(snapshot.getPartnerSku()))
                .findFirst()
                .orElseThrow();
        assertEquals(10002L, coldStart.getOwnerUserId());
        assertEquals("STR108065-NSA", coldStart.getStoreCode());
        assertEquals("SA", coldStart.getSiteCode());
        assertEquals("Z-COLD-1", coldStart.getSku());
        assertEquals("New active listing", coldStart.getProductTitle());
        assertEquals(0, coldStart.getHistoryUnits7());
        assertEquals(0, coldStart.getHistoryUnits30());
        assertEquals(0, coldStart.getHistoryUnits60());
        assertEquals(0, coldStart.getHistoryUnits90());
        assertEquals(0, coldStart.getObservedDays());
        assertEquals(24, coldStart.getCurrentStock());
        assertEquals(List.of("no_sales_training_data"), coldStart.getWarningCodes());
    }

    @Test
    void doesNotForecastHistoricalFactsWhenCurrentActiveProductBoundaryIsEmpty() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder();

        List<SalesForecastFeatureSnapshot> snapshots = builder.build(
                List.of(fact("PAPERSAYS065", "ZEA2BC495A97B0328CF53Z-1", "Paper", latestFactDate, 5)),
                latestFactDate,
                List.of()
        );

        assertEquals(0, snapshots.size());
    }

    @Test
    void normalizesHistoricalSalesWithDailyCalendarFactorsWithoutChangingRawHistory() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 30);
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder();
        List<DailySalesFact> facts = List.of(
                fact("PAPERSAYS065", "SKU-1", "A4 paper", LocalDate.of(2026, 5, 24), 10),
                fact("PAPERSAYS065", "SKU-1", "A4 paper", LocalDate.of(2026, 5, 25), 10),
                fact("PAPERSAYS065", "SKU-1", "A4 paper", LocalDate.of(2026, 5, 26), 7),
                fact("PAPERSAYS065", "SKU-1", "A4 paper", LocalDate.of(2026, 5, 27), 7),
                fact("PAPERSAYS065", "SKU-1", "A4 paper", LocalDate.of(2026, 5, 28), 7),
                fact("PAPERSAYS065", "SKU-1", "A4 paper", LocalDate.of(2026, 5, 29), 7),
                fact("PAPERSAYS065", "SKU-1", "A4 paper", LocalDate.of(2026, 5, 30), 11)
        );

        SalesForecastFeatureSnapshot snapshot = builder.build(
                facts,
                latestFactDate,
                List.of(new SalesForecastStockSnapshot(
                        10002L,
                        "STR108065-NSA",
                        "SA",
                        "PAPERSAYS065",
                        "SKU-1",
                        100,
                        "PAPERSAY",
                        "copy_multipurpose_paper",
                        "paper",
                        "A4 paper"
                )),
                (ownerUserId, storeCode, siteCode, dateFrom, dateTo, brand, productFulltype, productFamily) ->
                        java.util.stream.Stream.iterate(dateFrom, date -> date.plusDays(1))
                                .limit(java.time.temporal.ChronoUnit.DAYS.between(dateFrom, dateTo) + 1)
                                .map(date -> {
                                    if (!date.isBefore(LocalDate.of(2026, 5, 26))
                                            && !date.isAfter(LocalDate.of(2026, 5, 29))) {
                                        return new BigDecimal("0.70");
                                    }
                                    if (LocalDate.of(2026, 5, 30).equals(date)) {
                                        return new BigDecimal("1.10");
                                    }
                                    return BigDecimal.ONE;
                                })
                                .collect(java.util.stream.Collectors.toList())
        ).get(0);

        assertEquals(59, snapshot.getHistoryUnits7());
        assertEquals("70.0000", snapshot.getAdjustedHistoryUnits7().setScale(4).toPlainString());
        assertEquals("70.0000", snapshot.getAdjustedHistoryUnits30().setScale(4).toPlainString());
        assertEquals("70.0000", snapshot.getAdjustedHistoryUnits90().setScale(4).toPlainString());
    }

    @Test
    void imputesHistoricalStockoutDaysOnlyForForecastTrainingDemand() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 30);
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder();
        List<DailySalesFact> facts = new ArrayList<>();
        List<SalesForecastHistoricalStockSnapshot> historicalStocks = new ArrayList<>();
        for (int dayOffset = 59; dayOffset >= 30; dayOffset--) {
            LocalDate factDate = latestFactDate.minusDays(dayOffset);
            facts.add(fact("PAPERSAYS065", "SKU-1", "A4 paper", factDate, 10));
            historicalStocks.add(new SalesForecastHistoricalStockSnapshot(
                    10002L,
                    "STR245027-SAU",
                    "SA",
                    "PAPERSAYS065",
                    factDate,
                    20
            ));
        }
        for (int dayOffset = 29; dayOffset >= 0; dayOffset--) {
            LocalDate factDate = latestFactDate.minusDays(dayOffset);
            facts.add(fact("PAPERSAYS065", "SKU-1", "A4 paper", factDate, 0));
            historicalStocks.add(new SalesForecastHistoricalStockSnapshot(
                    10002L,
                    "STR245027-SAU",
                    "SA",
                    "PAPERSAYS065",
                    factDate,
                    0
            ));
        }

        SalesForecastFeatureSnapshot snapshot = builder.build(
                facts,
                latestFactDate,
                List.of(new SalesForecastStockSnapshot(
                        10002L,
                        "STR245027-SAU",
                        "SA",
                        "PAPERSAYS065",
                        "SKU-1",
                        0,
                        "PAPERSAY",
                        "copy_multipurpose_paper",
                        "paper",
                        "A4 paper"
                )),
                (ownerUserId, storeCode, siteCode, dateFrom, dateTo, brand, productFulltype, productFamily) ->
                        java.util.Collections.nCopies(
                                (int) java.time.temporal.ChronoUnit.DAYS.between(dateFrom, dateTo) + 1,
                                BigDecimal.ONE
                        ),
                historicalStocks
        ).get(0);

        assertEquals(0, snapshot.getHistoryUnits7());
        assertEquals(0, snapshot.getHistoryUnits30());
        assertEquals(300, snapshot.getHistoryUnits60());
        assertEquals("70.0000", snapshot.getAdjustedHistoryUnits7().setScale(4).toPlainString());
        assertEquals("300.0000", snapshot.getAdjustedHistoryUnits30().setScale(4).toPlainString());
        assertEquals("600.0000", snapshot.getAdjustedHistoryUnits60().setScale(4).toPlainString());
        assertEquals("600.0000", snapshot.getAdjustedHistoryUnits90().setScale(4).toPlainString());
        assertEquals(List.of("historical_stockout_imputed"), snapshot.getWarningCodes());
    }

    private List<DailySalesFact> factsForGrowthProduct(LocalDate latestFactDate) {
        List<DailySalesFact> facts = new ArrayList<>();
        for (int dayOffset = 89; dayOffset >= 0; dayOffset--) {
            int netUnits = unitsForOffset(dayOffset);
            facts.add(fact(latestFactDate.minusDays(dayOffset), netUnits));
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
        return fact("PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1", "Paper notebook", factDate, netUnits);
    }

    private DailySalesFact fact(LocalDate factDate, String sku, int netUnits) {
        return fact("PAPERSAYSB359", sku, "Paper notebook", factDate, netUnits);
    }

    private DailySalesFact fact(LocalDate factDate, String partnerSku, String sku, int netUnits) {
        return fact(partnerSku, sku, "Paper notebook", factDate, netUnits);
    }

    private DailySalesFact fact(String partnerSku, String sku, String productTitle, LocalDate factDate, int netUnits) {
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
                sku,
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

    private DailySalesFact scopedFact(
            String storeCode,
            String siteCode,
            LocalDate factDate,
            String partnerSku,
            String sku,
            int netUnits
    ) {
        return new DailySalesFact(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                10001L,
                10002L,
                245027L,
                storeCode,
                siteCode,
                factDate,
                partnerSku,
                sku,
                sku,
                siteCode,
                "SAR",
                "Paper notebook",
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

    private SalesForecastFeatureSnapshot snapshot(
            List<SalesForecastFeatureSnapshot> snapshots,
            String storeCode,
            String siteCode
    ) {
        return snapshots.stream()
                .filter(snapshot -> storeCode.equals(snapshot.getStoreCode()) && siteCode.equals(snapshot.getSiteCode()))
                .findFirst()
                .orElseThrow();
    }
}
