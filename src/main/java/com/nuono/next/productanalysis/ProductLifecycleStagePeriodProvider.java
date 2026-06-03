package com.nuono.next.productanalysis;

public interface ProductLifecycleStagePeriodProvider {

    ProductLifecycleStagePeriodConfig resolveStagePeriods(ProductLifecycleAnalysisQuery query);
}
