package com.nuono.next.orderfinance;

import java.util.List;

public interface NoonFinanceTransactionFactWriter {
    void upsert(NoonFinanceTransactionFact fact);

    default void upsertAll(List<NoonFinanceTransactionFact> facts) {
        if (facts == null) {
            return;
        }
        for (NoonFinanceTransactionFact fact : facts) {
            upsert(fact);
        }
    }
}
