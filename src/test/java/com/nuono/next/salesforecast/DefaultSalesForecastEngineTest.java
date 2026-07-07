package com.nuono.next.salesforecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultSalesForecastEngineTest {

    @Test
    void salesForecastV14ComputesDeterministicForecastWindowsFromHistoryWindows() {
        DefaultSalesForecastEngine engine = new DefaultSalesForecastEngine();
        SalesForecastFeatureSnapshot snapshot = snapshot();

        SalesForecastFormulaResult result = engine.forecast30(snapshot, DefaultSalesForecastEngine.CALCULATION_VERSION);

        assertEquals("SALES_FORECAST_V1_4", result.getCalculationVersion());
        assertEquals("SALES_FORECAST_V1_4", result.getConfigVersion());
        assertEquals(55, result.getForecastUnits30());
        assertEquals(110, result.getForecastUnits60());
        assertEquals(165, result.getForecastUnits90());
        assertEquals("1.8250", result.getBaseDailySales().toPlainString());
        assertEquals("0.5000", result.getRecentDailyTrendRate().toPlainString());
        assertEquals("1.0000", result.getTrendFactor().toPlainString());
        assertTrue(result.getShortReason().contains("近7日 21"));
        assertTrue(result.getShortReason().contains("30/60/90天"));
    }

    @Test
    void salesForecastV14KeepsTrendFactorNeutralForKnownProducts() {
        DefaultSalesForecastEngine engine = new DefaultSalesForecastEngine();
        SalesForecastFeatureSnapshot snapshot = snapshot();

        SalesForecastFormulaResult result = engine.forecast30(snapshot, DefaultSalesForecastEngine.CALCULATION_VERSION);

        assertEquals("SALES_FORECAST_V1_4", result.getCalculationVersion());
        assertEquals("0.5000", result.getRecentDailyTrendRate().toPlainString());
        assertEquals("1.0000", result.getTrendFactor().toPlainString());
        assertEquals(55, result.getForecastUnits30());
        assertEquals(110, result.getForecastUnits60());
        assertEquals(165, result.getForecastUnits90());
    }

    @Test
    void salesForecastV14RollsCalendarFactorsUpFromDailyForecastSeries() {
        DefaultSalesForecastEngine engine = new DefaultSalesForecastEngine();
        SalesForecastFeatureSnapshot snapshot = snapshot();

        SalesForecastFormulaResult result = engine.forecast(
                snapshot,
                "CALENDAR_FACTOR_CURRENT",
                new BigDecimal("1.10"),
                new BigDecimal("1.20"),
                new BigDecimal("0.90")
        );

        assertEquals("CALENDAR_FACTOR_CURRENT", result.getConfigVersion());
        assertEquals(61, result.getForecastUnits30());
        assertEquals(126, result.getForecastUnits60());
        assertEquals(176, result.getForecastUnits90());
        assertEquals("1.1000", result.getFutureFactor().toPlainString());
        assertEquals("1.1500", result.getFutureFactor60().toPlainString());
        assertEquals("1.0667", result.getFutureFactor90().toPlainString());
        assertTrue(result.getShortReason().contains("日历因子30/60/90天统计：1.1000 / 1.1500 / 1.0667"));
    }

    @Test
    void salesForecastV14BuildsDailyForecastHorizonAndRollsUpStatisticsFromDailySeries() {
        DefaultSalesForecastEngine engine = new DefaultSalesForecastEngine();
        SalesForecastFeatureSnapshot snapshot = snapshot();
        List<BigDecimal> futureDailyFactors = new ArrayList<>();
        for (int index = 0; index < DefaultSalesForecastEngine.FORECAST_HORIZON_DAYS; index++) {
            futureDailyFactors.add(BigDecimal.ONE);
        }
        futureDailyFactors.set(30, new BigDecimal("2.00"));

        SalesForecastFormulaResult result = engine.forecast(
                snapshot,
                "CALENDAR_FACTOR_CURRENT",
                snapshot.getLatestFactDate().plusDays(1),
                futureDailyFactors
        );

        assertEquals(DefaultSalesForecastEngine.FORECAST_HORIZON_DAYS, result.getDailyForecasts().size());
        assertEquals(LocalDate.of(2026, 5, 21), result.getDailyForecasts().get(0).getForecastDate());
        assertEquals(LocalDate.of(2026, 9, 17), result.getDailyForecasts().get(119).getForecastDate());
        assertEquals("1.8250", result.getDailyForecasts().get(0).getForecastUnits().setScale(4).toPlainString());
        assertEquals("3.6500", result.getDailyForecasts().get(30).getForecastUnits().setScale(4).toPlainString());
        assertEquals(ceilRollup(result, 30), result.getForecastUnits30());
        assertEquals(ceilRollup(result, 60), result.getForecastUnits60());
        assertEquals(ceilRollup(result, 90), result.getForecastUnits90());
        assertTrue(result.getShortReason().contains("按未来120天逐日预测"));
    }

    @Test
    void salesForecastV14UsesSixtyDayHeavyBlendWhenThirtyDayDailyIsMateriallyHigherThanSixtyDay() {
        DefaultSalesForecastEngine engine = new DefaultSalesForecastEngine();
        SalesForecastFeatureSnapshot snapshot = historySnapshot(
                "HIGH-RATIO",
                21,
                60,
                90,
                90,
                90
        );

        SalesForecastFormulaResult result = engine.forecast30(snapshot, DefaultSalesForecastEngine.CALCULATION_VERSION);

        assertEquals("SALES_FORECAST_V1_4", result.getCalculationVersion());
        assertEquals("1.8250", result.getBaseDailySales().toPlainString());
        assertEquals(55, result.getForecastUnits30());
        assertEquals(110, result.getForecastUnits60());
        assertEquals(165, result.getForecastUnits90());
    }

    @Test
    void salesForecastV14UsesSixtyDayHeavyBlendWhenThirtyDayDailyIsMateriallyLowerThanSixtyDayWithRecentSales() {
        DefaultSalesForecastEngine engine = new DefaultSalesForecastEngine();
        SalesForecastFeatureSnapshot snapshot = historySnapshot(
                "LOW-RATIO-WITH-RECENT-SALES",
                2,
                30,
                120,
                120,
                60
        );

        SalesForecastFormulaResult result = engine.forecast30(snapshot, DefaultSalesForecastEngine.CALCULATION_VERSION);

        assertEquals("SALES_FORECAST_V1_4", result.getCalculationVersion());
        assertEquals("1.4786", result.getBaseDailySales().toPlainString());
        assertEquals(45, result.getForecastUnits30());
    }

    @Test
    void salesForecastV14KeepsV13BlendWhenLowRecentDemandHasTooFewThirtyDayUnits() {
        DefaultSalesForecastEngine engine = new DefaultSalesForecastEngine();
        SalesForecastFeatureSnapshot snapshot = historySnapshot(
                "LOW-RATIO-THIN-RECENT-SALES",
                0,
                2,
                120,
                120,
                60
        );

        SalesForecastFormulaResult result = engine.forecast30(snapshot, DefaultSalesForecastEngine.CALCULATION_VERSION);

        assertEquals("SALES_FORECAST_V1_4", result.getCalculationVersion());
        assertEquals("0.0571", result.getBaseDailySales().toPlainString());
        assertEquals(2, result.getForecastUnits30());
    }

    @Test
    void salesForecastV14KeepsV13BlendBeforeFortyFiveObservedDays() {
        DefaultSalesForecastEngine engine = new DefaultSalesForecastEngine();
        SalesForecastFeatureSnapshot snapshot = historySnapshot(
                "HIGH-RATIO-BUT-THIN-OBSERVED-DAYS",
                14,
                60,
                60,
                60,
                44
        );

        SalesForecastFormulaResult result = engine.forecast30(snapshot, DefaultSalesForecastEngine.CALCULATION_VERSION);

        assertEquals("SALES_FORECAST_V1_4", result.getCalculationVersion());
        assertEquals("2.0000", result.getBaseDailySales().toPlainString());
        assertEquals(60, result.getForecastUnits30());
    }

    @Test
    void salesForecastV14CapsSevenDaySpikeBeforeWeightedBaseAndTrendRate() {
        DefaultSalesForecastEngine engine = new DefaultSalesForecastEngine();
        SalesForecastFeatureSnapshot snapshot = new SalesForecastFeatureSnapshot(
                10002L,
                "STR245027-SAU",
                "SA",
                "PAPERSAYSB244",
                "SKU-1",
                "Spike product",
                LocalDate.of(2026, 5, 30),
                70,
                30,
                60,
                90,
                90,
                12,
                null,
                List.of()
        );

        SalesForecastFormulaResult result = engine.forecast30(snapshot, DefaultSalesForecastEngine.CALCULATION_VERSION);

        assertEquals("1.0750", result.getBaseDailySales().toPlainString());
        assertEquals("0.7500", result.getRecentDailyTrendRate().toPlainString());
        assertEquals("1.0000", result.getTrendFactor().toPlainString());
        assertEquals(33, result.getForecastUnits30());
    }

    @Test
    void salesForecastV14DenoisesEstablishedThirtyDaySpikeOnlyWhenPriorSixtyHasDepth() {
        DefaultSalesForecastEngine engine = new DefaultSalesForecastEngine();
        SalesForecastFeatureSnapshot snapshot = spikeSnapshot(
                "PAPERSAYSB118",
                7,
                87,
                120,
                158
        );

        SalesForecastFormulaResult result = engine.forecast30(snapshot, DefaultSalesForecastEngine.CALCULATION_VERSION);

        assertEquals("SALES_FORECAST_V1_4", result.getCalculationVersion());
        assertEquals("1.9681", result.getBaseDailySales().toPlainString());
        assertEquals("1.0000", result.getTrendFactor().toPlainString());
        assertEquals(60, result.getForecastUnits30());
    }

    @Test
    void salesForecastV14KeepsThirtyDaySpikeDenoiseGuardWhenPriorSixtyIsThin() {
        DefaultSalesForecastEngine engine = new DefaultSalesForecastEngine();
        SalesForecastFeatureSnapshot snapshot = spikeSnapshot(
                "SGGRB296",
                1,
                43,
                57,
                67
        );

        SalesForecastFormulaResult result = engine.forecast30(snapshot, DefaultSalesForecastEngine.CALCULATION_VERSION);

        assertEquals("SALES_FORECAST_V1_4", result.getCalculationVersion());
        assertEquals("1.0385", result.getBaseDailySales().toPlainString());
        assertEquals("1.0000", result.getTrendFactor().toPlainString());
        assertEquals(32, result.getForecastUnits30());
    }

    private SalesForecastFeatureSnapshot snapshot() {
        return new SalesForecastFeatureSnapshot(
                10002L,
                "STR245027-SAU",
                "SA",
                "PAPERSAYSB359",
                "Z02AD5F198C0C2E813C30Z-1",
                "Paper notebook",
                LocalDate.of(2026, 5, 20),
                21,
                60,
                90,
                90,
                90,
                12,
                null,
                List.of()
        );
    }

    private int ceilRollup(SalesForecastFormulaResult result, int windowDays) {
        return result.getDailyForecasts().stream()
                .limit(windowDays)
                .map(SalesForecastDailyForecast::getForecastUnits)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.CEILING)
                .intValue();
    }

    private SalesForecastFeatureSnapshot historySnapshot(
            String partnerSku,
            int historyUnits7,
            int historyUnits30,
            int historyUnits60,
            int historyUnits90,
            int observedDays
    ) {
        return new SalesForecastFeatureSnapshot(
                10002L,
                "STR108065-NSA",
                "SA",
                partnerSku,
                partnerSku + "-SKU",
                "History candidate",
                LocalDate.of(2026, 2, 28),
                historyUnits7,
                historyUnits30,
                historyUnits60,
                historyUnits90,
                observedDays,
                12,
                null,
                List.of()
        );
    }

    private SalesForecastFeatureSnapshot spikeSnapshot(
            String partnerSku,
            int historyUnits7,
            int historyUnits30,
            int historyUnits60,
            int historyUnits90
    ) {
        return new SalesForecastFeatureSnapshot(
                10002L,
                "STR108065-NSA",
                "SA",
                partnerSku,
                partnerSku + "-SKU",
                "Spike candidate",
                LocalDate.of(2026, 2, 28),
                historyUnits7,
                historyUnits30,
                historyUnits60,
                historyUnits90,
                90,
                12,
                null,
                List.of()
        );
    }
}
