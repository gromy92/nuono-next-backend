package com.nuono.next.postsaleprofit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class PostSaleProfitPersistenceRecords {

    private PostSaleProfitPersistenceRecords() {
    }

    public static class RecalculationRunRow {
        public Long id;
        public Long ownerUserId;
        public String storeCode;
        public String siteCode;
        public LocalDate dateFrom;
        public LocalDate dateTo;
        public String status;
        public String scopeHash;
        public Integer orderLineCount;
        public BigDecimal attributedQuantity;
        public BigDecimal lockedQuantity;
        public BigDecimal unassignedQuantity;
        public Integer missingIssueCount;
        public String diagnosticJson;
        public LocalDateTime startedAt;
        public LocalDateTime finishedAt;
        public Long createdBy;
    }

    public static class BatchWriteRow {
        public Long id;
        public Long runId;
        public Long ownerUserId;
        public String storeCode;
        public String siteCode;
        public String sourceId;
        public String skuParent;
        public String partnerSku;
        public String productTitle;
        public String productImageUrl;
        public LocalDateTime purchaseBatchTime;
        public BigDecimal purchaseQuantity;
        public BigDecimal purchaseUnitCostCny;
        public BigDecimal purchaseCostCny;
        public String shippingSourceType;
        public String shippingSourceId;
        public String shippingBatchNo;
        public String inTransitBatchId;
        public String inTransitReferenceNo;
        public LocalDateTime availableAt;
        public String availableAtSource;
        public String headhaulCostSourceType;
        public BigDecimal headhaulUnitCostCny;
        public BigDecimal headhaulCostCny;
        public BigDecimal soldQuantity;
        public BigDecimal autoQuantity;
        public BigDecimal lockedQuantity;
        public BigDecimal netProceedsLcy;
        public BigDecimal referralFeeLcy;
        public BigDecimal fulfillmentFeeLcy;
        public BigDecimal otherFeeNetLcy;
        public String currency;
        public BigDecimal fxRateToCny;
        public BigDecimal profitCny;
        public BigDecimal profitRate;
        public String qualityStatusJson;
        public String evidenceJson;
    }

    public static class BatchReadRow {
        public Long id;
        public String sourceId;
        public String skuParent;
        public String partnerSku;
        public String productTitle;
        public String productImageUrl;
        public LocalDateTime purchaseBatchTime;
        public BigDecimal purchaseQuantity;
        public BigDecimal purchaseUnitCostCny;
        public BigDecimal purchaseCostCny;
        public String shippingSourceType;
        public String shippingSourceId;
        public String shippingBatchNo;
        public String inTransitBatchId;
        public String inTransitReferenceNo;
        public LocalDateTime availableAt;
        public String availableAtSource;
        public String headhaulCostSourceType;
        public BigDecimal headhaulUnitCostCny;
        public BigDecimal headhaulCostCny;
        public BigDecimal soldQuantity;
        public BigDecimal autoQuantity;
        public BigDecimal lockedQuantity;
        public BigDecimal netProceedsLcy;
        public BigDecimal referralFeeLcy;
        public BigDecimal fulfillmentFeeLcy;
        public BigDecimal otherFeeNetLcy;
        public BigDecimal averageSalePriceLcy;
        public BigDecimal gmvLcy;
        public Integer salePriceFactCount;
        public String currency;
        public BigDecimal fxRateToCny;
        public BigDecimal profitCny;
        public BigDecimal profitRate;
        public String qualityStatusJson;
        public String evidenceJson;
    }

    public static class AttributionWriteRow {
        public Long id;
        public Long runId;
        public Long batchId;
        public Long ownerUserId;
        public String storeCode;
        public String siteCode;
        public String financeItemNr;
        public String orderNr;
        public String itemNr;
        public LocalDateTime orderTime;
        public String partnerSku;
        public String sku;
        public BigDecimal attributedQuantity;
        public String attributionMethod;
        public boolean locked;
        public String manualReason;
        public BigDecimal netProceedsLcy;
        public BigDecimal referralFeeLcy;
        public BigDecimal fulfillmentFeeLcy;
        public BigDecimal otherFeeNetLcy;
        public String currency;
        public String qualityStatusJson;
    }

    public static class AttributionReadRow {
        public Long id;
        public String orderNr;
        public String itemNr;
        public LocalDateTime orderTime;
        public String partnerSku;
        public String sku;
        public BigDecimal attributedQuantity;
        public String attributionMethod;
        public boolean locked;
        public String manualReason;
        public BigDecimal netProceedsLcy;
        public BigDecimal referralFeeLcy;
        public BigDecimal fulfillmentFeeLcy;
        public BigDecimal otherFeeNetLcy;
        public String currency;
    }

    public static class LockedAttributionRow {
        public String itemNr;
        public String sourceId;
        public BigDecimal quantity;
        public String manualReason;
    }

    public static class BatchStatusRow {
        public Long id;
        public BigDecimal soldQuantity;
        public BigDecimal lockedQuantity;
        public String qualityStatusJson;
    }

    public static class BatchMoveRow {
        public Long id;
        public Long runId;
        public String partnerSku;
        public BigDecimal purchaseUnitCostCny;
        public BigDecimal headhaulUnitCostCny;
        public BigDecimal fxRateToCny;
        public String qualityStatusJson;
    }

    public static class TransferAttributionRow {
        public Long id;
        public Long runId;
        public String financeItemNr;
        public String orderNr;
        public String itemNr;
        public LocalDateTime orderTime;
        public String partnerSku;
        public String sku;
        public BigDecimal attributedQuantity;
        public BigDecimal netProceedsLcy;
        public BigDecimal referralFeeLcy;
        public BigDecimal fulfillmentFeeLcy;
        public BigDecimal otherFeeNetLcy;
        public String currency;
    }

    public static class AttributionTotalsRow {
        public BigDecimal soldQuantity;
        public BigDecimal autoQuantity;
        public BigDecimal lockedQuantity;
        public BigDecimal netProceedsLcy;
        public BigDecimal referralFeeLcy;
        public BigDecimal fulfillmentFeeLcy;
        public BigDecimal otherFeeNetLcy;
        public String currency;
    }

    public static class FxRateRow {
        public Long id;
        public Long ownerUserId;
        public String siteCode;
        public String currency;
        public BigDecimal rateToCny;
        public LocalDate effectiveFrom;
        public LocalDate effectiveTo;
        public String sourceLabel;
    }
}
