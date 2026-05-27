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

    public OperationLifecycleRuleThresholds() {
        this(
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
            @JsonProperty("longTailMaxMonthlySales") BigDecimal longTailMaxMonthlySales
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
                new BigDecimal("10.0000")
        );
    }

    public OperationLifecycleRuleThresholds withNewMinAgeDays(Integer value) {
        return copy(newMaxAgeDays, value, highPriceThreshold, growthMinSalesGrowthRate, growthMinPvGrowthRate,
                growthMinMonthlySales, growthMinActiveSalesDays, growthMaxVolatility, stableMinPvGrowthRate,
                stableVolatilityMin, stableVolatilityMax, declineMaxVolatility, declineMaxSalesGrowthRate,
                longTailMaxVolatility, longTailMaxMonthlySales);
    }

    public OperationLifecycleRuleThresholds withGrowthMinSalesGrowthRate(BigDecimal value) {
        return copy(newMaxAgeDays, newMinAgeDays, highPriceThreshold, value, growthMinPvGrowthRate,
                growthMinMonthlySales, growthMinActiveSalesDays, growthMaxVolatility, stableMinPvGrowthRate,
                stableVolatilityMin, stableVolatilityMax, declineMaxVolatility, declineMaxSalesGrowthRate,
                longTailMaxVolatility, longTailMaxMonthlySales);
    }

    public OperationLifecycleRuleThresholds withGrowthMinMonthlySales(BigDecimal value) {
        return copy(newMaxAgeDays, newMinAgeDays, highPriceThreshold, growthMinSalesGrowthRate, growthMinPvGrowthRate,
                value, growthMinActiveSalesDays, growthMaxVolatility, stableMinPvGrowthRate,
                stableVolatilityMin, stableVolatilityMax, declineMaxVolatility, declineMaxSalesGrowthRate,
                longTailMaxVolatility, longTailMaxMonthlySales);
    }

    public OperationLifecycleRuleThresholds withLongTailMaxMonthlySales(BigDecimal value) {
        return copy(newMaxAgeDays, newMinAgeDays, highPriceThreshold, growthMinSalesGrowthRate, growthMinPvGrowthRate,
                growthMinMonthlySales, growthMinActiveSalesDays, growthMaxVolatility, stableMinPvGrowthRate,
                stableVolatilityMin, stableVolatilityMax, declineMaxVolatility, declineMaxSalesGrowthRate,
                longTailMaxVolatility, value);
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
            BigDecimal nextLongTailMaxMonthlySales
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
                nextLongTailMaxMonthlySales
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
}
