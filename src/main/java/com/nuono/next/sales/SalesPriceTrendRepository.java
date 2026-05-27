package com.nuono.next.sales;

public interface SalesPriceTrendRepository {

    SalesPriceTrendResult getPriceTrend(SalesFactQuery query, String granularity);
}
