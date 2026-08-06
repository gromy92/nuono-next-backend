package com.nuono.next.orderfinance;

import com.nuono.next.datapull.report.ReportFactColumnContract;
import java.math.BigDecimal;

/** Exact row-level bridge from a Noon finance export to its fact table. */
final class NoonFinanceFactColumnContract {
    private NoonFinanceFactColumnContract() {
    }

    static NoonFinanceTransactionFact requirePersistable(NoonFinanceTransactionFact fact) {
        ReportFactColumnContract.positiveId(fact.getOwnerUserId());
        text(fact.getStoreCode(), 80);
        text(fact.getSiteCode(), 20);
        text(fact.getSourceBatchId(), 160);
        text(fact.getFileDigestSha256(), 128);
        text(fact.getRowHash(), 128);
        text(fact.getContractCode(), 80);
        text(fact.getContractTitle(), 160);
        text(fact.getReferenceNr(), 160);
        text(fact.getOrderNr(), 160);
        text(fact.getItemNr(), 160);
        text(fact.getTitle(), 1024);
        text(fact.getSku(), 160);
        text(fact.getPartnerSku(), 160);
        text(fact.getTransactionType(), 80);
        text(fact.getCurrency(), 20);
        ReportFactColumnContract.date(fact.getOrderDate());
        ReportFactColumnContract.date(fact.getTransactionDate());
        ReportFactColumnContract.date(fact.getReportDateFrom());
        ReportFactColumnContract.date(fact.getReportDateTo());
        exactDecimal(fact.getNetProceeds());
        exactDecimal(fact.getReferralFeeIncludingVat());
        exactDecimal(fact.getFulfillmentLogisticsFeesIncludingVat());
        exactDecimal(fact.getShippingCreditsIncludingVat());
        exactDecimal(fact.getOtherOrderFeesIncludingVat());
        exactDecimal(fact.getOrderSubsidiesIncludingVat());
        exactDecimal(fact.getNonOrderFeesIncludingVat());
        exactDecimal(fact.getNonOrderSubsidiesIncludingVat());
        exactDecimal(fact.getOthersIncludingVat());
        exactDecimal(fact.getTotalAmount());
        return fact;
    }

    private static void text(String value, int maximumCharacters) {
        ReportFactColumnContract.text(value, maximumCharacters);
    }

    private static void exactDecimal(BigDecimal value) {
        BigDecimal normalized = ReportFactColumnContract.decimal(value, 18, 6);
        if (normalized.compareTo(value) != 0) {
            throw new IllegalArgumentException("finance fact precision exceeds target column");
        }
    }
}
