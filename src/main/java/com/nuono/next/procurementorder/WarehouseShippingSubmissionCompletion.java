package com.nuono.next.procurementorder;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import java.util.List;

final class WarehouseShippingSubmissionCompletion {

    private WarehouseShippingSubmissionCompletion() {
    }

    static void markSubmitted(
            ProcurementPurchaseOrderMapper mapper,
            ShippingOrderRecord order,
            List<ShippingOrderSegmentRecord> segments,
            List<PurchaseOrderLogisticsQuoteLineRecord> facts,
            Long operatorUserId
    ) {
        requireExact(
                mapper.submitLogisticsQuoteLinesForShippingOrder(order.id, operatorUserId),
                facts == null ? 0 : facts.size()
        );
        if (segments != null && !segments.isEmpty()) {
            requireExact(
                    mapper.markShippingOrderSegmentsSubmitted(order.id, order.ownerUserId, operatorUserId),
                    segments.size()
            );
        }
        requireExact(mapper.markShippingOrderSubmitted(order.id, order.ownerUserId, operatorUserId), 1);
    }

    private static void requireExact(int affected, int expected) {
        if (affected != expected) {
            throw new IllegalArgumentException("仓库单提交状态已变化，请刷新后重试。");
        }
    }
}
