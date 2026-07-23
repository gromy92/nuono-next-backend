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

public class WarehouseMobileShippingViews extends WarehouseShippingBatchViews {

    public static class MobileShippingDecisionPreviewView {
        public String decisionStatus;
        public WarehouseDispatchViews.MobileShippingDecisionOptionView recommendedOption;
        public WarehouseDispatchViews.MobileShippingDecisionOptionView alternativeOption;
        public List<WarehouseDispatchViews.MobileShippingDecisionOptionView> options = new ArrayList<>();
        public List<String> blockers = new ArrayList<>();
        public List<String> reviewReasons = new ArrayList<>();
    }

    public static class MobileShippingDecisionConfirmView {
        public String shippingBatchId;
        public String batchNo;
        public String status;
        public String decisionStatus;
        public String recommendedOptionId;
        public WarehouseDispatchViews.MobileShippingDecisionOptionView recommendedSummary;
        public String nextAction;
    }

    public static class MobileShippingDecisionOptionView {
        public String optionKey;
        public String optionId;
        public String optionName;
        public String decisionStatus;
        public List<String> forwarderNames = new ArrayList<>();
        public List<String> routeNames = new ArrayList<>();
        public List<WarehouseDispatchViews.MobileShippingDecisionForwarderAllocationView> forwarderAllocations = new ArrayList<>();
        public BigDecimal estimatedTotalAmount;
        public BigDecimal avgUnitAmount;
        public String currency;
        public BigDecimal actualWeightKg;
        public BigDecimal volumeCbm;
        public BigDecimal chargeableWeightKg;
        public List<String> reasons = new ArrayList<>();
        public List<String> blockers = new ArrayList<>();
        public List<String> reviewReasons = new ArrayList<>();
        public List<WarehouseDispatchViews.MobileShippingDecisionLineView> lines = new ArrayList<>();
    }

    public static class MobileShippingDecisionLineView {
        public String id;
        public Long productVariantId;
        public String partnerSku;
        public String title;
        public String siteCode;
        public String transportMode;
        public String fulfillmentType;
        public String sourcePartyName;
        public Integer quantity;
        public String targetForwarderCode;
        public String targetForwarderName;
        public String routeCode;
        public String routeName;
        public String cargoCategoryCode;
        public String cargoCategoryName;
        public String quoteCargoCategoryCode;
        public String quoteCargoCategoryName;
        public Boolean cargoCategoryReviewRequired;
        public BigDecimal actualWeightKg;
        public BigDecimal volumeCbm;
        public BigDecimal chargeableWeightKg;
        public BigDecimal estimatedAmount;
        public String currency;
        public List<String> sensitiveReasons = new ArrayList<>();
        public List<String> blockers = new ArrayList<>();
        public List<String> reviewReasons = new ArrayList<>();
    }

    public static class MobileShippingDecisionForwarderAllocationView {
        public String forwarderCode;
        public String forwarderName;
        public Integer quantity;
        public BigDecimal quantityShare;
        public Integer pskuCount;
    }
}
