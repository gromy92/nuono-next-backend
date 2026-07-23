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

abstract class WarehouseMobileDecisionPresentationSupport extends WarehouseMobileDecisionAssessmentSupport {

    protected WarehouseMobileDecisionPresentationSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected List<String> mobileForwarderNames(ShippingSuggestionOptionView option) {
        List<String> names = emptyIfNull(option.lines).stream()
                .map(line -> line.targetForwarderName)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        return names.isEmpty() ? option.targetForwarderNames : names;
    }

protected List<String> mobileRouteNames(ShippingSuggestionOptionView option) {
        return emptyIfNull(option.lines).stream()
                .map(line -> line.routeName)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

protected List<MobileShippingDecisionForwarderAllocationView> mobileForwarderAllocations(
            ShippingSuggestionOptionView option
    ) {
        if (option == null || option.lines == null || option.lines.isEmpty()) {
            return List.of();
        }
        Map<String, ForwarderAllocationSummary> summaries = new LinkedHashMap<>();
        for (ShippingSuggestionLineView line : option.lines) {
            String forwarderCode = defaultText(line.targetForwarderCode, "");
            String forwarderName = defaultText(line.targetForwarderName, forwarderName(forwarderCode));
            String key = forwarderCode + "|" + forwarderName;
            ForwarderAllocationSummary summary = summaries.computeIfAbsent(
                    key,
                    ignored -> new ForwarderAllocationSummary(forwarderCode, forwarderName)
            );
            summary.quantity += nonNull(line.quantity);
            String psku = trim(line.partnerSku);
            if (StringUtils.hasText(psku)) {
                summary.pskus.add(psku);
            }
        }
        int totalQuantity = option.totalQuantity == null || option.totalQuantity <= 0
                ? summaries.values().stream().mapToInt(summary -> summary.quantity).sum()
                : option.totalQuantity;
        return summaries.values().stream()
                .sorted(Comparator
                        .comparingInt((ForwarderAllocationSummary summary) -> -summary.quantity)
                        .thenComparing(summary -> defaultText(summary.forwarderName, "")))
                .map(summary -> summary.toView(totalQuantity))
                .collect(Collectors.toList());
    }

protected String mobileOptionKey(String siteCode, String transportMode, List<String> forwarderCodes) {
        List<String> normalizedCodes = emptyIfNull(forwarderCodes).stream()
                .map(this::normalizeForwarderCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        return "MOBILE_" + siteCode.toUpperCase(Locale.ROOT)
                + "_" + normalizeTransportMode(transportMode)
                + "_" + String.join("_", normalizedCodes);
    }

protected void collectShippingLineDefects(List<String> blockedReasons, PendingShippingLine pendingLine) {
        if (isSpecMissing(pendingLine.firstSource.specStatus)) {
            addUnique(blockedReasons, "规格缺失");
        }
        if (!StringUtils.hasText(pendingLine.routeCode)) {
            addUnique(blockedReasons, "目标货代缺少可用线路");
        }
        boolean hasEstimatedAmount = pendingLine.estimatedAmount != null;
        if (!pendingLine.sensitiveReasons().isEmpty() && !hasEstimatedAmount) {
            addUnique(blockedReasons, "存在敏货，需确认目标货代是否接收");
        }
        if (Boolean.TRUE.equals(pendingLine.cargoCategoryReviewRequired) && !hasEstimatedAmount) {
            addUnique(blockedReasons, "货物类别需人工复核");
        }
        if (Boolean.TRUE.equals(pendingLine.quoteMissingForCargoCategory)) {
            addUnique(blockedReasons, "目标货代缺少匹配货物类别报价");
        }
        if (pendingLine.estimatedAmount == null) {
            addUnique(blockedReasons, "报价规则不足，需人工复核");
        }
    }

protected ShippingSuggestionOptionView recommendedShippingOption(List<ShippingSuggestionOptionView> options) {
        return options.stream()
                .filter(option -> option.estimatedTotalAmount != null)
                .sorted(Comparator
                        .comparing((ShippingSuggestionOptionView option) -> !"READY".equalsIgnoreCase(defaultText(option.evaluationStatus, "")))
                        .thenComparing(option -> option.estimatedTotalAmount)
                        .thenComparing(option -> option.score == null ? 0 : -option.score))
                .findFirst()
                .orElse(null);
    }

protected void mergeDefects(List<String> target, List<String> source) {
        for (String value : emptyIfNull(source)) {
            addUnique(target, value);
        }
    }

protected List<String> missingPlanSuggestions(List<String> defects) {
        List<String> suggestions = new ArrayList<>();
        if (defects.contains("规格缺失")) {
            suggestions.add("补齐商品长宽高重量规格，否则无法评估真实物流成本。");
        }
        if (defects.contains("目标货代缺少可用线路")) {
            suggestions.add("补充目标站点和运输方式的货代线路模板。");
        }
        if (defects.contains("目标货代缺少匹配货物类别报价") || defects.contains("报价规则不足，需人工复核")) {
            suggestions.add("补充货代对应货物类别的有效报价档，避免用普货价低估敏货成本。");
        }
        if (defects.contains("货物类别需人工复核") || defects.contains("存在敏货，需确认目标货代是否接收")) {
            suggestions.add("补充商品物流属性到货物类别的明确映射，并确认目标货代敏货接收规则。");
        }
        if (defects.contains("未达到目标货代最低计费单位")) {
            suggestions.add("小批量触发最低计费，可考虑合单或补充小包/小票专线方案。");
        }
        return suggestions;
    }

protected BigDecimal totalSourceActualWeightKg(List<ShippingBatchSourceRecord> sources) {
        BigDecimal total = BigDecimal.ZERO;
        for (ShippingBatchSourceRecord source : sources) {
            if (source.productWeightG == null) {
                return null;
            }
            total = total.add(source.productWeightG.multiply(BigDecimal.valueOf(nonNull(source.reservedQuantity))));
        }
        return total.divide(GRAMS_PER_KG, 3, RoundingMode.HALF_UP);
    }

protected BigDecimal totalSourceVolumeCbm(List<ShippingBatchSourceRecord> sources) {
        BigDecimal total = BigDecimal.ZERO;
        for (ShippingBatchSourceRecord source : sources) {
            if (source.productLengthCm == null || source.productWidthCm == null || source.productHeightCm == null) {
                return null;
            }
            total = total.add(source.productLengthCm
                    .multiply(source.productWidthCm)
                    .multiply(source.productHeightCm)
                    .multiply(BigDecimal.valueOf(nonNull(source.reservedQuantity))));
        }
        return total.divide(CUBIC_CM_PER_CBM, 4, RoundingMode.HALF_UP);
    }
}
