package com.nuono.next.procurementorder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarehouseForwarderEligibilityQuoteScopeLockTest {

    @Test
    void shippingCandidateWithoutQuoteIdMatchesRefreshedQuoteByDocumentReferences() {
        PurchaseOrderLogisticsQuoteLineRecord locked = line(null);
        PurchaseOrderLogisticsQuoteLineRecord refreshed = line(280001L);

        assertDoesNotThrow(() -> lock(locked).requireUnchanged(List.of(refreshed)));
    }

    @Test
    void incompleteOrDifferentShippingDocumentReferencesAreRejected() {
        PurchaseOrderLogisticsQuoteLineRecord locked = line(null);
        PurchaseOrderLogisticsQuoteLineRecord differentLine = line(280001L);
        differentLine.shippingOrderLineId = 291002L;
        PurchaseOrderLogisticsQuoteLineRecord differentItemSite = line(280001L);
        differentItemSite.purchaseOrderItemSiteId = 220003L;
        PurchaseOrderLogisticsQuoteLineRecord missingItemSite = line(280001L);
        missingItemSite.purchaseOrderItemSiteId = null;

        assertRejected(locked, differentLine);
        assertRejected(locked, differentItemSite);
        assertRejected(locked, missingItemSite);
    }

    @Test
    void differentNonNullQuoteIdsNeverFallBackToTheSameShippingLine() {
        PurchaseOrderLogisticsQuoteLineRecord locked = line(280001L);
        PurchaseOrderLogisticsQuoteLineRecord refreshed = line(280002L);

        assertRejected(locked, refreshed);
    }

    @Test
    void ownerStoreOrPskuDriftIsRejected() {
        PurchaseOrderLogisticsQuoteLineRecord locked = line(null);
        PurchaseOrderLogisticsQuoteLineRecord ownerDrift = line(280001L);
        ownerDrift.ownerUserId = 308L;
        PurchaseOrderLogisticsQuoteLineRecord storeDrift = line(280001L);
        storeDrift.logicalStoreId = 108066L;
        PurchaseOrderLogisticsQuoteLineRecord pskuDrift = line(280001L);
        pskuDrift.partnerSku = "PSKU-2";

        assertRejected(locked, ownerDrift);
        assertRejected(locked, storeDrift);
        assertRejected(locked, pskuDrift);
    }

    @Test
    void siteModeOrSegmentDecisionIdentityDriftIsRejected() {
        PurchaseOrderLogisticsQuoteLineRecord locked = line(null);
        PurchaseOrderLogisticsQuoteLineRecord siteDrift = line(280001L);
        siteDrift.siteCode = "AE";
        PurchaseOrderLogisticsQuoteLineRecord modeDrift = line(280001L);
        modeDrift.plannedTransportMode = "SEA";
        PurchaseOrderLogisticsQuoteLineRecord segmentDrift = line(280001L);
        segmentDrift.shippingOrderSegmentId = 292002L;

        assertRejected(locked, siteDrift);
        assertRejected(locked, modeDrift);
        assertRejected(locked, segmentDrift);
    }

    private static WarehouseForwarderEligibilityQuoteScopeLock lock(
            PurchaseOrderLogisticsQuoteLineRecord line
    ) {
        return new WarehouseForwarderEligibilityQuoteScopeLock(307L, List.of(line));
    }

    private static void assertRejected(
            PurchaseOrderLogisticsQuoteLineRecord locked,
            PurchaseOrderLogisticsQuoteLineRecord refreshed
    ) {
        assertThrows(IllegalArgumentException.class,
                () -> lock(locked).requireUnchanged(List.of(refreshed)));
    }

    private static PurchaseOrderLogisticsQuoteLineRecord line(Long id) {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.id = id;
        line.ownerUserId = 307L;
        line.logicalStoreId = 108065L;
        line.purchaseOrderItemSiteId = 220002L;
        line.shippingOrderLineId = 291001L;
        line.shippingOrderSegmentId = 292001L;
        line.partnerSku = "PSKU-1";
        line.siteCode = "SA";
        line.plannedTransportMode = "AIR";
        return line;
    }
}
