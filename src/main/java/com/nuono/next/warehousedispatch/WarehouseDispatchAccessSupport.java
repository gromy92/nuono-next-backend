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

abstract class WarehouseDispatchAccessSupport extends WarehousePackingProjectionSupport {

    protected WarehouseDispatchAccessSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected PurchaseOrderAccessRecord requireOrderAccess(BusinessAccessContext access, Long orderId) {
        PurchaseOrderAccessRecord order = mapper.selectOrderAccess(orderId);
        if (order == null) {
            throw new IllegalArgumentException("采购单不存在或已删除。");
        }
        if (access == null || !canAccessSourceStore(access, order.anchorStoreCodeCache)) {
            throw new IllegalArgumentException("当前账号不能操作该采购单。");
        }
        return order;
    }

protected PurchaseOrderItemRecord requireItem(PurchaseOrderAccessRecord order, Long itemId) {
        PurchaseOrderItemRecord item = mapper.selectPurchaseOrderItem(itemId);
        if (item == null || !order.id.equals(item.purchaseOrderId)) {
            throw new IllegalArgumentException("采购单商品不存在或已删除。");
        }
        return item;
    }

protected DispatchPlanRecord requireDispatchPlanAccess(BusinessAccessContext access, Long dispatchPlanId) {
        DispatchPlanRecord plan = mapper.selectDispatchPlanById(dispatchPlanId);
        if (plan == null) {
            throw new IllegalArgumentException("发运计划不存在或已删除。");
        }
        requireOwnerAccess(access, plan.ownerUserId);
        return plan;
    }

protected ShippingBatchRecord requireShippingBatchAccess(BusinessAccessContext access, Long shippingBatchId) {
        ShippingBatchRecord batch = mapper.selectShippingBatchById(shippingBatchId);
        if (batch == null) {
            throw new IllegalArgumentException("发货批次不存在或已删除。");
        }
        requireOwnerAccess(access, batch.ownerUserId);
        return batch;
    }

protected OutboundOrderRecord requireOutboundOrderAccess(BusinessAccessContext access, Long outboundOrderId) {
        OutboundOrderRecord outboundOrder = mapper.selectOutboundOrderById(outboundOrderId);
        if (outboundOrder == null) {
            throw new IllegalArgumentException("出库单不存在或已删除。");
        }
        requireOwnerAccess(access, outboundOrder.ownerUserId);
        return outboundOrder;
    }

protected PackingListRecord requirePackingListAccess(BusinessAccessContext access, Long packingListId) {
        PackingListRecord packingList = mapper.selectPackingListById(packingListId);
        if (packingList == null) {
            throw new IllegalArgumentException("装箱单不存在或已删除。");
        }
        requireOwnerAccess(access, packingList.ownerUserId);
        return packingList;
    }

protected DispatchPlanRecord requireHandoffAccess(BusinessAccessContext access, String handoffRequestNo) {
        DispatchPlanRecord plan = mapper.selectDispatchPlanByHandoffRequest(handoffRequestNo);
        if (plan == null) {
            throw new IllegalArgumentException("物流交接不存在或已失效。");
        }
        requireOwnerAccess(access, plan.ownerUserId);
        return plan;
    }

protected void requireOwnerAccess(BusinessAccessContext access, Long ownerUserId) {
        if (access == null || ownerUserId == null || !ownerUserId.equals(ownerUserId(access))) {
            throw new IllegalArgumentException("当前账号不能操作该发运计划。");
        }
    }

protected boolean canUseBalance(BusinessAccessContext access, FulfillmentBalanceRecord balance) {
        return balance != null
                && ownerUserId(access).equals(balance.ownerUserId)
                && canAccessSourceStore(access, balance.sourceStoreCode);
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
        return access != null && (access.getStoreCodes().isEmpty() || access.canAccessStore(storeCode));
    }

protected Long ownerUserId(BusinessAccessContext access) {
        if (access == null) {
            throw new IllegalArgumentException("缺少业务访问上下文。");
        }
        if (access.getBusinessOwnerUserId() != null) {
            return access.getBusinessOwnerUserId();
        }
        return access.getSessionUserId();
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
