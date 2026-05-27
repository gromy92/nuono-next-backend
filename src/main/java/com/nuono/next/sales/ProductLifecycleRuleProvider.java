package com.nuono.next.sales;

public interface ProductLifecycleRuleProvider {

    ProductLifecycleRuleSet resolve(ProductLifecycleCalculationScope scope);

    static ProductLifecycleRuleProvider defaultV1() {
        return scope -> ProductLifecycleRuleSet.defaultV1();
    }
}
