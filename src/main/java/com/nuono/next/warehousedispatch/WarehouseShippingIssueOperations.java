package com.nuono.next.warehousedispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreatePackingListCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.IssueShippingBatchCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionOptionRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.IssuedShippingBatchView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderView;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

abstract class WarehouseShippingIssueOperations extends WarehousePackingHandoffOperations {

    protected WarehouseShippingIssueOperations(
            WarehouseDispatchMapper mapper,
            ObjectMapper objectMapper
    ) {
        super(mapper, objectMapper);
    }

    @Transactional
    public IssuedShippingBatchView issueShippingBatch(
            BusinessAccessContext access,
            String shippingBatchId,
            IssueShippingBatchCommand command
    ) {
        Long batchId = parseLongId(shippingBatchId, "发货批次不存在或已删除。");
        Long optionId = parseLongId(
                command == null ? null : command.optionId,
                "请选择有效的货运计划方案。"
        );
        ShippingBatchRecord batch = requireShippingBatchAccessForUpdate(access, batchId);
        ShippingSuggestionOptionRecord option = mapper.selectShippingSuggestionOptionById(optionId);
        if (option == null || !batch.id.equals(option.batchId)) {
            throw new IllegalArgumentException("货运计划方案不存在或不属于该批次。");
        }
        DispatchPlanRecord linkedPlan = requireLinkedPlanAccess(access, batch);

        batch = selectOptionIfNeeded(access, batch, optionId);
        List<OutboundOrderRecord> existingOrders = emptyIfNull(
                mapper.listOutboundOrdersByBatch(
                        batch.id,
                        warehouseBusinessScope(access).storeOwnerUserIds()
                )
        );
        List<OutboundOrderView> outboundOrders = issueOutboundOrders(
                access,
                batch,
                linkedPlan,
                existingOrders
        );

        IssuedShippingBatchView result = new IssuedShippingBatchView();
        result.outboundOrders.addAll(outboundOrders);
        for (OutboundOrderView outboundOrder : outboundOrders) {
            List<PackingListRecord> packingLists = emptyIfNull(
                    mapper.listPackingListsByOutboundOrder(
                            Long.valueOf(outboundOrder.id),
                            warehouseBusinessScope(access).storeOwnerUserIds()
                    )
            );
            if (packingLists.isEmpty()) {
                result.packingLists.add(createPackingList(
                        access,
                        outboundOrder.id,
                        new CreatePackingListCommand()
                ));
                outboundOrder.status = "PACKING";
            } else {
                packingLists.stream()
                        .map(this::toPackingListDetail)
                        .forEach(result.packingLists::add);
            }
        }
        ShippingBatchRecord current = mapper.selectShippingBatchById(batch.id);
        result.shippingBatch = toShippingBatchDetail(current == null ? batch : current);
        return result;
    }

    private DispatchPlanRecord requireLinkedPlanAccess(
            BusinessAccessContext access,
            ShippingBatchRecord batch
    ) {
        if (batch.dispatchPlanId == null) {
            return null;
        }
        DispatchPlanRecord plan = requireDispatchPlanAccess(access, batch.dispatchPlanId);
        if (!batch.ownerUserId.equals(plan.ownerUserId)
                || !Set.of(
                "READY_FOR_LOGISTICS",
                "HANDOFF_FAILED",
                "LOGISTICS_REQUESTED"
        ).contains(plan.status)) {
            throw new WarehouseInventoryStateConflictException(
                    "发运计划与物流批次状态不一致，请刷新后重试。"
            );
        }
        return plan;
    }

    private ShippingBatchRecord selectOptionIfNeeded(
            BusinessAccessContext access,
            ShippingBatchRecord batch,
            Long optionId
    ) {
        if ("DRAFT".equals(batch.status)
                || ("OPTION_SELECTED".equals(batch.status)
                && !optionId.equals(batch.selectedOptionId))) {
            selectShippingOption(access, String.valueOf(batch.id), String.valueOf(optionId));
            ShippingBatchRecord selected = mapper.selectShippingBatchById(batch.id);
            if (selected != null) {
                return selected;
            }
            batch.selectedOptionId = optionId;
            batch.status = "OPTION_SELECTED";
            return batch;
        }
        if (!Set.of("OPTION_SELECTED", "OUTBOUND_CREATED").contains(batch.status)) {
            throw new WarehouseInventoryStateConflictException(
                    "当前发货批次状态不能下发仓库单，请刷新后重试。"
            );
        }
        if (!optionId.equals(batch.selectedOptionId)) {
            throw new WarehouseInventoryStateConflictException(
                    "当前批次已按其他物流方案下发，不能重复切换方案。"
            );
        }
        return batch;
    }

    private List<OutboundOrderView> issueOutboundOrders(
            BusinessAccessContext access,
            ShippingBatchRecord batch,
            DispatchPlanRecord linkedPlan,
            List<OutboundOrderRecord> existingOrders
    ) {
        if (existingOrders.isEmpty()) {
            if ("OUTBOUND_CREATED".equals(batch.status)
                    || (linkedPlan != null
                    && "LOGISTICS_REQUESTED".equals(linkedPlan.status))) {
                throw new WarehouseInventoryStateConflictException(
                        "发货批次已下发但缺少出库单，请联系管理员修复数据。"
                );
            }
            return createOutboundOrders(access, String.valueOf(batch.id));
        }
        if ("OPTION_SELECTED".equals(batch.status)) {
            if (mapper.updateShippingBatchOutboundCreated(
                    batch.id,
                    batch.ownerUserId,
                    batch.selectedOptionId,
                    access.getSessionUserId()
            ) != 1) {
                throw new WarehouseInventoryStateConflictException(
                        "发货批次状态已变化，请刷新后重试。"
                );
            }
            batch.status = "OUTBOUND_CREATED";
        }
        return existingOrders.stream()
                .map(this::toOutboundOrderDetail)
                .collect(Collectors.toList());
    }
}
