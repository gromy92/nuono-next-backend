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

abstract class WarehouseProductIdentitySupport extends WarehouseForwarderMatchingSupport {

    protected WarehouseProductIdentitySupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected String shippingLineKey(
            ShippingBatchSourceRecord source,
            String actualTransportMode,
            ShippingForwarderAssignment assignment
    ) {
        return productIdentityKey(source) + "|" + source.siteCode + "|" + normalizeTransportMode(actualTransportMode) + "|"
                + normalizeFulfillmentType(source.fulfillmentType) + "|"
                + defaultText(source.sourcePartyName, "") + "|"
                + defaultText(source.specStatus, "READY") + "|"
                + defaultText(assignment.targetForwarderCode, "") + "|"
                + defaultText(assignment.routeCode, "");
    }

    protected String shippingOriginKey(ShippingSuggestionLineRecord line) {
        return normalizeFulfillmentType(line.fulfillmentType) + "|"
                + defaultText(line.sourcePartyName, "") + "|"
                + defaultText(shippingPackingGroupCode(line), "LEGACY");
    }

protected int outboundSkuCount(List<ShippingSuggestionLineRecord> lines) {
        return (int) lines.stream()
                .map(this::productIdentityKey)
                .distinct()
                .count();
    }

protected Map<String, Integer> outboundSiteSummary(List<ShippingSuggestionLineRecord> lines) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ShippingSuggestionLineRecord line : lines) {
            result.merge(defaultText(line.siteCode, "UNKNOWN"), nonNull(line.quantity), Integer::sum);
        }
        return result;
    }

protected Map<String, Integer> outboundTransportSummary(List<ShippingSuggestionLineRecord> lines) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ShippingSuggestionLineRecord line : lines) {
            result.merge(normalizeTransportMode(line.actualTransportMode), nonNull(line.quantity), Integer::sum);
        }
        return result;
    }

protected String fulfillmentSourcePartyName(String fulfillmentType, FulfillmentBalanceRecord balance) {
        String sourceName = defaultText(balance.sourceStoreName, balance.sourceStoreCode);
        if (FULFILLMENT_FACTORY.equals(fulfillmentType)) {
            return sourceName;
        }
        return defaultText(sourceName, "WAREHOUSE");
    }

protected boolean isSpecMissing(String specStatus) {
        return "SPEC_MISSING".equalsIgnoreCase(defaultText(specStatus, "READY"));
    }

protected List<String> sensitiveReasons(FulfillmentBalanceRecord balance) {
        List<String> reasons = new ArrayList<>();
        addSensitiveReason(reasons, balance.batteryType, "带电");
        addSensitiveReason(reasons, balance.magneticType, "带磁");
        addSensitiveReason(reasons, balance.liquidPowderType, "液体/粉末");
        addSensitiveReason(reasons, balance.electricType, "电器");
        addSensitiveReason(reasons, balance.bladeWeaponType, "刀具");
        if (requiresManualConfirm(balance)) {
            addUnique(reasons, "新品物流属性需人工确认");
        }
        for (String tag : readJsonStringList(balance.sensitiveTagsJson)) {
            addUnique(reasons, tag);
        }
        return reasons;
    }

protected void addSensitiveReason(List<String> reasons, String value, String label) {
        String normalized = defaultText(value, "unknown").toLowerCase(Locale.ROOT);
        if (!"unknown".equals(normalized) && !"none".equals(normalized) && !"no".equals(normalized)
                && !"not_applicable".equals(normalized) && !"normal".equals(normalized)) {
            addUnique(reasons, label + ":" + value);
        }
    }

protected boolean requiresManualConfirm(FulfillmentBalanceRecord balance) {
        return balance != null
                && Boolean.TRUE.equals(balance.isNewProduct)
                && Boolean.TRUE.equals(balance.manualConfirmRequired);
    }

protected String dispatchLineKey(
            FulfillmentBalanceRecord balance,
            String actualTransportMode,
            String fulfillmentType,
            String specStatus
    ) {
        return productIdentityKey(balance) + "|" + balance.siteCode + "|" + actualTransportMode + "|"
                + fulfillmentType + "|" + specStatus;
    }

protected String productIdentityKey(FulfillmentBalanceRecord balance) {
        if (balance == null) {
            return "";
        }
        return productIdentityKey(balance.logicalStoreId, balance.sourceStoreCode, balance.partnerSku, balance.productVariantId);
    }

protected String productIdentityKey(DispatchPlanLineRecord line) {
        if (line == null) {
            return "";
        }
        return productIdentityKey(null, null, line.partnerSku, line.productVariantId);
    }

protected String productIdentityKey(ShippingBatchSourceRecord source) {
        if (source == null) {
            return "";
        }
        return productIdentityKey(null, source.sourceStoreCode, source.partnerSku, source.productVariantId);
    }

protected String productIdentityKey(ShippingSuggestionLineRecord line) {
        if (line == null) {
            return "";
        }
        return productIdentityKey(null, null, line.partnerSku, line.productVariantId);
    }

protected String productIdentityKey(Long logicalStoreId, String storeCode, String partnerSku, Long productVariantId) {
        String normalizedPartnerSku = trim(partnerSku);
        if (StringUtils.hasText(normalizedPartnerSku)) {
            String storeScope = logicalStoreId == null ? trim(storeCode) : String.valueOf(logicalStoreId);
            if (StringUtils.hasText(storeScope)) {
                return storeScope + "|" + normalizedPartnerSku;
            }
            return "PSKU|" + normalizedPartnerSku;
        }
        return productVariantId == null ? "" : "ROW|" + productVariantId;
    }
}
