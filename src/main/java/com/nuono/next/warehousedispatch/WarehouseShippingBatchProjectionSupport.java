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

abstract class WarehouseShippingBatchProjectionSupport extends WarehouseShippingSuggestionSupport {

    protected WarehouseShippingBatchProjectionSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected ShippingBatchSourceRecord toShippingBatchSourceRecord(
            Long batchId,
            Long ownerUserId,
            Long sourceId,
            FulfillmentBalanceRecord balance,
            Integer quantity
    ) {
        String fulfillmentType = normalizeFulfillmentType(balance.fulfillmentType);
        ShippingBatchSourceRecord record = new ShippingBatchSourceRecord();
        record.id = sourceId;
        record.batchId = batchId;
        record.ownerUserId = ownerUserId;
        record.fulfillmentBalanceId = balance.id;
        record.sourceStoreCode = balance.sourceStoreCode;
        record.sourceStoreName = balance.sourceStoreName;
        record.purchaseOrderId = balance.purchaseOrderId;
        record.purchaseOrderNo = balance.purchaseOrderNo;
        record.purchaseOrderTitle = balance.purchaseOrderTitle;
        record.purchaseOrderItemId = balance.purchaseOrderItemId;
        record.purchaseOrderItemSiteId = balance.purchaseOrderItemSiteId;
        record.productMasterId = balance.productMasterId;
        record.productVariantId = balance.productVariantId;
        record.partnerSku = balance.partnerSku;
        record.skuParent = balance.skuParent;
        record.titleCache = balance.titleCache;
        record.imageUrlCache = balance.imageUrlCache;
        record.siteCode = balance.siteCode;
        record.plannedTransportMode = normalizeTransportMode(balance.plannedTransportMode);
        record.fulfillmentType = fulfillmentType;
        record.sourcePartyName = fulfillmentSourcePartyName(fulfillmentType, balance);
        record.specStatus = defaultText(balance.specStatus, "READY");
        record.productLengthCm = balance.productLengthCm;
        record.productWidthCm = balance.productWidthCm;
        record.productHeightCm = balance.productHeightCm;
        record.productWeightG = balance.productWeightG;
        record.logisticsProfileStatus = balance.logisticsProfileStatus;
        List<String> sensitiveReasons = sensitiveReasons(balance);
        record.sensitiveFlag = !sensitiveReasons.isEmpty();
        record.sensitiveReasonJson = sensitiveReasons.isEmpty() ? null : writeJson(sensitiveReasons);
        record.reservedQuantity = nonNull(quantity);
        return record;
    }

protected ShippingBatchView toShippingBatchView(ShippingBatchRecord record) {
        ShippingBatchView view = new ShippingBatchView();
        if (record == null) {
            return view;
        }
        view.id = String.valueOf(record.id);
        view.ownerUserId = record.ownerUserId;
        view.batchNo = record.batchNo;
        view.status = record.status;
        view.selectedOptionId = record.selectedOptionId == null ? null : String.valueOf(record.selectedOptionId);
        view.sourceCount = nonNull(record.sourceCount);
        view.skuCount = nonNull(record.skuCount);
        view.totalQuantity = nonNull(record.totalQuantity);
        view.optionCount = nonNull(record.optionCount);
        view.packingListCount = nonNull(record.packingListCount);
        view.boxCount = nonNull(record.boxCount);
        view.packedQuantity = nonNull(record.packedQuantity);
        view.grossWeightKg = record.grossWeightKg;
        view.volumeCbm = record.volumeCbm;
        view.remark = record.remark;
        view.createdAt = record.createdAt;
        view.updatedAt = record.updatedAt;
        return view;
    }

protected ShippingBatchView toShippingBatchDetail(ShippingBatchRecord record) {
        ShippingBatchView view = toShippingBatchView(record);
        for (ShippingBatchSourceRecord source : emptyIfNull(mapper.listShippingBatchSources(record.id))) {
            view.sources.add(toShippingBatchSourceView(source));
        }
        Map<Long, List<ShippingSuggestionLineRecord>> linesByOption = emptyIfNull(mapper.listShippingSuggestionLines(record.id)).stream()
                .collect(Collectors.groupingBy(line -> line.optionId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<ShippingSuggestionLineSourceRecord>> sourcesByLine = emptyIfNull(mapper.listShippingSuggestionLineSources(record.id)).stream()
                .collect(Collectors.groupingBy(source -> source.lineId, LinkedHashMap::new, Collectors.toList()));
        for (ShippingSuggestionOptionRecord option : emptyIfNull(mapper.listShippingSuggestionOptions(record.id))) {
            ShippingSuggestionOptionView optionView = toShippingSuggestionOptionView(option);
            for (ShippingSuggestionLineRecord line : linesByOption.getOrDefault(option.id, List.of())) {
                ShippingSuggestionLineView lineView = toShippingSuggestionLineView(line);
                for (ShippingSuggestionLineSourceRecord source : sourcesByLine.getOrDefault(line.id, List.of())) {
                    lineView.sources.add(toShippingSuggestionLineSourceView(source));
                }
                optionView.lines.add(lineView);
            }
            view.options.add(optionView);
        }
        return view;
    }

protected ShippingBatchSourceView toShippingBatchSourceView(ShippingBatchSourceRecord source) {
        ShippingBatchSourceView view = new ShippingBatchSourceView();
        view.id = String.valueOf(source.id);
        view.batchId = source.batchId;
        view.fulfillmentBalanceId = source.fulfillmentBalanceId;
        view.sourceStoreCode = source.sourceStoreCode;
        view.sourceStoreName = source.sourceStoreName;
        view.purchaseOrderId = source.purchaseOrderId;
        view.purchaseOrderNo = source.purchaseOrderNo;
        view.purchaseOrderTitle = source.purchaseOrderTitle;
        view.purchaseOrderItemId = source.purchaseOrderItemId;
        view.purchaseOrderItemSiteId = source.purchaseOrderItemSiteId;
        view.productVariantId = source.productVariantId;
        view.partnerSku = source.partnerSku;
        view.skuParent = source.skuParent;
        view.productTitle = defaultText(source.titleCache, source.partnerSku);
        view.productImageUrl = ProductImageUrlSupport.normalize(source.imageUrlCache);
        view.siteCode = source.siteCode;
        view.plannedTransportMode = normalizeTransportMode(source.plannedTransportMode);
        view.fulfillmentType = normalizeFulfillmentType(source.fulfillmentType);
        view.sourcePartyName = source.sourcePartyName;
        view.specStatus = defaultText(source.specStatus, "READY");
        view.productLengthCm = source.productLengthCm == null ? null : source.productLengthCm.toPlainString();
        view.productWidthCm = source.productWidthCm == null ? null : source.productWidthCm.toPlainString();
        view.productHeightCm = source.productHeightCm == null ? null : source.productHeightCm.toPlainString();
        view.productWeightG = source.productWeightG == null ? null : source.productWeightG.toPlainString();
        view.logisticsProfileStatus = source.logisticsProfileStatus;
        view.sensitiveFlag = Boolean.TRUE.equals(source.sensitiveFlag);
        view.sensitiveReasons.addAll(readJsonStringList(source.sensitiveReasonJson));
        view.logisticsQuoteStatus = normalizeLogisticsQuoteStatus(source.logisticsQuoteStatus);
        view.logisticsShippingSubmitStatus = normalizeShippingSubmitStatus(source.logisticsShippingSubmitStatus);
        view.logisticsQuoteBlocking = Boolean.TRUE.equals(source.logisticsQuoteBlocking);
        view.reservedQuantity = nonNull(source.reservedQuantity);
        return view;
    }
}
