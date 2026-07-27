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

abstract class WarehouseDispatchValueSupport extends WarehouseProductIdentitySupport {

    protected WarehouseDispatchValueSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected <T> List<T> emptyIfNull(List<T> values) {
        return values == null ? List.of() : values;
    }

protected String normalizeFulfillmentType(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        switch (normalized) {
            case "FACTORY":
            case "FACTORY_DIRECT":
            case "厂家":
            case "厂家直发":
                return FULFILLMENT_FACTORY;
            case "WAREHOUSE":
            case "WAREHOUSE_RECEIPT":
            case "到仓":
            case "仓库":
            case "":
                return FULFILLMENT_WAREHOUSE;
            default:
                throw new IllegalArgumentException("不支持的履约方式：" + value);
        }
    }

protected String normalizeConfirmationType(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        switch (normalized) {
            case "FACTORY_DIRECT":
            case "WAREHOUSE_RECEIPT":
            case "ADJUSTMENT":
            case "CANCELLATION":
                return normalized;
            case "":
                return FULFILLMENT_WAREHOUSE;
            default:
                throw new IllegalArgumentException("不支持的履约确认类型：" + value);
        }
    }

protected String normalizeTransportMode(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        switch (normalized) {
            case "AIR":
            case "空":
            case "空运":
                return TRANSPORT_AIR;
            case "SEA":
            case "海":
            case "海运":
                return TRANSPORT_SEA;
            default:
                return TRANSPORT_UNSPECIFIED;
        }
    }

protected String normalizeSiteCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

protected String requireLogisticsSiteCode(String value) {
        String normalized = normalizeSiteCode(value);
        if (!"SA".equals(normalized) && !"AE".equals(normalized)) {
            throw new IllegalArgumentException("物流站点必须为 SA 或 AE。");
        }
        return normalized;
    }

protected String requireLogisticsTransportMode(String value) {
        String normalized = normalizeTransportMode(value);
        if (!TRANSPORT_AIR.equals(normalized) && !TRANSPORT_SEA.equals(normalized)) {
            throw new IllegalArgumentException("运输方式必须为空运或海运。");
        }
        return normalized;
    }

protected String resolvedSiteCode(FulfillmentBalanceRecord balance) {
        return normalizeSiteCode(defaultText(balance.targetSiteCode, balance.siteCode));
    }

protected String resolvedTransportMode(FulfillmentBalanceRecord balance) {
        return normalizeTransportMode(defaultText(balance.targetTransportMode, balance.plannedTransportMode));
    }

protected String effectiveSiteCode(FulfillmentBalanceRecord balance) {
        return requireLogisticsSiteCode(resolvedSiteCode(balance));
    }

protected String effectiveSiteCode(FulfillmentBalanceRecord balance, String requestedSiteCode) {
        return requireLogisticsSiteCode(defaultText(
                requestedSiteCode,
                defaultText(balance.targetSiteCode, balance.siteCode)
        ));
    }

protected String effectiveTransportMode(FulfillmentBalanceRecord balance) {
        return requireLogisticsTransportMode(resolvedTransportMode(balance));
    }

protected String effectiveTransportMode(FulfillmentBalanceRecord balance, String requestedTransportMode) {
        return requireLogisticsTransportMode(defaultText(
                requestedTransportMode,
                defaultText(balance.targetTransportMode, balance.plannedTransportMode)
        ));
    }

protected String logisticsPartitionKey(String siteCode, String transportMode) {
        return requireLogisticsSiteCode(siteCode) + "|" + requireLogisticsTransportMode(transportMode);
    }

protected void requireSingleLogisticsPartition(Collection<String> partitionKeys) {
        if (partitionKeys == null || partitionKeys.isEmpty()) {
            throw new IllegalArgumentException("物流分区不能为空。");
        }
        if (new LinkedHashSet<>(partitionKeys).size() != 1) {
            throw new IllegalArgumentException("请选择同一物流分区（相同站点和运输方式）的商品。");
        }
    }

protected String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

protected String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

protected String trimToNull(String value) {
        return trim(value);
    }

protected String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

protected int nonNull(Integer value) {
        return value == null ? 0 : value;
    }

protected BigDecimal positiveDecimal(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        try {
            BigDecimal decimal = new BigDecimal(value.trim());
            if (decimal.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(message);
            }
            return decimal;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(message);
        }
    }

protected BigDecimal optionalPositiveDecimal(String value, String message) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return positiveDecimal(value, message);
    }

protected Long parseLongId(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(message);
        }
    }

protected String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("仓库发运 JSON 序列化失败。", error);
        }
    }

protected Map<String, Object> readJsonObject(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(value, Map.class);
            return parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
        } catch (Exception ignored) {
            return Map.of();
        }
    }

protected String textFromObject(Object value, String fallback) {
        return value == null ? fallback : defaultText(String.valueOf(value), fallback);
    }

protected boolean booleanFromObject(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }

protected List<String> stringListFromObject(Object value) {
        if (!(value instanceof Collection<?>)) {
            return List.of();
        }
        return ((Collection<?>) value).stream()
                .map(item -> item == null ? null : String.valueOf(item))
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

protected List<String> readJsonStringList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            Object parsed = objectMapper.readValue(value, List.class);
            return stringListFromObject(parsed);
        } catch (Exception ignored) {
            return List.of();
        }
    }

protected List<String> orElse(List<String> primary, List<String> fallback) {
        return primary == null || primary.isEmpty() ? fallback : primary;
    }

protected void log(
            Long dispatchPlanId,
            String operationType,
            Long operatorUserId,
            String beforeStatus,
            String afterStatus,
            String detail
    ) {
        mapper.insertOperationLog(
                mapper.nextOperationLogId(),
                dispatchPlanId,
                operationType,
                operatorUserId,
                beforeStatus,
                afterStatus,
                detail == null ? null : writeJson(Map.of("detail", detail))
        );
    }
}
