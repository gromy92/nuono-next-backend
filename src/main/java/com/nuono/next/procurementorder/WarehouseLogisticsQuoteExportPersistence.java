package com.nuono.next.procurementorder;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import java.util.List;

final class WarehouseLogisticsQuoteExportPersistence {

    private WarehouseLogisticsQuoteExportPersistence() {
    }

    static void markPurchaseOrderExported(
            ProcurementPurchaseOrderMapper mapper,
            Long orderId,
            List<Long> lineIds,
            Long operatorUserId
    ) {
        requireExact(mapper.markLogisticsQuoteLinesExported(orderId, lineIds, operatorUserId), lineIds.size());
    }

    static void markShippingOrderExported(
            ProcurementPurchaseOrderMapper mapper,
            Long shippingOrderId,
            List<Long> lineIds,
            Long operatorUserId
    ) {
        requireExact(
                mapper.markShippingOrderLogisticsQuoteLinesExported(shippingOrderId, lineIds, operatorUserId),
                lineIds.size()
        );
    }

    private static void requireExact(int affected, int expected) {
        if (affected != expected) {
            throw new IllegalArgumentException("物流报价导出状态已变化，请刷新后重试。");
        }
    }
}
