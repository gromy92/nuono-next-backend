package com.nuono.next.warehousedispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import java.util.Map;

abstract class WarehouseAggregateAccessSupport extends WarehousePackingProjectionSupport {

    protected WarehouseAggregateAccessSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

    protected DispatchPlanRecord requireDispatchPlanAccess(
            BusinessAccessContext access,
            Long dispatchPlanId
    ) {
        return requireDispatchPlanAggregateAccess(access, mapper.selectDispatchPlanById(dispatchPlanId));
    }

    protected DispatchPlanRecord requireDispatchPlanAccessForUpdate(
            BusinessAccessContext access,
            Long dispatchPlanId
    ) {
        return requireDispatchPlanAggregateAccess(access, mapper.selectDispatchPlanByIdForUpdate(dispatchPlanId));
    }

    protected DispatchPlanRecord requireDispatchPlanAggregateAccess(
            BusinessAccessContext access,
            DispatchPlanRecord plan
    ) {
        if (plan == null) {
            throw new IllegalArgumentException("发运计划不存在或已删除。");
        }
        requireOwnerAccess(access, plan.ownerUserId);
        if (!mapper.isDispatchPlanSourceScopeAuthorized(plan.id, authorizedStoreOwners(access))) {
            throw aggregateAccessDenied("发运计划");
        }
        return plan;
    }

    protected ShippingBatchRecord requireShippingBatchAccess(
            BusinessAccessContext access,
            Long shippingBatchId
    ) {
        return requireShippingBatchAggregateAccess(access, mapper.selectShippingBatchById(shippingBatchId));
    }

    protected ShippingBatchRecord requireShippingBatchAccessForUpdate(
            BusinessAccessContext access,
            Long shippingBatchId
    ) {
        return requireShippingBatchAggregateAccess(
                access,
                mapper.selectShippingBatchByIdForUpdate(shippingBatchId)
        );
    }

    protected ShippingBatchRecord requireShippingBatchAggregateAccess(
            BusinessAccessContext access,
            ShippingBatchRecord batch
    ) {
        if (batch == null) {
            throw new IllegalArgumentException("发货批次不存在或已删除。");
        }
        requireOwnerAccess(access, batch.ownerUserId);
        if (!mapper.isShippingBatchSourceScopeAuthorized(batch.id, authorizedStoreOwners(access))) {
            throw aggregateAccessDenied("发货批次");
        }
        return batch;
    }

    protected OutboundOrderRecord requireOutboundOrderAccess(
            BusinessAccessContext access,
            Long outboundOrderId
    ) {
        return requireOutboundOrderAggregateAccess(access, mapper.selectOutboundOrderById(outboundOrderId));
    }

    protected OutboundOrderRecord requireOutboundOrderAccessForUpdate(
            BusinessAccessContext access,
            Long outboundOrderId
    ) {
        return requireOutboundOrderAggregateAccess(
                access,
                mapper.selectOutboundOrderByIdForUpdate(outboundOrderId)
        );
    }

    private OutboundOrderRecord requireOutboundOrderAggregateAccess(
            BusinessAccessContext access,
            OutboundOrderRecord outboundOrder
    ) {
        if (outboundOrder == null) {
            throw new IllegalArgumentException("出库单不存在或已删除。");
        }
        requireOwnerAccess(access, outboundOrder.ownerUserId);
        if (!mapper.isOutboundOrderSourceScopeAuthorized(outboundOrder.id, authorizedStoreOwners(access))) {
            throw aggregateAccessDenied("出库单");
        }
        return outboundOrder;
    }

    protected PackingListRecord requirePackingListAccess(
            BusinessAccessContext access,
            Long packingListId
    ) {
        return requirePackingListAggregateAccess(access, mapper.selectPackingListById(packingListId));
    }

    protected PackingListRecord requirePackingListAccessForUpdate(
            BusinessAccessContext access,
            Long packingListId
    ) {
        return requirePackingListAggregateAccess(access, mapper.selectPackingListByIdForUpdate(packingListId));
    }

    private PackingListRecord requirePackingListAggregateAccess(
            BusinessAccessContext access,
            PackingListRecord packingList
    ) {
        if (packingList == null) {
            throw new IllegalArgumentException("装箱单不存在或已删除。");
        }
        requireOwnerAccess(access, packingList.ownerUserId);
        if (!mapper.isPackingListSourceScopeAuthorized(packingList.id, authorizedStoreOwners(access))) {
            throw aggregateAccessDenied("装箱单");
        }
        return packingList;
    }

    protected DispatchPlanRecord requireHandoffAccess(
            BusinessAccessContext access,
            String handoffRequestNo
    ) {
        return requireHandoffAggregateAccess(
                access,
                mapper.selectDispatchPlanByHandoffRequest(handoffRequestNo)
        );
    }

    protected DispatchPlanRecord requireHandoffAccessForUpdate(
            BusinessAccessContext access,
            String handoffRequestNo
    ) {
        return requireHandoffAggregateAccess(
                access,
                mapper.selectDispatchPlanByHandoffRequestForUpdate(handoffRequestNo)
        );
    }

    private DispatchPlanRecord requireHandoffAggregateAccess(
            BusinessAccessContext access,
            DispatchPlanRecord plan
    ) {
        if (plan == null) {
            throw new IllegalArgumentException("物流交接不存在或已失效。");
        }
        return requireDispatchPlanAggregateAccess(access, plan);
    }

    protected void requireOwnerAccess(BusinessAccessContext access, Long ownerUserId) {
        if (!warehouseBusinessScope(access).allowsOwner(ownerUserId)) {
            throw aggregateAccessDenied("仓库单据");
        }
    }

    protected WarehouseBusinessScope warehouseBusinessScope(BusinessAccessContext access) {
        return WarehouseBusinessScope.from(access);
    }

    private Map<String, Long> authorizedStoreOwners(BusinessAccessContext access) {
        return warehouseBusinessScope(access).storeOwnerUserIds();
    }

    private IllegalArgumentException aggregateAccessDenied(String aggregateName) {
        return new IllegalArgumentException("当前账号不能操作该" + aggregateName + "。");
    }
}
