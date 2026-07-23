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

abstract class WarehousePackingProjectionSupport extends WarehouseOutboundProjectionSupport {

    protected WarehousePackingProjectionSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected PendingPackingBox toPendingPackingBox(PackingBoxCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("装箱参数不能为空。");
        }
        PendingPackingBox box = new PendingPackingBox(
                requiredText(command.boxNo, "箱号不能为空。"),
                normalizePackingBoxStatus(command.status),
                optionalPositiveDecimal(command.lengthCm, "箱长必须大于 0。"),
                optionalPositiveDecimal(command.widthCm, "箱宽必须大于 0。"),
                optionalPositiveDecimal(command.heightCm, "箱高必须大于 0。"),
                optionalPositiveDecimal(command.grossWeightKg, "箱毛重必须大于 0。")
        );
        List<PackingBoxItemCommand> items = command.items == null ? List.of() : command.items;
        for (PackingBoxItemCommand item : items) {
            if (item == null || item.outboundOrderLineId == null || nonNull(item.quantity) <= 0) {
                continue;
            }
            box.items.add(new PendingPackingItem(item.outboundOrderLineId, nonNull(item.quantity)));
        }
        return box;
    }

    private String normalizePackingBoxStatus(String status) {
        return "SEALED".equalsIgnoreCase(defaultText(status, "DRAFT")) ? "SEALED" : "DRAFT";
    }

    protected void validatePackingConfirmation(
            List<OutboundOrderLineRecord> outboundLines,
            List<PackingBoxRecord> boxes,
            List<PackingBoxItemRecord> items
    ) {
        if (boxes == null || boxes.isEmpty()) {
            throw new IllegalArgumentException("请先填写装箱箱明细。");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("请先填写装箱商品明细。");
        }
        for (PackingBoxRecord box : boxes) {
            if (box.lengthCm == null || box.lengthCm.compareTo(BigDecimal.ZERO) <= 0
                    || box.widthCm == null || box.widthCm.compareTo(BigDecimal.ZERO) <= 0
                    || box.heightCm == null || box.heightCm.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("确认装箱前必须填写箱规。");
            }
        }

        Map<Long, Integer> packedByLine = new LinkedHashMap<>();
        for (PackingBoxItemRecord item : items) {
            int quantity = nonNull(item.quantity);
            if (quantity <= 0) {
                throw new IllegalArgumentException("装箱商品数量必须大于 0。");
            }
            packedByLine.merge(item.outboundOrderLineId, quantity, Integer::sum);
        }
        for (OutboundOrderLineRecord line : outboundLines) {
            int expected = nonNull(line.quantity);
            int packed = packedByLine.getOrDefault(line.id, 0);
            if (expected != packed) {
                throw new IllegalArgumentException(line.partnerSku + " 装箱数量必须等于出库数量。");
            }
        }
    }

    protected void validatePendingPackingGroups(
            Long batchId,
            List<PendingPackingBox> boxes,
            Map<Long, OutboundOrderLineRecord> outboundLineById
    ) {
        Map<Long, ShippingSuggestionLineRecord> suggestionById = suggestionLinesById(batchId);
        for (PendingPackingBox box : boxes) {
            Set<String> groups = box.items.stream()
                    .map(item -> outboundLineById.get(item.outboundOrderLineId))
                    .filter(java.util.Objects::nonNull)
                    .map(line -> suggestionById.get(line.optionLineId))
                    .map(this::shippingPackingGroupCode)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            rejectMixedPackingGroups(groups);
        }
    }

    protected void validateStoredPackingGroups(
            Long batchId,
            List<OutboundOrderLineRecord> outboundLines,
            List<PackingBoxItemRecord> items
    ) {
        Map<Long, OutboundOrderLineRecord> outboundLineById = emptyIfNull(outboundLines).stream()
                .collect(Collectors.toMap(line -> line.id, line -> line, (left, right) -> left, LinkedHashMap::new));
        Map<Long, ShippingSuggestionLineRecord> suggestionById = suggestionLinesById(batchId);
        Map<Long, Set<String>> groupsByBox = new LinkedHashMap<>();
        for (PackingBoxItemRecord item : emptyIfNull(items)) {
            OutboundOrderLineRecord line = outboundLineById.get(item.outboundOrderLineId);
            ShippingSuggestionLineRecord suggestion = line == null ? null : suggestionById.get(line.optionLineId);
            String group = shippingPackingGroupCode(suggestion);
            if (StringUtils.hasText(group)) {
                groupsByBox.computeIfAbsent(item.packingBoxId, ignored -> new LinkedHashSet<>()).add(group);
            }
        }
        groupsByBox.values().forEach(this::rejectMixedPackingGroups);
    }

    protected Map<Long, ShippingSuggestionLineRecord> suggestionLinesById(Long batchId) {
        return emptyIfNull(mapper.listShippingSuggestionLines(batchId)).stream()
                .collect(Collectors.toMap(line -> line.id, line -> line, (left, right) -> left, LinkedHashMap::new));
    }

    protected void rejectMixedPackingGroups(Set<String> groups) {
        if (groups.size() > 1) {
            throw new IllegalArgumentException("不同物流方案或报价类别不能装在同一箱。");
        }
    }

protected PackingListView toPackingListView(PackingListRecord packingList) {
        PackingListView view = new PackingListView();
        view.id = String.valueOf(packingList.id);
        view.outboundOrderId = packingList.outboundOrderId;
        view.ownerUserId = packingList.ownerUserId;
        view.packingNo = packingList.packingNo;
        view.status = packingList.status;
        view.boxCount = nonNull(packingList.boxCount);
        view.packedQuantity = nonNull(packingList.packedQuantity);
        view.grossWeightKg = packingList.grossWeightKg == null ? null : packingList.grossWeightKg.toPlainString();
        view.volumeCbm = packingList.volumeCbm == null ? null : packingList.volumeCbm.toPlainString();
        view.remark = packingList.remark;
        view.createdAt = packingList.createdAt;
        view.updatedAt = packingList.updatedAt;
        return view;
    }

protected PackingListView toPackingListDetail(PackingListRecord packingList) {
        PackingListView view = toPackingListView(packingList);
        Map<Long, List<PackingBoxItemRecord>> itemsByBox = emptyIfNull(mapper.listPackingBoxItems(packingList.id)).stream()
                .collect(Collectors.groupingBy(item -> item.packingBoxId, LinkedHashMap::new, Collectors.toList()));
        for (PackingBoxRecord box : emptyIfNull(mapper.listPackingBoxes(packingList.id))) {
            PackingBoxView boxView = toPackingBoxView(box);
            applyPackingBoxSealSummary(boxView, packingList);
            for (PackingBoxItemRecord item : itemsByBox.getOrDefault(box.id, List.of())) {
                boxView.items.add(toPackingBoxItemView(item));
            }
            view.boxes.add(boxView);
        }
        return view;
    }

protected PackingBoxView toPackingBoxView(PackingBoxRecord box) {
        PackingBoxView view = new PackingBoxView();
        view.id = String.valueOf(box.id);
        view.packingListId = box.packingListId;
        view.outboundOrderId = box.outboundOrderId;
        view.boxNo = box.boxNo;
        view.lengthCm = box.lengthCm == null ? null : box.lengthCm.toPlainString();
        view.widthCm = box.widthCm == null ? null : box.widthCm.toPlainString();
        view.heightCm = box.heightCm == null ? null : box.heightCm.toPlainString();
        view.grossWeightKg = box.grossWeightKg == null ? null : box.grossWeightKg.toPlainString();
        view.status = defaultText(box.status, "DRAFT");
        view.isSealed = "SEALED".equals(view.status);
        view.quantity = nonNull(box.quantity);
        return view;
    }

    protected void applyPackingBoxSealSummary(PackingBoxView boxView, PackingListRecord packingList) {
        String packingListStatus = defaultText(packingList.status, "DRAFT");
        boolean packingListSealed = "CONFIRMED".equals(packingListStatus)
                || "SEALED".equals(packingListStatus)
                || "SHIPPED".equals(packingListStatus);
        if (packingListSealed) {
            boxView.status = packingListStatus;
        }
        boolean sealed = packingListSealed || "SEALED".equals(boxView.status);
        boxView.isSealed = sealed;
        if (sealed) {
            boxView.sealedAt = packingList.updatedAt;
        }
    }

protected PackingBoxCommand toPackingBoxCommand(PackingBoxRecord box, List<PackingBoxItemRecord> items) {
        PackingBoxCommand command = new PackingBoxCommand();
        command.boxNo = box.boxNo;
        command.status = box.status;
        command.lengthCm = box.lengthCm == null ? null : box.lengthCm.toPlainString();
        command.widthCm = box.widthCm == null ? null : box.widthCm.toPlainString();
        command.heightCm = box.heightCm == null ? null : box.heightCm.toPlainString();
        command.grossWeightKg = box.grossWeightKg == null ? null : box.grossWeightKg.toPlainString();
        for (PackingBoxItemRecord item : emptyIfNull(items)) {
            PackingBoxItemCommand itemCommand = new PackingBoxItemCommand();
            itemCommand.outboundOrderLineId = item.outboundOrderLineId;
            itemCommand.quantity = item.quantity;
            command.items.add(itemCommand);
        }
        return command;
    }

protected PackingBoxItemView toPackingBoxItemView(PackingBoxItemRecord item) {
        PackingBoxItemView view = new PackingBoxItemView();
        view.id = String.valueOf(item.id);
        view.packingListId = item.packingListId;
        view.packingBoxId = item.packingBoxId;
        view.outboundOrderId = item.outboundOrderId;
        view.outboundOrderLineId = item.outboundOrderLineId;
        view.productVariantId = item.productVariantId;
        view.partnerSku = item.partnerSku;
        view.siteCode = item.siteCode;
        view.actualTransportMode = normalizeTransportMode(item.actualTransportMode);
        view.quantity = nonNull(item.quantity);
        return view;
    }
}
