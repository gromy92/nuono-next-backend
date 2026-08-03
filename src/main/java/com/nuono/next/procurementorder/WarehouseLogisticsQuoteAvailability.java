package com.nuono.next.procurementorder;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import java.math.BigDecimal;

final class WarehouseLogisticsQuoteAvailability {

    static final String AVAILABLE = "CONFIRMED";
    static final String MISSING = "PENDING_QUOTE";

    private WarehouseLogisticsQuoteAvailability() {
    }

    static boolean hasUsablePrice(PurchaseOrderLogisticsQuoteLineRecord line) {
        return line != null && hasUsablePrice(line.unitPrice);
    }

    static boolean hasUsablePrice(PurchaseOrderLogisticsQuoteChannelLineView line) {
        return line != null && hasUsablePrice(line.unitPrice);
    }

    static boolean hasUsablePrice(BigDecimal unitPrice) {
        return unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0;
    }

    static String statusFor(BigDecimal unitPrice) {
        return hasUsablePrice(unitPrice) ? AVAILABLE : MISSING;
    }

    static void applyResolvedPrice(
            PurchaseOrderLogisticsQuoteLineRecord target,
            PurchaseOrderLogisticsQuoteChannelLineView resolved
    ) {
        if (target == null || resolved == null) {
            return;
        }
        target.unitPrice = hasUsablePrice(resolved) ? resolved.unitPrice : null;
        target.currency = hasUsablePrice(resolved) ? resolved.currency : null;
        target.billingUnit = hasUsablePrice(resolved) ? resolved.billingUnit : null;
        target.estimatedAmount = null;
        target.quoteStatus = statusFor(target.unitPrice);
        if (resolved.yiteMaterial != null && !resolved.yiteMaterial.trim().isEmpty()) {
            target.yiteMaterial = resolved.yiteMaterial;
        }
    }
}
