package com.nuono.next.procurementorder;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import java.util.List;
import java.util.stream.Collectors;

final class WarehouseLogisticsQuoteMutationScope {

    private WarehouseLogisticsQuoteMutationScope() {
    }

    static List<PurchaseOrderLogisticsQuoteLineRecord> mutableOnly(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines
    ) {
        return (lines == null ? List.<PurchaseOrderLogisticsQuoteLineRecord>of() : lines).stream()
                .filter(line -> line != null
                        && "NOT_SUBMITTED".equalsIgnoreCase(line.shippingSubmitStatus == null
                                ? "" : line.shippingSubmitStatus.trim()))
                .collect(Collectors.toList());
    }
}
