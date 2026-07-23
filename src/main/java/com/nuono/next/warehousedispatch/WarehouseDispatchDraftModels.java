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

final class PendingDispatchLine {
    final FulfillmentBalanceRecord firstBalance;
    final String actualTransportMode;
    final String fulfillmentType;
    final String specStatus;
    final List<PendingDispatchSource> sources = new ArrayList<>();

    PendingDispatchLine(
            FulfillmentBalanceRecord firstBalance,
            String actualTransportMode,
            String fulfillmentType,
            String specStatus
    ) {
        this.firstBalance = firstBalance;
        this.actualTransportMode = actualTransportMode;
        this.fulfillmentType = fulfillmentType;
        this.specStatus = specStatus;
    }

    DispatchPlanLineRecord toRecord(Long dispatchPlanId, Long ownerUserId, Long lineId) {
        DispatchPlanLineRecord record = new DispatchPlanLineRecord();
        record.id = lineId;
        record.dispatchPlanId = dispatchPlanId;
        record.ownerUserId = ownerUserId;
        record.productMasterId = firstBalance.productMasterId;
        record.productVariantId = firstBalance.productVariantId;
        record.partnerSku = firstBalance.partnerSku;
        record.skuParent = firstBalance.skuParent;
        record.titleCache = firstBalance.titleCache;
        record.imageUrlCache = firstBalance.imageUrlCache;
        record.siteCode = firstBalance.siteCode;
        record.actualTransportMode = actualTransportMode;
        record.fulfillmentType = fulfillmentType;
        record.specStatus = specStatus;
        record.quantity = sources.stream().mapToInt(source -> source.quantity).sum();
        record.sourceCount = sources.size();
        return record;
    }
}

final class PendingDispatchSource {
    final FulfillmentBalanceRecord balance;
    final Integer quantity;

    PendingDispatchSource(FulfillmentBalanceRecord balance, Integer quantity) {
        this.balance = balance;
        this.quantity = quantity;
    }

    DispatchPlanLineSourceRecord toRecord(
            Long dispatchPlanId,
            Long dispatchPlanLineId,
            Long ownerUserId,
            Long sourceId,
            String fulfillmentType
    ) {
        DispatchPlanLineSourceRecord record = new DispatchPlanLineSourceRecord();
        record.id = sourceId;
        record.dispatchPlanId = dispatchPlanId;
        record.dispatchPlanLineId = dispatchPlanLineId;
        record.ownerUserId = ownerUserId;
        record.fulfillmentBalanceId = balance.id;
        record.sourceStoreCode = balance.sourceStoreCode;
        record.sourceStoreName = balance.sourceStoreName;
        record.purchaseOrderId = balance.purchaseOrderId;
        record.purchaseOrderNo = balance.purchaseOrderNo;
        record.purchaseOrderItemId = balance.purchaseOrderItemId;
        record.purchaseOrderItemSiteId = balance.purchaseOrderItemSiteId;
        record.plannedTransportMode = balance.plannedTransportMode;
        record.fulfillmentType = fulfillmentType;
        record.quantity = quantity;
        return record;
    }
}
