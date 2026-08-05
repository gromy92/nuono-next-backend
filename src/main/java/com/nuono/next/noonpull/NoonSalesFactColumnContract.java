package com.nuono.next.noonpull;

import com.nuono.next.datapull.report.ReportFactColumnContract;
import java.math.BigDecimal;

/** Exact row bridge from a Noon sales export to daily_sales_fact. */
final class NoonSalesFactColumnContract {
    private NoonSalesFactColumnContract() {}

    static NoonSalesDailyFact requirePersistable(NoonSalesDailyFact fact) {
        ReportFactColumnContract.positiveId(fact.getOwnerUserId());
        text(fact.getStoreCode(), 80);
        text(fact.getSiteCode(), 20);
        text(fact.getSkuParent(), 160);
        text(fact.getSku(), 160);
        text(fact.getCurrency(), 20);
        ReportFactColumnContract.date(fact.getSalesDate());
        ReportFactColumnContract.signedInt(fact.getUnitsSold());
        exactDecimal(fact.getSalesAmount());
        return fact;
    }

    private static void text(String value, int maximumCharacters) {
        ReportFactColumnContract.text(value, maximumCharacters);
    }

    private static void exactDecimal(BigDecimal value) {
        BigDecimal normalized = ReportFactColumnContract.decimal(value, 18, 6);
        if (normalized.compareTo(value) != 0) {
            throw new IllegalArgumentException("sales fact precision exceeds target column");
        }
    }
}
