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

abstract class WarehouseMobileDecisionAssessmentSupport extends WarehouseMobileDecisionRequestSupport {

    protected WarehouseMobileDecisionAssessmentSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected List<String> mobileOptionForwarderCodes(String optionKey) {
        List<String> result = new ArrayList<>();
        for (String part : defaultText(optionKey, "").split("_")) {
            String code = normalizeForwarderCode(part);
            if (!StringUtils.hasText(code)) {
                continue;
            }
            addUnique(result, code);
        }
        return result;
    }

protected String preferredMobileAirForwarderCode(List<String> forwarderCodes) {
        for (String code : List.of("ZD", "ET")) {
            if (forwarderCodes.contains(code)) {
                return code;
            }
        }
        return forwarderCodes.stream().filter(this::isSupportedAirForwarderCode).findFirst().orElse("ZD");
    }

protected String preferredMobileSeaForwarderCode(List<String> forwarderCodes) {
        for (String code : List.of("ZD", "ET", "YT")) {
            if (forwarderCodes.contains(code)) {
                return code;
            }
        }
        return forwarderCodes.stream().filter(this::isSupportedSeaForwarderCode).findFirst().orElse("YT");
    }

protected ShippingDecisionOption assessMobileShippingDecisionOption(
            ShippingSuggestionOptionView view,
            ShippingOptionDefinition definition,
            MobileShippingDecisionRequest request
    ) {
        ShippingDecisionOption option = new ShippingDecisionOption();
        option.definition = definition;
        option.view = view;
        option.optionKey = mobileOptionKey(request.siteCode, request.transportMode, definition.targetForwarderCodes);
        List<String> hardBlockers = view.blockedReasons.stream()
                .filter(reason -> !"存在敏货，需确认目标货代是否接收".equals(reason)
                        && !"货物类别需人工复核".equals(reason)
                        && !"未达到目标货代最低计费单位".equals(reason))
                .collect(Collectors.toList());
        option.blockers.addAll(hardBlockers);
        if (view.estimatedTotalAmount == null && hardBlockers.isEmpty()) {
            addUnique(option.blockers, "报价规则不足，需人工复核");
        }
        if (view.blockedReasons.contains("存在敏货，需确认目标货代是否接收")) {
            addUnique(option.reviewReasons, "存在敏货，仓管需确认目标货代可接收");
        }
        if (view.blockedReasons.contains("货物类别需人工复核")) {
            addUnique(option.reviewReasons, "货物类别需人工复核");
        }
        if (view.blockedReasons.contains("未达到目标货代最低计费单位")) {
            addUnique(option.reviewReasons, "未达到目标货代最低计费单位");
        }
        if (!request.sensitiveConfirmed && !option.reviewReasons.isEmpty()) {
            option.decisionStatus = "REVIEW";
        } else {
            option.decisionStatus = option.blockers.isEmpty() ? "READY" : "BLOCKED";
        }
        return option;
    }

protected List<ShippingDecisionOption> rankedMobileShippingDecisionOptions(List<ShippingDecisionOption> candidates) {
        return candidates.stream()
                .filter(option -> !"BLOCKED".equals(option.decisionStatus))
                .sorted(Comparator
                        .comparing((ShippingDecisionOption option) -> !"READY".equals(option.decisionStatus))
                        .thenComparing(option -> option.view.estimatedTotalAmount == null
                                ? BigDecimal.valueOf(Long.MAX_VALUE)
                                : option.view.estimatedTotalAmount)
                        .thenComparing(option -> option.definition.score == null ? 0 : -option.definition.score))
                .collect(Collectors.toList());
    }

protected ShippingDecisionOption acceptedMobileShippingDecisionOption(
            ShippingDecisionEvaluation evaluation,
            String acceptedOptionKey
    ) {
        if (StringUtils.hasText(acceptedOptionKey)) {
            String normalized = acceptedOptionKey.trim().toUpperCase(Locale.ROOT);
            return evaluation.candidates.stream()
                    .filter(option -> normalized.equals(defaultText(option.optionKey, "").toUpperCase(Locale.ROOT))
                            || normalized.equals(defaultText(option.definition.optionType, "").toUpperCase(Locale.ROOT)))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("选择的物流方案已变化，请重新预览。"));
        }
        return evaluation.recommended;
    }

protected ShippingSuggestionOptionView persistedMobileDecisionOption(
            BusinessAccessContext access,
            ShippingBatchView batch,
            ShippingDecisionOption accepted
    ) {
        ShippingSuggestionOptionView persisted = emptyIfNull(batch.options).stream()
                .filter(option -> accepted.definition.optionType.equals(option.optionType))
                .findFirst()
                .orElse(null);
        if (persisted != null) {
            return persisted;
        }
        CreateShippingTargetOptionCommand command = new CreateShippingTargetOptionCommand();
        command.optionName = accepted.view.optionName;
        command.airForwarderCode = accepted.definition.airForwarderCode;
        command.seaForwarderCode = accepted.definition.seaForwarderCode;
        return createShippingTargetOption(access, batch.id, command);
    }

protected MobileShippingDecisionPreviewView toMobileShippingDecisionPreviewView(ShippingDecisionEvaluation evaluation) {
        MobileShippingDecisionPreviewView view = new MobileShippingDecisionPreviewView();
        view.decisionStatus = evaluation.decisionStatus;
        view.blockers.addAll(evaluation.blockers);
        view.reviewReasons.addAll(evaluation.reviewReasons);
        view.recommendedOption = evaluation.recommended == null
                ? null
                : toMobileShippingDecisionOptionView(evaluation.recommended, null);
        view.alternativeOption = evaluation.alternative == null
                ? null
                : toMobileShippingDecisionOptionView(evaluation.alternative, null);
        List<ShippingDecisionOption> optionSource = "COMBO".equals(evaluation.generationMode)
                ? evaluation.rankedOptions
                : evaluation.rankedOptions.stream().limit(2).collect(Collectors.toList());
        for (ShippingDecisionOption option : optionSource) {
            view.options.add(toMobileShippingDecisionOptionView(option, null));
        }
        return view;
    }

protected MobileShippingDecisionOptionView toMobileShippingDecisionOptionView(
            ShippingDecisionOption option,
            String persistedOptionId
    ) {
        MobileShippingDecisionOptionView view = new MobileShippingDecisionOptionView();
        view.optionKey = option.optionKey;
        view.optionId = persistedOptionId;
        view.optionName = option.view.optionName;
        view.decisionStatus = option.decisionStatus;
        view.forwarderNames.addAll(mobileForwarderNames(option.view));
        view.routeNames.addAll(mobileRouteNames(option.view));
        view.forwarderAllocations.addAll(mobileForwarderAllocations(option.view));
        view.estimatedTotalAmount = option.view.estimatedTotalAmount;
        view.avgUnitAmount = option.view.avgUnitAmount;
        view.currency = option.view.currency;
        view.actualWeightKg = option.view.actualWeightKg;
        view.volumeCbm = option.view.volumeCbm;
        view.chargeableWeightKg = option.view.chargeableWeightKg;
        view.blockers.addAll(option.blockers);
        view.reviewReasons.addAll(option.reviewReasons);
        for (ShippingSuggestionLineView line : emptyIfNull(option.view.lines)) {
            view.lines.add(toMobileShippingDecisionLineView(line));
        }
        if ("READY".equals(option.decisionStatus)) {
            view.reasons.add("成本最低");
        }
        return view;
    }

protected MobileShippingDecisionLineView toMobileShippingDecisionLineView(ShippingSuggestionLineView line) {
        MobileShippingDecisionLineView view = new MobileShippingDecisionLineView();
        view.id = line.id;
        view.productVariantId = line.productVariantId;
        view.partnerSku = line.partnerSku;
        view.title = line.productTitle;
        view.siteCode = line.siteCode;
        view.transportMode = line.actualTransportMode;
        view.fulfillmentType = line.fulfillmentType;
        view.sourcePartyName = line.sourcePartyName;
        view.quantity = line.quantity;
        view.targetForwarderCode = line.targetForwarderCode;
        view.targetForwarderName = line.targetForwarderName;
        view.routeCode = line.routeCode;
        view.routeName = line.routeName;
        view.cargoCategoryCode = line.cargoCategoryCode;
        view.cargoCategoryName = line.cargoCategoryName;
        view.quoteCargoCategoryCode = line.quoteCargoCategoryCode;
        view.quoteCargoCategoryName = line.quoteCargoCategoryName;
        view.cargoCategoryReviewRequired = line.cargoCategoryReviewRequired;
        view.actualWeightKg = line.actualWeightKg;
        view.volumeCbm = line.volumeCbm;
        view.chargeableWeightKg = line.chargeableWeightKg;
        view.estimatedAmount = line.estimatedAmount;
        view.currency = line.currency;
        if (isSpecMissing(line.specStatus)) {
            addUnique(view.blockers, "规格缺失");
        }
        if (!StringUtils.hasText(line.routeCode) || line.estimatedAmount == null) {
            addUnique(view.blockers, "无匹配报价或路线");
        }
        if (Boolean.TRUE.equals(line.cargoCategoryReviewRequired)) {
            addUnique(view.reviewReasons, "货物类别需人工复核");
        }
        return view;
    }
}
