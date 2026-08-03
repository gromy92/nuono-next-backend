package com.nuono.next.procurementorder;

import static com.nuono.next.procurementorder.WarehouseShippingQuoteChannelIdentity.applyChannel;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import java.util.List;
import org.springframework.util.StringUtils;

final class WarehouseLogisticsQuoteSelectionPersistence {

    private WarehouseLogisticsQuoteSelectionPersistence() {
    }

    static void persist(
            ProcurementPurchaseOrderMapper mapper,
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            ForwarderRouteRecommendationRecord candidate,
            Long operatorUserId
    ) {
        for (PurchaseOrderLogisticsQuoteLineRecord line : lines) {
            persistExact(mapper, line, line, candidate, operatorUserId);
        }
    }

    static void persistExact(
            ProcurementPurchaseOrderMapper mapper,
            PurchaseOrderLogisticsQuoteLineRecord target,
            PurchaseOrderLogisticsQuoteLineRecord current,
            ForwarderRouteRecommendationRecord candidate,
            Long operatorUserId
    ) {
        boolean sameChannel = WarehouseShippingQuoteChannelIdentity.matches(target, candidate);
        applyChannel(target, candidate.forwarderCode, candidate.forwarderName,
                candidate.routeCode, candidate.routeName, candidate.serviceCode, candidate.serviceName);
        target.unitPrice = WarehouseLogisticsQuoteAvailability.hasUsablePrice(current) ? current.unitPrice : null;
        target.currency = target.unitPrice == null ? null : current.currency;
        target.billingUnit = target.unitPrice == null ? null : current.billingUnit;
        target.estimatedAmount = null;
        target.remark = sameChannel ? target.remark : null;
        target.quoteStatus = WarehouseLogisticsQuoteAvailability.statusFor(target.unitPrice);
        if (StringUtils.hasText(current.yiteMaterial)) {
            target.yiteMaterial = current.yiteMaterial;
        }
        if (mapper.persistLogisticsQuoteLineSelection(target, operatorUserId) != 1) {
            throw new IllegalArgumentException("物流报价选择已变化，请刷新后重试。");
        }
    }
}
