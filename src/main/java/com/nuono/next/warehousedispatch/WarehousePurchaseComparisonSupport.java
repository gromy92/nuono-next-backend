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

abstract class WarehousePurchaseComparisonSupport extends WarehouseDispatchPlanProjectionSupport {

    protected WarehousePurchaseComparisonSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected PurchaseOrderLogisticsComparisonView toPurchaseOrderLogisticsComparisonView(List<FulfillmentBalanceRecord> balances) {
        FulfillmentBalanceRecord first = balances.get(0);
        PurchaseOrderLogisticsComparisonView view = new PurchaseOrderLogisticsComparisonView();
        view.purchaseOrderId = String.valueOf(first.purchaseOrderId);
        view.purchaseOrderNo = first.purchaseOrderNo;
        view.purchaseOrderTitle = first.purchaseOrderTitle;
        view.sourceStoreCode = first.sourceStoreCode;
        view.sourceStoreName = first.sourceStoreName;
        view.skuCount = (int) balances.stream()
                .map(this::productIdentityKey)
                .distinct()
                .count();
        view.totalQuantity = balances.stream().mapToInt(this::purchaseComparisonQuantity).sum();
        view.quantityBasis = "PLANNED_PURCHASE_QUANTITY";
        view.quantityBasisLabel = "按采购计划数量预估";
        view.fulfillmentReadinessNote = "这是采购阶段物流成本预估，不代表已可发货；需仓库验收产生可用数量后再生成货运计划。";

        Map<String, List<FulfillmentBalanceRecord>> bySegment = balances.stream()
                .collect(Collectors.groupingBy(
                        balance -> defaultText(balance.siteCode, "UNKNOWN") + "|"
                                + normalizeTransportMode(balance.plannedTransportMode),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        for (Map.Entry<String, List<FulfillmentBalanceRecord>> entry : bySegment.entrySet()) {
            PurchaseOrderLogisticsSegmentView segment = toPurchaseOrderLogisticsSegmentView(entry.getKey(), entry.getValue());
            view.segments.add(segment);
            mergeDefects(view.defects, segment.defects);
            mergeDefects(view.missingPlanSuggestions, segment.missingPlanSuggestions);
            if (segment.actualWeightKg != null) {
                view.actualWeightKg = view.actualWeightKg == null ? segment.actualWeightKg : view.actualWeightKg.add(segment.actualWeightKg);
            }
            if (segment.volumeCbm != null) {
                view.volumeCbm = view.volumeCbm == null ? segment.volumeCbm : view.volumeCbm.add(segment.volumeCbm);
            }
            if (segment.recommendedEstimatedAmount != null) {
                view.recommendedEstimatedAmount = view.recommendedEstimatedAmount == null
                        ? segment.recommendedEstimatedAmount
                        : view.recommendedEstimatedAmount.add(segment.recommendedEstimatedAmount);
                view.currency = defaultText(view.currency, segment.currency);
            }
        }
        view.actualWeightKg = view.actualWeightKg == null ? null : view.actualWeightKg.setScale(3, RoundingMode.HALF_UP);
        view.volumeCbm = view.volumeCbm == null ? null : view.volumeCbm.setScale(4, RoundingMode.HALF_UP);
        if (view.recommendedEstimatedAmount != null) {
            view.recommendedOptionId = "SEGMENT_RECOMMENDED";
            view.recommendedOptionName = "按目的地/运输方式分段推荐";
            view.recommendedEstimatedAmount = view.recommendedEstimatedAmount.setScale(4, RoundingMode.HALF_UP);
        } else {
            addUnique(view.defects, "没有可计价的物流方案");
            addUnique(view.missingPlanSuggestions, "补充至少一个可覆盖该采购单目的地和运输方式的货代报价。");
        }
        return view;
    }

protected PurchaseOrderLogisticsSegmentView toPurchaseOrderLogisticsSegmentView(
            String segmentKey,
            List<FulfillmentBalanceRecord> balances
    ) {
        List<ShippingBatchSourceRecord> sources = balances.stream()
                .map(this::toAnalysisShippingBatchSource)
                .collect(Collectors.toList());
        ShippingBatchSourceRecord first = sources.get(0);
        PurchaseOrderLogisticsSegmentView view = new PurchaseOrderLogisticsSegmentView();
        view.segmentKey = segmentKey;
        view.siteCode = first.siteCode;
        view.plannedTransportMode = normalizeTransportMode(first.plannedTransportMode);
        view.skuCount = shippingSkuCount(sources);
        view.totalQuantity = sources.stream().mapToInt(source -> nonNull(source.reservedQuantity)).sum();
        view.quantityBasis = "PLANNED_PURCHASE_QUANTITY";
        view.quantityBasisLabel = "按采购计划数量预估";
        view.actualWeightKg = totalSourceActualWeightKg(sources);
        view.volumeCbm = totalSourceVolumeCbm(sources);

        for (ShippingOptionDefinition definition : DEFAULT_SHIPPING_OPTIONS) {
            ShippingSuggestionOptionView option = evaluateShippingOptionPreview(segmentKey, sources, definition);
            view.options.add(option);
            mergeDefects(view.defects, option.blockedReasons);
        }
        ShippingSuggestionOptionView recommended = recommendedShippingOption(view.options);
        if (recommended != null) {
            view.recommendedOptionId = recommended.id;
            view.recommendedOptionName = recommended.optionName;
            view.recommendedEstimatedAmount = recommended.estimatedTotalAmount;
            view.currency = recommended.currency;
        } else {
            addUnique(view.defects, "该目的地/运输方式没有可计价方案");
        }
        view.missingPlanSuggestions.addAll(missingPlanSuggestions(view.defects));
        return view;
    }

protected ShippingBatchSourceRecord toAnalysisShippingBatchSource(FulfillmentBalanceRecord balance) {
        ShippingBatchSourceRecord record = new ShippingBatchSourceRecord();
        record.id = balance.id;
        record.ownerUserId = balance.ownerUserId;
        record.fulfillmentBalanceId = balance.id;
        record.sourceStoreCode = balance.sourceStoreCode;
        record.sourceStoreName = balance.sourceStoreName;
        record.purchaseOrderId = balance.purchaseOrderId;
        record.purchaseOrderNo = balance.purchaseOrderNo;
        record.purchaseOrderTitle = balance.purchaseOrderTitle;
        record.purchaseOrderItemId = balance.purchaseOrderItemId;
        record.purchaseOrderItemSiteId = balance.purchaseOrderItemSiteId;
        record.productMasterId = balance.productMasterId;
        record.productVariantId = balance.productVariantId;
        record.partnerSku = balance.partnerSku;
        record.skuParent = balance.skuParent;
        record.titleCache = balance.titleCache;
        record.imageUrlCache = balance.imageUrlCache;
        record.siteCode = balance.siteCode;
        record.plannedTransportMode = normalizeTransportMode(balance.plannedTransportMode);
        record.fulfillmentType = normalizeFulfillmentType(balance.fulfillmentType);
        record.sourcePartyName = fulfillmentSourcePartyName(record.fulfillmentType, balance);
        record.specStatus = defaultText(balance.specStatus, "READY");
        record.productLengthCm = balance.productLengthCm;
        record.productWidthCm = balance.productWidthCm;
        record.productHeightCm = balance.productHeightCm;
        record.productWeightG = balance.productWeightG;
        record.logisticsProfileStatus = balance.logisticsProfileStatus;
        record.logisticsQuoteStatus = normalizeLogisticsQuoteStatus(balance.logisticsQuoteStatus);
        record.logisticsShippingSubmitStatus = normalizeShippingSubmitStatus(balance.logisticsShippingSubmitStatus);
        record.logisticsQuoteBlocking = logisticsQuoteBlocks(balance);
        List<String> sensitiveReasons = sensitiveReasons(balance);
        record.sensitiveFlag = !sensitiveReasons.isEmpty();
        record.sensitiveReasonJson = sensitiveReasons.isEmpty() ? null : writeJson(sensitiveReasons);
        record.reservedQuantity = purchaseComparisonQuantity(balance);
        return record;
    }

protected int purchaseComparisonQuantity(FulfillmentBalanceRecord balance) {
        return nonNull(balance.plannedQuantity);
    }

protected ShippingSuggestionOptionView evaluateShippingOptionPreview(
            String segmentKey,
            List<ShippingBatchSourceRecord> sources,
            ShippingOptionDefinition definition
    ) {
        Map<String, PendingShippingLine> lineGroups = new LinkedHashMap<>();
        for (ShippingBatchSourceRecord source : sources) {
            String actualTransportMode = shippingActualTransportMode(definition.optionType, source);
            ShippingForwarderAssignment assignment = shippingForwarderAssignment(definition, actualTransportMode, source.siteCode);
            String key = shippingLineKey(source, actualTransportMode, assignment);
            lineGroups.computeIfAbsent(
                            key,
                            ignored -> new PendingShippingLine(
                                    source,
                                    actualTransportMode,
                                    assignment,
                                    writeJson(shippingLineAssignmentSnapshot(assignment))
                            )
                    )
                    .sources.add(new PendingShippingSource(
                            source,
                            nonNull(source.reservedQuantity),
                            readJsonStringList(source.sensitiveReasonJson)
                    ));
        }
        Map<String, List<ForwarderRouteQuoteRecord>> routeQuotes = forwarderRouteQuotes(lineGroups.values());
        for (PendingShippingLine pendingLine : lineGroups.values()) {
            pendingLine.evaluate(quotesForRoute(routeQuotes, pendingLine.routeCode));
        }

        ShippingSuggestionOptionView view = new ShippingSuggestionOptionView();
        view.id = "preview:" + segmentKey + ":" + definition.optionType;
        view.optionType = definition.optionType;
        view.optionName = definition.optionName;
        view.status = "CANDIDATE";
        view.selectedFlag = false;
        view.score = definition.score;
        view.skuCount = shippingSkuCount(sources);
        view.totalQuantity = 0;
        view.airQuantity = 0;
        view.seaQuantity = 0;
        view.specMissingCount = 0;
        view.warningCount = 0;
        view.forwarderPlanType = definition.forwarderPlanType;
        view.autoRecommended = definition.autoRecommended;
        view.targetForwarderCodes.addAll(definition.targetForwarderCodes);
        view.targetForwarderNames.addAll(definition.targetForwarderNames);
        view.routeCodes.addAll(routeCodes(lineGroups.values()));

        BigDecimal actualWeightKg = BigDecimal.ZERO;
        BigDecimal volumeCbm = BigDecimal.ZERO;
        BigDecimal chargeableWeightKg = BigDecimal.ZERO;
        BigDecimal estimatedTotalAmount = BigDecimal.ZERO;
        long lineId = -1L;
        for (PendingShippingLine pendingLine : lineGroups.values()) {
            int lineQuantity = pendingLine.quantity();
            view.totalQuantity += lineQuantity;
            if (TRANSPORT_AIR.equals(pendingLine.actualTransportMode)) {
                view.airQuantity += lineQuantity;
            }
            if (TRANSPORT_SEA.equals(pendingLine.actualTransportMode)) {
                view.seaQuantity += lineQuantity;
            }
            collectShippingLineDefects(view.blockedReasons, pendingLine);
            view.warningCount = view.blockedReasons.size();
            if (pendingLine.actualWeightKg != null) {
                actualWeightKg = actualWeightKg.add(pendingLine.actualWeightKg);
            }
            if (pendingLine.volumeCbm != null) {
                volumeCbm = volumeCbm.add(pendingLine.volumeCbm);
            }
            if (pendingLine.chargeableWeightKg != null) {
                chargeableWeightKg = chargeableWeightKg.add(pendingLine.chargeableWeightKg);
            }
            if (pendingLine.estimatedAmount != null) {
                estimatedTotalAmount = estimatedTotalAmount.add(pendingLine.estimatedAmount);
                view.currency = defaultText(view.currency, pendingLine.currency);
            }
            ShippingSuggestionLineRecord line = pendingLine.toRecord(null, null, null, lineId--);
            view.lines.add(toShippingSuggestionLineView(line));
        }
        view.actualWeightKg = zeroToNull(actualWeightKg, 3);
        view.volumeCbm = zeroToNull(volumeCbm, 4);
        view.chargeableWeightKg = zeroToNull(chargeableWeightKg, 3);
        view.estimatedTotalAmount = zeroToNull(estimatedTotalAmount, 4);
        view.avgUnitAmount = view.estimatedTotalAmount == null || view.totalQuantity == null || view.totalQuantity <= 0
                ? null
                : view.estimatedTotalAmount.divide(BigDecimal.valueOf(view.totalQuantity), 4, RoundingMode.HALF_UP);
        view.evaluationStatus = view.blockedReasons.isEmpty() ? "READY" : "NEEDS_REVIEW";
        return view;
    }
}
