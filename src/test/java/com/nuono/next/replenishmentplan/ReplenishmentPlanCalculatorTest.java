package com.nuono.next.replenishmentplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.InboundBatch;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.MissingEtaBatch;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanInput;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanItemView;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.StockSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
        assertTrue(result.getWarnings().isEmpty());
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
    void shouldExcludeMissingEtaInboundAndReturnWarningData() {
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
        inboundBatches.add(new InboundBatch(
                2002L,
                "BATCH-NEGATIVE",
                "AIR",
                "ACTIVE",
                anchorDate.plusDays(5),
                new BigDecimal("-5")
        ));
        List<MissingEtaBatch> missingEtaBatches = Collections.singletonList(new MissingEtaBatch(
                2001L,
                "BATCH-NO-ETA",
                "SEA",
                "ACTIVE",
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
                missingEtaBatches
        ), null);

        assertEquals(new BigDecimal("0"), result.getKnownInboundUnits());
        assertEquals(new BigDecimal("25"), result.getMissingEtaInboundQty());
        assertEquals(1, result.getMissingEtaBatchCount());
        assertEquals(1, result.getMissingEtaBatches().size());
        assertEquals("BATCH-NO-ETA", result.getMissingEtaBatches().get(0).getBatchReferenceNo());
        assertTrue(result.getWarnings().contains("missing_eta_inbound_excluded"));
        assertTrue(result.getWarnings().contains("daily_forecast_missing"));
        assertNotNull(result.getDailyProjection().get(4));
        assertEquals(new BigDecimal("0"), result.getDailyProjection().get(4).getInboundUnits());
    }
}
