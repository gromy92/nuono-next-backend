package com.nuono.next.sales;

import java.math.BigDecimal;
import java.util.List;

public class ProductLifecycleGrowthShapeMetrics {

    private final List<Integer> dailySales30;
    private final int validPointCount;
    private final BigDecimal trimmedFirstHalfAvg;
    private final BigDecimal trimmedSecondHalfAvg;
    private final BigDecimal momentumRate;

    public ProductLifecycleGrowthShapeMetrics(
            List<Integer> dailySales30,
            int validPointCount,
            BigDecimal trimmedFirstHalfAvg,
            BigDecimal trimmedSecondHalfAvg,
            BigDecimal momentumRate
    ) {
        this.dailySales30 = dailySales30 == null ? List.of() : List.copyOf(dailySales30);
        this.validPointCount = validPointCount;
        this.trimmedFirstHalfAvg = trimmedFirstHalfAvg;
        this.trimmedSecondHalfAvg = trimmedSecondHalfAvg;
        this.momentumRate = momentumRate;
    }

    public List<Integer> getDailySales30() {
        return dailySales30;
    }

    public int getValidPointCount() {
        return validPointCount;
    }

    public BigDecimal getTrimmedFirstHalfAvg() {
        return trimmedFirstHalfAvg;
    }

    public BigDecimal getTrimmedSecondHalfAvg() {
        return trimmedSecondHalfAvg;
    }

    public BigDecimal getMomentumRate() {
        return momentumRate;
    }
}
