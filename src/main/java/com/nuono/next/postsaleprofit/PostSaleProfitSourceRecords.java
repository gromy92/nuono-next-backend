package com.nuono.next.postsaleprofit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PostSaleProfitSourceRecords {

    private PostSaleProfitSourceRecords() {
    }

    public static class FinanceSaleCandidateRow {
        public String itemNr;
        public String orderNr;
        public String partnerSku;
        public String sku;
        public BigDecimal soldQuantity;
        public BigDecimal netProceedsLcy;
        public BigDecimal referralFeeLcy;
        public BigDecimal fulfillmentFeeLcy;
        public BigDecimal otherFeeNetLcy;
        public String currency;
        public LocalDateTime orderTime;
    }

    public static class OrderSaleCandidateRow {
        public Long orderLineFactId;
        public String itemNr;
        public String orderNr;
        public String partnerSku;
        public String sku;
        public BigDecimal soldQuantity;
        public BigDecimal offerPriceLcy;
        public BigDecimal gmvLcy;
        public String currency;
        public LocalDateTime orderTime;
    }

    public static class PurchaseCostBatchRow {
        public String sourceId;
        public Long batchId;
        public String sourceType;
        public String storeCode;
        public String siteCode;
        public String skuParent;
        public String partnerSku;
        public String pskuCode;
        public String productTitle;
        public String productImageUrl;
        public String batchLabel;
        public LocalDateTime purchaseBatchTime;
        public BigDecimal purchaseQuantity;
        public BigDecimal purchaseCostCny;
        public BigDecimal purchaseUnitCostCny;
        public String providerOrderNo;
        public Long itemId;
        public Long assignmentId;
        public String sourceTitle;
        public String sourceSkuText;
        public String sourceModelText;
        public String sourceImageUrl;
        public BigDecimal sourceQuantity;
        public String sourceUnit;
        public BigDecimal sourceAmount;
        public BigDecimal paidAmount;
        public BigDecimal assignmentQuantity;
        public BigDecimal consumedSourceQuantity;
        public BigDecimal skuQuantity;
        public BigDecimal packSize;
        public String allocationBasis;
        public String evidenceText;
    }

    public static class HeadhaulCostBatchRow {
        public String sourceId;
        public Long inTransitBatchId;
        public String partnerSku;
        public String siteCode;
        public String billNo;
        public String batchReferenceNo;
        public String transportMode;
        public String destinationCode;
        public BigDecimal freightQuantity;
        public BigDecimal headhaulCostCny;
        public BigDecimal headhaulUnitCostCny;
        public LocalDateTime freightOccurredAt;
        public String allocationBasis;
        public String evidenceText;
    }
}
