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

abstract class WarehouseDispatchPlanProjectionSupport extends WarehouseReceiptProjectionSupport {

    protected WarehouseDispatchPlanProjectionSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected DispatchPlanView toDispatchPlanView(DispatchPlanRecord record) {
        DispatchPlanView view = new DispatchPlanView();
        if (record == null) {
            return view;
        }
        view.id = String.valueOf(record.id);
        view.ownerUserId = record.ownerUserId;
        view.planNo = record.planNo;
        view.status = record.status;
        view.itemCount = nonNull(record.itemCount);
        view.skuCount = nonNull(record.skuCount);
        view.totalQuantity = nonNull(record.totalQuantity);
        view.handoffGenerationNo = nonNull(record.handoffGenerationNo);
        view.handoffRequestNo = record.handoffRequestNo;
        view.handoffErrorMessage = record.handoffErrorMessage;
        view.createdAt = record.createdAt;
        view.updatedAt = record.updatedAt;
        List<DispatchPlanLineRecord> lines = emptyIfNull(mapper.listDispatchPlanLines(record.id));
        Map<Long, List<DispatchPlanLineSourceRecord>> sourcesByLine = emptyIfNull(mapper.listDispatchLineSources(record.id)).stream()
                .collect(Collectors.groupingBy(source -> source.dispatchPlanLineId, LinkedHashMap::new, Collectors.toList()));
        for (DispatchPlanLineRecord line : lines) {
            DispatchPlanLineView lineView = toDispatchLineView(line);
            for (DispatchPlanLineSourceRecord source : sourcesByLine.getOrDefault(line.id, List.of())) {
                lineView.sources.add(toDispatchSourceView(source));
            }
            view.lines.add(lineView);
        }
        return view;
    }

protected DispatchPlanLineView toDispatchLineView(DispatchPlanLineRecord line) {
        DispatchPlanLineView view = new DispatchPlanLineView();
        view.id = String.valueOf(line.id);
        view.partnerSku = line.partnerSku;
        view.skuParent = line.skuParent;
        view.productTitle = defaultText(line.titleCache, line.partnerSku);
        view.productImageUrl = ProductImageUrlSupport.normalize(line.imageUrlCache);
        view.siteCode = line.siteCode;
        view.actualTransportMode = normalizeTransportMode(line.actualTransportMode);
        view.fulfillmentType = normalizeFulfillmentType(line.fulfillmentType);
        view.specStatus = defaultText(line.specStatus, "READY");
        view.quantity = nonNull(line.quantity);
        return view;
    }

protected DispatchPlanLineSourceView toDispatchSourceView(DispatchPlanLineSourceRecord source) {
        DispatchPlanLineSourceView view = new DispatchPlanLineSourceView();
        view.id = String.valueOf(source.id);
        view.dispatchPlanId = source.dispatchPlanId;
        view.dispatchPlanLineId = source.dispatchPlanLineId;
        view.fulfillmentBalanceId = source.fulfillmentBalanceId;
        view.sourceStoreCode = source.sourceStoreCode;
        view.sourceStoreName = source.sourceStoreName;
        view.purchaseOrderId = source.purchaseOrderId;
        view.purchaseOrderNo = source.purchaseOrderNo;
        view.purchaseOrderItemId = source.purchaseOrderItemId;
        view.purchaseOrderItemSiteId = source.purchaseOrderItemSiteId;
        view.plannedTransportMode = normalizeTransportMode(source.plannedTransportMode);
        view.fulfillmentType = normalizeFulfillmentType(source.fulfillmentType);
        view.quantity = nonNull(source.quantity);
        return view;
    }
}
