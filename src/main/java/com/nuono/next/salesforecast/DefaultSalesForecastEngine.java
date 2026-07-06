package com.nuono.next.salesforecast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class DefaultSalesForecastEngine {

    public static final String CALCULATION_VERSION = "SALES_FORECAST_V1_4";
    public static final String DEFAULT_CONFIG_VERSION = "DEFAULT_CALENDAR_CONFIG";
    private static final BigDecimal HISTORY_WEIGHT_7 = new BigDecimal("0.10");
    private static final BigDecimal HISTORY_WEIGHT_30 = new BigDecimal("0.60");
    private static final BigDecimal HISTORY_WEIGHT_90 = new BigDecimal("0.30");
    private static final BigDecimal SIXTY_DAY_HEAVY_WEIGHT_7 = new BigDecimal("0.10");
    private static final BigDecimal SIXTY_DAY_HEAVY_WEIGHT_30 = new BigDecimal("0.35");
    private static final BigDecimal SIXTY_DAY_HEAVY_WEIGHT_60 = new BigDecimal("0.55");
    private static final int SIXTY_DAY_HEAVY_MIN_OBSERVED_DAYS = 45;
    private static final BigDecimal SIXTY_DAY_HEAVY_HIGH_RATIO = new BigDecimal("1.10");
    private static final BigDecimal SIXTY_DAY_HEAVY_LOW_RATIO = new BigDecimal("0.75");
    private static final int SIXTY_DAY_HEAVY_LOW_RATIO_MIN_HISTORY_UNITS_30 = 3;
    private static final BigDecimal SEVEN_DAY_DAILY_CAP_MULTIPLIER = new BigDecimal("1.75");
    private static final BigDecimal THIRTY_DAY_SPIKE_RATIO = new BigDecimal("1.60");
    private static final BigDecimal THIRTY_DAY_SPIKE_RECENT_RATIO = new BigDecimal("0.55");
    private static final BigDecimal THIRTY_DAY_SPIKE_CAP_MULTIPLIER = new BigDecimal("1.25");
    private static final int THIRTY_DAY_SPIKE_PRIOR60_MIN_UNITS = 50;

    public SalesForecastFormulaResult forecast30(SalesForecastFeatureSnapshot snapshot, String configVersion) {
        return forecast30(snapshot, configVersion, BigDecimal.ONE);
    }

    public SalesForecastFormulaResult forecast30(
            SalesForecastFeatureSnapshot snapshot,
            String configVersion,
            BigDecimal futureFactor
    ) {
        return forecast(snapshot, configVersion, futureFactor, futureFactor, futureFactor);
    }

    public SalesForecastFormulaResult forecast(
            SalesForecastFeatureSnapshot snapshot,
            String configVersion,
            BigDecimal futureFactor30,
            BigDecimal futureFactor60,
            BigDecimal futureFactor90
    ) {
        return forecast(snapshot, configVersion, futureFactor30, futureFactor60, futureFactor90, BigDecimal.ZERO);
    }

    public SalesForecastFormulaResult forecast(
            SalesForecastFeatureSnapshot snapshot,
            String configVersion,
            BigDecimal futureFactor30,
            BigDecimal futureFactor60,
            BigDecimal futureFactor90,
            BigDecimal lowSampleDailyFloor
    ) {
        String resolvedConfigVersion = hasText(configVersion) ? configVersion : DEFAULT_CONFIG_VERSION;
        BigDecimal resolvedFutureFactor30 = safeFactor(futureFactor30);
        BigDecimal resolvedFutureFactor60 = safeFactor(futureFactor60);
        BigDecimal resolvedFutureFactor90 = safeFactor(futureFactor90);
        BigDecimal rawDaily7 = dailyAverage(snapshot.getAdjustedHistoryUnits7(), 7);
        BigDecimal rawDaily30 = dailyAverage(snapshot.getAdjustedHistoryUnits30(), 30);
        BigDecimal daily60 = dailyAverage(snapshot.getAdjustedHistoryUnits60(), 60);
        BigDecimal daily90 = dailyAverage(snapshot.getAdjustedHistoryUnits90(), 90);
        BigDecimal daily7 = cappedSevenDayDailyAverage(rawDaily7, rawDaily30);
        BigDecimal daily30 = denoisedThirtyDayDailyAverage(snapshot, daily7, rawDaily30, daily90);
        BigDecimal baseDailySales = weightedMean(snapshot, daily7, daily30, rawDaily30, daily60, daily90);
        BigDecimal appliedLowSampleDailyFloor = appliedLowSampleDailyFloor(baseDailySales, lowSampleDailyFloor);
        if (appliedLowSampleDailyFloor.compareTo(BigDecimal.ZERO) > 0) {
            baseDailySales = appliedLowSampleDailyFloor;
        }
        BigDecimal recentDailyTrendRate = recentDailyTrendRate(daily7, rawDaily30);
        BigDecimal trendFactor = neutralTrendFactor();
        int forecastUnits30 = forecastUnits(baseDailySales, trendFactor, resolvedFutureFactor30, 30);
        int forecastUnits60 = forecastUnits(baseDailySales, trendFactor, resolvedFutureFactor60, 60);
        int forecastUnits90 = forecastUnits(baseDailySales, trendFactor, resolvedFutureFactor90, 90);

        return new SalesForecastFormulaResult(
                CALCULATION_VERSION,
                resolvedConfigVersion,
                forecastUnits30,
                forecastUnits60,
                forecastUnits90,
                baseDailySales.setScale(4, RoundingMode.HALF_UP),
                recentDailyTrendRate.setScale(4, RoundingMode.HALF_UP),
                trendFactor.setScale(4, RoundingMode.HALF_UP),
                resolvedFutureFactor30.setScale(4, RoundingMode.HALF_UP),
                resolvedFutureFactor60.setScale(4, RoundingMode.HALF_UP),
                resolvedFutureFactor90.setScale(4, RoundingMode.HALF_UP),
                appliedLowSampleDailyFloor.setScale(4, RoundingMode.HALF_UP),
                shortReason(
                        snapshot,
                        forecastUnits30,
                        forecastUnits60,
                        forecastUnits90,
                        resolvedFutureFactor30,
                        resolvedFutureFactor60,
                        resolvedFutureFactor90,
                        appliedLowSampleDailyFloor
                )
        );
    }

    private BigDecimal safeFactor(BigDecimal factor) {
        return factor == null ? BigDecimal.ONE : factor;
    }

    private BigDecimal weightedMean(
            SalesForecastFeatureSnapshot snapshot,
            BigDecimal daily7,
            BigDecimal daily30,
            BigDecimal rawDaily30,
            BigDecimal daily60,
            BigDecimal daily90
    ) {
        if (shouldUseSixtyDayHeavyBlend(snapshot, rawDaily30, daily60)) {
            return sixtyDayHeavyWeightedMean(daily7, daily30, daily60);
        }
        return v13WeightedMean(snapshot, daily7, daily30, daily90);
    }

    private BigDecimal sixtyDayHeavyWeightedMean(
            BigDecimal daily7,
            BigDecimal daily30,
            BigDecimal daily60
    ) {
        BigDecimal weightedTotal = daily7.multiply(SIXTY_DAY_HEAVY_WEIGHT_7)
                .add(daily30.multiply(SIXTY_DAY_HEAVY_WEIGHT_30))
                .add(daily60.multiply(SIXTY_DAY_HEAVY_WEIGHT_60));
        BigDecimal weightTotal = SIXTY_DAY_HEAVY_WEIGHT_7
                .add(SIXTY_DAY_HEAVY_WEIGHT_30)
                .add(SIXTY_DAY_HEAVY_WEIGHT_60);
        return weightedTotal.divide(weightTotal, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal v13WeightedMean(
            SalesForecastFeatureSnapshot snapshot,
            BigDecimal daily7,
            BigDecimal daily30,
            BigDecimal daily90
    ) {
        BigDecimal weightedTotal = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;
        if (snapshot.getObservedDays() > 0) {
            weightedTotal = weightedTotal.add(daily7.multiply(HISTORY_WEIGHT_7));
            weightTotal = weightTotal.add(HISTORY_WEIGHT_7);
        }
        if (snapshot.getObservedDays() >= 30) {
            weightedTotal = weightedTotal.add(daily30.multiply(HISTORY_WEIGHT_30));
            weightTotal = weightTotal.add(HISTORY_WEIGHT_30);
        }
        if (snapshot.getObservedDays() >= 90) {
            weightedTotal = weightedTotal.add(daily90.multiply(HISTORY_WEIGHT_90));
            weightTotal = weightTotal.add(HISTORY_WEIGHT_90);
        }
        if (weightTotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return weightedTotal.divide(weightTotal, 8, RoundingMode.HALF_UP);
    }

    private boolean shouldUseSixtyDayHeavyBlend(
            SalesForecastFeatureSnapshot snapshot,
            BigDecimal rawDaily30,
            BigDecimal daily60
    ) {
        if (snapshot.getObservedDays() < SIXTY_DAY_HEAVY_MIN_OBSERVED_DAYS || daily60.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal daily30To60 = rawDaily30.divide(daily60, 8, RoundingMode.HALF_UP);
        if (daily30To60.compareTo(SIXTY_DAY_HEAVY_HIGH_RATIO) > 0) {
            return true;
        }
        return daily30To60.compareTo(SIXTY_DAY_HEAVY_LOW_RATIO) < 0
                && snapshot.getHistoryUnits30() >= SIXTY_DAY_HEAVY_LOW_RATIO_MIN_HISTORY_UNITS_30;
    }

    private BigDecimal cappedSevenDayDailyAverage(BigDecimal daily7, BigDecimal daily30) {
        if (daily30.compareTo(BigDecimal.ZERO) <= 0) {
            return daily7;
        }
        BigDecimal upperBound = daily30.multiply(SEVEN_DAY_DAILY_CAP_MULTIPLIER);
        if (daily7.compareTo(upperBound) > 0) {
            return upperBound;
        }
        return daily7;
    }

    private BigDecimal denoisedThirtyDayDailyAverage(
            SalesForecastFeatureSnapshot snapshot,
            BigDecimal daily7,
            BigDecimal daily30,
            BigDecimal daily90
    ) {
        if (daily30.compareTo(BigDecimal.ZERO) <= 0 || daily90.compareTo(BigDecimal.ZERO) <= 0) {
            return daily30;
        }
        int prior60Units = Math.max(0, snapshot.getHistoryUnits90() - snapshot.getHistoryUnits30());
        if (prior60Units < THIRTY_DAY_SPIKE_PRIOR60_MIN_UNITS) {
            return daily30;
        }
        boolean thirtyDaySpike = daily30.compareTo(daily90.multiply(THIRTY_DAY_SPIKE_RATIO)) > 0;
        boolean recentRetreat = daily7.compareTo(daily30.multiply(THIRTY_DAY_SPIKE_RECENT_RATIO)) < 0;
        if (!thirtyDaySpike || !recentRetreat) {
            return daily30;
        }
        BigDecimal cap = daily90.multiply(THIRTY_DAY_SPIKE_CAP_MULTIPLIER);
        if (daily30.compareTo(cap) > 0) {
            return cap;
        }
        return daily30;
    }

    private BigDecimal appliedLowSampleDailyFloor(BigDecimal baseDailySales, BigDecimal lowSampleDailyFloor) {
        BigDecimal safeFloor = lowSampleDailyFloor == null ? BigDecimal.ZERO : lowSampleDailyFloor;
        if (safeFloor.compareTo(BigDecimal.ZERO) <= 0 || safeFloor.compareTo(baseDailySales) <= 0) {
            return BigDecimal.ZERO;
        }
        return safeFloor;
    }

    private BigDecimal recentDailyTrendRate(BigDecimal daily7, BigDecimal daily30) {
        if (daily30.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return daily7.subtract(daily30).divide(daily30, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal neutralTrendFactor() {
        return BigDecimal.ONE;
    }

    private BigDecimal dailyAverage(int units, int days) {
        return BigDecimal.valueOf(units).divide(BigDecimal.valueOf(days), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal dailyAverage(BigDecimal units, int days) {
        BigDecimal safeUnits = units == null ? BigDecimal.ZERO : units;
        return safeUnits.divide(BigDecimal.valueOf(days), 8, RoundingMode.HALF_UP);
    }

    private int forecastUnits(
            BigDecimal baseDailySales,
            BigDecimal trendFactor,
            BigDecimal futureFactor,
            int windowDays
    ) {
        BigDecimal forecast = baseDailySales
                .multiply(trendFactor)
                .multiply(futureFactor)
                .multiply(BigDecimal.valueOf(windowDays));
        int forecastUnits = forecast.setScale(0, RoundingMode.CEILING).intValue();
        if (forecastUnits < 0) {
            return 0;
        }
        return forecastUnits;
    }

    private String shortReason(
            SalesForecastFeatureSnapshot snapshot,
            int forecastUnits30,
            int forecastUnits60,
            int forecastUnits90,
            BigDecimal futureFactor30,
            BigDecimal futureFactor60,
            BigDecimal futureFactor90,
            BigDecimal lowSampleDailyFloor
    ) {
        if (snapshot.getObservedDays() <= 0) {
            if (lowSampleDailyFloor != null && lowSampleDailyFloor.compareTo(BigDecimal.ZERO) > 0) {
                return "截至 " + snapshot.getLatestFactDate()
                        + " 没有自身销量训练样本，使用低样本同类目兜底日销 "
                        + lowSampleDailyFloor.setScale(4, RoundingMode.HALF_UP).toPlainString()
                        + "；日历因子30/60/90天："
                        + futureFactor30.setScale(4, RoundingMode.HALF_UP).toPlainString()
                        + " / "
                        + futureFactor60.setScale(4, RoundingMode.HALF_UP).toPlainString()
                        + " / "
                        + futureFactor90.setScale(4, RoundingMode.HALF_UP).toPlainString()
                        + "；预测未来30/60/90天约 "
                        + forecastUnits30 + " / " + forecastUnits60 + " / " + forecastUnits90 + " 件。";
            }
            return "截至 " + snapshot.getLatestFactDate()
                    + " 没有可用销量训练样本，预测按 0 处理；需补齐历史销量或人工复核。";
        }
        return "近7日 " + snapshot.getHistoryUnits7()
                + " 件，近30日 " + snapshot.getHistoryUnits30()
                + " 件，近90日 " + snapshot.getHistoryUnits90()
                + "；"
                + historyCalendarAdjustmentSummary(snapshot)
                + lowSampleDailyFloorSummary(lowSampleDailyFloor)
                + "日历因子30/60/90天："
                + futureFactor30.setScale(4, RoundingMode.HALF_UP).toPlainString()
                + " / "
                + futureFactor60.setScale(4, RoundingMode.HALF_UP).toPlainString()
                + " / "
                + futureFactor90.setScale(4, RoundingMode.HALF_UP).toPlainString()
                + "；预测未来30/60/90天约 "
                + forecastUnits30 + " / " + forecastUnits60 + " / " + forecastUnits90 + " 件。";
    }

    private String lowSampleDailyFloorSummary(BigDecimal lowSampleDailyFloor) {
        if (lowSampleDailyFloor == null || lowSampleDailyFloor.compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        return "低样本同类目兜底日销 "
                + lowSampleDailyFloor.setScale(4, RoundingMode.HALF_UP).toPlainString()
                + "；";
    }

    private String historyCalendarAdjustmentSummary(SalesForecastFeatureSnapshot snapshot) {
        if (!hasHistoryAdjustment(snapshot)) {
            return "";
        }
        return "训练销量已按历史日历因子还原为7/30/60/90天："
                + snapshot.getAdjustedHistoryUnits7().setScale(4, RoundingMode.HALF_UP).toPlainString()
                + " / "
                + snapshot.getAdjustedHistoryUnits30().setScale(4, RoundingMode.HALF_UP).toPlainString()
                + " / "
                + snapshot.getAdjustedHistoryUnits60().setScale(4, RoundingMode.HALF_UP).toPlainString()
                + " / "
                + snapshot.getAdjustedHistoryUnits90().setScale(4, RoundingMode.HALF_UP).toPlainString()
                + "；";
    }

    private boolean hasHistoryAdjustment(SalesForecastFeatureSnapshot snapshot) {
        return differs(snapshot.getAdjustedHistoryUnits7(), snapshot.getHistoryUnits7())
                || differs(snapshot.getAdjustedHistoryUnits30(), snapshot.getHistoryUnits30())
                || differs(snapshot.getAdjustedHistoryUnits60(), snapshot.getHistoryUnits60())
                || differs(snapshot.getAdjustedHistoryUnits90(), snapshot.getHistoryUnits90());
    }

    private boolean differs(BigDecimal adjustedUnits, int rawUnits) {
        if (adjustedUnits == null) {
            return rawUnits != 0;
        }
        return adjustedUnits.compareTo(BigDecimal.valueOf(rawUnits)) != 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
