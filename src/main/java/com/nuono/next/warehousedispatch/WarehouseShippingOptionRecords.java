package com.nuono.next.warehousedispatch;

import java.math.BigDecimal;

public class WarehouseShippingOptionRecords extends WarehouseShippingBatchRecords {

    public static class ShippingSuggestionOptionRecord {
        public Long id;
        public Long batchId;
        public Long ownerUserId;
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
        public String targetForwarderCodesJson;
        public String targetForwarderNamesJson;
        public String routeCodesJson;
        public String evaluationStatus;
        public String blockedReasonsJson;
        public BigDecimal actualWeightKg;
        public BigDecimal volumeCbm;
        public BigDecimal chargeableWeightKg;
        public BigDecimal estimatedTotalAmount;
        public BigDecimal avgUnitAmount;
        public String currency;
        public String costSnapshotJson;
        public String summaryJson;
        public String createdAt;
        public String updatedAt;
    }

    public static class ShippingSuggestionLineRecord {
        public Long id;
        public Long optionId;
        public Long batchId;
        public Long ownerUserId;
        public Long productMasterId;
        public Long productVariantId;
        public String partnerSku;
        public String skuParent;
        public String titleCache;
        public String imageUrlCache;
        public String siteCode;
        public String actualTransportMode;
        public String fulfillmentType;
        public String sourcePartyName;
        public String specStatus;
        public String targetForwarderCode;
        public String targetForwarderName;
        public String routeCode;
        public String routeName;
        public BigDecimal actualWeightKg;
        public BigDecimal volumeCbm;
        public BigDecimal chargeableWeightKg;
        public BigDecimal estimatedAmount;
        public String currency;
        public Integer quantity;
        public Integer sourceCount;
        public String warningJson;
    }

    public static class ForwarderRouteQuoteRecord {
        public String routeCode;
        public String routeName;
        public String forwarderCode;
        public String forwarderName;
        public String transportMode;
        public String cargoCategoryCode;
        public String cargoCategoryName;
        public String currency;
        public BigDecimal minUnitPrice;
        public String billingUnit;
        public BigDecimal minBillableUnit;
        public String minBillableUnitType;
        public BigDecimal minCharge;
        public BigDecimal volumeDivisor;
    }

    public static class ShippingSuggestionLineSourceRecord {
        public Long id;
        public Long optionId;
        public Long lineId;
        public Long batchId;
        public Long batchSourceId;
        public Long fulfillmentBalanceId;
        public String plannedTransportMode;
        public Integer quantity;
    }
}
