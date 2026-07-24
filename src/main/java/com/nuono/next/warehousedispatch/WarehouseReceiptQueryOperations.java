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

abstract class WarehouseReceiptQueryOperations extends WarehouseReceiptCommandOperations {

    protected WarehouseReceiptQueryOperations(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

@Transactional(readOnly = true)
    public List<PurchaseReceiptOrderView> listReceiptOrders(BusinessAccessContext access, String keyword) {
        Long ownerUserId = ownerUserId(access);
        Collection<String> storeCodes = access == null ? Set.of() : access.getStoreCodes();
        List<PurchaseReceiptRow> rows = mapper.listReceiptRows(ownerUserId, storeCodes, trim(keyword));
        Map<Long, PurchaseReceiptOrderView> byOrder = new LinkedHashMap<>();
        for (PurchaseReceiptRow row : rows) {
            if (!canAccessSourceStore(access, row.sourceStoreCode)) {
                continue;
            }
            Long receiptSourceId = row.receiptSourceId == null ? row.orderId : row.receiptSourceId;
            PurchaseReceiptOrderView order = byOrder.computeIfAbsent(receiptSourceId, ignored -> {
                PurchaseReceiptOrderView view = new PurchaseReceiptOrderView();
                view.id = String.valueOf(receiptSourceId);
                view.orderNo = defaultText(row.receiptSourceNo, row.orderNo);
                view.title = defaultText(row.receiptSourceTitle, row.orderTitle);
                view.storeName = defaultText(row.receiptSourceStoreName, row.storeName);
                view.storeCode = defaultText(row.receiptSourceStoreCode, row.sourceStoreCode);
                view.createdAt = defaultText(row.receiptSourceCreatedAt, row.createdAt);
                return view;
            });
            order.items.add(toReceiptItemView(row));
        }
        return new ArrayList<>(byOrder.values());
    }

@Transactional(readOnly = true)
    public List<ReadyItemView> listReadyItems(
            BusinessAccessContext access,
            String siteCode,
            String fulfillmentType,
            String keyword
    ) {
        Long ownerUserId = ownerUserId(access);
        Collection<String> storeCodes = access == null ? Set.of() : access.getStoreCodes();
        String normalizedSiteCode = normalizeSiteCode(siteCode);
        String normalizedFulfillmentType = StringUtils.hasText(fulfillmentType)
                ? normalizeFulfillmentType(fulfillmentType)
                : null;
        String normalizedKeyword = trim(keyword);
        List<FulfillmentBalanceRecord> balances = mapper.listReadyBalances(
                ownerUserId,
                storeCodes,
                normalizedSiteCode,
                normalizedFulfillmentType
        );
        Map<String, ReadyItemView> grouped = new LinkedHashMap<>();
        for (FulfillmentBalanceRecord balance : balances) {
            if (!matchesKeyword(balance, normalizedKeyword)) {
                continue;
            }
            if (!canAccessSourceStore(access, balance.sourceStoreCode)) {
                continue;
            }
            String targetSiteCode = resolvedSiteCode(balance);
            String targetTransportMode = resolvedTransportMode(balance);
            String key = productIdentityKey(balance) + "|" + targetSiteCode + "|" + targetTransportMode + "|"
                    + normalizeFulfillmentType(balance.fulfillmentType) + "|"
                    + defaultText(balance.specStatus, "READY");
            ReadyItemView view = grouped.computeIfAbsent(key, ignored -> {
                ReadyItemView item = new ReadyItemView();
                item.productVariantId = String.valueOf(balance.productVariantId);
                item.partnerSku = balance.partnerSku;
                item.skuParent = balance.skuParent;
                item.productTitle = defaultText(balance.titleCache, balance.partnerSku);
                item.productImageUrl = ProductImageUrlSupport.normalize(balance.imageUrlCache);
                item.siteCode = balance.siteCode;
                item.targetSiteCode = targetSiteCode;
                item.targetTransportMode = targetTransportMode;
                item.isNewProduct = Boolean.TRUE.equals(balance.isNewProduct);
                item.manualConfirmRequired = requiresManualConfirm(balance);
                item.fulfillmentType = normalizeFulfillmentType(balance.fulfillmentType);
                item.specStatus = defaultText(balance.specStatus, "READY");
                item.availableQuantity = 0;
                return item;
            });
            view.isNewProduct = Boolean.TRUE.equals(view.isNewProduct) || Boolean.TRUE.equals(balance.isNewProduct);
            view.manualConfirmRequired = Boolean.TRUE.equals(view.manualConfirmRequired) || requiresManualConfirm(balance);
            view.logisticsQuoteBlocking =
                    Boolean.TRUE.equals(view.logisticsQuoteBlocking) || logisticsQuoteBlocks(balance);
            view.logisticsQuoteStatus = mergedQuoteStatus(view.logisticsQuoteStatus, balance.logisticsQuoteStatus);
            view.logisticsShippingSubmitStatus =
                    mergedShippingSubmitStatus(view.logisticsShippingSubmitStatus, balance.logisticsShippingSubmitStatus);
            view.availableQuantity += nonNull(balance.availableQuantity);
            view.sources.add(toReadySourceView(balance));
        }
        return new ArrayList<>(grouped.values());
    }

@Transactional
    public ReadySourceView updateReadyItemDispatchTarget(
            BusinessAccessContext access,
            String fulfillmentBalanceId,
            UpdateDispatchTargetCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("缺少库存发货目标。");
        }
        Long balanceId = parseLongId(fulfillmentBalanceId, "可发运库存不存在或已删除。");
        List<FulfillmentBalanceRecord> balances = mapper.selectBalancesForUpdate(List.of(balanceId));
        if (balances.size() != 1) {
            throw new IllegalArgumentException("可发运库存不存在或已删除。");
        }
        FulfillmentBalanceRecord balance = balances.get(0);
        if (!canUseBalance(access, balance) || nonNull(balance.availableQuantity) <= 0) {
            throw new IllegalArgumentException("当前账号不能修改所选库存的发货目标。");
        }
        String targetSiteCode = requireLogisticsSiteCode(command.targetSiteCode);
        String targetTransportMode = requireLogisticsTransportMode(command.targetTransportMode);
        if (mapper.updateBalanceDispatchTarget(
                balance.id,
                balance.ownerUserId,
                targetSiteCode,
                targetTransportMode,
                access.getSessionUserId()
        ) != 1) {
            throw new IllegalArgumentException("库存状态已变化，请刷新后重试。");
        }
        balance.targetSiteCode = targetSiteCode;
        balance.targetTransportMode = targetTransportMode;
        return toReadySourceView(balance);
    }

@Transactional(readOnly = true)
    public List<PurchaseOrderLogisticsComparisonView> listPurchaseOrderLogisticsComparisons(
            BusinessAccessContext access,
            Integer limit
    ) {
        int normalizedLimit = Math.max(1, Math.min(limit == null ? 10 : limit, 50));
        Long ownerUserId = ownerUserId(access);
        Collection<String> storeCodes = access == null ? Set.of() : access.getStoreCodes();
        List<FulfillmentBalanceRecord> balances = mapper.listPurchasePlanBalances(ownerUserId, storeCodes).stream()
                .filter(balance -> balance.purchaseOrderId != null)
                .filter(balance -> canAccessSourceStore(access, balance.sourceStoreCode))
                .filter(balance -> purchaseComparisonQuantity(balance) > 0)
                .sorted(Comparator
                        .comparing((FulfillmentBalanceRecord balance) -> balance.purchaseOrderId, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(balance -> defaultText(balance.siteCode, ""))
                        .thenComparing(balance -> defaultText(balance.plannedTransportMode, "")))
                .collect(Collectors.toList());
        Set<Long> includedOrderIds = new LinkedHashSet<>();
        List<FulfillmentBalanceRecord> limitedBalances = new ArrayList<>();
        for (FulfillmentBalanceRecord balance : balances) {
            if (!includedOrderIds.contains(balance.purchaseOrderId) && includedOrderIds.size() >= normalizedLimit) {
                continue;
            }
            includedOrderIds.add(balance.purchaseOrderId);
            limitedBalances.add(balance);
        }

        Map<Long, List<FulfillmentBalanceRecord>> byOrder = limitedBalances.stream()
                .collect(Collectors.groupingBy(
                        balance -> balance.purchaseOrderId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<PurchaseOrderLogisticsComparisonView> result = new ArrayList<>();
        for (List<FulfillmentBalanceRecord> orderBalances : byOrder.values()) {
            result.add(toPurchaseOrderLogisticsComparisonView(orderBalances));
        }
        return result;
    }
}
