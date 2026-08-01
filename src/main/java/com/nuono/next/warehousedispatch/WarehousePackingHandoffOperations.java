package com.nuono.next.warehousedispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingListView;
import java.util.Objects;
import java.util.Set;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

abstract class WarehousePackingHandoffOperations extends WarehousePackingOperations {

    protected WarehousePackingHandoffOperations(
            WarehouseDispatchMapper mapper,
            ObjectMapper objectMapper
    ) {
        super(mapper, objectMapper);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PackingListView shipPackingList(
            BusinessAccessContext access,
            String packingListId
    ) {
        Long parsedPackingListId = parseLongId(packingListId, "装箱单不存在或已删除。");
        PackingListRecord discoveredPacking = requirePackingListAccess(access, parsedPackingListId);
        OutboundOrderRecord discoveredOutbound =
                requireOutboundOrderAccess(access, discoveredPacking.outboundOrderId);
        ShippingBatchRecord discoveredBatch =
                requireShippingBatchAccess(access, discoveredOutbound.batchId);

        DispatchPlanRecord dispatchPlan = discoveredBatch.dispatchPlanId == null
                ? null
                : requireDispatchPlanAccessForUpdate(access, discoveredBatch.dispatchPlanId);
        ShippingBatchRecord shippingBatch =
                requireShippingBatchAccessForUpdate(access, discoveredBatch.id);
        OutboundOrderRecord outboundOrder =
                requireOutboundOrderAccessForUpdate(access, discoveredOutbound.id);
        PackingListRecord packingList =
                requirePackingListAccessForUpdate(access, discoveredPacking.id);

        validateLockedHandoffChain(
                discoveredBatch,
                dispatchPlan,
                shippingBatch,
                outboundOrder,
                packingList
        );
        if ("SHIPPED".equals(packingList.status)) {
            validateCompletedReplay(dispatchPlan, outboundOrder);
            logisticsHandoff.validateCompletedPackingListHandoff(
                    dispatchPlan,
                    shippingBatch,
                    outboundOrder,
                    packingList
            );
            return toPackingListDetail(packingList);
        }
        if (!Set.of("CONFIRMED", "SEALED").contains(packingList.status)) {
            throw new WarehouseInventoryStateConflictException("只有已封箱的装箱单可以发货。");
        }
        if (!"PACKED".equals(outboundOrder.status)) {
            throw new WarehouseInventoryStateConflictException("只有已装箱的出库单可以发货。");
        }
        if (dispatchPlan != null && "LOGISTICS_REQUESTED".equals(dispatchPlan.status)) {
            logisticsHandoff.completeLegacyPackingListHandoff(
                    dispatchPlan,
                    shippingBatch,
                    outboundOrder,
                    packingList,
                    access.getSessionUserId()
            );
        } else {
            if (dispatchPlan != null
                    && !Set.of("READY_FOR_LOGISTICS", "HANDOFF_FAILED")
                    .contains(dispatchPlan.status)) {
                throw new WarehouseInventoryStateConflictException(
                        "发运计划与装箱单状态不一致，请联系管理员处理。"
                );
            }
            logisticsHandoff.completePackingListHandoff(
                    dispatchPlan,
                    shippingBatch,
                    outboundOrder,
                    packingList,
                    access.getSessionUserId()
            );
        }
        PackingListView view = toPackingListDetail(packingList);
        view.status = "SHIPPED";
        return view;
    }

    private void validateLockedHandoffChain(
            ShippingBatchRecord discoveredBatch,
            DispatchPlanRecord dispatchPlan,
            ShippingBatchRecord shippingBatch,
            OutboundOrderRecord outboundOrder,
            PackingListRecord packingList
    ) {
        boolean stable = discoveredBatch != null
                && shippingBatch != null
                && outboundOrder != null
                && packingList != null
                && discoveredBatch.id != null
                && shippingBatch.id != null
                && shippingBatch.ownerUserId != null
                && outboundOrder.id != null
                && outboundOrder.batchId != null
                && outboundOrder.ownerUserId != null
                && packingList.id != null
                && packingList.outboundOrderId != null
                && packingList.ownerUserId != null
                && Objects.equals(discoveredBatch.id, shippingBatch.id)
                && Objects.equals(packingList.outboundOrderId, outboundOrder.id)
                && Objects.equals(outboundOrder.batchId, shippingBatch.id)
                && Objects.equals(packingList.ownerUserId, outboundOrder.ownerUserId)
                && Objects.equals(outboundOrder.ownerUserId, shippingBatch.ownerUserId);
        if (dispatchPlan == null) {
            stable = stable && shippingBatch.dispatchPlanId == null;
        } else {
            stable = stable
                    && dispatchPlan.id != null
                    && dispatchPlan.ownerUserId != null
                    && Objects.equals(dispatchPlan.id, shippingBatch.dispatchPlanId)
                    && Objects.equals(dispatchPlan.ownerUserId, shippingBatch.ownerUserId);
        }
        if (!stable) {
            throw new WarehouseInventoryStateConflictException(
                    "发货单据关联已变化，请刷新后重试。"
            );
        }
    }

    private void validateCompletedReplay(
            DispatchPlanRecord dispatchPlan,
            OutboundOrderRecord outboundOrder
    ) {
        if (!"SHIPPED".equals(outboundOrder.status)
                || (dispatchPlan != null
                && !"LOGISTICS_REQUESTED".equals(dispatchPlan.status))) {
            throw new WarehouseInventoryStateConflictException(
                    "发货单据状态不一致，请联系管理员处理。"
            );
        }
    }
}
