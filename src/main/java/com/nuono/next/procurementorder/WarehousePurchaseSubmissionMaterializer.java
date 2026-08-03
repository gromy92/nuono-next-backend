package com.nuono.next.procurementorder;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

final class WarehousePurchaseSubmissionMaterializer {

    private WarehousePurchaseSubmissionMaterializer() {
    }

    static void materialize(
            ProcurementPurchaseOrderMapper mapper,
            WarehouseLogisticsQuotePriceService priceService,
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            Long operatorUserId
    ) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("当前采购单没有可提交发货的报价行。");
        }
        for (PurchaseOrderLogisticsQuoteLineRecord line : lines) {
            WarehouseShippingQuoteSnapshotRefresher.requireNotSubmitted(line);
            if (line.id == null || !StringUtils.hasText(line.forwarderCode)
                    || !StringUtils.hasText(line.routeCode)) {
                throw new IllegalArgumentException("请先选择并保存物流渠道。");
            }
        }
        Map<PurchaseOrderLogisticsQuoteLineRecord, ForwarderRouteRecommendationRecord> candidates =
                WarehouseCurrentRouteVerifier.requirePurchase(mapper, lines);
        Map<PurchaseOrderLogisticsQuoteLineRecord, PurchaseOrderLogisticsQuoteChannelLineView> prices =
                new IdentityHashMap<>();
        for (PurchaseOrderLogisticsQuoteLineRecord line : lines) {
            prices.put(line, priceService.resolve(line, candidates.get(line), line));
        }
        for (PurchaseOrderLogisticsQuoteLineRecord line : lines) {
            WarehouseLogisticsQuoteAvailability.applyResolvedPrice(line, prices.get(line));
            WarehouseShippingQuoteSnapshotRefresher.confirm(mapper, line, operatorUserId);
        }
    }
}
