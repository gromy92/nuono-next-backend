package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductLifecycleFeatureBuilderFormulaMetricsTest {

    private final ProductLifecycleFeatureBuilder builder = new ProductLifecycleFeatureBuilder();

    @Test
    void oldSnapshotConstructorDefaultsHistoricalWindowToEmptyWindow() {
        ProductLifecycleFeatureWindow emptyWindow = new ProductLifecycleFeatureWindow(30, 0, 0, null, 0, 0, false);

        ProductLifecycleFeatureSnapshot snapshot = new ProductLifecycleFeatureSnapshot(
                query(),
                LocalDate.of(2026, 5, 20),
                emptyWindow,
                emptyWindow,
                emptyWindow,
                emptyWindow,
                emptyWindow,
                emptyWindow,
                null,
                null,
                BigDecimal.ZERO,
                0,
                List.of(),
                "{}"
        );

        assertEquals(31, snapshot.getHistoricalT38ToT8().getDayCount());
        assertEquals(0, snapshot.getHistoricalT38ToT8().getObservedDays());
        assertEquals(0, snapshot.getHistoricalT38ToT8().getSalesUnits());
    }

    @Test
    void buildsHistoricalT38ToT8WindowForDeclineFormula() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        addFacts(facts, analysisDate.minusDays(38), 31, 2);
        addFacts(facts, analysisDate.minusDays(6), 7, 1);

        ProductLifecycleFeatureSnapshot snapshot = builder.build(query(), analysisDate, facts);

        assertEquals(31, snapshot.getHistoricalT38ToT8().getDayCount());
        assertEquals(31, snapshot.getHistoricalT38ToT8().getObservedDays());
        assertEquals(62, snapshot.getHistoricalT38ToT8().getSalesUnits());
    }

    @Test
    void calculatesTrimmedMomentumForVolatileGrowthShape() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        addFacts(facts, analysisDate.minusDays(29), 15, 2);
        addFacts(facts, analysisDate.minusDays(14), 15, 6);
        facts.add(fact(analysisDate.minusDays(29), 100));
        facts.add(fact(analysisDate.minusDays(1), 100));
        facts.add(fact(analysisDate.minusDays(14), -6));

        ProductLifecycleFeatureSnapshot snapshot = builder.build(query(), analysisDate, facts);
        ProductLifecycleGrowthShapeMetrics metrics = snapshot.getGrowthShapeMetrics();

        assertEquals(30, metrics.getDailySales30().size());
        assertEquals(30, metrics.getValidPointCount());
        assertEquals(new BigDecimal("2.0000"), metrics.getTrimmedFirstHalfAvg());
        assertEquals(new BigDecimal("6.0000"), metrics.getTrimmedSecondHalfAvg());
        assertEquals(new BigDecimal("2.0000"), metrics.getMomentumRate());
    }

    @Test
    void countsOnlyObservedFactDatesAsGrowthShapeValidPoints() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = List.of(
                fact(analysisDate.minusDays(29), 2),
                fact(analysisDate, 6)
        );

        ProductLifecycleFeatureSnapshot snapshot = builder.build(query(), analysisDate, facts);
        ProductLifecycleGrowthShapeMetrics metrics = snapshot.getGrowthShapeMetrics();

        assertEquals(30, metrics.getDailySales30().size());
        assertEquals(2, metrics.getValidPointCount());
    }

    private void addFacts(List<DailySalesFact> facts, LocalDate startDate, int dayCount, int units) {
        for (int i = 0; i < dayCount; i++) {
            facts.add(fact(startDate.plusDays(i), units));
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
