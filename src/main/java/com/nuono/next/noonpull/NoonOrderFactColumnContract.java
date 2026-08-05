package com.nuono.next.noonpull;

import com.nuono.next.datapull.report.ReportFactColumnContract;
import java.math.BigDecimal;

/** Exact row-level bridge from a Noon order export to its legacy fact table. */
final class NoonOrderFactColumnContract {
    private NoonOrderFactColumnContract() {
    }

    static NoonOrderLineFact requirePersistable(NoonOrderLineFact fact) {
        ReportFactColumnContract.positiveId(fact.getOwnerUserId());
        text(fact.getStoreCode(), 80);
        text(fact.getSiteCode(), 20);
        text(fact.getIdPartner(), 80);
        text(fact.getSourceCountry(), 20);
        text(fact.getCountryCode(), 20);
        text(fact.getDestinationCountry(), 20);
        text(fact.getBayanNr(), 120);
        text(fact.getOrderLineIdentity(), 160);
        text(fact.getOrderIdentity(), 160);
        text(fact.getPartnerSku(), 160);
        text(fact.getSku(), 160);
        text(fact.getStatus(), 80);
        exactDecimal(fact.getOfferPrice());
        exactDecimal(fact.getGmvLcy());
        text(fact.getCurrencyCode(), 20);
        text(fact.getBrandCode(), 160);
        text(fact.getFamily(), 255);
        text(fact.getFulfillmentModel(), 160);
        text(fact.getSourceBatchId(), 160);
        ReportFactColumnContract.dateTime(fact.getOrderTimestamp());
        ReportFactColumnContract.dateTime(fact.getShipmentTimestamp());
        ReportFactColumnContract.dateTime(fact.getDeliveredTimestamp());
        ReportFactColumnContract.date(fact.getReportDateFrom());
        ReportFactColumnContract.date(fact.getReportDateTo());
        return fact;
    }

    private static void text(String value, int maximumCharacters) {
        ReportFactColumnContract.text(value, maximumCharacters);
    }

    private static void exactDecimal(BigDecimal value) {
        BigDecimal normalized = ReportFactColumnContract.decimal(value, 18, 6);
        if (normalized.compareTo(value) != 0) {
            throw new IllegalArgumentException("order fact precision exceeds target column");
        }
    }
}
