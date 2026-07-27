package com.nuono.next.warehousedispatch;

import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionOptionRecord;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class WarehouseProcurementViews {

    public static class FulfillmentItemView {
        public String purchaseOrderId;
        public String purchaseOrderItemId;
        public String fulfillmentType;
        public String sourceName;
    }

    public static class ConfirmationView {
        public String id;
        public String confirmationNo;
        public String confirmationType;
        public String status;
        public Integer expectedQuantity;
        public Integer confirmedQuantity;
        public Integer abnormalQuantity;
        public List<WarehouseDispatchViews.ConfirmationLineView> lines = new ArrayList<>();
    }

    public static class ConfirmationLineView {
        public String purchaseOrderItemId;
        public String partnerSku;
        public Integer expectedQuantity;
        public Integer confirmedQuantity;
        public Integer abnormalQuantity;
    }

    public static class ReadyItemView {
        public String productVariantId;
        public String partnerSku;
        public String skuParent;
        public String productTitle;
        public String productImageUrl;
        public String siteCode;
        public String targetSiteCode;
        public String targetTransportMode;
        public Boolean isNewProduct;
        public Boolean manualConfirmRequired;
        public Boolean logisticsQuoteBlocking;
        public String logisticsQuoteStatus;
        public String logisticsShippingSubmitStatus;
        public String fulfillmentType;
        public String specStatus;
        public Integer availableQuantity;
        public List<WarehouseDispatchViews.ReadySourceView> sources = new ArrayList<>();
    }

    public static class PurchaseReceiptOrderView {
        public String id;
        public String orderNo;
        public String title;
        public String storeName;
        public String storeCode;
        public String createdAt;
        public List<WarehouseDispatchViews.PurchaseReceiptItemView> items = new ArrayList<>();
    }

    public static class PurchaseReceiptItemView {
        public String id;
        public String orderId;
        public String orderNo;
        public String purchaseOrderTitle;
        public String storeName;
        public String psku;
        public String title;
        public String imageUrl;
        public String siteCode;
        public String transportMode;
        public Integer expectedQty;
        public Integer receivedQty;
        public Integer plannedQty;
        public String specStatus;
        public String fulfillmentType;
        public String fulfillmentSourceName;
        public String exceptionText;
    }

    public static class ReadySourceView {
        public Long fulfillmentBalanceId;
        public String sourceStoreCode;
        public String sourceStoreName;
        public Long purchaseOrderId;
        public String purchaseOrderNo;
        public String purchaseOrderTitle;
        public Long purchaseOrderItemId;
        public Long purchaseOrderItemSiteId;
        public String siteCode;
        public String plannedTransportMode;
        public String targetSiteCode;
        public String targetTransportMode;
        public String logisticsQuoteStatus;
        public String logisticsShippingSubmitStatus;
        public Boolean logisticsQuoteBlocking;
        public Integer availableQuantity;
    }
}
