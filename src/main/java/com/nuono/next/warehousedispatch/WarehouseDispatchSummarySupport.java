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

abstract class WarehouseDispatchSummarySupport extends WarehouseDispatchAccessSupport {

    protected WarehouseDispatchSummarySupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected Map<String, Integer> siteSummary(List<DispatchPlanLineView> lines) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (DispatchPlanLineView line : lines) {
            result.merge(defaultText(line.siteCode, "UNKNOWN"), nonNull(line.quantity), Integer::sum);
        }
        return result;
    }

protected Map<String, Integer> transportSummary(List<DispatchPlanLineView> lines) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (DispatchPlanLineView line : lines) {
            result.merge(normalizeTransportMode(line.actualTransportMode), nonNull(line.quantity), Integer::sum);
        }
        return result;
    }

protected int shippingSkuCount(List<ShippingBatchSourceRecord> sources) {
        return (int) sources.stream()
                .map(this::productIdentityKey)
                .distinct()
                .count();
    }

protected Map<String, Integer> shippingStoreSummary(List<ShippingBatchSourceRecord> sources) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ShippingBatchSourceRecord source : sources) {
            result.merge(defaultText(source.sourceStoreCode, "UNKNOWN"), nonNull(source.reservedQuantity), Integer::sum);
        }
        return result;
    }

protected Map<String, Integer> shippingSiteSummary(List<ShippingBatchSourceRecord> sources) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ShippingBatchSourceRecord source : sources) {
            result.merge(defaultText(source.siteCode, "UNKNOWN"), nonNull(source.reservedQuantity), Integer::sum);
        }
        return result;
    }

protected Map<String, Integer> shippingPlannedTransportSummary(List<ShippingBatchSourceRecord> sources) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ShippingBatchSourceRecord source : sources) {
            result.merge(normalizeTransportMode(source.plannedTransportMode), nonNull(source.reservedQuantity), Integer::sum);
        }
        return result;
    }

protected String shippingBatchNo(Long batchId, List<ShippingBatchSourceRecord> sources) {
        String date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.ofPattern("MMdd"));
        String transportMode = shippingBatchTransportModeLabel(shippingPlannedTransportSummary(sources).keySet());
        int quantity = sources.stream().mapToInt(source -> nonNull(source.reservedQuantity)).sum();
        long codeNumber = batchId == null ? 0L : Math.floorMod(batchId, 100L);
        String code = String.format(Locale.ROOT, "%02d", codeNumber);
        return date + "-" + transportMode + "-" + quantity + "件-" + code;
    }

protected String shippingBatchTransportModeLabel(Collection<String> transportModes) {
        Set<String> normalized = transportModes == null ? Set.of() : transportModes.stream()
                .map(this::normalizeTransportMode)
                .filter(mode -> !TRANSPORT_UNSPECIFIED.equals(mode))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.contains(TRANSPORT_AIR) && normalized.contains(TRANSPORT_SEA)) {
            return "空海运";
        }
        if (normalized.contains(TRANSPORT_AIR)) {
            return "空运";
        }
        if (normalized.contains(TRANSPORT_SEA)) {
            return "海运";
        }
        return "货运";
    }

protected Map<String, Integer> shippingOriginSummary(List<ShippingBatchSourceRecord> sources) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ShippingBatchSourceRecord source : sources) {
            String key = normalizeFulfillmentType(source.fulfillmentType) + "|"
                    + defaultText(source.sourcePartyName, defaultText(source.sourceStoreName, "UNKNOWN"));
            result.merge(key, nonNull(source.reservedQuantity), Integer::sum);
        }
        return result;
    }

protected Map<String, Object> shippingOptionSummary(
            ShippingOptionDefinition definition,
            List<ShippingBatchSourceRecord> sources,
            Collection<PendingShippingLine> lines
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceCount", sources.size());
        result.put("lineCount", lines.size());
        result.put("forwarderPlanType", definition.forwarderPlanType);
        result.put("targetForwarderCodes", definition.targetForwarderCodes);
        result.put("targetForwarderNames", definition.targetForwarderNames);
        result.put("autoRecommended", definition.autoRecommended);
        result.put("routeCodes", routeCodes(lines));
        return result;
    }
}
