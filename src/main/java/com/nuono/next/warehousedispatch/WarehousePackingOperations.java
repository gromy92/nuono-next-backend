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

abstract class WarehousePackingOperations extends WarehouseShippingBatchOperations {

    protected WarehousePackingOperations(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

@Transactional
    public PackingListView createPackingList(
            BusinessAccessContext access,
            String outboundOrderId,
            CreatePackingListCommand command
    ) {
        OutboundOrderRecord outboundOrder = requireOutboundOrderAccess(
                access,
                parseLongId(outboundOrderId, "出库单不存在或已删除。")
        );
        if (!"DRAFT".equals(outboundOrder.status) && !"PACKING".equals(outboundOrder.status)) {
            throw new IllegalArgumentException("只有草稿或装箱中的出库单可以创建装箱单。");
        }

        Long operatorUserId = access.getSessionUserId();
        Long packingListId = mapper.nextPackingListId();
        PackingListRecord packingList = new PackingListRecord();
        packingList.id = packingListId;
        packingList.outboundOrderId = outboundOrder.id;
        packingList.ownerUserId = outboundOrder.ownerUserId;
        packingList.packingNo = "PK-" + packingListId;
        packingList.status = "DRAFT";
        packingList.boxCount = 0;
        packingList.packedQuantity = 0;
        packingList.remark = command == null ? null : trimToNull(command.remark);
        mapper.insertPackingList(packingList, operatorUserId);
        if (mapper.markOutboundOrderPacking(outboundOrder.id, outboundOrder.ownerUserId, operatorUserId) != 1) {
            throw new IllegalArgumentException("出库单状态已变化，请刷新后重试。");
        }
        log(null, "CREATE_PACKING_LIST", operatorUserId, outboundOrder.status, "PACKING", packingList.packingNo);
        return toPackingListView(packingList);
    }

@Transactional(readOnly = true)
    public List<PackingListView> listPackingLists(BusinessAccessContext access, String outboundOrderId) {
        OutboundOrderRecord outboundOrder = requireOutboundOrderAccess(
                access,
                parseLongId(outboundOrderId, "出库单不存在或已删除。")
        );
        return mapper.listPackingListsByOutboundOrder(outboundOrder.id).stream()
                .map(this::toPackingListDetail)
                .collect(Collectors.toList());
    }

@Transactional
    public PackingListView replacePackingBoxes(
            BusinessAccessContext access,
            String packingListId,
            ReplacePackingBoxesCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("缺少装箱参数。");
        }
        PackingListRecord packingList = requirePackingListAccess(
                access,
                parseLongId(packingListId, "装箱单不存在或已删除。")
        );
        if (!"DRAFT".equals(packingList.status)) {
            throw new IllegalArgumentException("只有草稿装箱单可以修改箱明细。");
        }
        OutboundOrderRecord outboundOrder = requireOutboundOrderAccess(access, packingList.outboundOrderId);
        Map<Long, OutboundOrderLineRecord> outboundLineById = mapper.listOutboundOrderLines(outboundOrder.id).stream()
                .collect(Collectors.toMap(line -> line.id, line -> line, (left, right) -> left, LinkedHashMap::new));
        List<PackingBoxCommand> boxCommands = command.boxes == null ? List.of() : command.boxes;

        List<PendingPackingBox> pendingBoxes = new ArrayList<>();
        Map<Long, Integer> packedByLine = new LinkedHashMap<>();
        BigDecimal grossWeightKg = BigDecimal.ZERO;
        BigDecimal volumeCbm = BigDecimal.ZERO;
        int packedQuantity = 0;
        for (PackingBoxCommand boxCommand : boxCommands) {
            PendingPackingBox pendingBox = toPendingPackingBox(boxCommand);
            if (pendingBox.items.isEmpty()) {
                throw new IllegalArgumentException("每个箱至少需要一个商品。");
            }
            for (PendingPackingItem pendingItem : pendingBox.items) {
                OutboundOrderLineRecord outboundLine = outboundLineById.get(pendingItem.outboundOrderLineId);
                if (outboundLine == null) {
                    throw new IllegalArgumentException("装箱商品不属于该出库单。");
                }
                packedByLine.merge(outboundLine.id, pendingItem.quantity, Integer::sum);
                if (packedByLine.get(outboundLine.id) > nonNull(outboundLine.quantity)) {
                    throw new IllegalArgumentException(outboundLine.partnerSku + " 装箱数量超过出库数量。");
                }
                packedQuantity += pendingItem.quantity;
            }
            if (pendingBox.grossWeightKg != null) {
                grossWeightKg = grossWeightKg.add(pendingBox.grossWeightKg);
            }
            volumeCbm = volumeCbm.add(pendingBox.volumeCbm());
            pendingBoxes.add(pendingBox);
        }
        validatePendingPackingGroups(outboundOrder.batchId, pendingBoxes, outboundLineById);

        Long operatorUserId = access.getSessionUserId();
        mapper.deletePackingBoxItems(packingList.id, operatorUserId);
        mapper.deletePackingBoxes(packingList.id, operatorUserId);

        PackingListView view = toPackingListView(packingList);
        view.boxes.clear();
        for (PendingPackingBox pendingBox : pendingBoxes) {
            Long boxId = mapper.nextPackingBoxId();
            PackingBoxRecord box = pendingBox.toRecord(
                    boxId,
                    packingList.id,
                    outboundOrder.id,
                    packingList.ownerUserId
            );
            mapper.insertPackingBox(box, operatorUserId);
            PackingBoxView boxView = toPackingBoxView(box);
            for (PendingPackingItem pendingItem : pendingBox.items) {
                OutboundOrderLineRecord outboundLine = outboundLineById.get(pendingItem.outboundOrderLineId);
                PackingBoxItemRecord item = pendingItem.toRecord(
                        mapper.nextPackingBoxItemId(),
                        packingList.id,
                        box.id,
                        outboundOrder.id,
                        packingList.ownerUserId,
                        outboundLine
                );
                mapper.insertPackingBoxItem(item, operatorUserId);
                boxView.items.add(toPackingBoxItemView(item));
            }
            view.boxes.add(boxView);
        }
        mapper.updatePackingListTotals(
                packingList.id,
                packingList.ownerUserId,
                pendingBoxes.size(),
                packedQuantity,
                grossWeightKg,
                volumeCbm.setScale(4, RoundingMode.HALF_UP),
                trimToNull(command.remark),
                operatorUserId
        );
        view.boxCount = pendingBoxes.size();
        view.packedQuantity = packedQuantity;
        view.grossWeightKg = grossWeightKg.toPlainString();
        view.volumeCbm = volumeCbm.setScale(4, RoundingMode.HALF_UP).toPlainString();
        view.remark = trimToNull(command.remark);
        return view;
    }

@Transactional
    public PackingListView savePackingBox(
            BusinessAccessContext access,
            String packingListId,
            String boxNo,
            PackingBoxCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("缺少装箱参数。");
        }
        String normalizedBoxNo = requiredText(
                StringUtils.hasText(command.boxNo) ? command.boxNo : boxNo,
                "箱号不能为空。"
        );
        if (StringUtils.hasText(boxNo) && !normalizedBoxNo.equals(requiredText(boxNo, "箱号不能为空。"))) {
            throw new IllegalArgumentException("箱号和请求路径不一致。");
        }
        command.boxNo = normalizedBoxNo;

        PackingListRecord packingList = requirePackingListAccess(
                access,
                parseLongId(packingListId, "装箱单不存在或已删除。")
        );
        if (!"DRAFT".equals(packingList.status)) {
            throw new IllegalArgumentException("只有草稿装箱单可以修改箱明细。");
        }

        Map<Long, List<PackingBoxItemRecord>> itemsByBox = emptyIfNull(mapper.listPackingBoxItems(packingList.id)).stream()
                .collect(Collectors.groupingBy(item -> item.packingBoxId, LinkedHashMap::new, Collectors.toList()));
        ReplacePackingBoxesCommand replace = new ReplacePackingBoxesCommand();
        replace.remark = packingList.remark;
        for (PackingBoxRecord existingBox : emptyIfNull(mapper.listPackingBoxes(packingList.id))) {
            if (!normalizedBoxNo.equals(existingBox.boxNo)) {
                replace.boxes.add(toPackingBoxCommand(existingBox, itemsByBox.getOrDefault(existingBox.id, List.of())));
            }
        }
        replace.boxes.add(command);
        return replacePackingBoxes(access, packingListId, replace);
    }

@Transactional
    public PackingListView confirmPackingList(BusinessAccessContext access, String packingListId) {
        PackingListRecord packingList = requirePackingListAccess(
                access,
                parseLongId(packingListId, "装箱单不存在或已删除。")
        );
        if (!"DRAFT".equals(packingList.status)) {
            throw new IllegalArgumentException("只有草稿装箱单可以确认。");
        }
        OutboundOrderRecord outboundOrder = requireOutboundOrderAccess(access, packingList.outboundOrderId);
        List<OutboundOrderLineRecord> outboundLines = mapper.listOutboundOrderLines(outboundOrder.id);
        List<PackingBoxRecord> boxes = mapper.listPackingBoxes(packingList.id);
        List<PackingBoxItemRecord> items = mapper.listPackingBoxItems(packingList.id);
        validateStoredPackingGroups(outboundOrder.batchId, outboundLines, items);
        validatePackingConfirmation(outboundLines, boxes, items);

        Long operatorUserId = access.getSessionUserId();
        if (mapper.confirmPackingList(packingList.id, packingList.ownerUserId, operatorUserId) != 1) {
            throw new IllegalArgumentException("装箱单状态已变化，请刷新后重试。");
        }
        if (mapper.markOutboundOrderPacked(outboundOrder.id, outboundOrder.ownerUserId, operatorUserId) != 1) {
            throw new IllegalArgumentException("出库单状态已变化，请刷新后重试。");
        }
        log(null, "CONFIRM_PACKING_LIST", operatorUserId, "DRAFT", "CONFIRMED", packingList.packingNo);

        PackingListView view = toPackingListView(packingList);
        view.status = "CONFIRMED";
        view.boxes.clear();
        Map<Long, List<PackingBoxItemRecord>> itemsByBox = items.stream()
                .collect(Collectors.groupingBy(item -> item.packingBoxId, LinkedHashMap::new, Collectors.toList()));
        for (PackingBoxRecord box : boxes) {
            PackingBoxView boxView = toPackingBoxView(box);
            boxView.status = "CONFIRMED";
            boxView.isSealed = true;
            boxView.sealedAt = view.updatedAt;
            for (PackingBoxItemRecord item : itemsByBox.getOrDefault(box.id, List.of())) {
                boxView.items.add(toPackingBoxItemView(item));
            }
            view.boxes.add(boxView);
        }
        view.boxCount = boxes.size();
        view.packedQuantity = items.stream().mapToInt(item -> nonNull(item.quantity)).sum();
        return view;
    }

@Transactional
    public PackingListView shipPackingList(BusinessAccessContext access, String packingListId) {
        PackingListRecord packingList = requirePackingListAccess(
                access,
                parseLongId(packingListId, "装箱单不存在或已删除。")
        );
        if (!"CONFIRMED".equals(packingList.status) && !"SEALED".equals(packingList.status)) {
            throw new IllegalArgumentException("只有已封箱的装箱单可以发货。");
        }
        OutboundOrderRecord outboundOrder = requireOutboundOrderAccess(access, packingList.outboundOrderId);

        Long operatorUserId = access.getSessionUserId();
        if (mapper.shipPackingList(packingList.id, packingList.ownerUserId, operatorUserId) != 1) {
            throw new IllegalArgumentException("装箱单状态已变化，请刷新后重试。");
        }
        if (mapper.markOutboundOrderShipped(outboundOrder.id, outboundOrder.ownerUserId, operatorUserId) != 1) {
            throw new IllegalArgumentException("出库单状态已变化，请刷新后重试。");
        }
        log(null, "SHIP_PACKING_LIST", operatorUserId, packingList.status, "SHIPPED", packingList.packingNo);

        PackingListRecord updated = mapper.selectPackingListById(packingList.id);
        PackingListView view = toPackingListDetail(updated == null ? packingList : updated);
        view.status = "SHIPPED";
        return view;
    }
}
