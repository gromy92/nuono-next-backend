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

abstract class WarehouseReceiptCommandOperations extends WarehouseDispatchValueSupport {

    protected WarehouseReceiptCommandOperations(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

@Transactional
    public FulfillmentItemView updateItemFulfillment(
            BusinessAccessContext access,
            String purchaseOrderId,
            String purchaseOrderItemId,
            UpdateFulfillmentCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("缺少履约方式参数。");
        }
        Long parsedOrderId = parseLongId(purchaseOrderId, "采购单不存在或已删除。");
        Long parsedItemId = parseLongId(purchaseOrderItemId, "采购单商品不存在或已删除。");
        PurchaseOrderAccessRecord order = requireOrderAccess(access, parsedOrderId);
        PurchaseOrderItemRecord item = requireItem(order, parsedItemId);
        String fulfillmentType = normalizeFulfillmentType(command.fulfillmentType);
        String sourceName = trimToNull(command.sourceName);

        ensureItemBalances(item, fulfillmentType, access.getSessionUserId());
        if (mapper.countItemFulfillmentActivity(item.id) > 0) {
            throw new IllegalArgumentException("该商品已经收货、预留或交接物流，不能修改履约方式。");
        }

        mapper.updatePurchaseOrderItemFulfillment(item.id, fulfillmentType, sourceName, access.getSessionUserId());
        mapper.updateActiveBalancesFulfillment(item.id, fulfillmentType, access.getSessionUserId());
        log(null, "UPDATE_ITEM_FULFILLMENT", access.getSessionUserId(), null, null, item.partnerSku);

        FulfillmentItemView view = new FulfillmentItemView();
        view.purchaseOrderId = String.valueOf(order.id);
        view.purchaseOrderItemId = String.valueOf(item.id);
        view.fulfillmentType = fulfillmentType;
        view.sourceName = sourceName;
        return view;
    }

@Transactional
    public ConfirmationView createConfirmation(BusinessAccessContext access, ConfirmationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少收货确认参数。");
        }
        Long purchaseOrderId = parseLongId(command.purchaseOrderId, "采购单不存在或已删除。");
        PurchaseOrderAccessRecord order = requireOrderAccess(access, purchaseOrderId);
        String confirmationType = normalizeConfirmationType(command.confirmationType);
        List<ConfirmationLineCommand> lines = command.lines == null ? List.of() : command.lines;
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("请选择至少一个确认商品。");
        }

        Long confirmationId = mapper.nextConfirmationId();
        List<FulfillmentConfirmationLineInsertRecord> lineRows = new ArrayList<>();
        List<BalanceQuantityDelta> balanceDeltas = new ArrayList<>();
        ConfirmationView view = new ConfirmationView();
        view.id = String.valueOf(confirmationId);
        view.confirmationNo = "FC-" + confirmationId;
        view.confirmationType = confirmationType;
        view.status = "CONFIRMED";

        int expectedTotal = 0;
        int confirmedTotal = 0;
        int abnormalTotal = 0;
        for (ConfirmationLineCommand lineCommand : lines) {
            Long itemId = parseLongId(lineCommand == null ? null : lineCommand.purchaseOrderItemId, "采购单商品不存在或已删除。");
            PurchaseOrderItemRecord item = requireItem(order, itemId);
            int confirmedDelta = nonNull(lineCommand.confirmedQuantity);
            int abnormalDelta = nonNull(lineCommand.abnormalQuantity);
            if (confirmedDelta < 0 || abnormalDelta < 0) {
                throw new IllegalArgumentException("收货数量不能为负数。");
            }
            if (confirmedDelta == 0 && abnormalDelta == 0) {
                throw new IllegalArgumentException("收货确认数量必须大于 0。");
            }

            ensureItemBalances(item, normalizeFulfillmentType(item.fulfillmentType), access.getSessionUserId());
            List<FulfillmentBalanceRecord> balances = mapper.listBalancesForItemForUpdate(item.id);
            if (balances.isEmpty()) {
                throw new IllegalArgumentException("采购单商品缺少站点计划，不能确认收货。");
            }
            int plannedTotal = balances.stream().mapToInt(row -> nonNull(row.plannedQuantity)).sum();
            int currentUsable = balances.stream()
                    .mapToInt(row -> nonNull(row.confirmedQuantity) - nonNull(row.abnormalQuantity))
                    .sum();
            int nextUsable = currentUsable + confirmedDelta - abnormalDelta;
            if (nextUsable > plannedTotal) {
                throw new IllegalArgumentException(item.partnerSku + " 可用收货数量超过采购计划。");
            }

            Map<Long, Integer> confirmedAllocations = allocateByPlannedQuantity(balances, confirmedDelta);
            Map<Long, Integer> abnormalAllocations = allocateByPlannedQuantity(balances, abnormalDelta);
            for (FulfillmentBalanceRecord balance : balances) {
                int allocatedConfirmed = confirmedAllocations.getOrDefault(balance.id, 0);
                int allocatedAbnormal = abnormalAllocations.getOrDefault(balance.id, 0);
                int nextAvailable = nonNull(balance.confirmedQuantity) + allocatedConfirmed
                        - nonNull(balance.abnormalQuantity) - allocatedAbnormal
                        - nonNull(balance.reservedQuantity)
                        - nonNull(balance.logisticsHandoffQuantity);
                if (nextAvailable < 0) {
                    throw new IllegalArgumentException(item.partnerSku + " 异常数量超过可用收货数量。");
                }
                if (allocatedConfirmed != 0 || allocatedAbnormal != 0) {
                    balanceDeltas.add(new BalanceQuantityDelta(
                            balance.id,
                            allocatedConfirmed,
                            allocatedAbnormal,
                            access.getSessionUserId()
                    ));
                }
            }

            Long lineId = mapper.nextConfirmationLineId();
            FulfillmentConfirmationLineInsertRecord row = new FulfillmentConfirmationLineInsertRecord();
            row.id = lineId;
            row.confirmationId = confirmationId;
            row.ownerUserId = order.ownerUserId;
            row.logicalStoreId = order.logicalStoreId;
            row.purchaseOrderId = order.id;
            row.purchaseOrderItemId = item.id;
            row.productMasterId = item.productMasterId;
            row.productVariantId = item.productVariantId;
            row.partnerSku = item.partnerSku;
            row.skuParent = item.skuParent;
            row.titleCache = item.titleCache;
            row.imageUrlCache = item.imageUrlCache;
            row.fulfillmentType = normalizeFulfillmentType(item.fulfillmentType);
            row.expectedQuantity = plannedTotal;
            row.confirmedQuantityDelta = confirmedDelta;
            row.abnormalQuantityDelta = abnormalDelta;
            row.exceptionReason = trimToNull(lineCommand.exceptionReason);
            row.snapshotJson = writeJson(Map.of(
                    "allocation", balanceDeltas.stream()
                            .filter(delta -> confirmedAllocations.containsKey(delta.balanceId)
                                    || abnormalAllocations.containsKey(delta.balanceId))
                            .collect(Collectors.toList())
            ));
            row.operatorUserId = access.getSessionUserId();
            lineRows.add(row);

            ConfirmationLineView lineView = new ConfirmationLineView();
            lineView.purchaseOrderItemId = String.valueOf(item.id);
            lineView.partnerSku = item.partnerSku;
            lineView.expectedQuantity = plannedTotal;
            lineView.confirmedQuantity = confirmedDelta;
            lineView.abnormalQuantity = abnormalDelta;
            view.lines.add(lineView);

            expectedTotal += plannedTotal;
            confirmedTotal += confirmedDelta;
            abnormalTotal += abnormalDelta;
        }

        FulfillmentConfirmationInsertRecord header = new FulfillmentConfirmationInsertRecord();
        header.id = confirmationId;
        header.ownerUserId = order.ownerUserId;
        header.logicalStoreId = order.logicalStoreId;
        header.purchaseOrderId = order.id;
        header.confirmationNo = view.confirmationNo;
        header.confirmationType = confirmationType;
        header.status = "CONFIRMED";
        header.sourcePartyName = trimToNull(command.sourcePartyName);
        header.operatorUserId = access.getSessionUserId();
        header.expectedQuantity = expectedTotal;
        header.confirmedQuantityDelta = confirmedTotal;
        header.abnormalQuantityDelta = abnormalTotal;
        header.remark = trimToNull(command.remark);
        mapper.insertConfirmation(header);
        for (FulfillmentConfirmationLineInsertRecord row : lineRows) {
            mapper.insertConfirmationLine(row);
        }
        for (BalanceQuantityDelta delta : balanceDeltas) {
            mapper.updateBalanceQuantities(delta);
        }
        log(null, "CREATE_FULFILLMENT_CONFIRMATION", access.getSessionUserId(), null, "CONFIRMED", view.confirmationNo);
        view.expectedQuantity = expectedTotal;
        view.confirmedQuantity = confirmedTotal;
        view.abnormalQuantity = abnormalTotal;
        return view;
    }
}
