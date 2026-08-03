package com.nuono.next.warehousedispatch;

import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

final class WarehouseHandoffSnapshotValidator {

    private static final String INVENTORY_CONFLICT_MESSAGE =
            "物流交接库存状态已变化，请刷新后重试。";

    private final WarehouseDispatchMapper mapper;

    WarehouseHandoffSnapshotValidator(WarehouseDispatchMapper mapper) {
        this.mapper = mapper;
    }

    Map<Long, Integer> validate(
            DispatchPlanRecord dispatchPlan,
            ShippingBatchRecord shippingBatch,
            OutboundOrderRecord outboundOrder,
            PackingListRecord packingList
    ) {
        validateDocumentChain(dispatchPlan, shippingBatch, outboundOrder, packingList);
        Map<Long, Integer> quantities = aggregatePackingSources(packingList, outboundOrder);
        if (!quantities.equals(aggregateBatchSources(shippingBatch))) {
            throw inventoryConflict();
        }
        if (dispatchPlan != null
                && !quantities.equals(aggregateDispatchSources(dispatchPlan))) {
            throw inventoryConflict();
        }
        validateDocumentTotals(dispatchPlan, shippingBatch, outboundOrder, packingList, quantities);
        return quantities;
    }

    private void validateDocumentChain(
            DispatchPlanRecord dispatchPlan,
            ShippingBatchRecord shippingBatch,
            OutboundOrderRecord outboundOrder,
            PackingListRecord packingList
    ) {
        boolean valid = shippingBatch != null
                && outboundOrder != null
                && packingList != null
                && shippingBatch.id != null
                && shippingBatch.ownerUserId != null
                && outboundOrder.id != null
                && outboundOrder.batchId != null
                && outboundOrder.ownerUserId != null
                && packingList.id != null
                && packingList.outboundOrderId != null
                && packingList.ownerUserId != null
                && Objects.equals(shippingBatch.id, outboundOrder.batchId)
                && Objects.equals(outboundOrder.id, packingList.outboundOrderId)
                && Objects.equals(shippingBatch.ownerUserId, outboundOrder.ownerUserId)
                && Objects.equals(outboundOrder.ownerUserId, packingList.ownerUserId);
        if (dispatchPlan == null) {
            valid = valid && shippingBatch.dispatchPlanId == null;
        } else {
            valid = valid
                    && dispatchPlan.id != null
                    && dispatchPlan.ownerUserId != null
                    && Objects.equals(dispatchPlan.id, shippingBatch.dispatchPlanId)
                    && Objects.equals(dispatchPlan.ownerUserId, shippingBatch.ownerUserId)
                    && dispatchPlan.handoffRequestNo != null
                    && !dispatchPlan.handoffRequestNo.trim().isEmpty();
        }
        if (!valid) {
            throw inventoryConflict();
        }
    }

    private Map<Long, Integer> aggregateDispatchSources(DispatchPlanRecord dispatchPlan) {
        List<DispatchPlanLineSourceRecord> sources = mapper.listDispatchLineSources(dispatchPlan.id);
        if (sources == null) {
            throw inventoryConflict();
        }
        for (DispatchPlanLineSourceRecord source : sources) {
            if (source == null
                    || !Objects.equals(dispatchPlan.id, source.dispatchPlanId)
                    || !Objects.equals(dispatchPlan.ownerUserId, source.ownerUserId)) {
                throw inventoryConflict();
            }
        }
        return aggregate(sources);
    }

    private Map<Long, Integer> aggregateBatchSources(ShippingBatchRecord shippingBatch) {
        List<ShippingBatchSourceRecord> sources = mapper.listShippingBatchSources(shippingBatch.id);
        if (sources == null) {
            throw inventoryConflict();
        }
        for (ShippingBatchSourceRecord source : sources) {
            if (source == null
                    || !Objects.equals(shippingBatch.id, source.batchId)
                    || !Objects.equals(shippingBatch.ownerUserId, source.ownerUserId)) {
                throw inventoryConflict();
            }
        }
        return aggregate(sources);
    }

    private Map<Long, Integer> aggregatePackingSources(
            PackingListRecord packingList,
            OutboundOrderRecord outboundOrder
    ) {
        List<OutboundOrderLineRecord> lines = mapper.listOutboundOrderLines(outboundOrder.id);
        List<OutboundOrderLineSourceRecord> sources = mapper.listOutboundOrderLineSources(outboundOrder.id);
        if (lines == null || lines.isEmpty() || sources == null || sources.isEmpty()) {
            throw inventoryConflict();
        }
        Map<Long, Integer> expectedByLine = new LinkedHashMap<>();
        Map<Long, Integer> actualByLine = new LinkedHashMap<>();
        try {
            for (OutboundOrderLineRecord line : lines) {
                if (line == null
                        || line.id == null
                        || !Objects.equals(outboundOrder.id, line.outboundOrderId)
                        || !Objects.equals(outboundOrder.ownerUserId, line.ownerUserId)
                        || line.quantity == null
                        || line.quantity <= 0
                        || expectedByLine.put(line.id, line.quantity) != null) {
                    throw inventoryConflict();
                }
            }
            for (OutboundOrderLineSourceRecord source : sources) {
                if (source == null
                        || !Objects.equals(outboundOrder.id, source.outboundOrderId)
                        || !expectedByLine.containsKey(source.outboundOrderLineId)) {
                    throw inventoryConflict();
                }
                actualByLine.merge(
                        source.outboundOrderLineId,
                        requirePositiveQuantity(source),
                        Math::addExact
                );
            }
        } catch (ArithmeticException exception) {
            throw inventoryConflict();
        }
        if (!expectedByLine.equals(actualByLine)) {
            throw inventoryConflict();
        }
        return aggregate(sources);
    }

    private Map<Long, Integer> aggregate(
            List<? extends WarehouseInventoryHandoffSource> sources
    ) {
        if (sources == null || sources.isEmpty()) {
            throw inventoryConflict();
        }
        Map<Long, Integer> quantities = new TreeMap<>();
        try {
            for (WarehouseInventoryHandoffSource source : sources) {
                quantities.merge(
                        requireBalanceId(source),
                        requirePositiveQuantity(source),
                        Math::addExact
                );
            }
        } catch (ArithmeticException exception) {
            throw inventoryConflict();
        }
        return quantities;
    }

    private void validateDocumentTotals(
            DispatchPlanRecord dispatchPlan,
            ShippingBatchRecord shippingBatch,
            OutboundOrderRecord outboundOrder,
            PackingListRecord packingList,
            Map<Long, Integer> quantities
    ) {
        int total;
        try {
            total = quantities.values().stream().reduce(0, Math::addExact);
        } catch (ArithmeticException exception) {
            throw inventoryConflict();
        }
        if (!Integer.valueOf(total).equals(shippingBatch.totalQuantity)
                || !Integer.valueOf(total).equals(outboundOrder.totalQuantity)
                || !Integer.valueOf(total).equals(packingList.packedQuantity)
                || (dispatchPlan != null
                && !Integer.valueOf(total).equals(dispatchPlan.totalQuantity))) {
            throw inventoryConflict();
        }
    }

    private Long requireBalanceId(WarehouseInventoryHandoffSource source) {
        if (source == null || source.fulfillmentBalanceId() == null) {
            throw inventoryConflict();
        }
        return source.fulfillmentBalanceId();
    }

    private int requirePositiveQuantity(WarehouseInventoryHandoffSource source) {
        if (source == null
                || source.handoffQuantity() == null
                || source.handoffQuantity() <= 0) {
            throw inventoryConflict();
        }
        return source.handoffQuantity();
    }

    private WarehouseInventoryStateConflictException inventoryConflict() {
        return new WarehouseInventoryStateConflictException(INVENTORY_CONFLICT_MESSAGE);
    }
}
