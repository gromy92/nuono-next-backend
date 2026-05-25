package com.nuono.next.noonpull;

public interface NoonSalesFactWriter {
    void upsert(NoonSalesDailyFact fact);
}
