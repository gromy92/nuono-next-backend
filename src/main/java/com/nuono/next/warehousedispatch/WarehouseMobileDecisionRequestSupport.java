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

abstract class WarehouseMobileDecisionRequestSupport extends WarehousePurchaseComparisonSupport {

    protected WarehouseMobileDecisionRequestSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected ShippingDecisionEvaluation evaluateMobileShippingDecision(
            BusinessAccessContext access,
            MobileShippingDecisionRequest request,
            List<FulfillmentBalanceRecord> balances
    ) {
        List<String> sourceBlockers = new ArrayList<>();
        List<ShippingBatchSourceRecord> sources = buildMobileShippingDecisionSources(access, request, balances, sourceBlockers);
        ShippingDecisionEvaluation evaluation = new ShippingDecisionEvaluation();
        evaluation.sources = sources;
        evaluation.generationMode = request.generationMode;
        evaluation.blockers.addAll(sourceBlockers);
        if (!sourceBlockers.isEmpty()) {
            evaluation.decisionStatus = "BLOCKED";
            return evaluation;
        }

        String segmentKey = request.siteCode + "|" + request.transportMode;
        for (ShippingOptionDefinition definition : mobileShippingCandidateDefinitions(request)) {
            ShippingSuggestionOptionView option = evaluateShippingOptionPreview(segmentKey, sources, definition);
            evaluation.candidates.add(assessMobileShippingDecisionOption(option, definition, request));
        }
        evaluation.rankedOptions.addAll(rankedMobileShippingDecisionOptions(evaluation.candidates));
        evaluation.recommended = evaluation.rankedOptions.isEmpty() ? null : evaluation.rankedOptions.get(0);
        evaluation.alternative = evaluation.rankedOptions.size() < 2 ? null : evaluation.rankedOptions.get(1);
        if (evaluation.recommended == null) {
            evaluation.decisionStatus = "BLOCKED";
            for (ShippingDecisionOption candidate : evaluation.candidates) {
                mergeDefects(evaluation.blockers, candidate.blockers);
            }
            if (evaluation.blockers.isEmpty()) {
                addUnique(evaluation.blockers, "没有可计价的发运方案");
            }
        } else {
            evaluation.decisionStatus = evaluation.recommended.decisionStatus;
            evaluation.blockers.addAll(evaluation.recommended.blockers);
            evaluation.reviewReasons.addAll(evaluation.recommended.reviewReasons);
        }
        return evaluation;
    }

protected MobileShippingDecisionRequest mobileShippingDecisionRequest(MobileShippingDecisionPreviewCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少物流计划参数。");
        }
        String siteCode = requiredText(command.siteCode, "请选择站点。").toUpperCase(Locale.ROOT);
        if (!"SA".equals(siteCode) && !"AE".equals(siteCode)) {
            throw new IllegalArgumentException("暂只支持沙特和阿联酋发运。");
        }
        String transportMode = normalizeTransportMode(requiredText(command.transportMode, "请选择运输方式。"));
        if (!TRANSPORT_AIR.equals(transportMode) && !TRANSPORT_SEA.equals(transportMode)) {
            throw new IllegalArgumentException("请选择空运或海运。");
        }
        LinkedHashMap<Long, Integer> requested = new LinkedHashMap<>();
        for (ShippingBatchSourceCommand source : emptyIfNull(command.sources)) {
            if (source == null || source.fulfillmentBalanceId == null || nonNull(source.quantity) <= 0) {
                continue;
            }
            requested.merge(source.fulfillmentBalanceId, nonNull(source.quantity), Integer::sum);
        }
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("请选择可发运商品。");
        }
        String generationMode = "COMBO".equalsIgnoreCase(defaultText(command.generationMode, "AUTO"))
                ? "COMBO"
                : "AUTO";
        List<String> targetForwarderCodes = emptyIfNull(command.targetForwarderCodes).stream()
                .map(this::normalizeForwarderCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        List<String> targetOptionKeys = emptyIfNull(command.targetOptionKeys).stream()
                .map(value -> defaultText(value, "").trim().toUpperCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        return new MobileShippingDecisionRequest(
                siteCode,
                transportMode,
                Boolean.TRUE.equals(command.sensitiveConfirmed),
                generationMode,
                targetForwarderCodes,
                targetOptionKeys,
                requested
        );
    }

protected List<ShippingBatchSourceRecord> buildMobileShippingDecisionSources(
            BusinessAccessContext access,
            MobileShippingDecisionRequest request,
            List<FulfillmentBalanceRecord> balances,
            List<String> blockers
    ) {
        if (balances == null || balances.size() != request.requested.size()) {
            throw new IllegalArgumentException("可发运来源不存在或已被占用。");
        }
        Long ownerUserId = ownerUserId(access);
        List<ShippingBatchSourceRecord> sources = new ArrayList<>();
        long transientSourceId = -1L;
        for (FulfillmentBalanceRecord balance : balances) {
            if (!canUseBalance(access, balance)) {
                throw new IllegalArgumentException("当前账号不能发运所选来源。");
            }
            int quantity = request.requested.getOrDefault(balance.id, 0);
            if (quantity <= 0 || quantity > nonNull(balance.availableQuantity)) {
                throw new IllegalArgumentException(balance.partnerSku + " 可发运数量不足。");
            }
            if (!request.siteCode.equals(effectiveSiteCode(balance))) {
                throw new IllegalArgumentException(balance.partnerSku + " 不属于当前站点发运范围。");
            }
            if (!request.transportMode.equals(effectiveTransportMode(balance))) {
                throw new IllegalArgumentException(balance.partnerSku + " 不属于当前运输方式发运范围。");
            }
            sources.add(toShippingBatchSourceRecord(
                    null,
                    ownerUserId,
                    transientSourceId--,
                    balance,
                    quantity
            ));
        }
        return sources;
    }

protected List<ShippingOptionDefinition> mobileShippingCandidateDefinitions(MobileShippingDecisionRequest request) {
        if (!"COMBO".equals(request.generationMode)) {
            return DEFAULT_SHIPPING_OPTIONS;
        }
        List<ShippingOptionDefinition> candidates = new ArrayList<>();
        if (!request.targetOptionKeys.isEmpty()) {
            for (String optionKey : request.targetOptionKeys) {
                ShippingOptionDefinition definition = mobileForwarderOption(request, optionKey);
                if (definition != null) {
                    candidates.add(definition);
                }
            }
        } else {
            for (String code : request.targetForwarderCodes) {
                ShippingOptionDefinition definition = mobileSingleForwarderOption(request, code);
                if (definition != null) {
                    candidates.add(definition);
                }
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("当前站点和运输方式没有可用的所选货代。");
        }
        return candidates;
    }

protected ShippingOptionDefinition mobileForwarderOption(MobileShippingDecisionRequest request, String optionKey) {
        List<String> forwarderCodes = mobileOptionForwarderCodes(optionKey);
        if (forwarderCodes.isEmpty()) {
            return null;
        }
        if (forwarderCodes.size() == 1) {
            return mobileSingleForwarderOption(request, forwarderCodes.get(0));
        }
        String transportMode = normalizeTransportMode(request.transportMode);
        if (TRANSPORT_AIR.equals(transportMode)
                && forwarderCodes.stream().noneMatch(this::isSupportedAirForwarderCode)) {
            return null;
        }
        if (TRANSPORT_SEA.equals(transportMode)
                && forwarderCodes.stream().noneMatch(this::isSupportedSeaForwarderCode)) {
            return null;
        }
        String airForwarderCode = preferredMobileAirForwarderCode(forwarderCodes);
        String seaForwarderCode = preferredMobileSeaForwarderCode(forwarderCodes);
        List<String> forwarderNames = forwarderCodes.stream()
                .map(this::forwarderName)
                .collect(Collectors.toList());
        return new ShippingOptionDefinition(
                mobileOptionKey(request.siteCode, request.transportMode, forwarderCodes),
                String.join("+", forwarderNames),
                80,
                "COMBINATION",
                forwarderCodes,
                forwarderNames,
                false,
                airForwarderCode,
                seaForwarderCode
        );
    }

protected ShippingOptionDefinition mobileSingleForwarderOption(MobileShippingDecisionRequest request, String forwarderCode) {
        String code = normalizeForwarderCode(forwarderCode);
        if (!StringUtils.hasText(code)) {
            return null;
        }
        if (TRANSPORT_AIR.equals(request.transportMode) && !isSupportedAirForwarderCode(code)) {
            return null;
        }
        if (TRANSPORT_SEA.equals(request.transportMode) && !isSupportedSeaForwarderCode(code)) {
            return null;
        }
        String optionName = forwarderName(code) + (TRANSPORT_AIR.equals(request.transportMode) ? "空运" : "海运");
        return new ShippingOptionDefinition(
                mobileOptionKey(request.siteCode, request.transportMode, List.of(code)),
                optionName,
                80,
                "SINGLE",
                List.of(code),
                List.of(forwarderName(code)),
                false,
                code,
                code
        );
    }
}
