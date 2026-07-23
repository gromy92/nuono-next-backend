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

final class ShippingOptionDefinition {
    final String optionType;
    final String optionName;
    final Integer score;
    final String forwarderPlanType;
    final List<String> targetForwarderCodes;
    final List<String> targetForwarderNames;
    final boolean autoRecommended;
    final String airForwarderCode;
    final String seaForwarderCode;

    ShippingOptionDefinition(
            String optionType,
            String optionName,
            Integer score,
            String forwarderPlanType,
            List<String> targetForwarderCodes,
            List<String> targetForwarderNames,
            boolean autoRecommended,
            String airForwarderCode,
            String seaForwarderCode
    ) {
        this.optionType = optionType;
        this.optionName = optionName;
        this.score = score;
        this.forwarderPlanType = forwarderPlanType;
        this.targetForwarderCodes = targetForwarderCodes;
        this.targetForwarderNames = targetForwarderNames;
        this.autoRecommended = autoRecommended;
        this.airForwarderCode = airForwarderCode;
        this.seaForwarderCode = seaForwarderCode;
    }
}

final class ShippingForwarderAssignment {
    final String targetForwarderCode;
    final String targetForwarderName;
    final String routeCode;
    final String routeName;

    ShippingForwarderAssignment(
            String targetForwarderCode,
            String targetForwarderName,
            String routeCode,
            String routeName
    ) {
        this.targetForwarderCode = targetForwarderCode;
        this.targetForwarderName = targetForwarderName;
        this.routeCode = routeCode;
        this.routeName = routeName;
    }
}

final class ForwarderRouteSnapshot {
    final String routeCode;
    final String routeName;

    ForwarderRouteSnapshot(String routeCode, String routeName) {
        this.routeCode = routeCode;
        this.routeName = routeName;
    }
}

final class CargoCategoryEstimate {
    final String code;
    final String name;
    final Boolean reviewRequired;

    CargoCategoryEstimate(String code, String name, Boolean reviewRequired) {
        this.code = code;
        this.name = name;
        this.reviewRequired = reviewRequired;
    }
}

final class PendingShippingSource {
    final ShippingBatchSourceRecord source;
    final Integer quantity;
    final List<String> sensitiveReasons;

    PendingShippingSource(ShippingBatchSourceRecord source, Integer quantity, List<String> sensitiveReasons) {
        this.source = source;
        this.quantity = quantity;
        this.sensitiveReasons = sensitiveReasons;
    }

    List<String> sensitiveReasons() {
        if (!Boolean.TRUE.equals(source.sensitiveFlag)) {
            return List.of();
        }
        return sensitiveReasons == null || sensitiveReasons.isEmpty() ? List.of("敏货") : sensitiveReasons;
    }

    ShippingSuggestionLineSourceRecord toRecord(
            Long optionId,
            Long lineId,
            Long batchId,
            Long lineSourceId
    ) {
        ShippingSuggestionLineSourceRecord record = new ShippingSuggestionLineSourceRecord();
        record.id = lineSourceId;
        record.optionId = optionId;
        record.lineId = lineId;
        record.batchId = batchId;
        record.batchSourceId = source.id;
        record.fulfillmentBalanceId = source.fulfillmentBalanceId;
        record.plannedTransportMode = source.plannedTransportMode;
        record.quantity = quantity;
        return record;
    }
}
