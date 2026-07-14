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
        demand.put(50, new BigDecimal("110"));
        demand.put(70, new BigDecimal("200"));

        PlanInput input = new PlanInput(
                "PSKU-001",
                "SKU-001",
                "Example Product",
                anchorDate,
                new StockSnapshot(new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("3")),
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
        assertEquals(new BigDecimal("10"), result.getFbnStockUnits());
        assertEquals(new BigDecimal("3"), result.getSupermallStockUnits());
        assertEquals(new BigDecimal("100"), result.getKnownInboundUnits());
        assertEquals(1, result.getInboundBatches().size());
        assertEquals("BATCH-1001", result.getInboundBatches().get(0).getBatchReferenceNo());
        assertEquals(anchorDate.plusDays(20), result.getInboundBatches().get(0).getEtaDate());
        assertEquals(new BigDecimal("100"), result.getInboundBatches().get(0).getRemainingQuantity());
        assertEquals(4, result.getFirstStockoutDay());
        assertEquals("4-12 天", result.getStockoutWindowLabel());
        assertEquals(12, result.getAirWindowStartDay());
        assertEquals(27, result.getAirWindowEndDay());
        assertEquals(new BigDecimal("40"), result.getAirWindowForecastUnits());
        assertEquals(new BigDecimal("40"), result.getAirSuggestedUnits());
        assertEquals(70, result.getSeaWindowStartDay());
        assertEquals(100, result.getSeaWindowEndDay());
        assertEquals(new BigDecimal("200"), result.getSeaWindowForecastUnits());
        assertEquals(new BigDecimal("160"), result.getSeaCalculatedUnits());
        assertEquals(new BigDecimal("160"), result.getSeaSuggestedUnits());
        assertEquals(0, result.getForecastUnits100().compareTo(new BigDecimal("360")));
        assertEquals(100, result.getDailyProjection().size());
        assertEquals(new BigDecimal("100"), result.getDailyProjection().get(23).getInboundUnits());
        assertTrue(result.getWarnings().contains("daily_forecast_gap"));
        assertFalse(result.getWarnings().contains("daily_forecast_missing"));
        assertEquals(70, result.getConfigSnapshot().getSeaLeadDays());
        assertTrue(result.getExplanation().contains("70-100"));
        assertTrue(result.getExplanation().contains("air emergency triggered"));
    }

    @Test
    void shouldNotSuggestSeaWhenExistingStockAndInboundCoverThroughSeaWindow() {
        LocalDate anchorDate = LocalDate.of(2026, 7, 8);
        Map<Integer, BigDecimal> demand = new HashMap<>();
        for (int day = 1; day <= 100; day++) {
            demand.put(day, BigDecimal.ONE);
        }

        PlanItemView result = calculator.calculate(new PlanInput(
                "PAPERSAYSB110",
                "Z6E3D255016D650A347D7Z-1",
                "50个18x25cm透明礼品袋",
                anchorDate,
                new StockSnapshot(new BigDecimal("23"), new BigDecimal("23"), new BigDecimal("9")),
                demand,
                Collections.singletonList(new InboundBatch(
                        4001L,
                        "XGGEKSA04072",
                        "SEA",
                        "ACTIVE",
                        anchorDate.plusDays(13),
                        new BigDecimal("200")
                )),
                Collections.emptyList()
        ), null);

        assertEquals(new BigDecimal("200"), result.getKnownInboundUnits());
        assertEquals(0, result.getForecastUnits100().compareTo(new BigDecimal("100")));
        assertNull(result.getFirstStockoutDay());
        assertEquals(new BigDecimal("30"), result.getSeaWindowForecastUnits());
        assertEquals(new BigDecimal("0"), result.getSeaSuggestedUnits());
        assertEquals(new BigDecimal("0"), result.getAirSuggestedUnits());
    }

    @Test
    void shouldUseOnlyFbnStockForCurrentInventoryAndProjection() {
        LocalDate anchorDate = LocalDate.of(2026, 7, 6);
        Map<Integer, BigDecimal> demand = new HashMap<>();
        demand.put(1, new BigDecimal("20"));

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-FBN-ONLY",
                "SKU-FBN-ONLY",
                "FBN Only Product",
                anchorDate,
                new StockSnapshot(new BigDecimal("110"), new BigDecimal("10"), new BigDecimal("100")),
                demand,
                Collections.emptyList(),
                Collections.emptyList()
        ), null);

        assertEquals(new BigDecimal("10"), result.getCurrentStockUnits());
        assertEquals(new BigDecimal("10"), result.getFbnStockUnits());
        assertEquals(new BigDecimal("100"), result.getSupermallStockUnits());
        assertEquals(1, result.getFirstStockoutDay());
        assertEquals(new BigDecimal("-10"), result.getDailyProjection().get(0).getProjectedStock());
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
        assertEquals(new BigDecimal("0"), result.getAirWindowForecastUnits());
        assertEquals(new BigDecimal("0"), result.getAirSuggestedUnits());
        assertEquals(new BigDecimal("30"), result.getSeaWindowForecastUnits());
        assertEquals(new BigDecimal("0"), result.getSeaSuggestedUnits());
        assertTrue(result.getExplanation().contains("air emergency not triggered"));
    }

    @Test
    void shouldKeepAirSuggestionZeroWhenStockoutOccursAfterAirCoverWindow() {
        Map<Integer, BigDecimal> demand = new HashMap<>();
        for (int day = 12; day < 27; day++) {
            demand.put(day, BigDecimal.ONE);
        }
        demand.put(50, new BigDecimal("20"));
        for (int day = 70; day < 100; day++) {
            demand.put(day, BigDecimal.ONE);
        }

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-LATE-STOCKOUT",
                "SKU-LATE-STOCKOUT",
                "Late Stockout Product",
                LocalDate.of(2026, 7, 6),
                new StockSnapshot(new BigDecimal("30"), new BigDecimal("30"), BigDecimal.ZERO),
                demand,
                Collections.emptyList(),
                Collections.emptyList()
        ), ReplenishmentPlanConfig.defaultBasicV1());

        assertEquals(50, result.getFirstStockoutDay());
        assertEquals(new BigDecimal("15"), result.getAirWindowForecastUnits());
        assertEquals(new BigDecimal("0"), result.getAirSuggestedUnits());
        assertEquals(new BigDecimal("30"), result.getSeaWindowForecastUnits());
        assertEquals(new BigDecimal("30"), result.getSeaSuggestedUnits());
        assertTrue(result.getExplanation().contains("air emergency not triggered"));
    }

    @Test
    void shouldDelaySameDayEtaInboundByReceivingBuffer() {
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
        assertEquals(new BigDecimal("-1"), result.getDailyProjection().get(0).getProjectedStock());
        assertEquals(new BigDecimal("2"), result.getDailyProjection().get(3).getInboundUnits());
        assertEquals(new BigDecimal("1"), result.getDailyProjection().get(3).getProjectedStock());
        assertEquals(1, result.getFirstStockoutDay());
    }

    @Test
    void shouldExcludePastEtaInboundByPlanDateAndKeepRecentPastEtaForReview() {
        LocalDate anchorDate = LocalDate.of(2026, 7, 5);
        LocalDate planDate = LocalDate.of(2026, 7, 8);

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-PAST-ETA",
                "SKU-PAST-ETA",
                "Past ETA Product",
                anchorDate,
                planDate,
                new StockSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                Collections.emptyMap(),
                Arrays.asList(
                        new InboundBatch(
                                3101L,
                                "BATCH-OLD-ETA",
                                "SEA",
                                "ACTIVE",
                                LocalDate.of(2026, 7, 3),
                                new BigDecimal("20")
                        ),
                        new InboundBatch(
                                3102L,
                                "BATCH-RECENT-ETA",
                                "SEA",
                                "ACTIVE",
                                LocalDate.of(2026, 7, 6),
                                new BigDecimal("10")
                        ),
                        new InboundBatch(
                                3103L,
                                "BATCH-FUTURE-ETA",
                                "SEA",
                                "ACTIVE",
                                LocalDate.of(2026, 7, 10),
                                new BigDecimal("30")
                        )
                ),
                Collections.emptyList()
        ), null);

        assertEquals(new BigDecimal("40"), result.getKnownInboundUnits());
        assertEquals(LocalDate.of(2026, 7, 6), result.getNearestInboundEtaDate());
        assertEquals(2, result.getInboundBatches().size());
        InboundBatch recentEta = inboundBatch(result, "BATCH-RECENT-ETA");
        assertNotNull(recentEta);
        assertTrue(recentEta.isCoverageIncluded());
        assertTrue(recentEta.isEtaReviewRequired());
        InboundBatch futureEta = inboundBatch(result, "BATCH-FUTURE-ETA");
        assertNotNull(futureEta);
        assertTrue(futureEta.isCoverageIncluded());
        assertFalse(futureEta.isEtaReviewRequired());
        assertTrue(result.getWarnings().contains("past_eta_inbound_excluded"));
        assertTrue(result.getWarnings().contains("recent_eta_review_required"));
        assertEquals(LocalDate.of(2026, 7, 9), result.getDailyProjection().get(0).getDate());
        assertEquals(LocalDate.of(2026, 7, 10), result.getDailyProjection().get(1).getDate());
        assertEquals(new BigDecimal("0"), result.getDailyProjection().get(0).getInboundUnits());
        assertEquals(new BigDecimal("0"), result.getDailyProjection().get(0).getProjectedStock());
        assertEquals(new BigDecimal("10"), result.getDailyProjection().get(1).getInboundUnits());
        assertEquals(new BigDecimal("30"), result.getDailyProjection().get(5).getInboundUnits());
    }

    @Test
    void shouldUseRecentPastEtaInboundAsStartingStockForSeaWindowShortage() {
        LocalDate anchorDate = LocalDate.of(2026, 7, 5);
        LocalDate planDate = LocalDate.of(2026, 7, 11);
        Map<Integer, BigDecimal> demand = new HashMap<>();
        for (int day = 1; day < 70; day++) {
            demand.put(day, new BigDecimal("0.59913043"));
        }
        for (int day = 70; day < 100; day++) {
            demand.put(day, new BigDecimal("0.635"));
        }

        PlanItemView result = calculator.calculate(new PlanInput(
                "paperSays011",
                "Z87FA5FC5791083958D8AZ-1",
                "10张NTAG215 NFC白卡",
                anchorDate,
                planDate,
                new StockSnapshot(new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("7")),
                demand,
                Arrays.asList(
                        new InboundBatch(
                                3201L,
                                "F2604304851631",
                                "SEA",
                                "in_transit",
                                LocalDate.of(2026, 7, 10),
                                new BigDecimal("80")
                        ),
                        new InboundBatch(
                                3202L,
                                "F2607034908619",
                                "SEA",
                                "pending_shipment",
                                LocalDate.of(2026, 8, 31),
                                new BigDecimal("30")
                        )
                ),
                Collections.emptyList()
        ), ReplenishmentPlanConfig.defaultBasicV1());

        assertEquals(new BigDecimal("110"), result.getKnownInboundUnits());
        assertEquals(0, result.getSeaWindowForecastUnits().compareTo(new BigDecimal("19.050")));
        assertEquals(new BigDecimal("0"), result.getSeaCalculatedUnits());
        assertEquals(new BigDecimal("0"), result.getSeaSuggestedUnits());
        assertTrue(result.getWarnings().contains("recent_eta_review_required"));
    }

    @Test
    void shouldRoundSeaSuggestionByRecentMonthlySalesTierWhileKeepingRawCalculation() {
        assertSeaSuggestion(25, 18, "18", "20");
        assertSeaSuggestion(25, 2, "2", "20");
        assertSeaSuggestion(25, 22, "22", "30");
        assertSeaSuggestion(7, 1, "1", "10");
        assertSeaSuggestion(7, 7, "7", "10");
        assertSeaSuggestion(7, 11, "11", "20");
        assertSeaSuggestion(2, 4, "4", "0");
        assertSeaSuggestion(2, 8, "8", "10");
        assertSeaSuggestion(2, 12, "12", "20");
        assertSeaSuggestion(35, 28, "28", "30");
        assertSeaSuggestion(35, 2, "2", "30");
        assertSeaSuggestion(35, 32, "32", "40");
        assertSeaSuggestion(87, 1, "1", "0");
        assertSeaSuggestion(87, 5, "5", "10");
        assertSeaSuggestion(87, 8, "8", "20");
        assertSeaSuggestion(87, 22, "22", "40");
        assertSeaSuggestion(87, 32, "32", "50");
        assertSeaSuggestion(87, 55, "55", "80");
        assertSeaSuggestion(118, 32, "32", "50");
        assertSeaSuggestion(118, 112, "112", "120");
        assertSeaSuggestion(118, 126, "126", "130");
    }

    @Test
    void shouldRoundAirSuggestionByFiveUnitStepWhileKeepingRawCalculation() {
        assertAirSuggestion(1, "1", "5");
        assertAirSuggestion(4, "4", "5");
        assertAirSuggestion(5, "5", "5");
        assertAirSuggestion(12, "12", "15");
        assertAirSuggestion(16, "16", "20");
    }

    @Test
    void shouldDeductSuggestedAirUnitsFromSeaPlan() {
        Map<Integer, BigDecimal> demand = new HashMap<>();
        demand.put(12, new BigDecimal("12"));
        demand.put(70, new BigDecimal("22"));

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-DEDUCT-AIR",
                "SKU-DEDUCT-AIR",
                "Deduct Air Product",
                LocalDate.of(2026, 7, 6),
                30,
                0,
                2,
                2,
                2,
                2,
                2,
                2,
                "low",
                null,
                LocalDate.of(2026, 7, 6),
                new StockSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                demand,
                Collections.emptyList(),
                Collections.emptyList()
        ), ReplenishmentPlanConfig.defaultBasicV1());

        assertEquals(new BigDecimal("12"), result.getAirCalculatedUnits());
        assertEquals(new BigDecimal("15"), result.getAirSuggestedUnits());
        assertEquals(new BigDecimal("22"), result.getSeaWindowForecastUnits());
        assertEquals(new BigDecimal("7"), result.getSeaCalculatedUnits());
        assertEquals(new BigDecimal("10"), result.getSeaSuggestedUnits());
    }

    @Test
    void shouldDelayEtaCoverageByFourDaysForWarehouseReceivingBuffer() {
        LocalDate planDate = LocalDate.of(2026, 7, 11);

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-ETA-BUFFER",
                "SKU-ETA-BUFFER",
                "ETA Buffer Product",
                LocalDate.of(2026, 7, 5),
                planDate,
                new StockSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                Collections.emptyMap(),
                Collections.singletonList(new InboundBatch(
                        3301L,
                        "BATCH-ETA-0710",
                        "SEA",
                        "in_transit",
                        LocalDate.of(2026, 7, 10),
                        new BigDecimal("80")
                )),
                Collections.emptyList()
        ), ReplenishmentPlanConfig.defaultBasicV1());

        assertEquals(new BigDecimal("80"), result.getKnownInboundUnits());
        assertEquals(new BigDecimal("0"), result.getDailyProjection().get(0).getInboundUnits());
        assertEquals(new BigDecimal("0"), result.getDailyProjection().get(1).getInboundUnits());
        assertEquals(LocalDate.of(2026, 7, 14), result.getDailyProjection().get(2).getDate());
        assertEquals(new BigDecimal("80"), result.getDailyProjection().get(2).getInboundUnits());
        assertEquals(new BigDecimal("80"), result.getDailyProjection().get(2).getProjectedStock());
        assertTrue(result.getWarnings().contains("recent_eta_review_required"));
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
        assertFalse(result.getWarnings().contains("supermall_stock_fact_missing"));
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
                Arrays.asList("FBN", "SUPERMALL"),
                false,
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
                true,
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

    private void assertAirSuggestion(int rawAirForecast, String expectedCalculated, String expectedSuggested) {
        Map<Integer, BigDecimal> demand = new HashMap<>();
        demand.put(12, new BigDecimal(rawAirForecast));

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-AIR-TIER-" + rawAirForecast,
                "SKU-AIR-TIER-" + rawAirForecast,
                "Tiered Air Suggestion Product",
                LocalDate.of(2026, 7, 6),
                new StockSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                demand,
                Collections.emptyList(),
                Collections.emptyList()
        ), config(12, 15, 70, 30, 100, false, "ceil"));

        assertEquals(new BigDecimal(expectedCalculated), result.getAirCalculatedUnits());
        assertEquals(new BigDecimal(expectedSuggested), result.getAirSuggestedUnits());
    }

    private void assertSeaSuggestion(int historyUnits30, int rawSeaShortage, String expectedCalculated, String expectedSuggested) {
        Map<Integer, BigDecimal> demand = new HashMap<>();
        demand.put(70, new BigDecimal(rawSeaShortage));

        PlanItemView result = calculator.calculate(new PlanInput(
                "PSKU-TIER-" + historyUnits30 + "-" + rawSeaShortage,
                "SKU-TIER-" + historyUnits30 + "-" + rawSeaShortage,
                "Tiered Sea Suggestion Product",
                LocalDate.of(2026, 7, 6),
                30,
                0,
                historyUnits30,
                historyUnits30,
                historyUnits30,
                historyUnits30,
                historyUnits30,
                historyUnits30,
                "medium",
                null,
                LocalDate.of(2026, 7, 6),
                new StockSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                demand,
                Collections.emptyList(),
                Collections.emptyList()
        ), ReplenishmentPlanConfig.defaultBasicV1());

        assertEquals(new BigDecimal(expectedCalculated), result.getSeaCalculatedUnits());
        assertEquals(new BigDecimal(expectedSuggested), result.getSeaSuggestedUnits());
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
                Collections.singletonList("FBN"),
                true,
                airEmergencyOnly,
                roundingMode
        );
    }

    private static InboundBatch inboundBatch(PlanItemView result, String batchReferenceNo) {
        return result.getInboundBatches().stream()
                .filter(batch -> batchReferenceNo.equals(batch.getBatchReferenceNo()))
                .findFirst()
                .orElse(null);
    }
}
