package com.nuono.next.warehousedispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingBatchCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ShippingBatchSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ShippingBatchView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

abstract class WarehouseShippingBatchCreationOperations extends WarehouseMobileShippingOperations {

    protected WarehouseShippingBatchCreationOperations(
            WarehouseDispatchMapper mapper,
            ObjectMapper objectMapper
    ) {
        super(mapper, objectMapper);
    }

    @Transactional
    public ShippingBatchView createShippingBatch(
            BusinessAccessContext access,
            CreateShippingBatchCommand command
    ) {
        String clientRequestId = normalizeShippingBatchClientRequestId(
                command == null ? null : command.clientRequestId
        );
        if (command == null || command.sources == null || command.sources.isEmpty()) {
            throw new IllegalArgumentException("请选择可发运商品。");
        }
        LinkedHashMap<Long, Integer> requested = new LinkedHashMap<>();
        for (ShippingBatchSourceCommand source : command.sources) {
            if (source == null || source.fulfillmentBalanceId == null || nonNull(source.quantity) <= 0) {
                continue;
            }
            requested.merge(source.fulfillmentBalanceId, nonNull(source.quantity), Integer::sum);
        }
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("请选择可发运商品。");
        }

        List<Long> balanceIds = new ArrayList<>(requested.keySet());
        Long ownerUserId = resolveAggregateOwner(access, balanceIds);
        RequestFingerprint requestFingerprint =
                shippingBatchRequestFingerprint(command.remark, requested);
        ShippingBatchView replay = lockAndReplayShippingBatch(
                access,
                ownerUserId,
                clientRequestId,
                requestFingerprint
        );
        if (replay != null) {
            return replay;
        }
        command.clientRequestId = clientRequestId;
        List<FulfillmentBalanceRecord> balances =
                selectAuthorizedBalancesForUpdate(access, balanceIds, ownerUserId);
        return createShippingBatchFromLockedBalances(access, command, requested, balances, ownerUserId);
    }

    @Override
    protected ShippingBatchView createShippingBatchFromLockedBalances(
            BusinessAccessContext access,
            CreateShippingBatchCommand command,
            Map<Long, Integer> requested,
            List<FulfillmentBalanceRecord> balances,
            Long ownerUserId
    ) {
        for (FulfillmentBalanceRecord balance : balances) {
            if (!canUseBalance(access, balance)) {
                throw new IllegalArgumentException("当前账号不能发运所选来源。");
            }
            int quantity = requested.getOrDefault(balance.id, 0);
            if (quantity <= 0 || quantity > nonNull(balance.availableQuantity)) {
                throw new IllegalArgumentException(balance.partnerSku + " 可发运数量不足。");
            }
        }
        requireSingleLogisticsPartition(balances.stream()
                .map(balance -> logisticsPartitionKey(
                        effectiveSiteCode(balance),
                        effectiveTransportMode(balance)
                ))
                .collect(Collectors.toList()));

        Long operatorUserId = access.getSessionUserId();
        Long batchId = mapper.nextShippingBatchId();

        List<ShippingBatchSourceRecord> sourceRows = new ArrayList<>();
        for (FulfillmentBalanceRecord balance : balances) {
            int quantity = requested.getOrDefault(balance.id, 0);
            int reserved = mapper.reserveBalance(balance.id, ownerUserId, quantity, operatorUserId);
            if (reserved != 1) {
                throw new IllegalArgumentException(balance.partnerSku + " 可发运数量不足或已被占用。");
            }
            sourceRows.add(toShippingBatchSourceRecord(
                    batchId,
                    ownerUserId,
                    mapper.nextShippingBatchSourceId(),
                    balance,
                    quantity
            ));
        }
        String batchNo = shippingBatchNo(batchId, sourceRows);

        ShippingBatchRecord batch = new ShippingBatchRecord();
        batch.id = batchId;
        batch.ownerUserId = ownerUserId;
        batch.clientRequestId = trimToNull(command.clientRequestId);
        batch.requestFingerprint = batch.clientRequestId == null
                ? null
                : shippingBatchRequestFingerprint(command.remark, requested).persistedValue();
        batch.batchNo = batchNo;
        batch.status = "DRAFT";
        batch.sourceCount = sourceRows.size();
        batch.skuCount = shippingSkuCount(sourceRows);
        batch.totalQuantity = sourceRows.stream().mapToInt(source -> nonNull(source.reservedQuantity)).sum();
        batch.storeSummaryJson = writeJson(shippingStoreSummary(sourceRows));
        batch.siteSummaryJson = writeJson(shippingSiteSummary(sourceRows));
        batch.transportSummaryJson = writeJson(shippingPlannedTransportSummary(sourceRows));
        batch.originSummaryJson = writeJson(shippingOriginSummary(sourceRows));
        batch.remark = trimToNull(command.remark);
        mapper.insertShippingBatch(batch, operatorUserId);
        for (ShippingBatchSourceRecord sourceRow : sourceRows) {
            mapper.insertShippingBatchSource(sourceRow, operatorUserId);
        }

        List<ShippingBatchSourceRecord> currentSources = emptyIfNull(mapper.listShippingBatchSources(batch.id));
        ShippingBatchView view = toShippingBatchView(batch);
        for (ShippingBatchSourceRecord sourceRow : currentSources) {
            view.sources.add(toShippingBatchSourceView(sourceRow));
        }
        view.options.addAll(createDefaultShippingSuggestionOptions(batch, currentSources, operatorUserId));
        view.optionCount = view.options.size();
        log(null, "CREATE_SHIPPING_BATCH", operatorUserId, null, "DRAFT", batchNo);
        return view;
    }
}
