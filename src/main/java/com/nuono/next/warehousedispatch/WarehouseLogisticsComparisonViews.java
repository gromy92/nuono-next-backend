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

public class WarehouseLogisticsComparisonViews extends WarehouseMobileShippingViews {

    public static class PurchaseOrderLogisticsComparisonView {
        public String purchaseOrderId;
        public String purchaseOrderNo;
        public String purchaseOrderTitle;
        public String sourceStoreCode;
        public String sourceStoreName;
        public Integer skuCount;
        public Integer totalQuantity;
        public String quantityBasis;
        public String quantityBasisLabel;
        public String fulfillmentReadinessNote;
        public BigDecimal actualWeightKg;
        public BigDecimal volumeCbm;
        public String recommendedOptionId;
        public String recommendedOptionName;
        public BigDecimal recommendedEstimatedAmount;
        public String currency;
        public List<String> defects = new ArrayList<>();
        public List<String> missingPlanSuggestions = new ArrayList<>();
        public List<WarehouseDispatchViews.PurchaseOrderLogisticsSegmentView> segments = new ArrayList<>();
    }

    public static class PurchaseOrderLogisticsSegmentView {
        public String segmentKey;
        public String siteCode;
        public String plannedTransportMode;
        public Integer skuCount;
        public Integer totalQuantity;
        public String quantityBasis;
        public String quantityBasisLabel;
        public BigDecimal actualWeightKg;
        public BigDecimal volumeCbm;
        public String recommendedOptionId;
        public String recommendedOptionName;
        public BigDecimal recommendedEstimatedAmount;
        public String currency;
        public List<String> defects = new ArrayList<>();
        public List<String> missingPlanSuggestions = new ArrayList<>();
        public List<WarehouseDispatchViews.ShippingSuggestionOptionView> options = new ArrayList<>();
    }
}
