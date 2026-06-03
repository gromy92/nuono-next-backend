package com.nuono.next.operationsconfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class OperationLifecycleRuleThresholds {

    private final Integer newMaxAgeDays;
    private final Integer newMinAgeDays;
    private final BigDecimal highPriceThreshold;
    private final BigDecimal growthMinSalesGrowthRate;
    private final BigDecimal growthMinPvGrowthRate;
    private final BigDecimal growthMinMonthlySales;
    private final Integer growthMinActiveSalesDays;
    private final BigDecimal growthMaxVolatility;
    private final BigDecimal stableMinPvGrowthRate;
    private final BigDecimal stableVolatilityMin;
    private final BigDecimal stableVolatilityMax;
    private final BigDecimal declineMaxVolatility;
    private final BigDecimal declineMaxSalesGrowthRate;
    private final BigDecimal longTailMaxVolatility;
    private final BigDecimal longTailMaxMonthlySales;
    private final BigDecimal explosiveInertiaFactor;
    private final BigDecimal steadyTrendFactor;
    private final BigDecimal stepGrowthMultiplier;
    private final BigDecimal volatileOutlierTrimRatio;
    private final BigDecimal volatileMomentumThreshold;
    private final BigDecimal declineDecayRatioThreshold;
    private final BigDecimal stableRisingShortWeight;
    private final BigDecimal stableFallingShortWeight;

    public OperationLifecycleRuleThresholds() {
        this(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null
        );
    }

    @JsonCreator
    public OperationLifecycleRuleThresholds(
            @JsonProperty("newMaxAgeDays") Integer newMaxAgeDays,
            @JsonProperty("newMinAgeDays") Integer newMinAgeDays,
            @JsonProperty("highPriceThreshold") BigDecimal highPriceThreshold,
            @JsonProperty("growthMinSalesGrowthRate") BigDecimal growthMinSalesGrowthRate,
            @JsonProperty("growthMinPvGrowthRate") BigDecimal growthMinPvGrowthRate,
            @JsonProperty("growthMinMonthlySales") BigDecimal growthMinMonthlySales,
            @JsonProperty("growthMinActiveSalesDays") Integer growthMinActiveSalesDays,
            @JsonProperty("growthMaxVolatility") BigDecimal growthMaxVolatility,
            @JsonProperty("stableMinPvGrowthRate") BigDecimal stableMinPvGrowthRate,
            @JsonProperty("stableVolatilityMin") BigDecimal stableVolatilityMin,
            @JsonProperty("stableVolatilityMax") BigDecimal stableVolatilityMax,
            @JsonProperty("declineMaxVolatility") BigDecimal declineMaxVolatility,
            @JsonProperty("declineMaxSalesGrowthRate") BigDecimal declineMaxSalesGrowthRate,
            @JsonProperty("longTailMaxVolatility") BigDecimal longTailMaxVolatility,
            @JsonProperty("longTailMaxMonthlySales") BigDecimal longTailMaxMonthlySales,
            @JsonProperty("explosiveInertiaFactor") BigDecimal explosiveInertiaFactor,
            @JsonProperty("steadyTrendFactor") BigDecimal steadyTrendFactor,
            @JsonProperty("stepGrowthMultiplier") BigDecimal stepGrowthMultiplier,
            @JsonProperty("volatileOutlierTrimRatio") BigDecimal volatileOutlierTrimRatio,
            @JsonProperty("volatileMomentumThreshold") BigDecimal volatileMomentumThreshold,
            @JsonProperty("declineDecayRatioThreshold") BigDecimal declineDecayRatioThreshold,
            @JsonProperty("stableRisingShortWeight") BigDecimal stableRisingShortWeight,
            @JsonProperty("stableFallingShortWeight") BigDecimal stableFallingShortWeight
    ) {
        this.newMaxAgeDays = newMaxAgeDays;
        this.newMinAgeDays = newMinAgeDays;
        this.highPriceThreshold = scale(highPriceThreshold);
        this.growthMinSalesGrowthRate = scale(growthMinSalesGrowthRate);
        this.growthMinPvGrowthRate = scale(growthMinPvGrowthRate);
        this.growthMinMonthlySales = scale(growthMinMonthlySales);
        this.growthMinActiveSalesDays = growthMinActiveSalesDays;
        this.growthMaxVolatility = scale(growthMaxVolatility);
        this.stableMinPvGrowthRate = scale(stableMinPvGrowthRate);
        this.stableVolatilityMin = scale(stableVolatilityMin);
        this.stableVolatilityMax = scale(stableVolatilityMax);
        this.declineMaxVolatility = scale(declineMaxVolatility);
        this.declineMaxSalesGrowthRate = scale(declineMaxSalesGrowthRate);
        this.longTailMaxVolatility = scale(longTailMaxVolatility);
        this.longTailMaxMonthlySales = scale(longTailMaxMonthlySales);
        this.explosiveInertiaFactor = scale(explosiveInertiaFactor);
        this.steadyTrendFactor = scale(steadyTrendFactor);
        this.stepGrowthMultiplier = scale(stepGrowthMultiplier);
        this.volatileOutlierTrimRatio = scale(volatileOutlierTrimRatio);
        this.volatileMomentumThreshold = scale(volatileMomentumThreshold);
        this.declineDecayRatioThreshold = scale(declineDecayRatioThreshold);
        this.stableRisingShortWeight = scale(stableRisingShortWeight);
        this.stableFallingShortWeight = scale(stableFallingShortWeight);
    }

    public static OperationLifecycleRuleThresholds defaultV1() {
        return new OperationLifecycleRuleThresholds(
                60,
                7,
                new BigDecimal("200.0000"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.2000"),
                new BigDecimal("10.0000"),
                5,
                new BigDecimal("0.9000"),
                new BigDecimal("-0.1000"),
                new BigDecimal("0.3000"),
                new BigDecimal("0.5000"),
                new BigDecimal("1.0000"),
                new BigDecimal("-0.1000"),
                new BigDecimal("0.6000"),
                new BigDecimal("10.0000"),
                new BigDecimal("1.5000"),
                new BigDecimal("1.0500"),
                new BigDecimal("2.0000"),
                new BigDecimal("0.1000"),
                new BigDecimal("0.1000"),
                new BigDecimal("0.8000"),
                new BigDecimal("0.7000"),
                new BigDecimal("0.6000")
        );
    }

    public OperationLifecycleRuleThresholds withNewMinAgeDays(Integer value) {
        return copy(newMaxAgeDays, value, highPriceThreshold, growthMinSalesGrowthRate, growthMinPvGrowthRate,
                growthMinMonthlySales, growthMinActiveSalesDays, growthMaxVolatility, stableMinPvGrowthRate,
                stableVolatilityMin, stableVolatilityMax, declineMaxVolatility, declineMaxSalesGrowthRate,
                longTailMaxVolatility, longTailMaxMonthlySales, explosiveInertiaFactor, steadyTrendFactor,
                stepGrowthMultiplier, volatileOutlierTrimRatio, volatileMomentumThreshold, declineDecayRatioThreshold,
                stableRisingShortWeight, stableFallingShortWeight);
    }

    public OperationLifecycleRuleThresholds withNewMaxAgeDays(Integer value) {
        return copy(value, newMinAgeDays, highPriceThreshold, growthMinSalesGrowthRate, growthMinPvGrowthRate,
                growthMinMonthlySales, growthMinActiveSalesDays, growthMaxVolatility, stableMinPvGrowthRate,
                stableVolatilityMin, stableVolatilityMax, declineMaxVolatility, declineMaxSalesGrowthRate,
                longTailMaxVolatility, longTailMaxMonthlySales, explosiveInertiaFactor, steadyTrendFactor,
                stepGrowthMultiplier, volatileOutlierTrimRatio, volatileMomentumThreshold, declineDecayRatioThreshold,
                stableRisingShortWeight, stableFallingShortWeight);
    }

    public OperationLifecycleRuleThresholds withGrowthMinSalesGrowthRate(BigDecimal value) {
        return copy(newMaxAgeDays, newMinAgeDays, highPriceThreshold, value, growthMinPvGrowthRate,
                growthMinMonthlySales, growthMinActiveSalesDays, growthMaxVolatility, stableMinPvGrowthRate,
                stableVolatilityMin, stableVolatilityMax, declineMaxVolatility, declineMaxSalesGrowthRate,
                longTailMaxVolatility, longTailMaxMonthlySales, explosiveInertiaFactor, steadyTrendFactor,
                stepGrowthMultiplier, volatileOutlierTrimRatio, volatileMomentumThreshold, declineDecayRatioThreshold,
                stableRisingShortWeight, stableFallingShortWeight);
    }

    public OperationLifecycleRuleThresholds withGrowthMinMonthlySales(BigDecimal value) {
        return copy(newMaxAgeDays, newMinAgeDays, highPriceThreshold, growthMinSalesGrowthRate, growthMinPvGrowthRate,
                value, growthMinActiveSalesDays, growthMaxVolatility, stableMinPvGrowthRate,
                stableVolatilityMin, stableVolatilityMax, declineMaxVolatility, declineMaxSalesGrowthRate,
                longTailMaxVolatility, longTailMaxMonthlySales, explosiveInertiaFactor, steadyTrendFactor,
                stepGrowthMultiplier, volatileOutlierTrimRatio, volatileMomentumThreshold, declineDecayRatioThreshold,
                stableRisingShortWeight, stableFallingShortWeight);
    }

    public OperationLifecycleRuleThresholds withLongTailMaxMonthlySales(BigDecimal value) {
        return copy(newMaxAgeDays, newMinAgeDays, highPriceThreshold, growthMinSalesGrowthRate, growthMinPvGrowthRate,
                growthMinMonthlySales, growthMinActiveSalesDays, growthMaxVolatility, stableMinPvGrowthRate,
                stableVolatilityMin, stableVolatilityMax, declineMaxVolatility, declineMaxSalesGrowthRate,
                longTailMaxVolatility, value, explosiveInertiaFactor, steadyTrendFactor, stepGrowthMultiplier,
                volatileOutlierTrimRatio, volatileMomentumThreshold, declineDecayRatioThreshold,
                stableRisingShortWeight, stableFallingShortWeight);
    }

    public OperationLifecycleRuleThresholds withVolatileOutlierTrimRatio(BigDecimal value) {
        return copy(newMaxAgeDays, newMinAgeDays, highPriceThreshold, growthMinSalesGrowthRate, growthMinPvGrowthRate,
                growthMinMonthlySales, growthMinActiveSalesDays, growthMaxVolatility, stableMinPvGrowthRate,
                stableVolatilityMin, stableVolatilityMax, declineMaxVolatility, declineMaxSalesGrowthRate,
                longTailMaxVolatility, longTailMaxMonthlySales, explosiveInertiaFactor, steadyTrendFactor,
                stepGrowthMultiplier, value, volatileMomentumThreshold, declineDecayRatioThreshold,
                stableRisingShortWeight, stableFallingShortWeight);
    }

    private OperationLifecycleRuleThresholds copy(
            Integer nextNewMaxAgeDays,
            Integer nextNewMinAgeDays,
            BigDecimal nextHighPriceThreshold,
            BigDecimal nextGrowthMinSalesGrowthRate,
            BigDecimal nextGrowthMinPvGrowthRate,
            BigDecimal nextGrowthMinMonthlySales,
            Integer nextGrowthMinActiveSalesDays,
            BigDecimal nextGrowthMaxVolatility,
            BigDecimal nextStableMinPvGrowthRate,
            BigDecimal nextStableVolatilityMin,
            BigDecimal nextStableVolatilityMax,
            BigDecimal nextDeclineMaxVolatility,
            BigDecimal nextDeclineMaxSalesGrowthRate,
            BigDecimal nextLongTailMaxVolatility,
            BigDecimal nextLongTailMaxMonthlySales,
            BigDecimal nextExplosiveInertiaFactor,
            BigDecimal nextSteadyTrendFactor,
            BigDecimal nextStepGrowthMultiplier,
            BigDecimal nextVolatileOutlierTrimRatio,
            BigDecimal nextVolatileMomentumThreshold,
            BigDecimal nextDeclineDecayRatioThreshold,
            BigDecimal nextStableRisingShortWeight,
            BigDecimal nextStableFallingShortWeight
    ) {
        return new OperationLifecycleRuleThresholds(
                nextNewMaxAgeDays,
                nextNewMinAgeDays,
                nextHighPriceThreshold,
                nextGrowthMinSalesGrowthRate,
                nextGrowthMinPvGrowthRate,
                nextGrowthMinMonthlySales,
                nextGrowthMinActiveSalesDays,
                nextGrowthMaxVolatility,
                nextStableMinPvGrowthRate,
                nextStableVolatilityMin,
                nextStableVolatilityMax,
                nextDeclineMaxVolatility,
                nextDeclineMaxSalesGrowthRate,
                nextLongTailMaxVolatility,
                nextLongTailMaxMonthlySales,
                nextExplosiveInertiaFactor,
                nextSteadyTrendFactor,
                nextStepGrowthMultiplier,
                nextVolatileOutlierTrimRatio,
                nextVolatileMomentumThreshold,
                nextDeclineDecayRatioThreshold,
                nextStableRisingShortWeight,
                nextStableFallingShortWeight
        );
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }

    public Integer getNewMaxAgeDays() { return newMaxAgeDays; }
    public Integer getNewMinAgeDays() { return newMinAgeDays; }
    public BigDecimal getHighPriceThreshold() { return highPriceThreshold; }
    public BigDecimal getGrowthMinSalesGrowthRate() { return growthMinSalesGrowthRate; }
    public BigDecimal getGrowthMinPvGrowthRate() { return growthMinPvGrowthRate; }
    public BigDecimal getGrowthMinMonthlySales() { return growthMinMonthlySales; }
    public Integer getGrowthMinActiveSalesDays() { return growthMinActiveSalesDays; }
    public BigDecimal getGrowthMaxVolatility() { return growthMaxVolatility; }
    public BigDecimal getStableMinPvGrowthRate() { return stableMinPvGrowthRate; }
    public BigDecimal getStableVolatilityMin() { return stableVolatilityMin; }
    public BigDecimal getStableVolatilityMax() { return stableVolatilityMax; }
    public BigDecimal getDeclineMaxVolatility() { return declineMaxVolatility; }
    public BigDecimal getDeclineMaxSalesGrowthRate() { return declineMaxSalesGrowthRate; }
    public BigDecimal getLongTailMaxVolatility() { return longTailMaxVolatility; }
    public BigDecimal getLongTailMaxMonthlySales() { return longTailMaxMonthlySales; }
    public BigDecimal getExplosiveInertiaFactor() { return explosiveInertiaFactor; }
    public BigDecimal getSteadyTrendFactor() { return steadyTrendFactor; }
    public BigDecimal getStepGrowthMultiplier() { return stepGrowthMultiplier; }
    public BigDecimal getVolatileOutlierTrimRatio() { return volatileOutlierTrimRatio; }
    public BigDecimal getVolatileMomentumThreshold() { return volatileMomentumThreshold; }
    public BigDecimal getDeclineDecayRatioThreshold() { return declineDecayRatioThreshold; }
    public BigDecimal getStableRisingShortWeight() { return stableRisingShortWeight; }
    public BigDecimal getStableFallingShortWeight() { return stableFallingShortWeight; }
}
