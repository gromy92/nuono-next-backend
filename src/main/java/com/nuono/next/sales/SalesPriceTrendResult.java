package com.nuono.next.sales;

import java.util.List;

public class SalesPriceTrendResult {

    private final List<SalesPriceTrendBucket> buckets;
    private final SalesPriceTrendState state;

    public SalesPriceTrendResult(List<SalesPriceTrendBucket> buckets, SalesPriceTrendState state) {
        this.buckets = buckets == null ? List.of() : List.copyOf(buckets);
        this.state = state == null ? SalesPriceTrendState.noOrderPriceFacts() : state;
    }

    public static SalesPriceTrendResult empty() {
        return new SalesPriceTrendResult(List.of(), SalesPriceTrendState.noOrderPriceFacts());
    }

    public static SalesPriceTrendResult ready(List<SalesPriceTrendBucket> buckets) {
        return new SalesPriceTrendResult(buckets, SalesPriceTrendState.ready());
    }

    public static SalesPriceTrendResult mixedCurrency() {
        return new SalesPriceTrendResult(List.of(), SalesPriceTrendState.mixedCurrency());
    }

    public static SalesPriceTrendResult invalidOrderPriceFacts() {
        return new SalesPriceTrendResult(List.of(), SalesPriceTrendState.invalidOrderPriceFacts());
    }

    public List<SalesPriceTrendBucket> getBuckets() {
        return buckets;
    }

    public SalesPriceTrendState getState() {
        return state;
    }
}
