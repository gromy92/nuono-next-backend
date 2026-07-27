package com.nuono.next.procurementorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductForwarderChannelQuoteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WarehouseShippingQuoteProjectionService {

    private final ProcurementPurchaseOrderMapper mapper;
    private final WarehouseLogisticsQuotePriceService priceService;
    private final ObjectMapper objectMapper;

    public WarehouseShippingQuoteProjectionService(
            ProcurementPurchaseOrderMapper mapper,
            WarehouseLogisticsQuotePriceService priceService,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.priceService = priceService;
        this.objectMapper = objectMapper;
    }

    void persistConfirmedQuote(
            PurchaseOrderLogisticsQuoteLineRecord line,
            Long operatorUserId,
            String sourceFilename,
            String sourceType
    ) {
        persistProductCurrentPrice(line, operatorUserId, sourceFilename, sourceType);
        priceService.syncConfirmedQuote(line, operatorUserId, sourceType);
    }

    private void persistProductCurrentPrice(
            PurchaseOrderLogisticsQuoteLineRecord line,
            Long operatorUserId,
            String sourceFilename,
            String sourceType
    ) {
        if (line == null
                || line.ownerUserId == null
                || line.productVariantId == null
                || line.unitPrice == null
                || line.unitPrice.signum() <= 0
                || !StringUtils.hasText(line.forwarderCode)) {
            return;
        }
        ProductForwarderChannelQuoteRecord quote = new ProductForwarderChannelQuoteRecord();
        quote.id = mapper.nextProductForwarderChannelQuoteId();
        quote.ownerUserId = line.ownerUserId;
        quote.productMasterId = line.productMasterId;
        quote.productVariantId = line.productVariantId;
        quote.logicalStoreId = line.logicalStoreId;
        quote.sourceStoreCode = line.sourceStoreCode;
        quote.partnerSku = line.partnerSku;
        quote.barcode = trim(line.barcode);
        quote.forwarderCode = trim(line.forwarderCode);
        quote.forwarderName = trim(line.forwarderName);
        quote.routeCode = trim(line.routeCode);
        quote.routeName = trim(line.routeName);
        quote.serviceCode = trim(line.serviceCode);
        quote.serviceName = trim(line.serviceName);
        quote.siteCode = trim(line.siteCode);
        quote.transportMode = trim(line.plannedTransportMode);
        quote.currency = trim(line.currency);
        quote.unitPrice = line.unitPrice;
        quote.billingUnit = defaultText(line.billingUnit, "UNKNOWN");
        quote.estimatedAmount = line.estimatedAmount;
        quote.sourceType = defaultText(sourceType, "SHIPPING_ORDER_QUOTE");
        quote.sourceShippingOrderId = line.shippingOrderId;
        quote.sourceShippingOrderLineId = line.shippingOrderLineId;
        quote.sourceQuoteLineId = line.id;
        quote.sourceFilename = trim(sourceFilename);
        quote.effectiveStatus = "CURRENT";
        quote.rawSnapshotJson = snapshot(line);
        mapper.markHistoricalProductForwarderChannelQuote(
                quote.ownerUserId,
                quote.sourceStoreCode,
                quote.logicalStoreId,
                quote.partnerSku,
                quote.productVariantId,
                quote.forwarderCode,
                quote.siteCode,
                quote.routeCode,
                quote.serviceCode,
                quote.billingUnit,
                operatorUserId
        );
        mapper.insertProductForwarderChannelQuote(quote, operatorUserId);
    }

    String snapshot(PurchaseOrderLogisticsQuoteLineRecord line) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("quoteLineId", line.id);
        snapshot.put("shippingOrderId", line.shippingOrderId);
        snapshot.put("shippingOrderNo", line.shippingOrderNo);
        snapshot.put("purchaseOrderId", line.purchaseOrderId);
        snapshot.put("purchaseOrderNo", line.purchaseOrderNo);
        snapshot.put("purchaseOrderItemSiteId", line.purchaseOrderItemSiteId);
        snapshot.put("barcode", line.barcode);
        snapshot.put("pskuCode", line.pskuCode);
        snapshot.put("siteCode", line.siteCode);
        snapshot.put("quantity", line.quantity);
        snapshot.put("forwarderCode", line.forwarderCode);
        snapshot.put("routeCode", line.routeCode);
        snapshot.put("serviceCode", line.serviceCode);
        snapshot.put("currency", line.currency);
        snapshot.put("unitPrice", line.unitPrice);
        snapshot.put("billingUnit", line.billingUnit);
        snapshot.put("estimatedAmount", line.estimatedAmount);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
