package com.nuono.next.procurementorder;

import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.normalized;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderLineRecord;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class WarehouseForwarderEligibilityScopeVerifier {

    private static final String CHANGED = "商品承运范围已变化，请刷新后重试。";

    private WarehouseForwarderEligibilityScopeVerifier() {
    }

    static void requireShippingLinesUnchanged(
            Long ownerUserId,
            List<ShippingOrderLineRecord> lockedLines,
            List<PurchaseOrderLogisticsQuoteLineRecord> refreshedLines
    ) {
        if (safe(lockedLines).stream().anyMatch(line -> line.id == null)) {
            throw changed();
        }
        Map<Long, ShippingOrderLineRecord> byLineId = safe(lockedLines).stream()
                .collect(Collectors.toMap(line -> line.id, line -> line, (left, ignored) -> duplicate()));
        Map<Long, ShippingOrderLineRecord> byItemSiteId = safe(lockedLines).stream()
                .filter(line -> line.purchaseOrderItemSiteId != null)
                .collect(Collectors.toMap(
                        line -> line.purchaseOrderItemSiteId, line -> line, (left, ignored) -> duplicate()));
        Set<Long> matchedLineIds = new HashSet<>();
        for (PurchaseOrderLogisticsQuoteLineRecord refreshed : safe(refreshedLines)) {
            ShippingOrderLineRecord locked = refreshed.shippingOrderLineId == null
                    ? byItemSiteId.get(refreshed.purchaseOrderItemSiteId)
                    : byLineId.get(refreshed.shippingOrderLineId);
            if (locked == null || !scope(ownerUserId, locked.ownerUserId,
                    locked.logicalStoreId, locked.partnerSku).equals(scope(ownerUserId,
                    refreshed.ownerUserId, refreshed.logicalStoreId, refreshed.partnerSku))) {
                throw changed();
            }
            matchedLineIds.add(locked.id);
        }
        if (safe(refreshedLines).size() != safe(lockedLines).size()
                || matchedLineIds.size() != safe(lockedLines).size()) {
            throw changed();
        }
    }

    static ProductForwarderEligibilityScopeAnchorRecord scope(
            Long expectedOwnerUserId,
            Long actualOwnerUserId,
            Long logicalStoreId,
            String partnerSku
    ) {
        if (!Objects.equals(expectedOwnerUserId, actualOwnerUserId)
                || logicalStoreId == null || logicalStoreId <= 0
                || normalized(partnerSku).isEmpty()) {
            throw changed();
        }
        return new ProductForwarderEligibilityScopeAnchorRecord(
                expectedOwnerUserId, logicalStoreId, normalized(partnerSku));
    }

    private static ShippingOrderLineRecord duplicate() {
        throw changed();
    }

    static IllegalArgumentException changed() {
        return new IllegalArgumentException(CHANGED);
    }

    static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
