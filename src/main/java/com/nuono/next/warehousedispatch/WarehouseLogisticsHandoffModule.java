package com.nuono.next.warehousedispatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WarehouseLogisticsHandoffModule {

    private static final String INVENTORY_CONFLICT_MESSAGE =
            "物流交接库存状态已变化，请刷新后重试。";
    private static final String COMPLETION_RECEIPT_MISSING_MESSAGE =
            "未找到可验证的库存交接凭证，请联系管理员处理。";

    private final WarehouseDispatchMapper mapper;
    private final ObjectMapper objectMapper;
    private final WarehouseHandoffSnapshotValidator snapshotValidator;

    WarehouseLogisticsHandoffModule(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.snapshotValidator = new WarehouseHandoffSnapshotValidator(mapper);
    }

    void validateCompletedPackingListHandoff(
            DispatchPlanRecord dispatchPlan,
            ShippingBatchRecord shippingBatch,
            OutboundOrderRecord outboundOrder,
            PackingListRecord packingList
    ) {
        snapshotValidator.validate(dispatchPlan, shippingBatch, outboundOrder, packingList);
        Long dispatchPlanId = dispatchPlan == null ? null : dispatchPlan.id;
        Long receiptId = mapper.selectInventoryHandoffCompletionReceiptId(
                dispatchPlanId,
                shippingBatch.id,
                outboundOrder.id,
                packingList.id
        );
        if (isPositive(receiptId)) {
            return;
        }
        if (dispatchPlan != null) {
            Long legacyReceiptId = mapper.selectLegacyDispatchPlanHandoffReceiptId(
                    dispatchPlan.id,
                    dispatchPlan.handoffRequestNo
            );
            if (isPositive(legacyReceiptId)) {
                return;
            }
        }
        throw new WarehouseInventoryStateConflictException(
                COMPLETION_RECEIPT_MISSING_MESSAGE
        );
    }

    private boolean isPositive(Long id) {
        return id != null && id > 0;
    }

    void completePackingListHandoff(
            DispatchPlanRecord dispatchPlan,
            ShippingBatchRecord shippingBatch,
            OutboundOrderRecord outboundOrder,
            PackingListRecord packingList,
            Long operatorUserId
    ) {
        Map<Long, Integer> quantities = snapshotValidator.validate(
                dispatchPlan,
                shippingBatch,
                outboundOrder,
                packingList
        );
        lockAndValidateBalances(quantities, outboundOrder.ownerUserId);
        shipDocuments(outboundOrder, packingList, operatorUserId);
        if (dispatchPlan != null && mapper.markDispatchPlanHandoffSuccess(
                dispatchPlan.id,
                dispatchPlan.ownerUserId,
                dispatchPlan.handoffRequestNo,
                operatorUserId
        ) != 1) {
            throw new WarehouseInventoryStateConflictException("发运计划状态已变化，请刷新后重试。");
        }
        moveReservedQuantities(quantities, operatorUserId);
        insertCompletionReceipt(
                dispatchPlan == null ? null : dispatchPlan.id,
                operatorUserId,
                packingList.status,
                "SHIPPED",
                handoffDetail(
                        "ATOMIC_INVENTORY_MOVE",
                        dispatchPlan,
                        shippingBatch,
                        outboundOrder,
                        packingList,
                        quantities
                )
        );
    }

    void completeLegacyPackingListHandoff(
            DispatchPlanRecord dispatchPlan,
            ShippingBatchRecord shippingBatch,
            OutboundOrderRecord outboundOrder,
            PackingListRecord packingList,
            Long operatorUserId
    ) {
        Map<Long, Integer> quantities = snapshotValidator.validate(
                dispatchPlan,
                shippingBatch,
                outboundOrder,
                packingList
        );
        Long legacyReceiptId = mapper.selectLegacyDispatchPlanHandoffReceiptId(
                dispatchPlan.id,
                dispatchPlan.handoffRequestNo
        );
        if (!isPositive(legacyReceiptId)) {
            throw new WarehouseInventoryStateConflictException(
                    COMPLETION_RECEIPT_MISSING_MESSAGE
            );
        }
        shipDocuments(outboundOrder, packingList, operatorUserId);
        insertCompletionReceipt(
                dispatchPlan.id,
                operatorUserId,
                packingList.status,
                "SHIPPED",
                handoffDetail(
                        "LEGACY_HANDOFF_RECEIPT",
                        dispatchPlan,
                        shippingBatch,
                        outboundOrder,
                        packingList,
                        quantities
                )
        );
    }

    private void shipDocuments(
            OutboundOrderRecord outboundOrder,
            PackingListRecord packingList,
            Long operatorUserId
    ) {
        if (mapper.shipPackingList(
                packingList.id,
                packingList.ownerUserId,
                operatorUserId
        ) != 1) {
            throw new WarehouseInventoryStateConflictException("装箱单状态已变化，请刷新后重试。");
        }
        if (mapper.markOutboundOrderShipped(
                outboundOrder.id,
                outboundOrder.ownerUserId,
                operatorUserId
        ) != 1) {
            throw new WarehouseInventoryStateConflictException("出库单状态已变化，请刷新后重试。");
        }
    }

    private void lockAndValidateBalances(Map<Long, Integer> quantities, Long ownerUserId) {
        List<Long> balanceIds = new ArrayList<>(quantities.keySet());
        List<FulfillmentBalanceRecord> balances = mapper.selectBalancesForUpdate(balanceIds);
        if (balances == null || balances.size() != balanceIds.size()) {
            throw inventoryConflict();
        }
        Map<Long, FulfillmentBalanceRecord> balanceById = new LinkedHashMap<>();
        for (FulfillmentBalanceRecord balance : balances) {
            if (balance == null
                    || balance.id == null
                    || !ownerUserId.equals(balance.ownerUserId)
                    || balanceById.put(balance.id, balance) != null) {
                throw inventoryConflict();
            }
        }
        if (!balanceById.keySet().equals(quantities.keySet())) {
            throw inventoryConflict();
        }
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Integer reservedQuantity = balanceById.get(entry.getKey()).reservedQuantity;
            if (reservedQuantity == null || reservedQuantity < entry.getValue()) {
                throw inventoryConflict();
            }
        }
    }

    private void moveReservedQuantities(Map<Long, Integer> quantities, Long operatorUserId) {
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            if (mapper.moveReservedToLogisticsHandoff(
                    entry.getKey(),
                    entry.getValue(),
                    operatorUserId
            ) != 1) {
                throw inventoryConflict();
            }
        }
    }

    private void insertCompletionReceipt(
            Long dispatchPlanId,
            Long operatorUserId,
            String beforeStatus,
            String afterStatus,
            Map<String, Object> detail
    ) {
        int inserted = mapper.insertOperationLog(
                mapper.nextOperationLogId(),
                dispatchPlanId,
                "INVENTORY_HANDOFF_COMPLETED",
                operatorUserId,
                beforeStatus,
                afterStatus,
                detailJson(detail)
        );
        if (inserted != 1) {
            throw new WarehouseInventoryStateConflictException(
                    "物流交接凭证写入失败，请刷新后重试。"
            );
        }
    }

    private Map<String, Object> handoffDetail(
            String completionMode,
            DispatchPlanRecord dispatchPlan,
            ShippingBatchRecord shippingBatch,
            OutboundOrderRecord outboundOrder,
            PackingListRecord packingList,
            Map<Long, Integer> quantities
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("completionMode", completionMode);
        detail.put("dispatchPlanId", dispatchPlan == null ? null : dispatchPlan.id);
        detail.put("handoffRequestNo", dispatchPlan == null ? null : dispatchPlan.handoffRequestNo);
        detail.put("shippingBatchId", shippingBatch.id);
        detail.put("outboundOrderId", outboundOrder.id);
        detail.put("packingListId", packingList.id);
        detail.put("quantityByBalance", quantities);
        return detail;
    }

    private String detailJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("物流交接审计序列化失败。", exception);
        }
    }

    private WarehouseInventoryStateConflictException inventoryConflict() {
        return new WarehouseInventoryStateConflictException(INVENTORY_CONFLICT_MESSAGE);
    }
}
