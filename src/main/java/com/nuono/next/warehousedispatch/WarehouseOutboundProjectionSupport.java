package com.nuono.next.warehousedispatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.product.ProductImageUrlSupport;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.*;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.*;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

abstract class WarehouseOutboundProjectionSupport extends WarehouseShippingOptionProjectionSupport {

    protected WarehouseOutboundProjectionSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected OutboundOrderLineRecord toOutboundOrderLineRecord(
            Long outboundOrderId,
            Long batchId,
            Long ownerUserId,
            Long outboundOrderLineId,
            ShippingSuggestionLineRecord suggestionLine
    ) {
        OutboundOrderLineRecord record = new OutboundOrderLineRecord();
        record.id = outboundOrderLineId;
        record.outboundOrderId = outboundOrderId;
        record.batchId = batchId;
        record.optionLineId = suggestionLine.id;
        record.ownerUserId = ownerUserId;
        record.productMasterId = suggestionLine.productMasterId;
        record.productVariantId = suggestionLine.productVariantId;
        record.partnerSku = suggestionLine.partnerSku;
        record.skuParent = suggestionLine.skuParent;
        record.titleCache = suggestionLine.titleCache;
        record.imageUrlCache = suggestionLine.imageUrlCache;
        record.siteCode = suggestionLine.siteCode;
        record.actualTransportMode = normalizeTransportMode(suggestionLine.actualTransportMode);
        record.fulfillmentType = normalizeFulfillmentType(suggestionLine.fulfillmentType);
        record.sourcePartyName = suggestionLine.sourcePartyName;
        record.specStatus = defaultText(suggestionLine.specStatus, "READY");
        record.quantity = nonNull(suggestionLine.quantity);
        record.packedQuantity = 0;
        return record;
    }

protected OutboundOrderLineSourceRecord toOutboundOrderLineSourceRecord(
            Long outboundOrderId,
            Long outboundOrderLineId,
            Long outboundOrderLineSourceId,
            ShippingSuggestionLineSourceRecord suggestionSource,
            ShippingBatchSourceRecord batchSource
    ) {
        if (batchSource == null) {
            throw new IllegalArgumentException("货运计划方案包含无效来源。");
        }
        OutboundOrderLineSourceRecord record = new OutboundOrderLineSourceRecord();
        record.id = outboundOrderLineSourceId;
        record.outboundOrderId = outboundOrderId;
        record.outboundOrderLineId = outboundOrderLineId;
        record.batchSourceId = suggestionSource.batchSourceId;
        record.fulfillmentBalanceId = suggestionSource.fulfillmentBalanceId;
        record.sourceStoreCode = batchSource.sourceStoreCode;
        record.sourceStoreName = batchSource.sourceStoreName;
        record.purchaseOrderId = batchSource.purchaseOrderId;
        record.purchaseOrderNo = batchSource.purchaseOrderNo;
        record.purchaseOrderTitle = batchSource.purchaseOrderTitle;
        record.purchaseOrderItemId = batchSource.purchaseOrderItemId;
        record.purchaseOrderItemSiteId = batchSource.purchaseOrderItemSiteId;
        record.plannedTransportMode = normalizeTransportMode(suggestionSource.plannedTransportMode);
        record.quantity = nonNull(suggestionSource.quantity);
        return record;
    }

protected OutboundOrderView toOutboundOrderView(OutboundOrderRecord order) {
        OutboundOrderView view = new OutboundOrderView();
        view.id = String.valueOf(order.id);
        view.batchId = order.batchId;
        view.optionId = order.optionId;
        view.ownerUserId = order.ownerUserId;
        view.outboundNo = order.outboundNo;
        view.status = order.status;
        view.originType = normalizeFulfillmentType(order.originType);
        view.originName = order.originName;
        view.skuCount = nonNull(order.skuCount);
        view.totalQuantity = nonNull(order.totalQuantity);
        view.remark = order.remark;
        view.createdAt = order.createdAt;
        view.updatedAt = order.updatedAt;
        return view;
    }

    protected OutboundOrderView toOutboundOrderDetail(OutboundOrderRecord order) {
        OutboundOrderView view = toOutboundOrderView(order);
        Map<Long, List<OutboundOrderLineSourceRecord>> sourcesByLine = emptyIfNull(mapper.listOutboundOrderLineSources(order.id)).stream()
                .collect(Collectors.groupingBy(source -> source.outboundOrderLineId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, ShippingSuggestionLineRecord> suggestionById = emptyIfNull(mapper.listShippingSuggestionLines(order.batchId)).stream()
                .collect(Collectors.toMap(line -> line.id, line -> line, (left, right) -> left, LinkedHashMap::new));
        for (OutboundOrderLineRecord line : emptyIfNull(mapper.listOutboundOrderLines(order.id))) {
            OutboundOrderLineView lineView = toOutboundOrderLineView(line);
            applyOutboundLogisticsAssignment(lineView, suggestionById.get(line.optionLineId));
            for (OutboundOrderLineSourceRecord source : sourcesByLine.getOrDefault(line.id, List.of())) {
                lineView.sources.add(toOutboundOrderLineSourceView(source));
            }
            hydrateOutboundLineStoreScope(lineView);
            view.lines.add(lineView);
        }
        return view;
    }

    protected void applyOutboundLogisticsAssignment(
            OutboundOrderLineView view,
            ShippingSuggestionLineRecord suggestion
    ) {
        if (suggestion == null) {
            return;
        }
        ShippingSuggestionLineView assignment = toShippingSuggestionLineView(suggestion);
        view.targetForwarderCode = assignment.targetForwarderCode;
        view.targetForwarderName = assignment.targetForwarderName;
        view.routeCode = assignment.routeCode;
        view.routeName = assignment.routeName;
        view.cargoCategoryCode = assignment.cargoCategoryCode;
        view.cargoCategoryName = assignment.cargoCategoryName;
        view.quoteCargoCategoryCode = assignment.quoteCargoCategoryCode;
        view.quoteCargoCategoryName = assignment.quoteCargoCategoryName;
        view.packingGroupCode = shippingPackingGroupCode(suggestion);
        view.packingGroupName = shippingPackingGroupName(suggestion);
    }

protected OutboundOrderLineView toOutboundOrderLineView(OutboundOrderLineRecord line) {
        OutboundOrderLineView view = new OutboundOrderLineView();
        view.id = String.valueOf(line.id);
        view.outboundOrderId = line.outboundOrderId;
        view.batchId = line.batchId;
        view.optionLineId = line.optionLineId;
        view.productVariantId = line.productVariantId;
        view.logicalStoreId = line.logicalStoreId;
        view.storeCode = line.storeCode;
        view.partnerSku = line.partnerSku;
        view.skuParent = line.skuParent;
        view.productTitle = defaultText(line.titleCache, line.partnerSku);
        view.productImageUrl = ProductImageUrlSupport.normalize(line.imageUrlCache);
        view.siteCode = line.siteCode;
        view.actualTransportMode = normalizeTransportMode(line.actualTransportMode);
        view.fulfillmentType = normalizeFulfillmentType(line.fulfillmentType);
        view.sourcePartyName = line.sourcePartyName;
        view.specStatus = defaultText(line.specStatus, "READY");
        view.quantity = nonNull(line.quantity);
        view.packedQuantity = nonNull(line.packedQuantity);
        return view;
    }

protected OutboundOrderLineSourceView toOutboundOrderLineSourceView(OutboundOrderLineSourceRecord source) {
        OutboundOrderLineSourceView view = new OutboundOrderLineSourceView();
        view.id = String.valueOf(source.id);
        view.outboundOrderId = source.outboundOrderId;
        view.outboundOrderLineId = source.outboundOrderLineId;
        view.batchSourceId = source.batchSourceId;
        view.fulfillmentBalanceId = source.fulfillmentBalanceId;
        view.logicalStoreId = source.logicalStoreId;
        view.sourceStoreCode = source.sourceStoreCode;
        view.sourceStoreName = source.sourceStoreName;
        view.purchaseOrderId = source.purchaseOrderId;
        view.purchaseOrderNo = source.purchaseOrderNo;
        view.purchaseOrderTitle = source.purchaseOrderTitle;
        view.purchaseOrderItemId = source.purchaseOrderItemId;
        view.purchaseOrderItemSiteId = source.purchaseOrderItemSiteId;
        view.plannedTransportMode = normalizeTransportMode(source.plannedTransportMode);
        view.quantity = nonNull(source.quantity);
        return view;
    }

protected void hydrateOutboundLineStoreScope(OutboundOrderLineView lineView) {
        for (OutboundOrderLineSourceView source : emptyIfNull(lineView.sources)) {
            if (lineView.logicalStoreId == null && source.logicalStoreId != null) {
                lineView.logicalStoreId = source.logicalStoreId;
            }
            if (!StringUtils.hasText(lineView.storeCode) && StringUtils.hasText(source.sourceStoreCode)) {
                lineView.storeCode = source.sourceStoreCode;
            }
            if (lineView.logicalStoreId != null && StringUtils.hasText(lineView.storeCode)) {
                return;
            }
        }
    }
}
