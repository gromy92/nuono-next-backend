package com.nuono.next.procurementorder;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import java.util.List;

final class WarehouseShippingQuoteSnapshotRefresher {

    private WarehouseShippingQuoteSnapshotRefresher() {
    }

    static PurchaseOrderLogisticsQuoteLineRecord refresh(
            ProcurementPurchaseOrderMapper mapper,
            PurchaseOrderLogisticsQuoteLineRecord existing,
            PurchaseOrderLogisticsQuoteLineRecord currentBase,
            Long operatorUserId
    ) {
        PurchaseOrderLogisticsQuoteLineRecord rebound = rebind(existing, currentBase);
        if (mapper.refreshLogisticsQuoteLineSnapshot(rebound, operatorUserId) != 1) {
            throw new IllegalArgumentException("物流报价商品快照已变化，请刷新后重试。");
        }
        return rebound;
    }

    static void refreshUnlessSubmitted(
            ProcurementPurchaseOrderMapper mapper,
            PurchaseOrderLogisticsQuoteLineRecord line,
            Long operatorUserId
    ) {
        if (line != null && "SUBMITTED".equalsIgnoreCase(line.shippingSubmitStatus)) {
            return;
        }
        refresh(mapper, line, line, operatorUserId);
    }

    static PurchaseOrderLogisticsQuoteLineRecord rebind(
            PurchaseOrderLogisticsQuoteLineRecord existing,
            PurchaseOrderLogisticsQuoteLineRecord currentBase
    ) {
        if (existing == null || existing.id == null || currentBase == null) {
            throw new IllegalArgumentException("物流报价商品不存在或已删除。");
        }
        PurchaseOrderLogisticsQuoteLineRecord rebound = WarehouseLogisticsQuoteLineFactory.copyOf(currentBase);
        rebound.id = existing.id;
        rebound.quoteStatus = existing.quoteStatus;
        rebound.shippingSubmitStatus = existing.shippingSubmitStatus;
        rebound.forwarderCode = existing.forwarderCode;
        rebound.forwarderName = existing.forwarderName;
        rebound.routeCode = existing.routeCode;
        rebound.routeName = existing.routeName;
        rebound.serviceCode = existing.serviceCode;
        rebound.serviceName = existing.serviceName;
        rebound.currency = existing.currency;
        rebound.unitPrice = existing.unitPrice;
        rebound.billingUnit = existing.billingUnit;
        rebound.estimatedAmount = existing.estimatedAmount;
        rebound.remark = existing.remark;
        rebound.exportedAt = existing.exportedAt;
        rebound.confirmedAt = existing.confirmedAt;
        rebound.shippingSubmittedAt = existing.shippingSubmittedAt;
        return rebound;
    }

    static PurchaseOrderLogisticsQuoteLineRecord selectForImport(
            ProcurementPurchaseOrderMapper mapper,
            Long documentId,
            boolean shippingOrder,
            Long quoteLineId,
            Long itemSiteId
    ) {
        PurchaseOrderLogisticsQuoteLineRecord selected = quoteLineId == null
                ? (shippingOrder ? null : mapper.selectLogisticsQuoteLineByItemSiteForUpdate(documentId, itemSiteId))
                : mapper.selectLogisticsQuoteLineByDocumentLineForUpdate(documentId, quoteLineId, itemSiteId);
        if (selected != null) {
            requireNotSubmitted(selected);
        }
        if (selected == null || !shippingOrder) {
            return selected;
        }
        PurchaseOrderLogisticsQuoteLineRecord currentBase = safe(
                mapper.listLogisticsQuoteCandidatesByShippingOrder(documentId)
        ).stream()
                .filter(line -> itemSiteId.equals(line.purchaseOrderItemSiteId))
                .findFirst()
                .orElse(null);
        return currentBase == null ? null : rebind(selected, currentBase);
    }

    static void confirm(
            ProcurementPurchaseOrderMapper mapper,
            PurchaseOrderLogisticsQuoteLineRecord line,
            Long operatorUserId
    ) {
        requireNotSubmitted(line);
        if (mapper.confirmLogisticsQuoteLine(line, operatorUserId) != 1) {
            throw new IllegalArgumentException("物流报价状态已变化，请刷新后重试。");
        }
    }

    static void requireNotSubmitted(PurchaseOrderLogisticsQuoteLineRecord line) {
        if (line == null || !"NOT_SUBMITTED".equals(line.shippingSubmitStatus)) {
            throw new IllegalArgumentException("物流报价已提交仓库，不能再次导入或修改。");
        }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
