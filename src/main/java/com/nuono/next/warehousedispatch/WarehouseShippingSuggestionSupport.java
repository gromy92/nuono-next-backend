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

abstract class WarehouseShippingSuggestionSupport extends WarehouseMobileDecisionPresentationSupport {

    protected WarehouseShippingSuggestionSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected List<ShippingSuggestionOptionView> createDefaultShippingSuggestionOptions(
            ShippingBatchRecord batch,
            List<ShippingBatchSourceRecord> sources,
            Long operatorUserId
    ) {
        List<ShippingSuggestionOptionView> result = new ArrayList<>();
        for (ShippingOptionDefinition definition : DEFAULT_SHIPPING_OPTIONS) {
            result.add(createShippingSuggestionOption(batch, sources, definition, operatorUserId));
        }
        return result;
    }

protected ShippingSuggestionOptionView createShippingSuggestionOption(
            ShippingBatchRecord batch,
            List<ShippingBatchSourceRecord> sources,
            ShippingOptionDefinition definition,
            Long operatorUserId
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

        ShippingSuggestionOptionRecord option = new ShippingSuggestionOptionRecord();
        option.id = mapper.nextShippingSuggestionOptionId();
        option.batchId = batch.id;
        option.ownerUserId = batch.ownerUserId;
        option.optionType = definition.optionType;
        option.optionName = definition.optionName;
        option.status = "CANDIDATE";
        option.selectedFlag = false;
        option.score = definition.score;
        option.skuCount = shippingSkuCount(sources);
        option.totalQuantity = 0;
        option.airQuantity = 0;
        option.seaQuantity = 0;
        option.specMissingCount = 0;
        option.warningCount = 0;
        option.forwarderPlanType = definition.forwarderPlanType;
        option.autoRecommended = definition.autoRecommended;
        option.targetForwarderCodesJson = writeJson(definition.targetForwarderCodes);
        option.targetForwarderNamesJson = writeJson(definition.targetForwarderNames);
        option.routeCodesJson = writeJson(routeCodes(lineGroups.values()));
        option.actualWeightKg = BigDecimal.ZERO;
        option.volumeCbm = BigDecimal.ZERO;
        option.chargeableWeightKg = BigDecimal.ZERO;
        option.estimatedTotalAmount = BigDecimal.ZERO;
        option.currency = null;
        List<String> blockedReasons = new ArrayList<>();
        List<Map<String, Object>> costSnapshots = new ArrayList<>();
        for (PendingShippingLine pendingLine : lineGroups.values()) {
            int lineQuantity = pendingLine.quantity();
            option.totalQuantity += lineQuantity;
            if (TRANSPORT_AIR.equals(pendingLine.actualTransportMode)) {
                option.airQuantity += lineQuantity;
            }
            if (TRANSPORT_SEA.equals(pendingLine.actualTransportMode)) {
                option.seaQuantity += lineQuantity;
            }
            if (isSpecMissing(pendingLine.firstSource.specStatus)) {
                option.specMissingCount += 1;
                option.warningCount += 1;
                addUnique(blockedReasons, "规格缺失");
            }
            if (!StringUtils.hasText(pendingLine.routeCode)) {
                option.warningCount += 1;
                addUnique(blockedReasons, "目标货代缺少可用线路");
            }
            if (!pendingLine.sensitiveReasons().isEmpty()) {
                option.warningCount += 1;
                addUnique(blockedReasons, "存在敏货，需确认目标货代是否接收");
            }
            if (Boolean.TRUE.equals(pendingLine.cargoCategoryReviewRequired)) {
                option.warningCount += 1;
                addUnique(blockedReasons, "货物类别需人工复核");
            }
            if (Boolean.TRUE.equals(pendingLine.quoteMissingForCargoCategory)) {
                option.warningCount += 1;
                addUnique(blockedReasons, "目标货代缺少匹配货物类别报价");
            }
            if (pendingLine.actualWeightKg != null) {
                option.actualWeightKg = option.actualWeightKg.add(pendingLine.actualWeightKg);
            }
            if (pendingLine.volumeCbm != null) {
                option.volumeCbm = option.volumeCbm.add(pendingLine.volumeCbm);
            }
            if (pendingLine.chargeableWeightKg != null) {
                option.chargeableWeightKg = option.chargeableWeightKg.add(pendingLine.chargeableWeightKg);
            }
            if (pendingLine.estimatedAmount != null) {
                option.estimatedTotalAmount = option.estimatedTotalAmount.add(pendingLine.estimatedAmount);
                option.currency = defaultText(option.currency, pendingLine.currency);
            } else {
                option.warningCount += 1;
                addUnique(blockedReasons, "报价规则不足，需人工复核");
            }
            if (pendingLine.minimumNotMet) {
                option.warningCount += 1;
                addUnique(blockedReasons, "未达到目标货代最低计费单位");
            }
            costSnapshots.add(pendingLine.costSnapshot());
        }
        option.actualWeightKg = zeroToNull(option.actualWeightKg, 3);
        option.volumeCbm = zeroToNull(option.volumeCbm, 4);
        option.chargeableWeightKg = zeroToNull(option.chargeableWeightKg, 3);
        option.estimatedTotalAmount = zeroToNull(option.estimatedTotalAmount, 4);
        option.avgUnitAmount = option.estimatedTotalAmount == null || option.totalQuantity == null || option.totalQuantity <= 0
                ? null
                : option.estimatedTotalAmount.divide(BigDecimal.valueOf(option.totalQuantity), 4, RoundingMode.HALF_UP);
        option.blockedReasonsJson = writeJson(blockedReasons);
        option.costSnapshotJson = writeJson(costSnapshots);
        option.evaluationStatus = blockedReasons.isEmpty() ? "READY" : "NEEDS_REVIEW";
        option.summaryJson = writeJson(shippingOptionSummary(definition, sources, lineGroups.values()));
        mapper.insertShippingSuggestionOption(option, operatorUserId);

        ShippingSuggestionOptionView optionView = toShippingSuggestionOptionView(option);
        for (PendingShippingLine pendingLine : lineGroups.values()) {
            ShippingSuggestionLineRecord line = pendingLine.toRecord(
                    option.id,
                    batch.id,
                    batch.ownerUserId,
                    mapper.nextShippingSuggestionLineId()
            );
            mapper.insertShippingSuggestionLine(line, operatorUserId);
            ShippingSuggestionLineView lineView = toShippingSuggestionLineView(line);
            for (PendingShippingSource pendingSource : pendingLine.sources) {
                ShippingSuggestionLineSourceRecord source = pendingSource.toRecord(
                        option.id,
                        line.id,
                        batch.id,
                        mapper.nextShippingSuggestionLineSourceId()
                );
                mapper.insertShippingSuggestionLineSource(source, operatorUserId);
                lineView.sources.add(toShippingSuggestionLineSourceView(source));
            }
            optionView.lines.add(lineView);
        }
        return optionView;
    }
}
