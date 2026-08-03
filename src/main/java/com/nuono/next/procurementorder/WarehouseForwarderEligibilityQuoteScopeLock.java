package com.nuono.next.procurementorder;

import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityScopeVerifier.changed;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityScopeVerifier.safe;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityScopeVerifier.scope;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.normalized;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class WarehouseForwarderEligibilityQuoteScopeLock {

    private final Long ownerUserId;
    private final List<BoundQuoteLineScope> lockedLines;

    WarehouseForwarderEligibilityQuoteScopeLock(
            Long ownerUserId,
            List<PurchaseOrderLogisticsQuoteLineRecord> lines
    ) {
        this.ownerUserId = ownerUserId;
        this.lockedLines = Collections.unmodifiableList(safe(lines).stream()
                .map(line -> new BoundQuoteLineScope(ownerUserId, line))
                .collect(Collectors.toList()));
    }

    void requireUnchanged(List<PurchaseOrderLogisticsQuoteLineRecord> refreshedLines) {
        Set<BoundQuoteLineScope> matched = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PurchaseOrderLogisticsQuoteLineRecord refreshed : safe(refreshedLines)) {
            List<BoundQuoteLineScope> matches = lockedLines.stream()
                    .filter(locked -> locked.matchesReference(refreshed))
                    .collect(Collectors.toList());
            if (matches.size() != 1 || !matched.add(matches.get(0))
                    || !matches.get(0).matchesIdentity(ownerUserId, refreshed)) {
                throw changed();
            }
        }
    }

    private static final class BoundQuoteLineScope {
        private final Long id;
        private final Long purchaseOrderItemSiteId;
        private final Long shippingOrderLineId;
        private final Long shippingOrderSegmentId;
        private final String siteCode;
        private final String transportMode;
        private final ProductForwarderEligibilityScopeAnchorRecord scope;

        private BoundQuoteLineScope(Long ownerUserId, PurchaseOrderLogisticsQuoteLineRecord line) {
            if (line == null) {
                throw changed();
            }
            this.id = line.id;
            this.purchaseOrderItemSiteId = line.purchaseOrderItemSiteId;
            this.shippingOrderLineId = line.shippingOrderLineId;
            this.shippingOrderSegmentId = line.shippingOrderSegmentId;
            this.siteCode = normalized(line.siteCode);
            this.transportMode = normalized(line.plannedTransportMode);
            this.scope = scope(ownerUserId, line.ownerUserId, line.logicalStoreId, line.partnerSku);
        }

        private boolean matchesReference(PurchaseOrderLogisticsQuoteLineRecord refreshed) {
            if (refreshed == null) {
                return false;
            }
            if (id != null && refreshed.id != null) {
                return Objects.equals(id, refreshed.id);
            }
            if (shippingOrderLineId != null || refreshed.shippingOrderLineId != null) {
                return shippingOrderLineId != null && refreshed.shippingOrderLineId != null
                        && purchaseOrderItemSiteId != null && refreshed.purchaseOrderItemSiteId != null
                        && Objects.equals(shippingOrderLineId, refreshed.shippingOrderLineId)
                        && Objects.equals(purchaseOrderItemSiteId, refreshed.purchaseOrderItemSiteId);
            }
            return purchaseOrderItemSiteId != null && refreshed.purchaseOrderItemSiteId != null
                    && Objects.equals(purchaseOrderItemSiteId, refreshed.purchaseOrderItemSiteId);
        }

        private boolean matchesIdentity(Long ownerUserId, PurchaseOrderLogisticsQuoteLineRecord refreshed) {
            return Objects.equals(purchaseOrderItemSiteId, refreshed.purchaseOrderItemSiteId)
                    && Objects.equals(shippingOrderLineId, refreshed.shippingOrderLineId)
                    && Objects.equals(shippingOrderSegmentId, refreshed.shippingOrderSegmentId)
                    && siteCode.equals(normalized(refreshed.siteCode))
                    && transportMode.equals(normalized(refreshed.plannedTransportMode))
                    && scope.equals(scope(ownerUserId, refreshed.ownerUserId,
                    refreshed.logicalStoreId, refreshed.partnerSku));
        }
    }
}
