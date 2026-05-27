package com.nuono.next.salesforecast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class DefaultSalesForecastEngine {

    public static final String CALCULATION_VERSION = "DEFAULT_V1";
    public static final String DEFAULT_CONFIG_VERSION = "DEFAULT_V1";

    public SalesForecastFormulaResult forecast30(SalesForecastFeatureSnapshot snapshot, String configVersion) {
        return forecast30(snapshot, configVersion, BigDecimal.ONE);
    }

    public SalesForecastFormulaResult forecast30(
            SalesForecastFeatureSnapshot snapshot,
            String configVersion,
            BigDecimal futureFactor
    ) {
        String resolvedConfigVersion = hasText(configVersion) ? configVersion : DEFAULT_CONFIG_VERSION;
        BigDecimal resolvedFutureFactor = futureFactor == null ? BigDecimal.ONE : futureFactor;
        BigDecimal daily7 = dailyAverage(snapshot.getHistoryUnits7(), 7);
        BigDecimal daily30 = dailyAverage(snapshot.getHistoryUnits30(), 30);
        BigDecimal daily90 = dailyAverage(snapshot.getHistoryUnits90(), 90);
        BigDecimal baseDailySales = weightedMean(snapshot, daily7, daily30, daily90);
        BigDecimal recentDailyTrendRate = recentDailyTrendRate(daily7, daily30);
        BigDecimal trendFactor = trendFactor(recentDailyTrendRate);
        BigDecimal lifecycleFactor = lifecycleFactor(snapshot.getLifecycleCode());
        int forecastUnits30 = forecastUnits(baseDailySales, trendFactor, lifecycleFactor, resolvedFutureFactor, 30);
        int forecastUnits60 = forecastUnits(baseDailySales, trendFactor, lifecycleFactor, resolvedFutureFactor, 60);
        int forecastUnits90 = forecastUnits(baseDailySales, trendFactor, lifecycleFactor, resolvedFutureFactor, 90);

        return new SalesForecastFormulaResult(
                CALCULATION_VERSION,
                resolvedConfigVersion,
                forecastUnits30,
                forecastUnits60,
                forecastUnits90,
                baseDailySales.setScale(4, RoundingMode.HALF_UP),
                recentDailyTrendRate.setScale(4, RoundingMode.HALF_UP),
                trendFactor.setScale(4, RoundingMode.HALF_UP),
                lifecycleFactor.setScale(4, RoundingMode.HALF_UP),
                resolvedFutureFactor.setScale(4, RoundingMode.HALF_UP),
                lifecycleExplanation(snapshot.getLifecycleCode(), snapshot.getLifecycleLabel(), lifecycleFactor),
                shortReason(snapshot, forecastUnits30, forecastUnits60, forecastUnits90)
        );
    }

    private BigDecimal weightedMean(
            SalesForecastFeatureSnapshot snapshot,
            BigDecimal daily7,
            BigDecimal daily30,
            BigDecimal daily90
    ) {
        BigDecimal weightedTotal = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;
        if (snapshot.getObservedDays() > 0) {
            weightedTotal = weightedTotal.add(daily7.multiply(new BigDecimal("0.50")));
            weightTotal = weightTotal.add(new BigDecimal("0.50"));
        }
        if (snapshot.getObservedDays() >= 30) {
            weightedTotal = weightedTotal.add(daily30.multiply(new BigDecimal("0.30")));
            weightTotal = weightTotal.add(new BigDecimal("0.30"));
        }
        if (snapshot.getObservedDays() >= 90) {
            weightedTotal = weightedTotal.add(daily90.multiply(new BigDecimal("0.20")));
            weightTotal = weightTotal.add(new BigDecimal("0.20"));
        }
        if (weightTotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return weightedTotal.divide(weightTotal, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal recentDailyTrendRate(BigDecimal daily7, BigDecimal daily30) {
        if (daily30.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return daily7.subtract(daily30).divide(daily30, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal trendFactor(BigDecimal recentDailyTrendRate) {
        BigDecimal factor = BigDecimal.ONE.add(recentDailyTrendRate.multiply(new BigDecimal("0.50")));
        return clamp(factor, new BigDecimal("0.70"), new BigDecimal("1.35"));
    }

    private BigDecimal lifecycleFactor(String lifecycleCode) {
        if ("new".equals(lifecycleCode)) {
            return new BigDecimal("1.05");
        }
        if ("growth".equals(lifecycleCode)) {
            return new BigDecimal("1.22");
        }
        if ("decline".equals(lifecycleCode)) {
            return new BigDecimal("0.82");
        }
        if ("longTail".equals(lifecycleCode)) {
            return new BigDecimal("0.62");
        }
        if ("data_insufficient".equals(lifecycleCode)) {
            return BigDecimal.ONE;
        }
        return BigDecimal.ONE;
    }

    private BigDecimal dailyAverage(int units, int days) {
        return BigDecimal.valueOf(units).divide(BigDecimal.valueOf(days), 8, RoundingMode.HALF_UP);
    }

    private int forecastUnits(
            BigDecimal baseDailySales,
            BigDecimal trendFactor,
            BigDecimal lifecycleFactor,
            BigDecimal futureFactor,
            int windowDays
    ) {
        BigDecimal forecast = baseDailySales
                .multiply(trendFactor)
                .multiply(lifecycleFactor)
                .multiply(futureFactor)
                .multiply(BigDecimal.valueOf(windowDays));
        int forecastUnits = forecast.setScale(0, RoundingMode.CEILING).intValue();
        if (forecastUnits < 0) {
            return 0;
        }
        return forecastUnits;
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        if (value.compareTo(minimum) < 0) {
            return minimum;
        }
        if (value.compareTo(maximum) > 0) {
            return maximum;
        }
        return value;
    }

    private String lifecycleExplanation(String lifecycleCode, String lifecycleLabel, BigDecimal lifecycleFactor) {
        String label = hasText(lifecycleLabel) ? lifecycleLabel : "未分类";
        String factorText = lifecycleFactor.setScale(2, RoundingMode.HALF_UP).toPlainString();
        if ("new".equals(lifecycleCode)) {
            return label + "商品：样本较短，使用新品因子 " + factorText + " 保守放大。";
        }
        if ("growth".equals(lifecycleCode)) {
            return label + "商品：近期销量高于基线，使用增长因子 " + factorText + "。";
        }
        if ("decline".equals(lifecycleCode)) {
            return label + "商品：近期销量走弱，使用衰退因子 " + factorText + "。";
        }
        if ("longTail".equals(lifecycleCode)) {
            return label + "商品：长期低动销，使用长尾期因子 " + factorText + " 控制预测。";
        }
        if ("data_insufficient".equals(lifecycleCode)) {
            return label + "商品：生命周期样本不足，使用中性因子 " + factorText + "。";
        }
        return label + "商品：销量相对平稳，生命周期因子 " + factorText + "。";
    }

    private String shortReason(
            SalesForecastFeatureSnapshot snapshot,
            int forecastUnits30,
            int forecastUnits60,
            int forecastUnits90
    ) {
        return "近7日 " + snapshot.getHistoryUnits7()
                + " 件，近30日 " + snapshot.getHistoryUnits30()
                + " 件，近90日 " + snapshot.getHistoryUnits90()
                + " 件；生命周期：" + snapshot.getLifecycleLabel()
                + "；预测未来30/60/90天约 "
                + forecastUnits30 + " / " + forecastUnits60 + " / " + forecastUnits90 + " 件。";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
