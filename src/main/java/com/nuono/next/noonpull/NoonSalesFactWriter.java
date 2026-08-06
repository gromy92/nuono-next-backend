package com.nuono.next.noonpull;

import java.util.List;

public interface NoonSalesFactWriter {
    void upsert(NoonSalesDailyFact fact);

    default void upsertAll(List<NoonSalesDailyFact> facts) {
        if (facts == null) {
            return;
        }
        for (NoonSalesDailyFact fact : facts) {
            upsert(fact);
        }
    }
}
