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

abstract class WarehouseDispatchPlanOperations extends WarehouseReceiptQueryOperations {

    protected WarehouseDispatchPlanOperations(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

@Transactional
    public DispatchPlanView createDispatchPlan(BusinessAccessContext access, CreateDispatchPlanCommand command) {
        if (command == null || command.sources == null || command.sources.isEmpty()) {
            throw new IllegalArgumentException("请选择可发运商品。");
        }
        LinkedHashMap<Long, List<DispatchPlanSourceCommand>> requested = new LinkedHashMap<>();
        for (DispatchPlanSourceCommand source : command.sources) {
            if (source == null || source.fulfillmentBalanceId == null || nonNull(source.quantity) <= 0) {
                continue;
            }
            requested.computeIfAbsent(source.fulfillmentBalanceId, ignored -> new ArrayList<>()).add(source);
        }
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("请选择可发运商品。");
        }

        List<FulfillmentBalanceRecord> balances = mapper.selectBalancesForUpdate(new ArrayList<>(requested.keySet()));
        if (balances.size() != requested.size()) {
            throw new IllegalArgumentException("可发运来源不存在或已被占用。");
        }
        Long operatorUserId = access.getSessionUserId();
        Long ownerUserId = ownerUserId(access);
        Long planId = mapper.nextDispatchPlanId();
        String planNo = "DP-" + planId;

        Map<String, PendingDispatchLine> lineGroups = new LinkedHashMap<>();
        for (FulfillmentBalanceRecord balance : balances) {
            List<DispatchPlanSourceCommand> sourceCommands = requested.get(balance.id);
            if (!canUseBalance(access, balance)) {
                throw new IllegalArgumentException("当前账号不能发运所选来源。");
            }
            if (logisticsQuoteBlocks(balance)) {
                throw new IllegalArgumentException(LOGISTICS_QUOTE_BLOCK_MESSAGE);
            }
            int totalQuantity = sourceCommands.stream().mapToInt(source -> nonNull(source.quantity)).sum();
            if (totalQuantity > nonNull(balance.availableQuantity)) {
                throw new IllegalArgumentException(balance.partnerSku + " 可发运数量不足。");
            }
            int reserved = mapper.reserveBalance(balance.id, totalQuantity, operatorUserId);
            if (reserved != 1) {
                throw new IllegalArgumentException(balance.partnerSku + " 可发运数量不足或已被占用。");
            }
            String fulfillmentType = normalizeFulfillmentType(balance.fulfillmentType);
            String specStatus = defaultText(balance.specStatus, "READY");
            for (DispatchPlanSourceCommand source : sourceCommands) {
                int quantity = nonNull(source.quantity);
                String actualTransportMode = normalizeTransportMode(
                        StringUtils.hasText(source.actualTransportMode)
                                ? source.actualTransportMode
                                : balance.plannedTransportMode
                );
                String key = dispatchLineKey(balance, actualTransportMode, fulfillmentType, specStatus);
                lineGroups.computeIfAbsent(key, ignored -> new PendingDispatchLine(
                        balance,
                        actualTransportMode,
                        fulfillmentType,
                        specStatus
                )).sources.add(new PendingDispatchSource(balance, quantity));
            }
        }

        DispatchPlanView view = new DispatchPlanView();
        view.id = String.valueOf(planId);
        view.ownerUserId = ownerUserId;
        view.planNo = planNo;
        view.status = "DRAFT";
        view.totalQuantity = 0;

        LinkedHashSet<String> skuKeys = new LinkedHashSet<>();
        for (PendingDispatchLine pendingLine : lineGroups.values()) {
            DispatchPlanLineRecord line = pendingLine.toRecord(planId, ownerUserId, mapper.nextDispatchLineId());
            mapper.insertDispatchPlanLine(line, operatorUserId);
            DispatchPlanLineView lineView = toDispatchLineView(line);
            for (PendingDispatchSource pendingSource : pendingLine.sources) {
                DispatchPlanLineSourceRecord sourceRow = pendingSource.toRecord(
                        planId,
                        line.id,
                        ownerUserId,
                        mapper.nextDispatchSourceId(),
                        pendingLine.fulfillmentType
                );
                mapper.insertDispatchPlanLineSource(sourceRow, operatorUserId);
                lineView.sources.add(toDispatchSourceView(sourceRow));
            }
            view.lines.add(lineView);
            view.totalQuantity += nonNull(line.quantity);
            skuKeys.add(productIdentityKey(line));
        }

        view.itemCount = view.lines.size();
        view.skuCount = skuKeys.size();
        DispatchPlanRecord plan = new DispatchPlanRecord();
        plan.id = planId;
        plan.ownerUserId = ownerUserId;
        plan.planNo = planNo;
        plan.status = "DRAFT";
        plan.remark = trimToNull(command.remark);
        plan.handoffGenerationNo = 0;
        plan.itemCount = view.itemCount;
        plan.skuCount = view.skuCount;
        plan.totalQuantity = view.totalQuantity;
        plan.siteSummaryJson = writeJson(siteSummary(view.lines));
        plan.transportSummaryJson = writeJson(transportSummary(view.lines));
        mapper.insertDispatchPlan(plan, operatorUserId);
        log(planId, "CREATE_DISPATCH_PLAN", operatorUserId, null, "DRAFT", planNo);
        return toDispatchPlanView(plan);
    }

@Transactional(readOnly = true)
    public List<DispatchPlanView> listDispatchPlans(BusinessAccessContext access) {
        Long ownerUserId = ownerUserId(access);
        return mapper.listDispatchPlans(ownerUserId).stream()
                .map(this::toDispatchPlanView)
                .collect(Collectors.toList());
    }

@Transactional
    public DispatchPlanView readyForLogistics(BusinessAccessContext access, String dispatchPlanId) {
        Long parsedPlanId = parseLongId(dispatchPlanId, "发运计划不存在或已删除。");
        DispatchPlanRecord plan = requireDispatchPlanAccess(access, parsedPlanId);
        if (!"DRAFT".equals(plan.status) && !"HANDOFF_FAILED".equals(plan.status)) {
            throw new IllegalArgumentException("只有草稿或物流交接失败的发运计划可以提交物流。");
        }
        int nextGeneration = nonNull(plan.handoffGenerationNo) + 1;
        String handoffRequestNo = "WDH-" + plan.id + "-" + nextGeneration;
        mapper.updateDispatchPlanReady(
                plan.id,
                plan.ownerUserId,
                nextGeneration,
                handoffRequestNo,
                access.getSessionUserId()
        );
        log(plan.id, "READY_FOR_LOGISTICS", access.getSessionUserId(), plan.status, "READY_FOR_LOGISTICS", handoffRequestNo);
        DispatchPlanRecord updated = mapper.selectDispatchPlanById(plan.id);
        if (updated == null) {
            updated = plan;
            updated.status = "READY_FOR_LOGISTICS";
            updated.handoffGenerationNo = nextGeneration;
            updated.handoffRequestNo = handoffRequestNo;
        }
        return toDispatchPlanView(updated);
    }

@Transactional(readOnly = true)
    public LogisticsHandoffView getLogisticsHandoff(BusinessAccessContext access, String dispatchPlanId) {
        DispatchPlanRecord plan = requireDispatchPlanAccess(access, parseLongId(dispatchPlanId, "发运计划不存在或已删除。"));
        LogisticsHandoffView view = new LogisticsHandoffView();
        view.dispatchPlanId = String.valueOf(plan.id);
        view.dispatchPlanNo = plan.planNo;
        view.status = plan.status;
        view.handoffGenerationNo = nonNull(plan.handoffGenerationNo);
        view.handoffRequestNo = plan.handoffRequestNo;
        view.lines = toDispatchPlanView(plan).lines;
        return view;
    }

@Transactional
    public DispatchPlanView reopenDraft(BusinessAccessContext access, String dispatchPlanId) {
        DispatchPlanRecord plan = requireDispatchPlanAccess(access, parseLongId(dispatchPlanId, "发运计划不存在或已删除。"));
        mapper.reopenDispatchPlanDraft(plan.id, plan.ownerUserId, access.getSessionUserId());
        log(plan.id, "REOPEN_DRAFT", access.getSessionUserId(), plan.status, "DRAFT", plan.planNo);
        DispatchPlanRecord updated = mapper.selectDispatchPlanById(plan.id);
        return toDispatchPlanView(updated == null ? plan : updated);
    }

@Transactional
    public DispatchPlanView markLogisticsHandoffSuccess(BusinessAccessContext access, String handoffRequestNo) {
        String requestNo = requiredText(handoffRequestNo, "缺少物流交接编号。");
        DispatchPlanRecord plan = requireHandoffAccess(access, requestNo);
        int changed = mapper.markDispatchPlanHandoffSuccess(requestNo, access.getSessionUserId());
        if (changed > 0) {
            for (DispatchPlanLineSourceRecord source : mapper.listDispatchLineSources(plan.id)) {
                mapper.moveReservedToLogisticsHandoff(
                        source.fulfillmentBalanceId,
                        nonNull(source.quantity),
                        access.getSessionUserId()
                );
            }
            log(plan.id, "HANDOFF_SUCCESS", access.getSessionUserId(), plan.status, "LOGISTICS_REQUESTED", requestNo);
        }
        DispatchPlanRecord updated = mapper.selectDispatchPlanByHandoffRequest(requestNo);
        return toDispatchPlanView(updated == null ? plan : updated);
    }

@Transactional
    public DispatchPlanView markLogisticsHandoffFailure(BusinessAccessContext access, HandoffFailureCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少物流交接失败参数。");
        }
        String requestNo = requiredText(command.handoffRequestNo, "缺少物流交接编号。");
        DispatchPlanRecord plan = requireHandoffAccess(access, requestNo);
        mapper.markDispatchPlanHandoffFailed(requestNo, trimToNull(command.errorMessage), access.getSessionUserId());
        log(plan.id, "HANDOFF_FAILED", access.getSessionUserId(), plan.status, "HANDOFF_FAILED", command.errorMessage);
        DispatchPlanRecord updated = mapper.selectDispatchPlanByHandoffRequest(requestNo);
        return toDispatchPlanView(updated == null ? plan : updated);
    }
}
