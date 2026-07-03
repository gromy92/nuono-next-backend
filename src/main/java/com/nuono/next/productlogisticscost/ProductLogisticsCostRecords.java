package com.nuono.next.productlogisticscost;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ProductLogisticsCostRecords {

    private ProductLogisticsCostRecords() {
    }

    public static class CostHistoryRow {
        public Long id;
        public Long ownerUserId;
        public Long logicalStoreId;
        public Long productMasterId;
        public Long productVariantId;
        public String partnerSku;
        public String barcode;
        public String siteCode;
        public String forwarderCode;
        public String forwarderName;
        public String transportMode;
        public String routeCode;
        public String routeName;
        public String serviceCode;
        public String serviceName;
        public Long inTransitBatchId;
        public String batchReferenceNo;
        public String sourceType;
        public String costType;
        public Long sourceActualBillId;
        public Long sourceActualComponentId;
        public Long sourceShippingOrderId;
        public Long sourceQuoteLineId;
        public String feeType;
        public String rawFeeName;
        public String cargoCategoryCode;
        public String cargoCategoryName;
        public BigDecimal quantity;
        public BigDecimal chargeQuantity;
        public String chargeUnit;
        public BigDecimal unitCost;
        public BigDecimal totalCost;
        public String currencyCode;
        public BigDecimal exchangeRateToCny;
        public BigDecimal unitCostCny;
        public BigDecimal totalCostCny;
        public String allocationBasis;
        public String confidenceLevel;
        public LocalDateTime costOccurredAt;
        public String idempotencyKey;
        public String evidenceJson;
        public String rawSnapshotJson;
        public String reviewStatus;
    }

    public static class CurrentCostRow {
        public Long id;
        public Long ownerUserId;
        public Long logicalStoreId;
        public Long productMasterId;
        public Long productVariantId;
        public String partnerSku;
        public String barcode;
        public String siteCode;
        public String forwarderCode;
        public String forwarderName;
        public String transportMode;
        public String routeCode;
        public String routeName;
        public String serviceCode;
        public String serviceName;
        public Long currentHistoryId;
        public String sourceType;
        public String costType;
        public String feeType;
        public String cargoCategoryCode;
        public String cargoCategoryName;
        public String chargeUnit;
        public BigDecimal unitCostCny;
        public BigDecimal totalCostCny;
        public String currencyCode;
        public String confidenceLevel;
        public LocalDateTime costOccurredAt;
        public LocalDateTime refreshedAt;
        public String evidenceJson;
    }

    public static class CostExceptionRow {
        public Long id;
        public Long ownerUserId;
        public Long inTransitBatchId;
        public String batchReferenceNo;
        public String sourceType;
        public Long sourceActualBillId;
        public Long sourceActualComponentId;
        public String storeCode;
        public String partnerSku;
        public String siteCode;
        public String forwarderCode;
        public String transportMode;
        public String exceptionType;
        public String exceptionMessage;
        public String resolutionStatus;
        public String evidenceJson;
    }

    public static class RateCardRow {
        public Long id;
        public Long ownerUserId;
        public String siteCode;
        public String forwarderCode;
        public String forwarderName;
        public String transportMode;
        public String feeType;
        public String cargoCategoryCode;
        public String cargoCategoryName;
        public String chargeUnit;
        public BigDecimal unitCostCny;
        public String currencyCode;
        public String sourceType;
        public String sourceReference;
        public LocalDateTime effectiveAt;
        public String remark;
        public String evidenceJson;
    }

    public static class BatchCategoryAssignmentItemResult {
        public String partnerSku;
        public String resolvedPartnerSku;
        public String status;
        public String message;
    }

    public static class CurrentCostView {
        public List<CurrentCostRow> items = new ArrayList<>();
    }

    public static class CostHistoryView {
        public List<CostHistoryRow> items = new ArrayList<>();
    }

    public static class CostExceptionView {
        public List<CostExceptionRow> items = new ArrayList<>();
    }

    public static class RateCardView {
        public List<RateCardRow> items = new ArrayList<>();
    }

    public static class BatchCategoryAssignmentResult {
        public int requestedCount;
        public int updatedCount;
        public int skippedCount;
        public List<BatchCategoryAssignmentItemResult> items = new ArrayList<>();
    }
}
