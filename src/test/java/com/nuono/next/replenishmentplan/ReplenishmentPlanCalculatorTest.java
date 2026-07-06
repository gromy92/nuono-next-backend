package com.nuono.next.replenishmentplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.InboundBatch;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.MissingEtaBatch;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanInput;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanItemView;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.StockSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReplenishmentPlanCalculatorTest {

    private final ReplenishmentPlanCalculator calculator = new ReplenishmentPlanCalculator();

    @Test
    void shouldCalculateBasicExampleAirEmergencyAndSeaRegularPlan() {
        LocalDate anchorDate = LocalDate.of(2026, 7, 6);
        Map<Integer, BigDecimal> demand = new HashMap<>();
        demand.put(1, new BigDecimal("2.5"));
        demand.put(2, new BigDecimal("2.5"));
        demand.put(3, new BigDecimal("2.5"));
        demand.put(4, new BigDecimal("2.5"));
        demand.put(12, new BigDecimal("40"));
        demand.put(70, new BigDecimal("200"));

        PlanInput input = new PlanInput(
                "PSKU-001",
                "SKU-001",
                "Example Product",
                anchorDate,
                new StockSnapshot(new BigDecimal("10"), new BigDecimal("7"), new BigDecimal("3")),
                demand,
                Collections.singletonList(new InboundBatch(
                        1001L,
                        "BATCH-1001",
                        "SEA",
                        "ACTIVE",
                        anchorDate.plusDays(20),
                        new BigDecimal("100")
                )),
                Collections.emptyList()
        );

        PlanItemView result = calculator.calculate(input, null);

        assertEquals("REPLENISHMENT_PLAN_BASIC_V1", result.getCalculationVersion());
        assertEquals("PSKU-001", result.getPartnerSku());
        assertEquals("SKU-001", result.getSku());
        assertEquals("Example Product", result.getProductTitle());
        assertEquals(new BigDecimal("10"), result.getCurrentStockUnits());
        assertEquals(new BigDecimal("7"), result.getFbnStockUnits());
        assertEquals(new BigDecimal("3"), result.getSupermallStockUnits());
        assertEquals(new BigDecimal("100"), result.getKnownInboundUnits());
        assertEquals(4, result.getFirstStockoutDay());
        assertEquals("4-12 天", result.getStockoutWindowLabel());
        assertEquals(12, result.getAirWindowStartDay());
        assertEquals(27, result.getAirWindowEndDay());
        assertEquals(new BigDecimal("40"), result.getAirSuggestedUnits());
        assertEquals(70, result.getSeaWindowStartDay());
        assertEquals(100, result.getSeaWindowEndDay());
        assertEquals(new BigDecimal("200"), result.getSeaSuggestedUnits());
        assertEquals(100, result.getDailyProjection().size());
        assertEquals(new BigDecimal("100"), result.getDailyProjection().get(19).getInboundUnits());
        assertTrue(result.getWarnings().contains("daily_forecast_gap"));
        assertFalse(result.getWarnings().contains("daily_forecast_missing"));
        assertEquals(70, result.getConfigSnapshot().getSeaLeadDays());
        assertTrue(result.getExplanation().contains("70-100"));
        assertTrue(result.getExplanation().contains("air emergency triggered"));
    }

    @Test
    void shouldKeepAirSuggestionZeroWhenStockoutDoesNotHappenBeforeSeaArrival() {
        Map<Integer, BigDecimal> demand = new HashMap<>();
        for (int day = 70; day < 100; day++) {
            demand.put(day, BigDecimal.ONE);
        }

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-NO-AIR",
                "SKU-NO-AIR",
                "No Air Product",
                LocalDate.of(2026, 7, 6),
                new StockSnapshot(new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO),
                demand,
                Collections.emptyList(),
                Collections.emptyList()
        ), ReplenishmentPlanConfig.defaultBasicV1());

        assertNull(result.getFirstStockoutDay());
        assertNull(result.getStockoutWindowLabel());
        assertEquals(new BigDecimal("0"), result.getAirSuggestedUnits());
        assertEquals(new BigDecimal("30"), result.getSeaSuggestedUnits());
        assertTrue(result.getExplanation().contains("air emergency not triggered"));
    }

    @Test
    void shouldApplySameDayEtaInboundToInitialStockWithoutShowingItOnDayOne() {
        LocalDate anchorDate = LocalDate.of(2026, 7, 6);
        Map<Integer, BigDecimal> demand = new HashMap<>();
        demand.put(1, new BigDecimal("2"));

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-SAME-DAY",
                "SKU-SAME-DAY",
                "Same Day Product",
                anchorDate,
                new StockSnapshot(new BigDecimal("1"), new BigDecimal("1"), BigDecimal.ZERO),
                demand,
                Collections.singletonList(new InboundBatch(
                        3001L,
                        "BATCH-SAME-DAY",
                        "AIR",
                        "ACTIVE",
                        anchorDate,
                        new BigDecimal("2")
                )),
                Collections.emptyList()
        ), null);

        assertEquals(new BigDecimal("2"), result.getKnownInboundUnits());
        assertEquals(new BigDecimal("0"), result.getDailyProjection().get(0).getInboundUnits());
        assertEquals(new BigDecimal("1"), result.getDailyProjection().get(0).getProjectedStock());
        assertNull(result.getFirstStockoutDay());
    }

    @Test
    void shouldReportDayZeroStockoutWhenInitialAvailableStockIsZero() {
        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-ZERO-INITIAL",
                "SKU-ZERO-INITIAL",
                "Zero Initial Product",
                LocalDate.of(2026, 7, 6),
                new StockSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyList()
        ), null);

        assertEquals(0, result.getFirstStockoutDay());
        assertEquals("0-12 天", result.getStockoutWindowLabel());
    }

    @Test
    void shouldPreserveMissingStockFactsWhileUsingZeroForProjection() {
        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-MISSING-STOCK",
                "SKU-MISSING-STOCK",
                "Missing Stock Product",
                LocalDate.of(2026, 7, 6),
                new StockSnapshot(null, null, null),
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyList()
        ), null);

        assertNull(result.getCurrentStockUnits());
        assertNull(result.getFbnStockUnits());
        assertNull(result.getSupermallStockUnits());
        assertEquals(0, result.getFirstStockoutDay());
        assertTrue(result.getWarnings().contains("stock_fact_missing"));
        assertTrue(result.getWarnings().contains("fbn_stock_fact_missing"));
        assertTrue(result.getWarnings().contains("supermall_stock_fact_missing"));
    }

    @Test
    void shouldRejectNullAnchorDate() {
        PlanInput input = new PlanInput(
                "PSKU-NO-ANCHOR",
                "SKU-NO-ANCHOR",
                "No Anchor Product",
                null,
                new StockSnapshot(new BigDecimal("10"), new BigDecimal("10"), BigDecimal.ZERO),
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(input, null)
        );
        assertTrue(exception.getMessage().contains("anchorDate"));
    }

    @Test
    void shouldExcludeInactiveEtaInboundFromKnownInboundAndProjection() {
        LocalDate anchorDate = LocalDate.of(2026, 7, 6);

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-CANCELLED-INBOUND",
                "SKU-CANCELLED-INBOUND",
                "Cancelled Inbound Product",
                anchorDate,
                new StockSnapshot(new BigDecimal("10"), new BigDecimal("10"), BigDecimal.ZERO),
                Collections.emptyMap(),
                Collections.singletonList(new InboundBatch(
                        4001L,
                        "BATCH-CANCELLED",
                        "SEA",
                        "CANCELLED",
                        anchorDate.plusDays(5),
                        new BigDecimal("30")
                )),
                Collections.emptyList()
        ), null);

        assertEquals(new BigDecimal("0"), result.getKnownInboundUnits());
        assertEquals(new BigDecimal("0"), result.getDailyProjection().get(4).getInboundUnits());
    }

    @Test
    void shouldDeriveMissingEtaInboundFromInboundBatches() {
        LocalDate anchorDate = LocalDate.of(2026, 7, 6);
        List<InboundBatch> inboundBatches = new ArrayList<>();
        inboundBatches.add(new InboundBatch(
                2001L,
                "BATCH-NO-ETA",
                "SEA",
                "ACTIVE",
                null,
                new BigDecimal("25")
        ));

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-MISSING-ETA",
                "SKU-MISSING-ETA",
                "Missing ETA Product",
                anchorDate,
                new StockSnapshot(new BigDecimal("50"), new BigDecimal("20"), new BigDecimal("30")),
                Collections.emptyMap(),
                inboundBatches,
                Collections.emptyList()
        ), null);

        assertEquals(new BigDecimal("0"), result.getKnownInboundUnits());
        assertEquals(new BigDecimal("25"), result.getMissingEtaInboundQty());
        assertEquals(1, result.getMissingEtaBatchCount());
        assertEquals(1, result.getMissingEtaBatches().size());
        assertEquals("BATCH-NO-ETA", result.getMissingEtaBatches().get(0).getBatchReferenceNo());
        assertTrue(result.getWarnings().contains("missing_eta_inbound_excluded"));
        assertTrue(result.getWarnings().contains("daily_forecast_missing"));
        assertTrue(result.getWarnings().contains("daily_forecast_gap"));
        assertNotNull(result.getDailyProjection().get(4));
        assertEquals(new BigDecimal("0"), result.getDailyProjection().get(4).getInboundUnits());
    }

    @Test
    void shouldNotConvertInactiveNullEtaInboundToMissingEtaWarningData() {
        LocalDate anchorDate = LocalDate.of(2026, 7, 6);

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-COMPLETED-MISSING-ETA",
                "SKU-COMPLETED-MISSING-ETA",
                "Completed Missing ETA Product",
                anchorDate,
                new StockSnapshot(new BigDecimal("50"), new BigDecimal("50"), BigDecimal.ZERO),
                Collections.emptyMap(),
                Collections.singletonList(new InboundBatch(
                        4002L,
                        "BATCH-COMPLETED",
                        "SEA",
                        "Completed",
                        null,
                        new BigDecimal("40")
                )),
                Collections.emptyList()
        ), null);

        assertEquals(new BigDecimal("0"), result.getMissingEtaInboundQty());
        assertEquals(0, result.getMissingEtaBatchCount());
        assertTrue(result.getMissingEtaBatches().isEmpty());
        assertFalse(result.getWarnings().contains("missing_eta_inbound_excluded"));
    }

    @Test
    void shouldAvoidDuplicateMissingEtaInboundWhenCallerAlsoPassesSummary() {
        LocalDate anchorDate = LocalDate.of(2026, 7, 6);

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-DUP-MISSING-ETA",
                "SKU-DUP-MISSING-ETA",
                "Duplicate Missing ETA Product",
                anchorDate,
                new StockSnapshot(new BigDecimal("50"), new BigDecimal("20"), new BigDecimal("30")),
                Collections.emptyMap(),
                Collections.singletonList(new InboundBatch(
                        2001L,
                        "BATCH-NO-ETA",
                        "SEA",
                        "ACTIVE",
                        null,
                        new BigDecimal("25")
                )),
                Collections.singletonList(new MissingEtaBatch(
                        2001L,
                        "BATCH-NO-ETA",
                        "SEA",
                        "ACTIVE",
                        new BigDecimal("25")
                ))
        ), null);

        assertEquals(new BigDecimal("25"), result.getMissingEtaInboundQty());
        assertEquals(1, result.getMissingEtaBatchCount());
        assertEquals(1, result.getMissingEtaBatches().size());
    }

    @Test
    void shouldWarnWhenRequiredForecastWindowHasGaps() {
        Map<Integer, BigDecimal> demand = new HashMap<>();
        demand.put(1, BigDecimal.ONE);
        demand.put(70, BigDecimal.TEN);

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-SPARSE",
                "SKU-SPARSE",
                "Sparse Forecast Product",
                LocalDate.of(2026, 7, 6),
                new StockSnapshot(new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO),
                demand,
                Collections.emptyList(),
                Collections.emptyList()
        ), null);

        assertTrue(result.getWarnings().contains("daily_forecast_gap"));
        assertFalse(result.getWarnings().contains("daily_forecast_missing"));
    }

    @Test
    void shouldNotWarnForecastGapWhenRequiredDaysHaveExplicitZeroDemand() {
        Map<Integer, BigDecimal> demand = new HashMap<>();
        demand.put(1, BigDecimal.ZERO);
        demand.put(2, BigDecimal.ZERO);

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-ZERO",
                "SKU-ZERO",
                "Zero Forecast Product",
                LocalDate.of(2026, 7, 6),
                new StockSnapshot(new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO),
                demand,
                Collections.emptyList(),
                Collections.emptyList()
        ), config(1, 1, 2, 1, 3, true, "ceil"));

        assertFalse(result.getWarnings().contains("daily_forecast_gap"));
        assertFalse(result.getWarnings().contains("daily_forecast_missing"));
    }

    @Test
    void shouldUseExactMaxOfForecastHorizonAndSeaWindowEndAsProjectionHorizon() {
        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-SHORT-HORIZON",
                "SKU-SHORT-HORIZON",
                "Short Horizon Product",
                LocalDate.of(2026, 7, 6),
                new StockSnapshot(new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO),
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyList()
        ), config(10, 10, 2, 3, 3, true, "ceil"));

        assertEquals(5, result.getDailyProjection().size());
        assertEquals(5, result.getDailyProjection().get(4).getDay());
    }

    @Test
    void shouldRejectNonsensicalConfigWindowsAndUnsupportedRoundingMode() {
        assertThrows(IllegalArgumentException.class, () -> config(0, 15, 70, 30, 100, true, "ceil"));
        assertThrows(IllegalArgumentException.class, () -> config(12, 0, 70, 30, 100, true, "ceil"));
        assertThrows(IllegalArgumentException.class, () -> config(12, 15, 0, 30, 100, true, "ceil"));
        assertThrows(IllegalArgumentException.class, () -> config(12, 15, 70, 0, 100, true, "ceil"));
        assertThrows(IllegalArgumentException.class, () -> config(12, 15, 70, 30, 0, true, "ceil"));
        assertThrows(IllegalArgumentException.class, () -> config(12, 15, 70, 30, 100, true, "floor"));
    }

    @Test
    void shouldRejectUnsupportedV1ConfigSurface() {
        assertThrows(IllegalArgumentException.class, () -> new ReplenishmentPlanConfig(
                "TEST_REPLENISHMENT_PLAN_BASIC_V1",
                12,
                15,
                70,
                30,
                100,
                Collections.singletonList("FBN"),
                true,
                true,
                "ceil"
        ));
        assertThrows(IllegalArgumentException.class, () -> new ReplenishmentPlanConfig(
                "TEST_REPLENISHMENT_PLAN_BASIC_V1",
                12,
                15,
                70,
                30,
                100,
                Arrays.asList("FBN", "SUPERMALL"),
                false,
                true,
                "ceil"
        ));
    }

    @Test
    void shouldCalculateAirWindowWhenEmergencyOnlyIsDisabled() {
        Map<Integer, BigDecimal> demand = new HashMap<>();
        for (int day = 12; day < 27; day++) {
            demand.put(day, BigDecimal.ONE);
        }

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-AIR-REGULAR",
                "SKU-AIR-REGULAR",
                "Air Regular Product",
                LocalDate.of(2026, 7, 6),
                new StockSnapshot(new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO),
                demand,
                Collections.emptyList(),
                Collections.emptyList()
        ), config(12, 15, 70, 30, 100, false, "ceil"));

        assertNull(result.getFirstStockoutDay());
        assertEquals(new BigDecimal("15"), result.getAirSuggestedUnits());
    }

    private static ReplenishmentPlanConfig config(
            int airLeadDays,
            int airCoverDays,
            int seaLeadDays,
            int seaCoverDays,
            int forecastHorizonDays,
            boolean airEmergencyOnly,
            String roundingMode
    ) {
        return new ReplenishmentPlanConfig(
                "TEST_REPLENISHMENT_PLAN_BASIC_V1",
                airLeadDays,
                airCoverDays,
                seaLeadDays,
                seaCoverDays,
                forecastHorizonDays,
                Arrays.asList("FBN", "SUPERMALL"),
                true,
                airEmergencyOnly,
                roundingMode
        );
    }
}
