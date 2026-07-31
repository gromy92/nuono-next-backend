package com.nuono.next.warehousedispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderAccessRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderItemRecord;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

abstract class WarehouseDispatchAccessSupport extends WarehouseAggregateAccessSupport {

    protected WarehouseDispatchAccessSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected PurchaseOrderAccessRecord requireOrderAccess(BusinessAccessContext access, Long orderId) {
        Map<String, Long> authorizedStoreOwners = warehouseBusinessScope(access).storeOwnerUserIds();
        if (authorizedStoreOwners.isEmpty()) {
            throw purchaseOrderAccessDenied();
        }
        PurchaseOrderAccessRecord order = mapper.selectOrderAccess(orderId, authorizedStoreOwners);
        if (order == null || !canAccessPurchaseOrder(access, order)) {
            throw purchaseOrderAccessDenied();
        }
        return order;
    }

protected PurchaseOrderItemRecord requireItem(PurchaseOrderAccessRecord order, Long itemId) {
        PurchaseOrderItemRecord item = mapper.selectPurchaseOrderItem(itemId, order.id, order.ownerUserId);
        if (item == null
                || !order.id.equals(item.purchaseOrderId)
                || !order.ownerUserId.equals(item.ownerUserId)) {
            throw new IllegalArgumentException("采购单商品不存在或已删除。");
        }
        return item;
    }

protected boolean canUseBalance(BusinessAccessContext access, FulfillmentBalanceRecord balance) {
        return balance != null
                && warehouseBusinessScope(access).allows(balance.ownerUserId, balance.sourceStoreCode);
    }

protected Long resolveAggregateOwner(BusinessAccessContext access, List<Long> balanceIds) {
        WarehouseBusinessScope scope = warehouseBusinessScope(access);
        if (scope.ownerUserIds().isEmpty()) {
            throw new IllegalArgumentException("当前账号没有可发运的店铺范围。");
        }
        List<FulfillmentBalanceRecord> balanceScopes =
                mapper.selectBalanceScopes(balanceIds, scope.storeOwnerUserIds());
        if (balanceScopes.size() != balanceIds.size()) {
            throw new IllegalArgumentException("可发运来源不存在或已被占用。");
        }
        return scope.requireSingleBalanceOwner(balanceScopes);
    }

protected List<FulfillmentBalanceRecord> selectAuthorizedBalances(
            BusinessAccessContext access,
            List<Long> balanceIds
    ) {
        WarehouseBusinessScope scope = warehouseBusinessScope(access);
        List<FulfillmentBalanceRecord> balances =
                mapper.selectAuthorizedBalances(balanceIds, scope.storeOwnerUserIds());
        if (balances.size() != balanceIds.size()) {
            throw new IllegalArgumentException("可发运来源不存在或已被占用。");
        }
        scope.requireSingleBalanceOwner(balances);
        return balances;
    }

protected List<FulfillmentBalanceRecord> selectAuthorizedBalancesForUpdate(
            BusinessAccessContext access,
            List<Long> balanceIds,
            Long expectedOwnerUserId
    ) {
        WarehouseBusinessScope scope = warehouseBusinessScope(access);
        List<FulfillmentBalanceRecord> balances =
                mapper.selectAuthorizedBalancesForUpdate(balanceIds, scope.storeOwnerUserIds());
        if (balances.size() != balanceIds.size()) {
            throw new IllegalArgumentException("可发运来源不存在或已被占用。");
        }
        Long lockedOwnerUserId = scope.requireSingleBalanceOwner(balances);
        if (!lockedOwnerUserId.equals(expectedOwnerUserId)) {
            throw new IllegalArgumentException("所选库存的业务归属已变化，请刷新后重试。");
        }
        return balances;
    }

protected boolean logisticsQuoteBlocks(FulfillmentBalanceRecord balance) {
        return balance == null
                || !LOGISTICS_QUOTE_CONFIRMED.equals(normalizeLogisticsQuoteStatus(balance.logisticsQuoteStatus))
                || !SHIPPING_SUBMITTED.equals(normalizeShippingSubmitStatus(balance.logisticsShippingSubmitStatus));
    }

protected String normalizeLogisticsQuoteStatus(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        return LOGISTICS_QUOTE_CONFIRMED.equals(normalized) ? LOGISTICS_QUOTE_CONFIRMED : "PENDING_QUOTE";
    }

protected String normalizeShippingSubmitStatus(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        return SHIPPING_SUBMITTED.equals(normalized) ? SHIPPING_SUBMITTED : "NOT_SUBMITTED";
    }

protected String mergedQuoteStatus(String current, String next) {
        if (!LOGISTICS_QUOTE_CONFIRMED.equals(normalizeLogisticsQuoteStatus(next))) {
            return "PENDING_QUOTE";
        }
        return current == null ? LOGISTICS_QUOTE_CONFIRMED : normalizeLogisticsQuoteStatus(current);
    }

protected String mergedShippingSubmitStatus(String current, String next) {
        if (!SHIPPING_SUBMITTED.equals(normalizeShippingSubmitStatus(next))) {
            return "NOT_SUBMITTED";
        }
        return current == null ? SHIPPING_SUBMITTED : normalizeShippingSubmitStatus(current);
    }

protected boolean canAccessSourceStore(BusinessAccessContext access, String storeCode) {
        return access != null && access.canAccessStore(storeCode);
    }

private boolean canAccessPurchaseOrder(BusinessAccessContext access, PurchaseOrderAccessRecord order) {
        return order != null
                && warehouseBusinessScope(access).allows(order.ownerUserId, order.anchorStoreCodeCache);
    }

private BusinessAccessDeniedException purchaseOrderAccessDenied() {
        return new BusinessAccessDeniedException("当前账号不能操作该采购单。");
    }

protected boolean matchesKeyword(FulfillmentBalanceRecord balance, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return contains(balance.partnerSku, normalized)
                || contains(balance.skuParent, normalized)
                || contains(balance.titleCache, normalized)
                || contains(balance.purchaseOrderNo, normalized)
                || contains(balance.purchaseOrderTitle, normalized);
    }

protected boolean contains(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }
}
