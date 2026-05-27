package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductLifecycleFeatureBuilderTest {

    private final ProductLifecycleFeatureBuilder builder = new ProductLifecycleFeatureBuilder();

    @Test
    void buildsSalesAndPvWindowsFromInclusiveAnalysisDateBoundaries() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        addFacts(facts, analysisDate.minusDays(59), 30, 1, 10);
        addFacts(facts, analysisDate.minusDays(29), 15, 2, 20);
        addFacts(facts, analysisDate.minusDays(14), 15, 4, 30);

        ProductLifecycleFeatureSnapshot snapshot = builder.build(query(), analysisDate, facts);

        assertEquals(28, snapshot.getRecent7().getSalesUnits());
        assertEquals(60, snapshot.getRecent15().getSalesUnits());
        assertEquals(30, snapshot.getPrevious15().getSalesUnits());
        assertEquals(90, snapshot.getRecent30().getSalesUnits());
        assertEquals(120, snapshot.getRecent60().getSalesUnits());
        assertEquals(450L, snapshot.getRecent15().getPv());
        assertEquals(300L, snapshot.getPrevious15().getPv());
        assertEquals(1.0, snapshot.getSalesGrowth15().doubleValue(), 0.0001);
        assertEquals(0.5, snapshot.getPvGrowth15().doubleValue(), 0.0001);
        assertEquals(30, snapshot.getActiveSalesDays30());
    }

    @Test
    void estimatesMissingRecentPvFromPreviousTrafficToSalesRatioAndFlagsEvidence() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        addFacts(facts, analysisDate.minusDays(29), 15, 2, 20);
        addFacts(facts, analysisDate.minusDays(14), 15, 4, null);

        ProductLifecycleFeatureSnapshot snapshot = builder.build(query(), analysisDate, facts);

        assertEquals(600L, snapshot.getRecent15().getPv());
        assertTrue(snapshot.getRecent15().isPvEstimated());
        assertTrue(snapshot.getEvidenceJson().contains("pv_estimated"));
        assertEquals(1.0, snapshot.getPvGrowth15().doubleValue(), 0.0001);
    }

    @Test
    void keepsPvMissingWithQualityReasonWhenNoAdjacentEvidenceCanEstimateIt() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        addFacts(facts, analysisDate.minusDays(14), 15, 4, null);

        ProductLifecycleFeatureSnapshot snapshot = builder.build(query(), analysisDate, facts);

        assertNull(snapshot.getRecent15().getPv());
        assertTrue(snapshot.getQualityReasons().contains("pv_unresolvable"));
        assertTrue(snapshot.getEvidenceJson().contains("pv_unresolvable"));
    }

    @Test
    void calculatesThirtyDaySalesVolatilityCoefficient() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            int units = i % 2 == 0 ? 10 : 0;
            facts.add(fact(analysisDate.minusDays(i), units, 10));
        }

        ProductLifecycleFeatureSnapshot snapshot = builder.build(query(), analysisDate, facts);

        assertEquals(1.0, snapshot.getSalesVolatility30().doubleValue(), 0.0001);
        assertEquals(15, snapshot.getActiveSalesDays30());
    }

    private void addFacts(List<DailySalesFact> facts, LocalDate startDate, int dayCount, int units, Integer pv) {
        for (int i = 0; i < dayCount; i++) {
            facts.add(fact(startDate.plusDays(i), units, pv));
        }
    }

    private DailySalesFact fact(LocalDate date, int netUnits, Integer pv) {
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
                pv,
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
