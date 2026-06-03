package com.nuono.next.productanalysis;

import java.util.Map;

public class ProductLifecycleStagePeriodConfig {

    private final Map<String, Integer> durationDaysByLifecycleCode;
    private final boolean ruleConfigAvailable;

    public ProductLifecycleStagePeriodConfig(Map<String, Integer> durationDaysByLifecycleCode) {
        this(durationDaysByLifecycleCode, true);
    }

    private ProductLifecycleStagePeriodConfig(
            Map<String, Integer> durationDaysByLifecycleCode,
            boolean ruleConfigAvailable
    ) {
        this.durationDaysByLifecycleCode = durationDaysByLifecycleCode == null
                ? Map.of()
                : Map.copyOf(durationDaysByLifecycleCode);
        this.ruleConfigAvailable = ruleConfigAvailable;
    }

    public static ProductLifecycleStagePeriodConfig missingRuleConfig() {
        return new ProductLifecycleStagePeriodConfig(Map.of(), false);
    }

    public Integer getDurationDays(String lifecycleCode) {
        if (lifecycleCode == null) {
            return null;
        }
        return durationDaysByLifecycleCode.get(lifecycleCode);
    }

    public boolean isRuleConfigAvailable() {
        return ruleConfigAvailable;
    }
}
