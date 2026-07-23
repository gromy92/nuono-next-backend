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

final class PendingPackingBox {
    final String boxNo;
    final String status;
    final BigDecimal lengthCm;
    final BigDecimal widthCm;
    final BigDecimal heightCm;
    final BigDecimal grossWeightKg;
    final List<PendingPackingItem> items = new ArrayList<>();

    PendingPackingBox(
            String boxNo,
            String status,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            BigDecimal grossWeightKg
    ) {
        this.boxNo = boxNo;
        this.status = status;
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
        this.grossWeightKg = grossWeightKg;
    }

    int quantity() {
        return items.stream().mapToInt(item -> item.quantity).sum();
    }

    BigDecimal volumeCbm() {
        if (lengthCm == null || widthCm == null || heightCm == null) {
            return BigDecimal.ZERO;
        }
        return lengthCm.multiply(widthCm)
                .multiply(heightCm)
                .divide(BigDecimal.valueOf(1_000_000L), 8, RoundingMode.HALF_UP);
    }

    PackingBoxRecord toRecord(
            Long packingBoxId,
            Long packingListId,
            Long outboundOrderId,
            Long ownerUserId
    ) {
        PackingBoxRecord record = new PackingBoxRecord();
        record.id = packingBoxId;
        record.packingListId = packingListId;
        record.outboundOrderId = outboundOrderId;
        record.ownerUserId = ownerUserId;
        record.boxNo = boxNo;
        record.status = status;
        record.lengthCm = lengthCm;
        record.widthCm = widthCm;
        record.heightCm = heightCm;
        record.grossWeightKg = grossWeightKg;
        record.quantity = quantity();
        return record;
    }
}

final class PendingPackingItem {
    final Long outboundOrderLineId;
    final Integer quantity;

    PendingPackingItem(Long outboundOrderLineId, Integer quantity) {
        this.outboundOrderLineId = outboundOrderLineId;
        this.quantity = quantity;
    }

    PackingBoxItemRecord toRecord(
            Long packingBoxItemId,
            Long packingListId,
            Long packingBoxId,
            Long outboundOrderId,
            Long ownerUserId,
            OutboundOrderLineRecord outboundLine
    ) {
        PackingBoxItemRecord record = new PackingBoxItemRecord();
        record.id = packingBoxItemId;
        record.packingListId = packingListId;
        record.packingBoxId = packingBoxId;
        record.outboundOrderId = outboundOrderId;
        record.outboundOrderLineId = outboundOrderLineId;
        record.ownerUserId = ownerUserId;
        record.productVariantId = outboundLine.productVariantId;
        record.partnerSku = outboundLine.partnerSku;
        record.siteCode = outboundLine.siteCode;
        record.actualTransportMode = outboundLine.actualTransportMode;
        record.quantity = quantity;
        return record;
    }
}
