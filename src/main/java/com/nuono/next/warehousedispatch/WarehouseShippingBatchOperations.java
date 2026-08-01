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

abstract class WarehouseShippingBatchOperations extends WarehouseShippingBatchCreationOperations {

    protected WarehouseShippingBatchOperations(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

@Transactional(readOnly = true)
    public List<ShippingBatchView> listShippingBatches(BusinessAccessContext access) {
        return mapper.listShippingBatches(warehouseBusinessScope(access).storeOwnerUserIds()).stream()
                .map(this::toShippingBatchView)
                .collect(Collectors.toList());
    }

@Transactional(readOnly = true)
    public ShippingBatchView getShippingBatch(BusinessAccessContext access, String shippingBatchId) {
        return toShippingBatchDetail(requireShippingBatchAccess(
                access,
                parseLongId(shippingBatchId, "发货批次不存在或已删除。")
        ));
    }

@Transactional
    public ShippingSuggestionOptionView createShippingTargetOption(
            BusinessAccessContext access,
            String shippingBatchId,
            CreateShippingTargetOptionCommand command
    ) {
        Long parsedBatchId = parseLongId(shippingBatchId, "发货批次不存在或已删除。");
        ShippingBatchRecord batch = requireShippingBatchAccessForUpdate(access, parsedBatchId);
        if (!"DRAFT".equals(batch.status) && !"OPTION_SELECTED".equals(batch.status)) {
            throw new IllegalArgumentException("只有草稿状态的发货批次可以新增目标货代方案。");
        }

        List<ShippingBatchSourceRecord> sources = emptyIfNull(mapper.listShippingBatchSources(batch.id));
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("发货批次没有可评估商品。");
        }

        Long operatorUserId = access.getSessionUserId();
        ShippingOptionDefinition definition = customShippingOptionDefinition(command);
        ShippingSuggestionOptionView optionView = createShippingSuggestionOption(
                batch,
                sources,
                definition,
                operatorUserId
        );
        log(null, "CREATE_SHIPPING_TARGET_OPTION", operatorUserId, batch.status, batch.status, definition.optionName);
        return optionView;
    }

@Transactional
    public ShippingBatchView selectShippingOption(
            BusinessAccessContext access,
            String shippingBatchId,
            String optionId
    ) {
        Long parsedBatchId = parseLongId(shippingBatchId, "发货批次不存在或已删除。");
        Long parsedOptionId = parseLongId(optionId, "货运计划方案不存在或已删除。");
        ShippingBatchRecord batch = requireShippingBatchAccessForUpdate(access, parsedBatchId);
        if (!"DRAFT".equals(batch.status) && !"OPTION_SELECTED".equals(batch.status)) {
            throw new IllegalArgumentException("只有草稿状态的发货批次可以选择建议方案。");
        }
        ShippingSuggestionOptionRecord option = mapper.selectShippingSuggestionOptionById(parsedOptionId);
        if (option == null || !parsedBatchId.equals(option.batchId)) {
            throw new IllegalArgumentException("货运计划方案不存在或不属于该批次。");
        }

        Long operatorUserId = access.getSessionUserId();
        mapper.clearSelectedShippingOptions(batch.id, operatorUserId);
        if (mapper.selectShippingSuggestionOption(batch.id, option.id, operatorUserId) != 1) {
            throw new IllegalArgumentException("货运计划方案不存在或已失效。");
        }
        if (mapper.updateShippingBatchSelectedOption(batch.id, batch.ownerUserId, option.id, operatorUserId) != 1) {
            throw new WarehouseInventoryStateConflictException("发货批次状态已变化，请刷新后重试。");
        }
        log(null, "SELECT_SHIPPING_OPTION", operatorUserId, batch.status, "OPTION_SELECTED", option.optionType);

        ShippingBatchRecord updated = mapper.selectShippingBatchById(batch.id);
        return toShippingBatchView(updated == null ? batch : updated);
    }

@Transactional
    public List<OutboundOrderView> createOutboundOrders(BusinessAccessContext access, String shippingBatchId) {
        Long parsedBatchId = parseLongId(shippingBatchId, "发货批次不存在或已删除。");
        ShippingBatchRecord batch = requireShippingBatchAccessForUpdate(access, parsedBatchId);
        if (!"OPTION_SELECTED".equals(batch.status)) {
            throw new IllegalArgumentException("请先选择货运计划方案。");
        }
        if (batch.selectedOptionId == null) {
            throw new IllegalArgumentException("请先选择货运计划方案。");
        }
        ShippingSuggestionOptionRecord option = mapper.selectShippingSuggestionOptionById(batch.selectedOptionId);
        if (option == null || !batch.id.equals(option.batchId)) {
            throw new IllegalArgumentException("选中的货运计划方案不存在或已失效。");
        }

        List<ShippingBatchSourceRecord> sources = emptyIfNull(mapper.listShippingBatchSources(batch.id));
        List<ShippingSuggestionLineRecord> optionLines = emptyIfNull(mapper.listShippingSuggestionLines(batch.id)).stream()
                .filter(line -> batch.selectedOptionId.equals(line.optionId))
                .collect(Collectors.toList());
        List<ShippingSuggestionLineSourceRecord> optionSources = emptyIfNull(mapper.listShippingSuggestionLineSources(batch.id)).stream()
                .filter(source -> batch.selectedOptionId.equals(source.optionId))
                .collect(Collectors.toList());
        if (optionLines.isEmpty()) {
            throw new IllegalArgumentException("选中的货运计划方案没有可出库明细。");
        }
        validateSelectedOptionAllocation(sources, optionLines, optionSources);

        Long operatorUserId = access.getSessionUserId();
        Map<Long, ShippingBatchSourceRecord> batchSourceById = sources.stream()
                .collect(Collectors.toMap(source -> source.id, source -> source, (left, right) -> left, LinkedHashMap::new));
        Map<Long, List<ShippingSuggestionLineSourceRecord>> suggestionSourcesByLine = optionSources.stream()
                .collect(Collectors.groupingBy(source -> source.lineId, LinkedHashMap::new, Collectors.toList()));
        List<OutboundOrderView> result = new ArrayList<>();
        Map<String, List<ShippingSuggestionLineRecord>> linesByPartition = optionLines.stream()
                .collect(Collectors.groupingBy(
                        line -> logisticsPartitionKey(line.siteCode, line.actualTransportMode),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        for (List<ShippingSuggestionLineRecord> originLines : linesByPartition.values()) {
            ShippingSuggestionLineRecord firstLine = originLines.get(0);
            Long outboundOrderId = mapper.nextOutboundOrderId();
            OutboundOrderRecord order = new OutboundOrderRecord();
            order.id = outboundOrderId;
            order.batchId = batch.id;
            order.optionId = batch.selectedOptionId;
            order.ownerUserId = batch.ownerUserId;
            order.outboundNo = "WO-" + outboundOrderId;
            order.status = "DRAFT";
            order.originType = normalizeFulfillmentType(firstLine.fulfillmentType);
            order.originName = originLines.stream().map(line -> defaultText(line.sourcePartyName, ""))
                    .distinct().count() == 1 ? firstLine.sourcePartyName : "多来源";
            order.skuCount = outboundSkuCount(originLines);
            order.totalQuantity = originLines.stream().mapToInt(line -> nonNull(line.quantity)).sum();
            order.siteSummaryJson = writeJson(outboundSiteSummary(originLines));
            order.transportSummaryJson = writeJson(outboundTransportSummary(originLines));
            mapper.insertOutboundOrder(order, operatorUserId);

            OutboundOrderView orderView = toOutboundOrderView(order);
            for (ShippingSuggestionLineRecord suggestionLine : originLines) {
                OutboundOrderLineRecord outboundLine = toOutboundOrderLineRecord(
                        outboundOrderId,
                        batch.id,
                        batch.ownerUserId,
                        mapper.nextOutboundOrderLineId(),
                        suggestionLine
                );
                mapper.insertOutboundOrderLine(outboundLine, operatorUserId);
                OutboundOrderLineView lineView = toOutboundOrderLineView(outboundLine);
                for (ShippingSuggestionLineSourceRecord suggestionSource
                        : suggestionSourcesByLine.getOrDefault(suggestionLine.id, List.of())) {
                    ShippingBatchSourceRecord batchSource = batchSourceById.get(suggestionSource.batchSourceId);
                    OutboundOrderLineSourceRecord outboundSource = toOutboundOrderLineSourceRecord(
                            outboundOrderId,
                            outboundLine.id,
                            mapper.nextOutboundOrderLineSourceId(),
                            suggestionSource,
                            batchSource
                    );
                    mapper.insertOutboundOrderLineSource(outboundSource, operatorUserId);
                    lineView.sources.add(toOutboundOrderLineSourceView(outboundSource));
                }
                hydrateOutboundLineStoreScope(lineView);
                orderView.lines.add(lineView);
            }
            result.add(orderView);
        }

        if (mapper.updateShippingBatchOutboundCreated(
                batch.id, batch.ownerUserId, batch.selectedOptionId, operatorUserId
        ) != 1) {
            throw new WarehouseInventoryStateConflictException("发货批次方案已变化，请刷新后重试。");
        }
        log(null, "CREATE_OUTBOUND_ORDER", operatorUserId, batch.status, "OUTBOUND_CREATED", batch.batchNo);
        return result;
    }

@Transactional(readOnly = true)
    public List<OutboundOrderView> listOutboundOrders(BusinessAccessContext access, String shippingBatchId) {
        ShippingBatchRecord batch = requireShippingBatchAccess(
                access,
                parseLongId(shippingBatchId, "发货批次不存在或已删除。")
        );
        return mapper.listOutboundOrdersByBatch(
                batch.id,
                warehouseBusinessScope(access).storeOwnerUserIds()
        ).stream()
                .map(this::toOutboundOrderDetail)
                .collect(Collectors.toList());
    }
}
