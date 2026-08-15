package com.nuono.next.intransit;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class InTransitPackageWeightMergePolicy {

    private InTransitPackageWeightMergePolicy() {
    }

    static BigDecimal merge(
            BigDecimal incoming,
            BigDecimal existing,
            boolean preserveHigherPrecisionEquivalentWeights
    ) {
        if (incoming == null) {
            return existing;
        }
        if (!preserveHigherPrecisionEquivalentWeights
                || existing == null
                || effectiveScale(incoming) > 1
                || effectiveScale(existing) <= 1) {
            return incoming;
        }
        return existing.setScale(1, RoundingMode.HALF_UP).compareTo(incoming.setScale(1, RoundingMode.HALF_UP)) == 0
                ? existing
                : incoming;
    }

    private static int effectiveScale(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }
}
