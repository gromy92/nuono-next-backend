package com.nuono.next.postsaleprofit.logisticsclosure;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class PostSaleProfitLogisticsClosureRecords {

    private PostSaleProfitLogisticsClosureRecords() {
    }

    public static class LogisticsClosureSummaryView {
        private int purchaseBatchCount;
        private int confirmedBatchCount;
        private int partialBatchCount;
        private int missingHeadhaulBatchCount;
        private int estimatedHeadhaulBatchCount;
        private int missingInTransitCandidateBatchCount;
        private int missingHeadhaulBillBatchCount;
        private int headhaulAvailableUnconfirmedBatchCount;

        public int getPurchaseBatchCount() { return purchaseBatchCount; }
        public void setPurchaseBatchCount(int purchaseBatchCount) { this.purchaseBatchCount = purchaseBatchCount; }
        public int getConfirmedBatchCount() { return confirmedBatchCount; }
        public void setConfirmedBatchCount(int confirmedBatchCount) { this.confirmedBatchCount = confirmedBatchCount; }
        public int getPartialBatchCount() { return partialBatchCount; }
        public void setPartialBatchCount(int partialBatchCount) { this.partialBatchCount = partialBatchCount; }
        public int getMissingHeadhaulBatchCount() { return missingHeadhaulBatchCount; }
        public void setMissingHeadhaulBatchCount(int missingHeadhaulBatchCount) { this.missingHeadhaulBatchCount = missingHeadhaulBatchCount; }
        public int getEstimatedHeadhaulBatchCount() { return estimatedHeadhaulBatchCount; }
        public void setEstimatedHeadhaulBatchCount(int estimatedHeadhaulBatchCount) { this.estimatedHeadhaulBatchCount = estimatedHeadhaulBatchCount; }
        public int getMissingInTransitCandidateBatchCount() { return missingInTransitCandidateBatchCount; }
        public void setMissingInTransitCandidateBatchCount(int missingInTransitCandidateBatchCount) { this.missingInTransitCandidateBatchCount = missingInTransitCandidateBatchCount; }
        public int getMissingHeadhaulBillBatchCount() { return missingHeadhaulBillBatchCount; }
        public void setMissingHeadhaulBillBatchCount(int missingHeadhaulBillBatchCount) { this.missingHeadhaulBillBatchCount = missingHeadhaulBillBatchCount; }
        public int getHeadhaulAvailableUnconfirmedBatchCount() { return headhaulAvailableUnconfirmedBatchCount; }
        public void setHeadhaulAvailableUnconfirmedBatchCount(int headhaulAvailableUnconfirmedBatchCount) { this.headhaulAvailableUnconfirmedBatchCount = headhaulAvailableUnconfirmedBatchCount; }
    }

    public static class LogisticsClosurePurchaseBatchRow {
        public String sourceType;
        public String sourceId;
        public Long purchaseBatchId;
        public Long sourceLineId;
        public String targetStoreCode;
        public String targetSiteCode;
        public String partnerSku;
        public String skuParent;
        public Long productVariantId;
        public String productTitle;
        public String productImageUrl;
        public LocalDateTime purchaseBatchTime;
        public BigDecimal purchaseQuantity;
        public BigDecimal confirmedQuantity;
        public BigDecimal remainingQuantity;
        public BigDecimal purchaseCostCny;
        public String closureStatus;
        public Integer candidateLineCount;
        public BigDecimal candidateShippedQuantity;
        public Integer headhaulCandidateLineCount;
        public BigDecimal headhaulCandidateQuantity;
        public String headhaulGapType;
    }

    public static class LogisticsClosurePurchaseBatchView {
        private String sourceType;
        private String sourceId;
        private Long purchaseBatchId;
        private Long sourceLineId;
        private String targetStoreCode;
        private String targetSiteCode;
        private String partnerSku;
        private String skuParent;
        private Long productVariantId;
        private String productTitle;
        private String productImageUrl;
        private LocalDateTime purchaseBatchTime;
        private BigDecimal purchaseQuantity;
        private BigDecimal confirmedQuantity;
        private BigDecimal remainingQuantity;
        private BigDecimal purchaseCostCny;
        private String closureStatus;
        private Integer candidateLineCount;
        private BigDecimal candidateShippedQuantity;
        private Integer headhaulCandidateLineCount;
        private BigDecimal headhaulCandidateQuantity;
        private String headhaulGapType;

        public static LogisticsClosurePurchaseBatchView from(LogisticsClosurePurchaseBatchRow row) {
            LogisticsClosurePurchaseBatchView view = new LogisticsClosurePurchaseBatchView();
            if (row == null) {
                return view;
            }
            view.sourceType = row.sourceType;
            view.sourceId = row.sourceId;
            view.purchaseBatchId = row.purchaseBatchId;
            view.sourceLineId = row.sourceLineId;
            view.targetStoreCode = row.targetStoreCode;
            view.targetSiteCode = row.targetSiteCode;
            view.partnerSku = row.partnerSku;
            view.skuParent = row.skuParent;
            view.productVariantId = row.productVariantId;
            view.productTitle = row.productTitle;
            view.productImageUrl = row.productImageUrl;
            view.purchaseBatchTime = row.purchaseBatchTime;
            view.purchaseQuantity = row.purchaseQuantity;
            view.confirmedQuantity = row.confirmedQuantity;
            view.remainingQuantity = row.remainingQuantity;
            view.purchaseCostCny = row.purchaseCostCny;
            view.closureStatus = row.closureStatus;
            view.candidateLineCount = row.candidateLineCount;
            view.candidateShippedQuantity = row.candidateShippedQuantity;
            view.headhaulCandidateLineCount = row.headhaulCandidateLineCount;
            view.headhaulCandidateQuantity = row.headhaulCandidateQuantity;
            view.headhaulGapType = row.headhaulGapType;
            return view;
        }

        public String getSourceType() { return sourceType; }
        public String getSourceId() { return sourceId; }
        public Long getPurchaseBatchId() { return purchaseBatchId; }
        public Long getSourceLineId() { return sourceLineId; }
        public String getTargetStoreCode() { return targetStoreCode; }
        public String getTargetSiteCode() { return targetSiteCode; }
        public String getPartnerSku() { return partnerSku; }
        public String getSkuParent() { return skuParent; }
        public Long getProductVariantId() { return productVariantId; }
        public String getProductTitle() { return productTitle; }
        public String getProductImageUrl() { return productImageUrl; }
        public LocalDateTime getPurchaseBatchTime() { return purchaseBatchTime; }
        public BigDecimal getPurchaseQuantity() { return purchaseQuantity; }
        public BigDecimal getConfirmedQuantity() { return confirmedQuantity; }
        public BigDecimal getRemainingQuantity() { return remainingQuantity; }
        public BigDecimal getPurchaseCostCny() { return purchaseCostCny; }
        public String getClosureStatus() { return closureStatus; }
        public Integer getCandidateLineCount() { return candidateLineCount; }
        public BigDecimal getCandidateShippedQuantity() { return candidateShippedQuantity; }
        public Integer getHeadhaulCandidateLineCount() { return headhaulCandidateLineCount; }
        public BigDecimal getHeadhaulCandidateQuantity() { return headhaulCandidateQuantity; }
        public String getHeadhaulGapType() { return headhaulGapType; }
    }

    public static class LogisticsClosureCandidateRow {
        public Long inTransitBatchId;
        public Long inTransitGoodsLineId;
        public String batchReferenceNo;
        public String forwarderName;
        public String transportMode;
        public String nodeStatus;
        public LocalDateTime nodeHappenedAt;
        public String siteCode;
        public String partnerSku;
        public BigDecimal shippedQuantity;
        public BigDecimal confirmedQuantity;
        public BigDecimal remainingQuantity;
        public BigDecimal headhaulUnitCostCny;
        public BigDecimal headhaulCostCny;
        public String headhaulStatus;
        public String candidateStrength;
        public Integer confidenceScore;
        public String matchReasonsText;
    }

    public static class LogisticsClosureCandidateView {
        private Long inTransitBatchId;
        private Long inTransitGoodsLineId;
        private String batchReferenceNo;
        private String forwarderName;
        private String transportMode;
        private String nodeStatus;
        private LocalDateTime nodeHappenedAt;
        private String siteCode;
        private String partnerSku;
        private BigDecimal shippedQuantity;
        private BigDecimal confirmedQuantity;
        private BigDecimal remainingQuantity;
        private BigDecimal headhaulUnitCostCny;
        private BigDecimal headhaulCostCny;
        private String headhaulStatus;
        private String candidateStrength;
        private int confidenceScore;
        private List<String> matchReasons = List.of();

        public static LogisticsClosureCandidateView from(LogisticsClosureCandidateRow row) {
            LogisticsClosureCandidateView view = new LogisticsClosureCandidateView();
            if (row == null) {
                return view;
            }
            view.inTransitBatchId = row.inTransitBatchId;
            view.inTransitGoodsLineId = row.inTransitGoodsLineId;
            view.batchReferenceNo = row.batchReferenceNo;
            view.forwarderName = row.forwarderName;
            view.transportMode = row.transportMode;
            view.nodeStatus = row.nodeStatus;
            view.nodeHappenedAt = row.nodeHappenedAt;
            view.siteCode = row.siteCode;
            view.partnerSku = row.partnerSku;
            view.shippedQuantity = row.shippedQuantity;
            view.confirmedQuantity = row.confirmedQuantity;
            view.remainingQuantity = row.remainingQuantity;
            view.headhaulUnitCostCny = row.headhaulUnitCostCny;
            view.headhaulCostCny = row.headhaulCostCny;
            view.headhaulStatus = row.headhaulStatus;
            view.candidateStrength = row.candidateStrength;
            view.confidenceScore = row.confidenceScore == null ? 0 : row.confidenceScore;
            view.matchReasons = splitReasons(row.matchReasonsText);
            return view;
        }

        private static List<String> splitReasons(String text) {
            if (text == null || text.isBlank()) {
                return List.of();
            }
            List<String> reasons = new ArrayList<>();
            for (String token : text.split(",")) {
                if (token != null && !token.isBlank()) {
                    reasons.add(token.trim());
                }
            }
            return reasons;
        }

        public Long getInTransitBatchId() { return inTransitBatchId; }
        public Long getInTransitGoodsLineId() { return inTransitGoodsLineId; }
        public String getBatchReferenceNo() { return batchReferenceNo; }
        public String getForwarderName() { return forwarderName; }
        public String getTransportMode() { return transportMode; }
        public String getNodeStatus() { return nodeStatus; }
        public LocalDateTime getNodeHappenedAt() { return nodeHappenedAt; }
        public String getSiteCode() { return siteCode; }
        public String getPartnerSku() { return partnerSku; }
        public BigDecimal getShippedQuantity() { return shippedQuantity; }
        public BigDecimal getConfirmedQuantity() { return confirmedQuantity; }
        public BigDecimal getRemainingQuantity() { return remainingQuantity; }
        public BigDecimal getHeadhaulUnitCostCny() { return headhaulUnitCostCny; }
        public BigDecimal getHeadhaulCostCny() { return headhaulCostCny; }
        public String getHeadhaulStatus() { return headhaulStatus; }
        public String getCandidateStrength() { return candidateStrength; }
        public int getConfidenceScore() { return confidenceScore; }
        public List<String> getMatchReasons() { return matchReasons; }
    }

    public static class LogisticsClosurePurchaseBatchListView {
        private LogisticsClosureSummaryView summary;
        private List<LogisticsClosurePurchaseBatchView> rows = List.of();

        public LogisticsClosurePurchaseBatchListView() {
        }

        public LogisticsClosurePurchaseBatchListView(
                LogisticsClosureSummaryView summary,
                List<LogisticsClosurePurchaseBatchView> rows
        ) {
            this.summary = summary;
            this.rows = rows == null ? List.of() : rows;
        }

        public LogisticsClosureSummaryView getSummary() { return summary; }
        public void setSummary(LogisticsClosureSummaryView summary) { this.summary = summary; }
        public List<LogisticsClosurePurchaseBatchView> getRows() { return rows; }
        public void setRows(List<LogisticsClosurePurchaseBatchView> rows) { this.rows = rows == null ? List.of() : rows; }
    }

    public static class LogisticsClosureCandidateListView {
        private LogisticsClosurePurchaseBatchView purchaseBatch;
        private List<LogisticsClosureCandidateView> rows = List.of();

        public LogisticsClosureCandidateListView() {
        }

        public LogisticsClosureCandidateListView(
                LogisticsClosurePurchaseBatchView purchaseBatch,
                List<LogisticsClosureCandidateView> rows
        ) {
            this.purchaseBatch = purchaseBatch;
            this.rows = rows == null ? List.of() : rows;
        }

        public LogisticsClosurePurchaseBatchView getPurchaseBatch() { return purchaseBatch; }
        public void setPurchaseBatch(LogisticsClosurePurchaseBatchView purchaseBatch) { this.purchaseBatch = purchaseBatch; }
        public List<LogisticsClosureCandidateView> getRows() { return rows; }
        public void setRows(List<LogisticsClosureCandidateView> rows) { this.rows = rows == null ? List.of() : rows; }
    }

    public static class LogisticsClosureAllocationRequest {
        private String storeCode;
        private String siteCode;
        private String sourceType;
        private String sourceId;
        private Long inTransitBatchId;
        private Long inTransitGoodsLineId;
        private BigDecimal allocatedQuantity;
        private String matchMethod;
        private String reason;

        public String getStoreCode() { return storeCode; }
        public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
        public String getSiteCode() { return siteCode; }
        public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getSourceId() { return sourceId; }
        public void setSourceId(String sourceId) { this.sourceId = sourceId; }
        public Long getInTransitBatchId() { return inTransitBatchId; }
        public void setInTransitBatchId(Long inTransitBatchId) { this.inTransitBatchId = inTransitBatchId; }
        public Long getInTransitGoodsLineId() { return inTransitGoodsLineId; }
        public void setInTransitGoodsLineId(Long inTransitGoodsLineId) { this.inTransitGoodsLineId = inTransitGoodsLineId; }
        public BigDecimal getAllocatedQuantity() { return allocatedQuantity; }
        public void setAllocatedQuantity(BigDecimal allocatedQuantity) { this.allocatedQuantity = allocatedQuantity; }
        public String getMatchMethod() { return matchMethod; }
        public void setMatchMethod(String matchMethod) { this.matchMethod = matchMethod; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class LogisticsClosureConfirmCommand {
        private Long ownerUserId;
        private Long operatorUserId;
        private String storeCode;
        private String siteCode;
        private String sourceType;
        private String sourceId;
        private Long purchaseBatchId;
        private Long inTransitBatchId;
        private Long inTransitGoodsLineId;
        private BigDecimal allocatedQuantity;
        private String matchMethod;
        private String reason;

        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
        public String getStoreCode() { return storeCode; }
        public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
        public String getSiteCode() { return siteCode; }
        public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getSourceId() { return sourceId; }
        public void setSourceId(String sourceId) { this.sourceId = sourceId; }
        public Long getPurchaseBatchId() { return purchaseBatchId; }
        public void setPurchaseBatchId(Long purchaseBatchId) { this.purchaseBatchId = purchaseBatchId; }
        public Long getInTransitBatchId() { return inTransitBatchId; }
        public void setInTransitBatchId(Long inTransitBatchId) { this.inTransitBatchId = inTransitBatchId; }
        public Long getInTransitGoodsLineId() { return inTransitGoodsLineId; }
        public void setInTransitGoodsLineId(Long inTransitGoodsLineId) { this.inTransitGoodsLineId = inTransitGoodsLineId; }
        public BigDecimal getAllocatedQuantity() { return allocatedQuantity; }
        public void setAllocatedQuantity(BigDecimal allocatedQuantity) { this.allocatedQuantity = allocatedQuantity; }
        public String getMatchMethod() { return matchMethod; }
        public void setMatchMethod(String matchMethod) { this.matchMethod = matchMethod; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class LogisticsClosureRejectCommand extends LogisticsClosureConfirmCommand {
    }

    public static class LogisticsClosureAllocationResultView {
        private Long allocationId;
        private String confirmationStatus;

        public LogisticsClosureAllocationResultView() {
        }

        public LogisticsClosureAllocationResultView(Long allocationId, String confirmationStatus) {
            this.allocationId = allocationId;
            this.confirmationStatus = confirmationStatus;
        }

        public Long getAllocationId() { return allocationId; }
        public void setAllocationId(Long allocationId) { this.allocationId = allocationId; }
        public String getConfirmationStatus() { return confirmationStatus; }
        public void setConfirmationStatus(String confirmationStatus) { this.confirmationStatus = confirmationStatus; }
    }

    public static class LogisticsClosureAllocationRow {
        public Long id;
        public Long ownerUserId;
        public String sourceType;
        public String sourceId;
        public Long sourceLineId;
        public String targetStoreCode;
        public String targetSiteCode;
        public String partnerSku;
        public String skuParent;
        public Long productVariantId;
        public Long purchaseBatchId;
        public Long warehouseShippingBatchId;
        public Long warehouseShippingBatchSourceId;
        public Long inTransitBatchId;
        public Long inTransitGoodsLineId;
        public BigDecimal allocatedQuantity;
        public String allocationUnit;
        public String matchMethod;
        public String confirmationStatus;
        public Integer confidenceScore;
        public String evidenceJson;
        public String rejectReason;
        public Long confirmedBy;
        public LocalDateTime confirmedAt;
        public Long createdBy;
        public Long updatedBy;
    }

    public static class ConfirmedHeadhaulAllocationRow {
        public String sourceId;
        public Long purchaseBatchId;
        public String partnerSku;
        public String siteCode;
        public Long inTransitBatchId;
        public Long inTransitGoodsLineId;
        public String billNo;
        public String batchReferenceNo;
        public BigDecimal allocatedQuantity;
        public BigDecimal freightQuantity;
        public BigDecimal headhaulCostCny;
        public BigDecimal headhaulUnitCostCny;
        public LocalDateTime freightOccurredAt;
        public String allocationBasis;
        public String evidenceText;
        public Long confirmedBy;
        public LocalDateTime confirmedAt;
    }
}
