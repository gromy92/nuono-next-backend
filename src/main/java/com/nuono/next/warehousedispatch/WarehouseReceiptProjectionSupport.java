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

abstract class WarehouseReceiptProjectionSupport extends WarehouseDispatchCoreContract {

    protected WarehouseReceiptProjectionSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected void ensureItemBalances(PurchaseOrderItemRecord item, String fulfillmentType, Long operatorUserId) {
        List<PurchaseOrderItemSiteRecord> sites = mapper.listItemSitesForBalance(item.id);
        if (sites.isEmpty()) {
            throw new IllegalArgumentException("采购单商品缺少站点计划，不能进入仓库发运。");
        }
        for (PurchaseOrderItemSiteRecord site : sites) {
            mapper.upsertBalanceFromItemSite(site.id, fulfillmentType, operatorUserId);
        }
    }

protected Map<Long, Integer> allocateByPlannedQuantity(List<FulfillmentBalanceRecord> balances, int quantity) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (quantity <= 0) {
            for (FulfillmentBalanceRecord balance : balances) {
                result.put(balance.id, 0);
            }
            return result;
        }
        int plannedTotal = balances.stream().mapToInt(balance -> nonNull(balance.plannedQuantity)).sum();
        if (plannedTotal <= 0) {
            throw new IllegalArgumentException("采购计划数量为空，不能分摊收货。");
        }
        List<AllocationRemainder> remainders = new ArrayList<>();
        int allocated = 0;
        int index = 0;
        for (FulfillmentBalanceRecord balance : balances) {
            BigDecimal exact = BigDecimal.valueOf(quantity)
                    .multiply(BigDecimal.valueOf(nonNull(balance.plannedQuantity)))
                    .divide(BigDecimal.valueOf(plannedTotal), 8, RoundingMode.HALF_UP);
            int floor = exact.setScale(0, RoundingMode.DOWN).intValue();
            result.put(balance.id, floor);
            allocated += floor;
            remainders.add(new AllocationRemainder(
                    balance.id,
                    exact.subtract(BigDecimal.valueOf(floor)),
                    nonNull(balance.plannedQuantity),
                    index
            ));
            index++;
        }
        int remaining = quantity - allocated;
        remainders.sort(Comparator
                .comparing((AllocationRemainder item) -> item.remainder).reversed()
                .thenComparing((AllocationRemainder item) -> item.plannedQuantity, Comparator.reverseOrder())
                .thenComparing(item -> item.index));
        for (int i = 0; i < remaining && i < remainders.size(); i++) {
            AllocationRemainder remainder = remainders.get(i);
            result.put(remainder.balanceId, result.get(remainder.balanceId) + 1);
        }
        return result;
    }

protected ReadySourceView toReadySourceView(FulfillmentBalanceRecord balance) {
        ReadySourceView source = new ReadySourceView();
        source.fulfillmentBalanceId = balance.id;
        source.sourceStoreCode = balance.sourceStoreCode;
        source.sourceStoreName = balance.sourceStoreName;
        source.purchaseOrderId = balance.purchaseOrderId;
        source.purchaseOrderNo = balance.purchaseOrderNo;
        source.purchaseOrderTitle = balance.purchaseOrderTitle;
        source.purchaseOrderItemId = balance.purchaseOrderItemId;
        source.purchaseOrderItemSiteId = balance.purchaseOrderItemSiteId;
        source.plannedTransportMode = normalizeTransportMode(balance.plannedTransportMode);
        source.logisticsQuoteStatus = normalizeLogisticsQuoteStatus(balance.logisticsQuoteStatus);
        source.logisticsShippingSubmitStatus = normalizeShippingSubmitStatus(balance.logisticsShippingSubmitStatus);
        source.logisticsQuoteBlocking = logisticsQuoteBlocks(balance);
        source.availableQuantity = nonNull(balance.availableQuantity);
        return source;
    }

protected PurchaseReceiptItemView toReceiptItemView(PurchaseReceiptRow row) {
        PurchaseReceiptItemView view = new PurchaseReceiptItemView();
        view.id = String.valueOf(row.itemId);
        view.orderId = String.valueOf(row.orderId);
        view.orderNo = row.orderNo;
        view.purchaseOrderTitle = row.orderTitle;
        view.storeName = row.storeName;
        view.psku = row.partnerSku;
        view.title = defaultText(row.titleCache, row.partnerSku);
        view.imageUrl = ProductImageUrlSupport.normalize(row.imageUrlCache);
        view.siteCode = defaultText(row.siteCode, "SA");
        view.transportMode = normalizeTransportMode(row.transportMode);
        view.expectedQty = nonNull(row.expectedQuantity);
        view.receivedQty = nonNull(row.receivedQuantity);
        view.plannedQty = nonNull(row.plannedQuantity);
        view.specStatus = "SPEC_MISSING".equals(row.specStatus) ? "missing" : "complete";
        view.fulfillmentType = normalizeFulfillmentType(row.fulfillmentType);
        view.fulfillmentSourceName = row.fulfillmentSourceName;
        if (view.receivedQty < view.expectedQty && view.receivedQty > 0) {
            view.exceptionText = "少货 " + (view.expectedQty - view.receivedQty);
        }
        return view;
    }
}
