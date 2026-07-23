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

abstract class WarehouseShippingOptionProjectionSupport extends WarehouseShippingBatchProjectionSupport {

    protected WarehouseShippingOptionProjectionSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected ShippingSuggestionOptionView toShippingSuggestionOptionView(ShippingSuggestionOptionRecord option) {
        ShippingSuggestionOptionView view = new ShippingSuggestionOptionView();
        view.id = String.valueOf(option.id);
        view.batchId = option.batchId;
        view.optionType = option.optionType;
        view.optionName = option.optionName;
        view.status = option.status;
        view.selectedFlag = Boolean.TRUE.equals(option.selectedFlag);
        view.score = nonNull(option.score);
        view.skuCount = nonNull(option.skuCount);
        view.totalQuantity = nonNull(option.totalQuantity);
        view.airQuantity = nonNull(option.airQuantity);
        view.seaQuantity = nonNull(option.seaQuantity);
        view.specMissingCount = nonNull(option.specMissingCount);
        view.warningCount = nonNull(option.warningCount);
        Map<String, Object> summary = readJsonObject(option.summaryJson);
        view.forwarderPlanType = defaultText(option.forwarderPlanType, textFromObject(summary.get("forwarderPlanType"), "SINGLE"));
        view.autoRecommended = Boolean.TRUE.equals(option.autoRecommended) || booleanFromObject(summary.get("autoRecommended"));
        view.targetForwarderCodes.addAll(orElse(
                readJsonStringList(option.targetForwarderCodesJson),
                stringListFromObject(summary.get("targetForwarderCodes"))
        ));
        view.targetForwarderNames.addAll(orElse(
                readJsonStringList(option.targetForwarderNamesJson),
                stringListFromObject(summary.get("targetForwarderNames"))
        ));
        view.routeCodes.addAll(orElse(
                readJsonStringList(option.routeCodesJson),
                stringListFromObject(summary.get("routeCodes"))
        ));
        view.evaluationStatus = defaultText(option.evaluationStatus, "PENDING");
        view.blockedReasons.addAll(readJsonStringList(option.blockedReasonsJson));
        view.actualWeightKg = option.actualWeightKg;
        view.volumeCbm = option.volumeCbm;
        view.chargeableWeightKg = option.chargeableWeightKg;
        view.estimatedTotalAmount = option.estimatedTotalAmount;
        view.avgUnitAmount = option.avgUnitAmount;
        view.currency = option.currency;
        return view;
    }

protected ShippingSuggestionLineView toShippingSuggestionLineView(ShippingSuggestionLineRecord line) {
        ShippingSuggestionLineView view = new ShippingSuggestionLineView();
        view.id = String.valueOf(line.id);
        view.optionId = line.optionId;
        view.batchId = line.batchId;
        view.productVariantId = line.productVariantId;
        view.partnerSku = line.partnerSku;
        view.skuParent = line.skuParent;
        view.productTitle = defaultText(line.titleCache, line.partnerSku);
        view.productImageUrl = ProductImageUrlSupport.normalize(line.imageUrlCache);
        view.siteCode = line.siteCode;
        view.actualTransportMode = normalizeTransportMode(line.actualTransportMode);
        view.fulfillmentType = normalizeFulfillmentType(line.fulfillmentType);
        view.sourcePartyName = line.sourcePartyName;
        view.specStatus = defaultText(line.specStatus, "READY");
        Map<String, Object> assignment = readJsonObject(line.warningJson);
        view.targetForwarderCode = defaultText(line.targetForwarderCode, textFromObject(assignment.get("targetForwarderCode"), null));
        view.targetForwarderName = defaultText(line.targetForwarderName, textFromObject(assignment.get("targetForwarderName"), null));
        view.routeCode = defaultText(line.routeCode, textFromObject(assignment.get("routeCode"), null));
        view.routeName = defaultText(line.routeName, textFromObject(assignment.get("routeName"), null));
        view.cargoCategoryCode = textFromObject(assignment.get("cargoCategoryCode"), null);
        view.cargoCategoryName = textFromObject(assignment.get("cargoCategoryName"), null);
        view.quoteCargoCategoryCode = textFromObject(assignment.get("quoteCargoCategoryCode"), null);
        view.quoteCargoCategoryName = textFromObject(assignment.get("quoteCargoCategoryName"), null);
        view.cargoCategoryReviewRequired = booleanFromObject(assignment.get("cargoCategoryReviewRequired"));
        view.actualWeightKg = line.actualWeightKg;
        view.volumeCbm = line.volumeCbm;
        view.chargeableWeightKg = line.chargeableWeightKg;
        view.estimatedAmount = line.estimatedAmount;
        view.currency = line.currency;
        view.quantity = nonNull(line.quantity);
        return view;
    }

    protected String shippingPackingGroupCode(ShippingSuggestionLineRecord line) {
        if (line == null) {
            return null;
        }
        ShippingSuggestionLineView assignment = toShippingSuggestionLineView(line);
        String forwarderCode = trim(assignment.targetForwarderCode);
        String routeCode = trim(assignment.routeCode);
        String categoryCode = defaultText(
                trim(assignment.quoteCargoCategoryCode),
                trim(assignment.cargoCategoryCode)
        );
        if (!StringUtils.hasText(forwarderCode)
                && !StringUtils.hasText(routeCode)
                && !StringUtils.hasText(categoryCode)) {
            return null;
        }
        return defaultText(forwarderCode, "UNASSIGNED") + "::"
                + defaultText(routeCode, "UNROUTED") + "::"
                + defaultText(categoryCode, "UNCLASSIFIED");
    }

    protected String shippingPackingGroupName(ShippingSuggestionLineRecord line) {
        if (line == null) {
            return null;
        }
        ShippingSuggestionLineView assignment = toShippingSuggestionLineView(line);
        List<String> labels = new ArrayList<>();
        addUnique(labels, trim(assignment.targetForwarderName));
        addUnique(labels, trim(assignment.routeName));
        addUnique(labels, defaultText(
                trim(assignment.quoteCargoCategoryName),
                trim(assignment.cargoCategoryName)
        ));
        return labels.isEmpty() ? null : String.join(" · ", labels);
    }

protected ShippingSuggestionLineSourceView toShippingSuggestionLineSourceView(ShippingSuggestionLineSourceRecord source) {
        ShippingSuggestionLineSourceView view = new ShippingSuggestionLineSourceView();
        view.id = String.valueOf(source.id);
        view.optionId = source.optionId;
        view.lineId = source.lineId;
        view.batchId = source.batchId;
        view.batchSourceId = source.batchSourceId;
        view.fulfillmentBalanceId = source.fulfillmentBalanceId;
        view.plannedTransportMode = normalizeTransportMode(source.plannedTransportMode);
        view.quantity = nonNull(source.quantity);
        return view;
    }

protected void validateSelectedOptionAllocation(
            List<ShippingBatchSourceRecord> batchSources,
            List<ShippingSuggestionLineRecord> optionLines,
            List<ShippingSuggestionLineSourceRecord> optionSources
    ) {
        Map<Long, ShippingBatchSourceRecord> batchSourceById = batchSources.stream()
                .collect(Collectors.toMap(source -> source.id, source -> source, (left, right) -> left, LinkedHashMap::new));
        Map<Long, ShippingSuggestionLineRecord> lineById = optionLines.stream()
                .collect(Collectors.toMap(line -> line.id, line -> line, (left, right) -> left, LinkedHashMap::new));
        Map<Long, Integer> allocatedBySource = new LinkedHashMap<>();
        Map<Long, Integer> allocatedByLine = new LinkedHashMap<>();
        for (ShippingSuggestionLineSourceRecord source : optionSources) {
            if (!batchSourceById.containsKey(source.batchSourceId)) {
                throw new IllegalArgumentException("货运计划方案包含无效来源。");
            }
            if (!lineById.containsKey(source.lineId)) {
                throw new IllegalArgumentException("货运计划方案包含无效明细。");
            }
            int quantity = nonNull(source.quantity);
            if (quantity <= 0) {
                throw new IllegalArgumentException("货运计划方案来源数量必须大于 0。");
            }
            allocatedBySource.merge(source.batchSourceId, quantity, Integer::sum);
            allocatedByLine.merge(source.lineId, quantity, Integer::sum);
        }
        for (ShippingBatchSourceRecord source : batchSources) {
            int reservedQuantity = nonNull(source.reservedQuantity);
            int allocatedQuantity = allocatedBySource.getOrDefault(source.id, 0);
            if (reservedQuantity != allocatedQuantity) {
                throw new IllegalArgumentException(source.partnerSku + " 货运计划方案未完整分配已预留数量。");
            }
        }
        for (ShippingSuggestionLineRecord line : optionLines) {
            int lineQuantity = nonNull(line.quantity);
            int allocatedQuantity = allocatedByLine.getOrDefault(line.id, 0);
            if (lineQuantity != allocatedQuantity) {
                throw new IllegalArgumentException(line.partnerSku + " 货运计划方案明细数量与来源数量不一致。");
            }
        }
    }
}
