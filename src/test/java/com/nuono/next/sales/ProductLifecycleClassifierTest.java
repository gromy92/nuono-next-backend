package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductLifecycleClassifierTest {

    @Test
    void classifiesNewProductsFromRecentFirstListingSignal() {
        ProductLifecycleClassifier classifier = new ProductLifecycleClassifier();

        ProductLifecycleResult result = classifier.classify(new ProductLifecycleSignal(
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 5, 5),
                20,
                facts(1, 1, 1)
        ));

        assertEquals("new", result.getCode());
        assertEquals("新品", result.getLabel());
        assertEquals("DEFAULT_V1", result.getRuleVersion());
    }

    @Test
    void classifiesGrowthStableDeclineAndLongTailFromRecentSalesShape() {
        ProductLifecycleClassifier classifier = new ProductLifecycleClassifier();

        assertEquals("growth", classifier.classify(signal(3, 12, 20)).getCode());
        assertEquals("stable", classifier.classify(signal(10, 11, 20)).getCode());
        assertEquals("decline", classifier.classify(signal(12, 3, 20)).getCode());
        ProductLifecycleResult longTail = classifier.classify(signal(1, 1, 20));
        assertEquals("longTail", longTail.getCode());
        assertEquals("长尾期", longTail.getLabel());
    }

    @Test
    void classifiesDataInsufficientAsSeparateDefaultV1Category() {
        ProductLifecycleClassifier classifier = new ProductLifecycleClassifier();

        ProductLifecycleResult result = classifier.classify(new ProductLifecycleSignal(
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 1, 1),
                20,
                factsOnDistinctDays(5, 2)
        ));

        assertEquals("data_insufficient", result.getCode());
        assertEquals("数据不足", result.getLabel());
        assertEquals("DEFAULT_V1", result.getRuleVersion());
    }

    @Test
    void flagsStockoutDistortionInsteadOfNaturalDeclineWhenRecentSalesExistWithZeroStock() {
        ProductLifecycleClassifier classifier = new ProductLifecycleClassifier();

        ProductLifecycleResult result = classifier.classify(signal(12, 3, 0));

        assertEquals("stable", result.getCode());
        assertTrue(result.getWarningCodes().contains("possible_stockout_distortion"));
    }

    private ProductLifecycleSignal signal(int previous14DaysUnits, int recent14DaysUnits, int availableStock) {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        addDailyFacts(facts, analysisDate.minusDays(20), 7, previous14DaysUnits);
        addDailyFacts(facts, analysisDate.minusDays(7), 7, recent14DaysUnits);
        return new ProductLifecycleSignal(
                analysisDate,
                LocalDate.of(2026, 1, 1),
                availableStock,
                facts
        );
    }

    private List<DailySalesFact> facts(int... units) {
        LocalDate date = LocalDate.of(2026, 5, 18);
        List<DailySalesFact> facts = new ArrayList<>();
        for (int unit : units) {
            facts.add(fact(date, unit));
            date = date.plusDays(1);
        }
        return facts;
    }

    private List<DailySalesFact> factsOnDistinctDays(int dayCount, int unitPerDay) {
        LocalDate date = LocalDate.of(2026, 5, 1);
        List<DailySalesFact> facts = new ArrayList<>();
        for (int i = 0; i < dayCount; i++) {
            facts.add(fact(date.plusDays(i), unitPerDay));
        }
        return facts;
    }

    private void addDailyFacts(List<DailySalesFact> facts, LocalDate startDate, int dayCount, int totalUnits) {
        int baseUnits = totalUnits / dayCount;
        int extraUnits = totalUnits % dayCount;
        for (int i = 0; i < dayCount; i++) {
            int unit = baseUnits + (i < extraUnits ? 1 : 0);
            facts.add(fact(startDate.plusDays(i), unit));
        }
    }

    private DailySalesFact fact(LocalDate date, int netUnits) {
        return new DailySalesFact(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                10001L,
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                date,
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                "Z57C90A4184D0CFD75218Z",
                "SA",
                "SAR",
                "Sanitized sample product",
                null,
                null,
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
}
