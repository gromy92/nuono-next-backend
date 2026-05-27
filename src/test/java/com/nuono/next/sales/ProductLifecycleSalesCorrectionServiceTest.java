package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductLifecycleSalesCorrectionServiceTest {

    private final ProductLifecycleFeatureBuilder featureBuilder = new ProductLifecycleFeatureBuilder();
    private final ProductLifecycleSalesCorrectionService correctionService = new ProductLifecycleSalesCorrectionService();

    @Test
    void leavesSalesUnchangedWhenNoEnabledActivityWindowApplies() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = facts(analysisDate.minusDays(14), 15, 4);
        ProductLifecycleFeatureSnapshot raw = featureBuilder.build(query(), analysisDate, facts);

        ProductLifecycleCorrectedFeatureSnapshot corrected = correctionService.correct(
                query(),
                analysisDate,
                raw,
                facts,
                List.of(disabledWindow(analysisDate.minusDays(14), analysisDate, "Disabled", "2.00"))
        );

        assertEquals(0, new BigDecimal("60.0000").compareTo(corrected.getCorrectedRecent15Sales()));
        assertTrue(corrected.getEvidenceJson().contains("no_activity_factor"));
    }

    @Test
    void dividesSalesBySingleActiveFactorToNormalizeActivityLift() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = facts(analysisDate.minusDays(14), 15, 4);
        ProductLifecycleFeatureSnapshot raw = featureBuilder.build(query(), analysisDate, facts);

        ProductLifecycleCorrectedFeatureSnapshot corrected = correctionService.correct(
                query(),
                analysisDate,
                raw,
                facts,
                List.of(window(analysisDate.minusDays(14), analysisDate, "Ramadan", "2.00"))
        );

        assertEquals(0, new BigDecimal("30.0000").compareTo(corrected.getCorrectedRecent15Sales()));
        assertTrue(corrected.getEvidenceJson().contains("Ramadan"));
        assertTrue(corrected.getEvidenceJson().contains("activity_factor_applied"));
    }

    @Test
    void activityFactorEvidenceCarriesOperationsConfigBundleVersion() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = facts(analysisDate.minusDays(14), 15, 4);
        ProductLifecycleFeatureSnapshot raw = featureBuilder.build(query(), analysisDate, facts);

        ProductLifecycleCorrectedFeatureSnapshot corrected = correctionService.correct(
                query(),
                analysisDate,
                raw,
                facts,
                List.of(operationConfigWindow(analysisDate.minusDays(14), analysisDate, "Ramadan Suite", "2.00"))
        );

        assertTrue(corrected.getEvidenceJson().contains("\"bundleVersionId\":86040"));
        assertTrue(corrected.getEvidenceJson().contains("\"bundleVersionNo\":\"OPS_CONFIG_86040\""));
        assertTrue(corrected.getEvidenceJson().contains("\"bundleSourceLabel\":\"运营发布\""));
    }

    @Test
    void appliesOverlappingFactorsDeterministicallyByMultiplyingDailyFactors() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = facts(analysisDate.minusDays(14), 15, 4);
        ProductLifecycleFeatureSnapshot raw = featureBuilder.build(query(), analysisDate, facts);

        ProductLifecycleCorrectedFeatureSnapshot corrected = correctionService.correct(
                query(),
                analysisDate,
                raw,
                facts,
                List.of(
                        window(analysisDate.minusDays(14), analysisDate, "Ramadan", "2.00"),
                        window(analysisDate.minusDays(14), analysisDate, "Salary Day", "1.50")
                )
        );

        assertEquals(0, new BigDecimal("20.0000").compareTo(corrected.getCorrectedRecent15Sales()));
        assertEquals(List.of("Ramadan", "Salary Day"), corrected.getAppliedFactorNames());
    }

    @Test
    void exposesCorrectedTrendWhenPreviousWindowHadActivityLift() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        facts.addAll(facts(analysisDate.minusDays(29), 15, 4));
        facts.addAll(facts(analysisDate.minusDays(14), 15, 4));
        ProductLifecycleFeatureSnapshot raw = featureBuilder.build(query(), analysisDate, facts);

        ProductLifecycleCorrectedFeatureSnapshot corrected = correctionService.correct(
                query(),
                analysisDate,
                raw,
                facts,
                List.of(window(analysisDate.minusDays(29), analysisDate.minusDays(15), "Old Promo", "2.00"))
        );

        assertEquals(0.0, raw.getSalesGrowth15().doubleValue(), 0.0001);
        assertEquals(0, new BigDecimal("60.0000").compareTo(corrected.getCorrectedRecent15Sales()));
        assertEquals(0, new BigDecimal("30.0000").compareTo(corrected.getCorrectedPrevious15Sales()));
        assertEquals(1.0, corrected.getCorrectedSalesGrowth15().doubleValue(), 0.0001);
    }

    private SalesActivityWindowRecord window(LocalDate from, LocalDate to, String name, String factor) {
        return new SalesActivityWindowRecord(
                Math.abs(name.hashCode()) + 1L,
                10002L,
                "STR245027-SAU",
                "SA",
                name,
                "seasonal",
                null,
                from,
                to,
                new BigDecimal(factor),
                true,
                1,
                10003L,
                10003L
        );
    }

    private SalesActivityWindowRecord operationConfigWindow(LocalDate from, LocalDate to, String name, String factor) {
        return new SalesActivityWindowRecord(
                Math.abs(name.hashCode()) + 1L,
                10002L,
                "STR245027-SAU",
                "SA",
                name,
                "seasonal",
                null,
                from,
                to,
                new BigDecimal(factor),
                true,
                1,
                10003L,
                10003L,
                86040L,
                "OPS_CONFIG_86040",
                "operator",
                "运营发布"
        );
    }

    private SalesActivityWindowRecord disabledWindow(LocalDate from, LocalDate to, String name, String factor) {
        SalesActivityWindowRecord enabled = window(from, to, name, factor);
        return enabled.withEnabled(false, 10003L);
    }

    private List<DailySalesFact> facts(LocalDate startDate, int dayCount, int units) {
        List<DailySalesFact> facts = new ArrayList<>();
        for (int i = 0; i < dayCount; i++) {
            facts.add(fact(startDate.plusDays(i), units));
        }
        return facts;
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
                10,
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

    private ProductLifecycleStateQuery query() {
        return new ProductLifecycleStateQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1"
        );
    }
}
