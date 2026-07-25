package com.nuono.next.procurementorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductLogisticsCostMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CostHistoryRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CurrentCostRow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WarehouseProductLogisticsPriceBridge {

    static final String HEADHAUL = "HEADHAUL";
    static final String CURRENT_QUOTE = "CURRENT_QUOTE";

    private final ProductLogisticsCostMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public WarehouseProductLogisticsPriceBridge(
            ProductLogisticsCostMapper mapper,
            ObjectMapper objectMapper
    ) {
        this(mapper, objectMapper, Clock.systemDefaultZone());
    }

    WarehouseProductLogisticsPriceBridge(
            ProductLogisticsCostMapper mapper,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public CurrentCostRow findCurrentCost(
            PurchaseOrderLogisticsQuoteLineRecord line,
            String forwarderCode
    ) {
        if (!hasBusinessKey(line) || !StringUtils.hasText(forwarderCode)) {
            return null;
        }
        List<CurrentCostRow> rows = mapper.listCurrentCosts(
                line.ownerUserId,
                line.logicalStoreId,
                null,
                line.partnerSku,
                normalize(line.siteCode),
                normalize(forwarderCode),
                normalize(line.plannedTransportMode),
                20
        );
        return rows == null ? null : rows.stream()
                .filter(row -> HEADHAUL.equalsIgnoreCase(defaultText(row.feeType, HEADHAUL)))
                .findFirst()
                .orElse(null);
    }

    public void syncConfirmedQuote(
            PurchaseOrderLogisticsQuoteLineRecord line,
            Long operatorUserId,
            String sourceType
    ) {
        if (!canSync(line, operatorUserId)) {
            return;
        }
        CurrentCostRow existing = findCurrentCost(line, line.forwarderCode);
        LocalDateTime occurredAt = LocalDateTime.now(clock);
        String normalizedSourceType = defaultText(sourceType, "SHIPPING_ORDER_QUOTE");
        String evidenceJson = evidenceJson(line, normalizedSourceType);

        CostHistoryRow history = history(line, existing, normalizedSourceType, occurredAt, evidenceJson);
        history.id = mapper.nextProductLogisticsCostHistoryId();
        mapper.insertCostHistory(history, operatorUserId);

        CurrentCostRow current = current(line, existing, normalizedSourceType, occurredAt, evidenceJson);
        current.id = mapper.nextProductLogisticsCurrentCostId();
        current.currentHistoryId = history.id;
        mapper.upsertCurrentCost(current, operatorUserId);
    }

    private CostHistoryRow history(
            PurchaseOrderLogisticsQuoteLineRecord line,
            CurrentCostRow existing,
            String sourceType,
            LocalDateTime occurredAt,
            String evidenceJson
    ) {
        CostHistoryRow row = new CostHistoryRow();
        copyIdentityAndChannel(line, row);
        row.sourceType = sourceType;
        row.costType = CURRENT_QUOTE;
        row.sourceShippingOrderId = line.shippingOrderId;
        row.sourceQuoteLineId = line.id;
        row.feeType = HEADHAUL;
        row.rawFeeName = "仓库发运确认报价";
        row.cargoCategoryCode = existing == null ? null : existing.cargoCategoryCode;
        row.cargoCategoryName = existing == null ? null : existing.cargoCategoryName;
        row.quantity = line.quantity == null ? null : BigDecimal.valueOf(line.quantity);
        row.chargeUnit = normalize(line.billingUnit);
        row.unitCost = line.unitPrice;
        row.currencyCode = "CNY";
        row.exchangeRateToCny = BigDecimal.ONE;
        row.unitCostCny = line.unitPrice;
        row.allocationBasis = "warehouse_confirmed_unit_price";
        row.confidenceLevel = "MANUAL";
        row.costOccurredAt = occurredAt;
        row.idempotencyKey = String.join(":",
                sourceType,
                String.valueOf(line.ownerUserId),
                String.valueOf(line.shippingOrderId),
                String.valueOf(line.id),
                UUID.randomUUID().toString()
        );
        row.evidenceJson = evidenceJson;
        row.rawSnapshotJson = evidenceJson;
        row.reviewStatus = "ACCEPTED";
        return row;
    }

    private CurrentCostRow current(
            PurchaseOrderLogisticsQuoteLineRecord line,
            CurrentCostRow existing,
            String sourceType,
            LocalDateTime occurredAt,
            String evidenceJson
    ) {
        CurrentCostRow row = new CurrentCostRow();
        row.ownerUserId = line.ownerUserId;
        row.logicalStoreId = line.logicalStoreId;
        row.productMasterId = line.productMasterId;
        row.productVariantId = line.productVariantId;
        row.partnerSku = line.partnerSku;
        row.barcode = line.barcode;
        row.siteCode = normalize(line.siteCode);
        row.forwarderCode = normalize(line.forwarderCode);
        row.forwarderName = line.forwarderName;
        row.transportMode = normalize(line.plannedTransportMode);
        row.routeCode = line.routeCode;
        row.routeName = line.routeName;
        row.serviceCode = line.serviceCode;
        row.serviceName = line.serviceName;
        row.sourceType = sourceType;
        row.costType = CURRENT_QUOTE;
        row.feeType = HEADHAUL;
        row.cargoCategoryCode = existing == null ? null : existing.cargoCategoryCode;
        row.cargoCategoryName = existing == null ? null : existing.cargoCategoryName;
        row.chargeUnit = normalize(line.billingUnit);
        row.unitCostCny = line.unitPrice;
        row.currencyCode = "CNY";
        row.confidenceLevel = "MANUAL";
        row.costOccurredAt = occurredAt;
        row.evidenceJson = evidenceJson;
        return row;
    }

    private void copyIdentityAndChannel(PurchaseOrderLogisticsQuoteLineRecord line, CostHistoryRow row) {
        row.ownerUserId = line.ownerUserId;
        row.logicalStoreId = line.logicalStoreId;
        row.productMasterId = line.productMasterId;
        row.productVariantId = line.productVariantId;
        row.partnerSku = line.partnerSku;
        row.barcode = line.barcode;
        row.siteCode = normalize(line.siteCode);
        row.forwarderCode = normalize(line.forwarderCode);
        row.forwarderName = line.forwarderName;
        row.transportMode = normalize(line.plannedTransportMode);
        row.routeCode = line.routeCode;
        row.routeName = line.routeName;
        row.serviceCode = line.serviceCode;
        row.serviceName = line.serviceName;
    }

    private boolean canSync(PurchaseOrderLogisticsQuoteLineRecord line, Long operatorUserId) {
        return operatorUserId != null
                && hasBusinessKey(line)
                && line.productMasterId != null
                && line.productVariantId != null
                && StringUtils.hasText(line.forwarderCode)
                && StringUtils.hasText(line.billingUnit)
                && line.unitPrice != null
                && line.unitPrice.signum() > 0
                && isCny(line.currency);
    }

    private boolean hasBusinessKey(PurchaseOrderLogisticsQuoteLineRecord line) {
        return line != null
                && line.ownerUserId != null
                && line.logicalStoreId != null
                && StringUtils.hasText(line.partnerSku)
                && StringUtils.hasText(line.siteCode)
                && StringUtils.hasText(line.plannedTransportMode);
    }

    private boolean isCny(String currency) {
        String code = normalize(currency);
        return "CNY".equals(code) || "RMB".equals(code);
    }

    private String evidenceJson(PurchaseOrderLogisticsQuoteLineRecord line, String sourceType) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("sourceType", sourceType);
        evidence.put("shippingOrderId", line.shippingOrderId);
        evidence.put("shippingOrderNo", line.shippingOrderNo);
        evidence.put("shippingOrderLineId", line.shippingOrderLineId);
        evidence.put("quoteLineId", line.id);
        evidence.put("partnerSku", line.partnerSku);
        evidence.put("siteCode", line.siteCode);
        evidence.put("transportMode", line.plannedTransportMode);
        evidence.put("forwarderCode", line.forwarderCode);
        evidence.put("routeCode", line.routeCode);
        evidence.put("serviceCode", line.serviceCode);
        evidence.put("unitPrice", line.unitPrice);
        evidence.put("billingUnit", line.billingUnit);
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("仓库报价证据序列化失败。", exception);
        }
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
