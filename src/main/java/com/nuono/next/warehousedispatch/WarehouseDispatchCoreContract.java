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

abstract class WarehouseDispatchCoreContract extends WarehouseShippingPlanningContract {

    protected WarehouseDispatchCoreContract(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected abstract String shippingLineKey(
            ShippingBatchSourceRecord source,
            String actualTransportMode,
            ShippingForwarderAssignment assignment
    );

protected abstract String shippingOriginKey(ShippingSuggestionLineRecord line);

protected abstract int outboundSkuCount(List<ShippingSuggestionLineRecord> lines);

protected abstract Map<String, Integer> outboundSiteSummary(List<ShippingSuggestionLineRecord> lines);

protected abstract Map<String, Integer> outboundTransportSummary(List<ShippingSuggestionLineRecord> lines);

protected abstract String fulfillmentSourcePartyName(String fulfillmentType, FulfillmentBalanceRecord balance);

protected abstract boolean isSpecMissing(String specStatus);

protected abstract List<String> sensitiveReasons(FulfillmentBalanceRecord balance);

protected abstract void addSensitiveReason(List<String> reasons, String value, String label);

protected abstract boolean requiresManualConfirm(FulfillmentBalanceRecord balance);

protected abstract String dispatchLineKey(
            FulfillmentBalanceRecord balance,
            String actualTransportMode,
            String fulfillmentType,
            String specStatus
    );

protected abstract String productIdentityKey(FulfillmentBalanceRecord balance);

protected abstract String productIdentityKey(DispatchPlanLineRecord line);

protected abstract String productIdentityKey(ShippingBatchSourceRecord source);

protected abstract String productIdentityKey(ShippingSuggestionLineRecord line);

protected abstract String productIdentityKey(Long logicalStoreId, String storeCode, String partnerSku, Long productVariantId);

protected abstract <T> List<T> emptyIfNull(List<T> values);

protected abstract String normalizeFulfillmentType(String value);

protected abstract String normalizeConfirmationType(String value);

protected abstract String normalizeTransportMode(String value);

protected abstract String normalizeSiteCode(String value);

protected abstract String requiredText(String value, String message);

protected abstract String trim(String value);

protected abstract String trimToNull(String value);

protected abstract String defaultText(String value, String fallback);

protected abstract int nonNull(Integer value);

protected abstract BigDecimal positiveDecimal(String value, String message);

protected abstract BigDecimal optionalPositiveDecimal(String value, String message);

protected abstract Long parseLongId(String value, String message);

protected abstract String textFromObject(Object value, String fallback);

protected abstract boolean booleanFromObject(Object value);

protected abstract List<String> stringListFromObject(Object value);

protected abstract List<String> readJsonStringList(String value);

protected abstract List<String> orElse(List<String> primary, List<String> fallback);

protected abstract void log(
            Long dispatchPlanId,
            String operationType,
            Long operatorUserId,
            String beforeStatus,
            String afterStatus,
            String detail
    );
}
