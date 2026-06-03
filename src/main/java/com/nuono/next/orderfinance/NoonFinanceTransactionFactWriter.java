package com.nuono.next.orderfinance;

public interface NoonFinanceTransactionFactWriter {
    void upsert(NoonFinanceTransactionFact fact);
}
