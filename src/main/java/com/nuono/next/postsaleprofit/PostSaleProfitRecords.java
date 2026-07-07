package com.nuono.next.postsaleprofit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

final class PostSaleProfitSaleCandidate {
    private final String itemNr;
    private final String orderNr;
    private final String partnerSku;
    private final BigDecimal quantity;
    private final BigDecimal netProceedsLcy;
    private final BigDecimal referralFeeLcy;
    private final BigDecimal fulfillmentFeeLcy;
    private final BigDecimal otherFeeNetLcy;
    private final String currency;
    private final LocalDateTime orderTime;

    PostSaleProfitSaleCandidate(
            String itemNr,
            String orderNr,
            String partnerSku,
            BigDecimal quantity,
            BigDecimal netProceedsLcy,
            BigDecimal referralFeeLcy,
            BigDecimal fulfillmentFeeLcy,
            BigDecimal otherFeeNetLcy,
            String currency,
            LocalDateTime orderTime
    ) {
        this.itemNr = itemNr;
        this.orderNr = orderNr;
        this.partnerSku = partnerSku;
        this.quantity = quantity;
        this.netProceedsLcy = netProceedsLcy;
        this.referralFeeLcy = referralFeeLcy;
        this.fulfillmentFeeLcy = fulfillmentFeeLcy;
        this.otherFeeNetLcy = otherFeeNetLcy;
        this.currency = currency;
        this.orderTime = orderTime;
    }

    String itemNr() {
        return itemNr;
    }

    String orderNr() {
        return orderNr;
    }

    String partnerSku() {
        return partnerSku;
    }

    BigDecimal quantity() {
        return quantity;
    }

    BigDecimal netProceedsLcy() {
        return netProceedsLcy;
    }

    BigDecimal referralFeeLcy() {
        return referralFeeLcy;
    }

    BigDecimal fulfillmentFeeLcy() {
        return fulfillmentFeeLcy;
    }

    BigDecimal otherFeeNetLcy() {
        return otherFeeNetLcy;
    }

    String currency() {
        return currency;
    }

    LocalDateTime orderTime() {
        return orderTime;
    }
}

final class PostSaleProfitBatchCandidate {
    private final String sourceId;
    private final String partnerSku;
    private final String skuParent;
    private final String productTitle;
    private final String productImageUrl;
    private final LocalDateTime purchaseBatchTime;
    private final BigDecimal availableQuantity;
    private final BigDecimal purchaseUnitCostCny;
    private final BigDecimal headhaulUnitCostCny;
    private final boolean estimatedHeadhaul;
    private final BigDecimal fxRateToCny;
    private final String evidenceJson;
    private final boolean purchaseSourceReview;

    PostSaleProfitBatchCandidate(
            String sourceId,
            String partnerSku,
            LocalDateTime purchaseBatchTime,
            BigDecimal availableQuantity,
            BigDecimal purchaseUnitCostCny,
            BigDecimal headhaulUnitCostCny,
            BigDecimal fxRateToCny
    ) {
        this(sourceId, partnerSku, null, null, null, purchaseBatchTime, availableQuantity, purchaseUnitCostCny, headhaulUnitCostCny, false, fxRateToCny, null, false);
    }

    PostSaleProfitBatchCandidate(
            String sourceId,
            String partnerSku,
            LocalDateTime purchaseBatchTime,
            BigDecimal availableQuantity,
            BigDecimal purchaseUnitCostCny,
            BigDecimal headhaulUnitCostCny,
            boolean estimatedHeadhaul,
            BigDecimal fxRateToCny
    ) {
        this(sourceId, partnerSku, null, null, null, purchaseBatchTime, availableQuantity, purchaseUnitCostCny, headhaulUnitCostCny, estimatedHeadhaul, fxRateToCny, null, false);
    }

    PostSaleProfitBatchCandidate(
            String sourceId,
            String partnerSku,
            String skuParent,
            String productTitle,
            String productImageUrl,
            LocalDateTime purchaseBatchTime,
            BigDecimal availableQuantity,
            BigDecimal purchaseUnitCostCny,
            BigDecimal headhaulUnitCostCny,
            boolean estimatedHeadhaul,
            BigDecimal fxRateToCny
    ) {
        this(sourceId, partnerSku, skuParent, productTitle, productImageUrl, purchaseBatchTime, availableQuantity, purchaseUnitCostCny, headhaulUnitCostCny, estimatedHeadhaul, fxRateToCny, null, false);
    }

    PostSaleProfitBatchCandidate(
            String sourceId,
            String partnerSku,
            String skuParent,
            String productTitle,
            String productImageUrl,
            LocalDateTime purchaseBatchTime,
            BigDecimal availableQuantity,
            BigDecimal purchaseUnitCostCny,
            BigDecimal headhaulUnitCostCny,
            boolean estimatedHeadhaul,
            BigDecimal fxRateToCny,
            String evidenceJson,
            boolean purchaseSourceReview
    ) {
        this.sourceId = sourceId;
        this.partnerSku = partnerSku;
        this.skuParent = skuParent;
        this.productTitle = productTitle;
        this.productImageUrl = productImageUrl;
        this.purchaseBatchTime = purchaseBatchTime;
        this.availableQuantity = availableQuantity;
        this.purchaseUnitCostCny = purchaseUnitCostCny;
        this.headhaulUnitCostCny = headhaulUnitCostCny;
        this.estimatedHeadhaul = estimatedHeadhaul;
        this.fxRateToCny = fxRateToCny;
        this.evidenceJson = evidenceJson;
        this.purchaseSourceReview = purchaseSourceReview;
    }

    String sourceId() {
        return sourceId;
    }

    String partnerSku() {
        return partnerSku;
    }

    String skuParent() {
        return skuParent;
    }

    String productTitle() {
        return productTitle;
    }

    String productImageUrl() {
        return productImageUrl;
    }

    LocalDateTime purchaseBatchTime() {
        return purchaseBatchTime;
    }

    BigDecimal availableQuantity() {
        return availableQuantity;
    }

    BigDecimal purchaseUnitCostCny() {
        return purchaseUnitCostCny;
    }

    BigDecimal headhaulUnitCostCny() {
        return headhaulUnitCostCny;
    }

    boolean estimatedHeadhaul() {
        return estimatedHeadhaul;
    }

    BigDecimal fxRateToCny() {
        return fxRateToCny;
    }

    String evidenceJson() {
        return evidenceJson;
    }

    boolean purchaseSourceReview() {
        return purchaseSourceReview;
    }
}

final class PostSaleProfitLockedAttribution {
    private final String itemNr;
    private final String sourceId;
    private final BigDecimal quantity;
    private final String manualReason;

    PostSaleProfitLockedAttribution(String itemNr, String sourceId, BigDecimal quantity) {
        this(itemNr, sourceId, quantity, null);
    }

    PostSaleProfitLockedAttribution(String itemNr, String sourceId, BigDecimal quantity, String manualReason) {
        this.itemNr = itemNr;
        this.sourceId = sourceId;
        this.quantity = quantity;
        this.manualReason = manualReason;
    }

    String itemNr() {
        return itemNr;
    }

    String sourceId() {
        return sourceId;
    }

    BigDecimal quantity() {
        return quantity;
    }

    String manualReason() {
        return manualReason;
    }
}

final class PostSaleProfitOrderAttributionSlice {
    private final String itemNr;
    private final String orderNr;
    private final String sourceId;
    private final String partnerSku;
    private final BigDecimal quantity;
    private final BigDecimal netProceedsLcy;
    private final BigDecimal referralFeeLcy;
    private final BigDecimal fulfillmentFeeLcy;
    private final BigDecimal otherFeeNetLcy;
    private final String currency;
    private final LocalDateTime orderTime;
    private final PostSaleProfitAttributionMethod attributionMethod;
    private final String manualReason;

    PostSaleProfitOrderAttributionSlice(
            String itemNr,
            String orderNr,
            String sourceId,
            String partnerSku,
            BigDecimal quantity,
            BigDecimal netProceedsLcy,
            BigDecimal referralFeeLcy,
            BigDecimal fulfillmentFeeLcy,
            BigDecimal otherFeeNetLcy,
            String currency,
            LocalDateTime orderTime,
            PostSaleProfitAttributionMethod attributionMethod,
            String manualReason
    ) {
        this.itemNr = itemNr;
        this.orderNr = orderNr;
        this.sourceId = sourceId;
        this.partnerSku = partnerSku;
        this.quantity = quantity;
        this.netProceedsLcy = netProceedsLcy;
        this.referralFeeLcy = referralFeeLcy;
        this.fulfillmentFeeLcy = fulfillmentFeeLcy;
        this.otherFeeNetLcy = otherFeeNetLcy;
        this.currency = currency;
        this.orderTime = orderTime;
        this.attributionMethod = attributionMethod;
        this.manualReason = manualReason;
    }

    String itemNr() {
        return itemNr;
    }

    String orderNr() {
        return orderNr;
    }

    String sourceId() {
        return sourceId;
    }

    String partnerSku() {
        return partnerSku;
    }

    BigDecimal quantity() {
        return quantity;
    }

    BigDecimal netProceedsLcy() {
        return netProceedsLcy;
    }

    BigDecimal referralFeeLcy() {
        return referralFeeLcy;
    }

    BigDecimal fulfillmentFeeLcy() {
        return fulfillmentFeeLcy;
    }

    BigDecimal otherFeeNetLcy() {
        return otherFeeNetLcy;
    }

    String currency() {
        return currency;
    }

    LocalDateTime orderTime() {
        return orderTime;
    }

    PostSaleProfitAttributionMethod attributionMethod() {
        return attributionMethod;
    }

    String manualReason() {
        return manualReason;
    }
}

final class PostSaleProfitBatchResult {
    private final String sourceId;
    private final String partnerSku;
    private final String skuParent;
    private final String productTitle;
    private final String productImageUrl;
    private final LocalDateTime purchaseBatchTime;
    private final BigDecimal availableQuantity;
    private final BigDecimal soldQuantity;
    private final BigDecimal autoQuantity;
    private final BigDecimal lockedQuantity;
    private final BigDecimal purchaseUnitCostCny;
    private final BigDecimal purchaseCostCny;
    private final BigDecimal headhaulUnitCostCny;
    private final BigDecimal headhaulCostCny;
    private final BigDecimal netProceedsLcy;
    private final BigDecimal referralFeeLcy;
    private final BigDecimal fulfillmentFeeLcy;
    private final BigDecimal otherFeeNetLcy;
    private final String currency;
    private final BigDecimal fxRateToCny;
    private final BigDecimal profitCny;
    private final BigDecimal profitRate;
    private final List<PostSaleProfitQualityStatus> qualityStatuses;
    private final String evidenceJson;

    PostSaleProfitBatchResult(
            String sourceId,
            String partnerSku,
            String skuParent,
            String productTitle,
            String productImageUrl,
            LocalDateTime purchaseBatchTime,
            BigDecimal availableQuantity,
            BigDecimal soldQuantity,
            BigDecimal autoQuantity,
            BigDecimal lockedQuantity,
            BigDecimal purchaseUnitCostCny,
            BigDecimal purchaseCostCny,
            BigDecimal headhaulUnitCostCny,
            BigDecimal headhaulCostCny,
            BigDecimal netProceedsLcy,
            BigDecimal referralFeeLcy,
            BigDecimal fulfillmentFeeLcy,
            BigDecimal otherFeeNetLcy,
            String currency,
            BigDecimal fxRateToCny,
            BigDecimal profitCny,
            BigDecimal profitRate,
            List<PostSaleProfitQualityStatus> qualityStatuses,
            String evidenceJson
    ) {
        this.sourceId = sourceId;
        this.partnerSku = partnerSku;
        this.skuParent = skuParent;
        this.productTitle = productTitle;
        this.productImageUrl = productImageUrl;
        this.purchaseBatchTime = purchaseBatchTime;
        this.availableQuantity = availableQuantity;
        this.soldQuantity = soldQuantity;
        this.autoQuantity = autoQuantity;
        this.lockedQuantity = lockedQuantity;
        this.purchaseUnitCostCny = purchaseUnitCostCny;
        this.purchaseCostCny = purchaseCostCny;
        this.headhaulUnitCostCny = headhaulUnitCostCny;
        this.headhaulCostCny = headhaulCostCny;
        this.netProceedsLcy = netProceedsLcy;
        this.referralFeeLcy = referralFeeLcy;
        this.fulfillmentFeeLcy = fulfillmentFeeLcy;
        this.otherFeeNetLcy = otherFeeNetLcy;
        this.currency = currency;
        this.fxRateToCny = fxRateToCny;
        this.profitCny = profitCny;
        this.profitRate = profitRate;
        this.qualityStatuses = qualityStatuses;
        this.evidenceJson = evidenceJson;
    }

    String sourceId() {
        return sourceId;
    }

    String partnerSku() {
        return partnerSku;
    }

    String skuParent() {
        return skuParent;
    }

    String productTitle() {
        return productTitle;
    }

    String productImageUrl() {
        return productImageUrl;
    }

    LocalDateTime purchaseBatchTime() {
        return purchaseBatchTime;
    }

    BigDecimal availableQuantity() {
        return availableQuantity;
    }

    BigDecimal soldQuantity() {
        return soldQuantity;
    }

    BigDecimal autoQuantity() {
        return autoQuantity;
    }

    BigDecimal lockedQuantity() {
        return lockedQuantity;
    }

    BigDecimal purchaseUnitCostCny() {
        return purchaseUnitCostCny;
    }

    BigDecimal purchaseCostCny() {
        return purchaseCostCny;
    }

    BigDecimal headhaulUnitCostCny() {
        return headhaulUnitCostCny;
    }

    BigDecimal headhaulCostCny() {
        return headhaulCostCny;
    }

    BigDecimal netProceedsLcy() {
        return netProceedsLcy;
    }

    BigDecimal referralFeeLcy() {
        return referralFeeLcy;
    }

    BigDecimal fulfillmentFeeLcy() {
        return fulfillmentFeeLcy;
    }

    BigDecimal otherFeeNetLcy() {
        return otherFeeNetLcy;
    }

    String currency() {
        return currency;
    }

    BigDecimal fxRateToCny() {
        return fxRateToCny;
    }

    BigDecimal profitCny() {
        return profitCny;
    }

    BigDecimal profitRate() {
        return profitRate;
    }

    List<PostSaleProfitQualityStatus> qualityStatuses() {
        return qualityStatuses;
    }

    String evidenceJson() {
        return evidenceJson;
    }
}

final class PostSaleProfitAttributionResult {
    private final List<PostSaleProfitBatchResult> batchResults;
    private final List<PostSaleProfitOrderAttributionSlice> attributions;
    private final BigDecimal unassignedQuantity;

    PostSaleProfitAttributionResult(
            List<PostSaleProfitBatchResult> batchResults,
            List<PostSaleProfitOrderAttributionSlice> attributions,
            BigDecimal unassignedQuantity
    ) {
        this.batchResults = batchResults;
        this.attributions = attributions;
        this.unassignedQuantity = unassignedQuantity;
    }

    List<PostSaleProfitBatchResult> batchResults() {
        return batchResults;
    }

    List<PostSaleProfitOrderAttributionSlice> attributions() {
        return attributions;
    }

    BigDecimal unassignedQuantity() {
        return unassignedQuantity;
    }
}
