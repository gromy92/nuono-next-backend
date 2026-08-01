package com.nuono.next.warehousedispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.DispatchPlanView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ShippingBatchView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

abstract class WarehouseLinkedShippingBatchOperations extends WarehouseShippingBatchOperations {

    protected WarehouseLinkedShippingBatchOperations(
            WarehouseDispatchMapper mapper,
            ObjectMapper objectMapper
    ) {
        super(mapper, objectMapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DispatchPlanView> listDispatchPlans(BusinessAccessContext access) {
        List<DispatchPlanView> plans = super.listDispatchPlans(access);
        if (plans.isEmpty()) {
            return plans;
        }
        Map<Long, ShippingBatchRecord> batchByPlanId = emptyIfNull(
                mapper.listLatestShippingBatchSummariesByDispatchPlanIds(
                        plans.stream().map(plan -> Long.valueOf(plan.id)).collect(Collectors.toList()),
                        warehouseBusinessScope(access).storeOwnerUserIds()
                )
        ).stream().collect(Collectors.toMap(
                batch -> batch.dispatchPlanId,
                batch -> batch,
                (left, right) -> left,
                LinkedHashMap::new
        ));
        for (DispatchPlanView plan : plans) {
            ShippingBatchRecord batch = batchByPlanId.get(Long.valueOf(plan.id));
            if (batch != null && plan.ownerUserId.equals(batch.ownerUserId)) {
                plan.currentShippingBatch = toShippingBatchView(batch);
            }
        }
        return plans;
    }

    @Transactional
    public ShippingBatchView createShippingBatchFromDispatchPlan(
            BusinessAccessContext access,
            String dispatchPlanId
    ) {
        DispatchPlanRecord plan = requireDispatchPlanAccessForUpdate(
                access,
                parseLongId(dispatchPlanId, "发运计划不存在或已删除。")
        );
        ShippingBatchRecord existing = mapper.selectLatestShippingBatchByDispatchPlan(plan.id);
        if (existing != null) {
            validateLinkedBatch(plan, requireShippingBatchAggregateAccess(access, existing));
            return toShippingBatchDetail(existing);
        }
        if (!List.of("DRAFT", "READY_FOR_LOGISTICS", "HANDOFF_FAILED").contains(plan.status)) {
            throw new WarehouseInventoryStateConflictException(
                    "当前发运计划状态不能生成物流计划，请刷新后重试。"
            );
        }

        List<DispatchPlanLineRecord> lines = emptyIfNull(mapper.listDispatchPlanLines(plan.id));
        List<DispatchPlanLineSourceRecord> sources = emptyIfNull(mapper.listDispatchLineSources(plan.id));
        PlanSnapshot snapshot = validatePlanSnapshot(access, plan, lines, sources);
        Long operatorUserId = access.getSessionUserId();
        Long batchId = mapper.nextShippingBatchId();
        List<ShippingBatchSourceRecord> batchSources = new ArrayList<>();
        for (DispatchPlanLineSourceRecord source : sources) {
            DispatchPlanLineRecord line = snapshot.lineById.get(source.dispatchPlanLineId);
            FulfillmentBalanceRecord balance = snapshot.balanceById.get(source.fulfillmentBalanceId);
            ShippingBatchSourceRecord batchSource = toShippingBatchSourceRecord(
                    batchId,
                    plan.ownerUserId,
                    mapper.nextShippingBatchSourceId(),
                    balance,
                    source.quantity
            );
            batchSource.siteCode = line.siteCode;
            batchSource.plannedTransportMode = line.actualTransportMode;
            batchSources.add(batchSource);
        }
        requireSingleLogisticsPartition(batchSources.stream()
                .map(source -> logisticsPartitionKey(source.siteCode, source.plannedTransportMode))
                .collect(Collectors.toList()));

        ShippingBatchRecord batch = shippingBatch(plan, batchId, batchSources);
        mapper.insertShippingBatch(batch, operatorUserId);
        for (ShippingBatchSourceRecord source : batchSources) {
            mapper.insertShippingBatchSource(source, operatorUserId);
        }
        String nextPlanStatus = ensurePlanReady(plan, operatorUserId);

        ShippingBatchView view = toShippingBatchView(batch);
        batchSources.stream().map(this::toShippingBatchSourceView).forEach(view.sources::add);
        view.options.addAll(createDefaultShippingSuggestionOptions(batch, batchSources, operatorUserId));
        view.optionCount = view.options.size();
        log(plan.id, "GENERATE_SHIPPING_BATCH", operatorUserId, plan.status, nextPlanStatus, batch.batchNo);
        return view;
    }

    private PlanSnapshot validatePlanSnapshot(
            BusinessAccessContext access,
            DispatchPlanRecord plan,
            List<DispatchPlanLineRecord> lines,
            List<DispatchPlanLineSourceRecord> sources
    ) {
        if (lines.isEmpty() || sources.isEmpty()) {
            throw new WarehouseInventoryStateConflictException("发运计划商品来源不完整，请刷新后重试。");
        }
        Map<Long, DispatchPlanLineRecord> lineById = new LinkedHashMap<>();
        for (DispatchPlanLineRecord line : lines) {
            if (line == null
                    || line.id == null
                    || !plan.id.equals(line.dispatchPlanId)
                    || !plan.ownerUserId.equals(line.ownerUserId)
                    || lineById.put(line.id, line) != null) {
                throw new WarehouseInventoryStateConflictException(
                        "发运计划商品来源不完整，请刷新后重试。"
                );
            }
        }
        Map<Long, Integer> quantityByLine = new LinkedHashMap<>();
        Map<Long, Integer> quantityByBalance = new TreeMap<>();
        try {
            for (DispatchPlanLineSourceRecord source : sources) {
                DispatchPlanLineRecord line = source == null ? null : lineById.get(source.dispatchPlanLineId);
                if (source == null || line == null || source.quantity == null || source.quantity <= 0
                        || !plan.id.equals(source.dispatchPlanId)
                        || !plan.ownerUserId.equals(source.ownerUserId)
                        || source.fulfillmentBalanceId == null) {
                    throw new WarehouseInventoryStateConflictException(
                            "发运计划商品来源不完整，请刷新后重试。"
                    );
                }
                quantityByLine.merge(line.id, source.quantity, Math::addExact);
                quantityByBalance.merge(source.fulfillmentBalanceId, source.quantity, Math::addExact);
            }
            int total = 0;
            for (DispatchPlanLineRecord line : lines) {
                Integer quantity = quantityByLine.get(line.id);
                if (line.quantity == null || !line.quantity.equals(quantity)) {
                    throw new WarehouseInventoryStateConflictException(
                            "发运计划商品数量不一致，请刷新后重试。"
                    );
                }
                total = Math.addExact(total, line.quantity);
            }
            if (!Integer.valueOf(total).equals(plan.totalQuantity)) {
                throw new WarehouseInventoryStateConflictException(
                        "发运计划商品数量不一致，请刷新后重试。"
                );
            }
        } catch (ArithmeticException exception) {
            throw new WarehouseInventoryStateConflictException(
                    "发运计划商品数量不一致，请刷新后重试。"
            );
        }

        Map<Long, FulfillmentBalanceRecord> balanceById = new LinkedHashMap<>();
        for (FulfillmentBalanceRecord balance : emptyIfNull(
                mapper.selectBalancesForUpdate(new ArrayList<>(quantityByBalance.keySet()))
        )) {
            if (balance == null
                    || balance.id == null
                    || balanceById.put(balance.id, balance) != null) {
                throw new WarehouseInventoryStateConflictException(
                        "发运计划来源库存不存在或已变化。"
                );
            }
        }
        if (balanceById.size() != quantityByBalance.size()) {
            throw new WarehouseInventoryStateConflictException("发运计划来源库存不存在或已变化。");
        }
        for (Map.Entry<Long, Integer> entry : quantityByBalance.entrySet()) {
            FulfillmentBalanceRecord balance = balanceById.get(entry.getKey());
            if (balance == null || !plan.ownerUserId.equals(balance.ownerUserId)
                    || !canUseBalance(access, balance)
                    || nonNull(balance.reservedQuantity) < entry.getValue()) {
                throw new WarehouseInventoryStateConflictException("发运计划预留库存不足或已变化。");
            }
        }
        return new PlanSnapshot(lineById, balanceById);
    }

    private ShippingBatchRecord shippingBatch(
            DispatchPlanRecord plan,
            Long batchId,
            List<ShippingBatchSourceRecord> sources
    ) {
        ShippingBatchRecord batch = new ShippingBatchRecord();
        batch.id = batchId;
        batch.ownerUserId = plan.ownerUserId;
        batch.dispatchPlanId = plan.id;
        batch.batchNo = shippingBatchNo(batchId, sources);
        batch.status = "DRAFT";
        batch.sourceCount = sources.size();
        batch.skuCount = shippingSkuCount(sources);
        batch.totalQuantity = plan.totalQuantity;
        batch.storeSummaryJson = writeJson(shippingStoreSummary(sources));
        batch.siteSummaryJson = writeJson(shippingSiteSummary(sources));
        batch.transportSummaryJson = writeJson(shippingPlannedTransportSummary(sources));
        batch.originSummaryJson = writeJson(shippingOriginSummary(sources));
        batch.remark = "来自发运计划 " + plan.planNo;
        return batch;
    }

    private String ensurePlanReady(DispatchPlanRecord plan, Long operatorUserId) {
        if ("READY_FOR_LOGISTICS".equals(plan.status)) {
            requiredText(plan.handoffRequestNo, "发运计划缺少物流交接编号，请重新提交。");
            if (nonNull(plan.handoffGenerationNo) <= 0) {
                throw new WarehouseInventoryStateConflictException(
                        "发运计划物流交接代次无效，请重新提交。"
                );
            }
            return plan.status;
        }
        int generation = nonNull(plan.handoffGenerationNo) + 1;
        String requestNo = "WDH-" + plan.id + "-" + generation;
        if (mapper.updateDispatchPlanReady(
                plan.id, plan.ownerUserId, generation, requestNo, operatorUserId
        ) != 1) {
            throw new WarehouseInventoryStateConflictException("发运计划状态已变化，请刷新后重试。");
        }
        return "READY_FOR_LOGISTICS";
    }

    private void validateLinkedBatch(DispatchPlanRecord plan, ShippingBatchRecord batch) {
        if (!plan.id.equals(batch.dispatchPlanId)
                || !plan.ownerUserId.equals(batch.ownerUserId)) {
            throw new WarehouseInventoryStateConflictException("发运计划与物流批次关联不一致。");
        }
    }

    private static final class PlanSnapshot {
        private final Map<Long, DispatchPlanLineRecord> lineById;
        private final Map<Long, FulfillmentBalanceRecord> balanceById;

        private PlanSnapshot(
                Map<Long, DispatchPlanLineRecord> lineById,
                Map<Long, FulfillmentBalanceRecord> balanceById
        ) {
            this.lineById = lineById;
            this.balanceById = balanceById;
        }
    }
}
