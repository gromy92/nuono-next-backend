package com.nuono.next.procurementorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.ReassignShippingOrderLinesCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WarehouseShippingLineReassignmentService {

    private static final String PENDING_QUOTE = "PENDING_QUOTE";
    private static final String NOT_SUBMITTED = "NOT_SUBMITTED";
    private static final Set<String> TRANSPORT_MODES = Set.of("AIR", "SEA");
    private final ProcurementPurchaseOrderMapper mapper;
    private final ObjectMapper objectMapper;

    public WarehouseShippingLineReassignmentService(
            ProcurementPurchaseOrderMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    void reassign(
            ShippingOrderRecord visibleOrder,
            ReassignShippingOrderLinesCommand command,
            Long operatorUserId
    ) {
        if (command == null || command.lineIds == null || command.lineIds.isEmpty()) {
            throw new IllegalArgumentException("请选择要调整运输方案的商品。");
        }
        ShippingOrderRecord order = visibleOrder == null ? null : mapper.selectShippingOrderByIdForUpdate(
                visibleOrder.id,
                visibleOrder.ownerUserId
        );
        if (order == null) {
            throw new IllegalArgumentException("发货单不存在或已删除。");
        }
        requirePending(order.shippingSubmitStatus, "只有未提交发货的仓库单才能调整运输方案。");
        LinkedHashSet<Long> requestedLineIds = command.lineIds.stream()
                .map(this::parseLineId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ShippingOrderLineRecord> lineById = safe(mapper.listShippingOrderLines(order.id)).stream()
                .filter(line -> line.id != null && requestedLineIds.contains(line.id))
                .collect(Collectors.toMap(
                        line -> line.id,
                        Function.identity(),
                        (left, ignored) -> left,
                        LinkedHashMap::new
                ));
        if (lineById.size() != requestedLineIds.size()) {
            throw new IllegalArgumentException("运输方案商品不存在或已删除。");
        }
        List<ShippingOrderLineRecord> selectedLines = requestedLineIds.stream()
                .map(lineById::get)
                .collect(Collectors.toList());
        if (selectedLines.stream().anyMatch(
                line -> !NOT_SUBMITTED.equals(normalized(line.shippingSubmitStatus)))) {
            throw new IllegalArgumentException("只有未提交仓库的商品才能调整运输方案。");
        }
        List<String> siteCodes = selectedLines.stream()
                .map(line -> normalized(line.siteCode))
                .distinct()
                .collect(Collectors.toList());
        if (siteCodes.size() != 1 || !StringUtils.hasText(siteCodes.get(0))) {
            throw new IllegalArgumentException("只能一起调整同一站点的商品。");
        }
        String transportMode = normalized(command.targetTransportMode);
        if (!TRANSPORT_MODES.contains(transportMode)) {
            throw new IllegalArgumentException("请选择空运或海运。");
        }
        List<ShippingOrderSegmentRecord> segments = safe(mapper.listShippingOrderSegments(order.id));
        ShippingOrderSegmentRecord target = resolveTarget(
                order,
                segments,
                command.targetSegmentId,
                siteCodes.get(0),
                transportMode,
                operatorUserId
        );
        if (selectedLines.stream().allMatch(line -> Objects.equals(line.shippingOrderSegmentId, target.id)
                && transportMode.equals(normalized(line.plannedTransportMode)))) {
            throw new IllegalArgumentException("商品已经在目标运输分区。");
        }
        int updated = mapper.reassignShippingOrderLines(
                order.id,
                order.ownerUserId,
                new ArrayList<>(requestedLineIds),
                target.id,
                transportMode,
                operatorUserId
        );
        if (updated != requestedLineIds.size()) {
            throw new IllegalArgumentException("运输方案已被其他操作更新，请刷新后重试。");
        }
        LinkedHashSet<Long> affectedSegmentIds = selectedLines.stream()
                .map(line -> line.shippingOrderSegmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        affectedSegmentIds.add(target.id);
        if (mapper.resetShippingOrderSegmentsAfterReassignment(
                order.id,
                new ArrayList<>(affectedSegmentIds),
                operatorUserId
        ) != affectedSegmentIds.size()) {
            throw new IllegalArgumentException("运输分区报价状态重置失败，请刷新后重试。");
        }
        mapper.softDeleteEmptyShippingOrderSegments(order.id, operatorUserId);
        if (mapper.recalculateShippingOrderSegmentAggregates(order.id, operatorUserId) <= 0) {
            throw new IllegalArgumentException("运输分区汇总更新失败，请刷新后重试。");
        }
        List<ShippingOrderLineRecord> refreshed = safe(mapper.listShippingOrderLines(order.id));
        if (mapper.updateShippingOrderTransportSummary(
                order.id,
                order.ownerUserId,
                writeJson(countByTransport(refreshed)),
                operatorUserId
        ) != 1 || mapper.refreshShippingOrderHeaderState(
                order.id, order.ownerUserId, operatorUserId) != 1) {
            throw new IllegalArgumentException("仓库单运输状态更新失败，请刷新后重试。");
        }
    }

    private ShippingOrderSegmentRecord resolveTarget(
            ShippingOrderRecord order,
            List<ShippingOrderSegmentRecord> segments,
            String rawTargetId,
            String siteCode,
            String transportMode,
            Long operatorUserId
    ) {
        if (StringUtils.hasText(rawTargetId)) {
            Long targetId = parsePositiveId(rawTargetId, "目标分区不存在或已删除。");
            ShippingOrderSegmentRecord target = segments.stream()
                    .filter(segment -> targetId.equals(segment.id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("目标分区不存在或已删除。"));
            if (!siteCode.equals(normalized(target.siteCode))
                    || !transportMode.equals(normalized(target.transportMode))) {
                throw new IllegalArgumentException("目标分区站点或运输方式不匹配。");
            }
            requirePending(target.shippingSubmitStatus, "只有未提交的目标分区才能接收商品。");
            return target;
        }
        ShippingOrderSegmentRecord target = new ShippingOrderSegmentRecord();
        target.id = mapper.nextShippingOrderSegmentId();
        target.shippingOrderId = order.id;
        target.ownerUserId = order.ownerUserId;
        target.siteCode = siteCode;
        target.transportMode = transportMode;
        target.segmentNo = order.shippingOrderNo + "-" + siteCode + "-" + transportMode + "-" + target.id;
        target.quoteStatus = PENDING_QUOTE;
        target.shippingSubmitStatus = NOT_SUBMITTED;
        target.lineCount = 0;
        target.skuCount = 0;
        target.totalQuantity = 0;
        target.missingYiteMaterialCount = 0;
        if (mapper.insertShippingOrderSegment(target, operatorUserId) != 1) {
            throw new IllegalArgumentException("目标运输分区创建失败，请刷新后重试。");
        }
        return target;
    }

    private Map<String, Integer> countByTransport(List<ShippingOrderLineRecord> lines) {
        return safe(lines).stream().collect(Collectors.groupingBy(
                line -> normalized(line.plannedTransportMode),
                LinkedHashMap::new,
                Collectors.summingInt(line -> line.quantity == null ? 0 : line.quantity)
        ));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("仓库单运输汇总保存失败。", exception);
        }
    }

    private Long parseLineId(String value) {
        return parsePositiveId(value, "发货单商品不存在或已删除。");
    }

    private static Long parsePositiveId(String value, String message) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requirePending(String value, String message) {
        if (!NOT_SUBMITTED.equals(normalized(value))) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
