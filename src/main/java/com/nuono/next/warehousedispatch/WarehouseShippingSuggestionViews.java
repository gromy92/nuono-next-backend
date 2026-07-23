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

public class WarehouseShippingSuggestionViews extends WarehouseDispatchPlanViews {

    public static class ShippingSuggestionOptionView {
        public String id;
        public Long batchId;
        public String optionType;
        public String optionName;
        public String status;
        public Boolean selectedFlag;
        public Integer score;
        public Integer skuCount;
        public Integer totalQuantity;
        public Integer airQuantity;
        public Integer seaQuantity;
        public Integer specMissingCount;
        public Integer warningCount;
        public String forwarderPlanType;
        public Boolean autoRecommended;
        public List<String> targetForwarderCodes = new ArrayList<>();
        public List<String> targetForwarderNames = new ArrayList<>();
        public List<String> routeCodes = new ArrayList<>();
        public String evaluationStatus;
        public List<String> blockedReasons = new ArrayList<>();
        public BigDecimal actualWeightKg;
        public BigDecimal volumeCbm;
        public BigDecimal chargeableWeightKg;
        public BigDecimal estimatedTotalAmount;
        public BigDecimal avgUnitAmount;
        public String currency;
        public List<WarehouseDispatchViews.ShippingSuggestionLineView> lines = new ArrayList<>();

        public ShippingSuggestionOptionRecord toRecord() {
            ShippingSuggestionOptionRecord record = new ShippingSuggestionOptionRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.batchId = batchId;
            record.optionType = optionType;
            record.optionName = optionName;
            record.status = status;
            record.selectedFlag = selectedFlag;
            record.score = score;
            record.skuCount = skuCount;
            record.totalQuantity = totalQuantity;
            record.airQuantity = airQuantity;
            record.seaQuantity = seaQuantity;
            record.specMissingCount = specMissingCount;
            record.warningCount = warningCount;
            return record;
        }
    }

    public static class ShippingSuggestionLineView {
        public String id;
        public Long optionId;
        public Long batchId;
        public Long productVariantId;
        public String partnerSku;
        public String skuParent;
        public String productTitle;
        public String productImageUrl;
        public String siteCode;
        public String actualTransportMode;
        public String fulfillmentType;
        public String sourcePartyName;
        public String specStatus;
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
        public Integer quantity;
        public List<WarehouseDispatchViews.ShippingSuggestionLineSourceView> sources = new ArrayList<>();

        public ShippingSuggestionLineRecord toRecord() {
            ShippingSuggestionLineRecord record = new ShippingSuggestionLineRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.optionId = optionId;
            record.batchId = batchId;
            record.productVariantId = productVariantId;
            record.partnerSku = partnerSku;
            record.skuParent = skuParent;
            record.titleCache = productTitle;
            record.imageUrlCache = productImageUrl;
            record.siteCode = siteCode;
            record.actualTransportMode = actualTransportMode;
            record.fulfillmentType = fulfillmentType;
            record.sourcePartyName = sourcePartyName;
            record.specStatus = specStatus;
            record.quantity = quantity;
            record.sourceCount = sources == null ? 0 : sources.size();
            return record;
        }
    }

    public static class ShippingSuggestionLineSourceView {
        public String id;
        public Long optionId;
        public Long lineId;
        public Long batchId;
        public Long batchSourceId;
        public Long fulfillmentBalanceId;
        public String plannedTransportMode;
        public Integer quantity;

        public ShippingSuggestionLineSourceRecord toRecord() {
            ShippingSuggestionLineSourceRecord record = new ShippingSuggestionLineSourceRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.optionId = optionId;
            record.lineId = lineId;
            record.batchId = batchId;
            record.batchSourceId = batchSourceId;
            record.fulfillmentBalanceId = fulfillmentBalanceId;
            record.plannedTransportMode = plannedTransportMode;
            record.quantity = quantity;
            return record;
        }
    }
}
