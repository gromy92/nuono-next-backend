package com.nuono.next.procurement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProcurementRequirementConfirmationListView {

    private String mode;

    private boolean ready;

    private String message;

    private Integer page;

    private Integer pageSize;

    private Integer total;

    private List<String> missingFeatureTables = new ArrayList<>();

    private List<DemandListItemView> items = new ArrayList<>();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public List<String> getMissingFeatureTables() {
        return missingFeatureTables;
    }

    public void setMissingFeatureTables(List<String> missingFeatureTables) {
        this.missingFeatureTables = missingFeatureTables;
    }

    public List<DemandListItemView> getItems() {
        return items;
    }

    public void setItems(List<DemandListItemView> items) {
        this.items = items;
    }

    public static class DemandListItemView {

        private Long demandItemId;

        private Long orderId;

        private Long ownerUserId;

        private String orderNo;

        private String orderTitle;

        private String demandTitle;

        private String demandStatus;

        private String sourcePlatform;

        private String sourceUrl;

        private String sourceTitle;

        private String sourceImageUrl;

        private String sourceDetailImageUrl;

        private String sourcePackageImageUrl;

        private BigDecimal targetPriceMin;

        private BigDecimal targetPriceMax;

        private Integer targetQuantity;

        private String targetSite;

        private String specialRequirement;

        private String targetMaterial;

        private String targetPowerMode;

        private String targetSizeText;

        private String targetPackageType;

        private String deliveryExpectation;

        private Long assignedBuyerId;

        private String assignedBuyerName;

        private Long poolId;

        private String poolNo;

        private String poolStatus;

        private Integer poolCount;

        private Integer maxPoolSize;

        private Integer finalCandidateCount;

        private Integer candidateCount;

        private CandidateCollectionTaskView candidateCollectionTask;

        private CandidateSummaryView previewCandidate;

        private String updatedAt;

        public Long getDemandItemId() {
            return demandItemId;
        }

        public void setDemandItemId(Long demandItemId) {
            this.demandItemId = demandItemId;
        }

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getOrderTitle() {
            return orderTitle;
        }

        public void setOrderTitle(String orderTitle) {
            this.orderTitle = orderTitle;
        }

        public String getDemandTitle() {
            return demandTitle;
        }

        public void setDemandTitle(String demandTitle) {
            this.demandTitle = demandTitle;
        }

        public String getDemandStatus() {
            return demandStatus;
        }

        public void setDemandStatus(String demandStatus) {
            this.demandStatus = demandStatus;
        }

        public String getSourcePlatform() {
            return sourcePlatform;
        }

        public void setSourcePlatform(String sourcePlatform) {
            this.sourcePlatform = sourcePlatform;
        }

        public String getSourceUrl() {
            return sourceUrl;
        }

        public void setSourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
        }

        public String getSourceTitle() {
            return sourceTitle;
        }

        public void setSourceTitle(String sourceTitle) {
            this.sourceTitle = sourceTitle;
        }

        public String getSourceImageUrl() {
            return sourceImageUrl;
        }

        public void setSourceImageUrl(String sourceImageUrl) {
            this.sourceImageUrl = sourceImageUrl;
        }

        public String getSourceDetailImageUrl() {
            return sourceDetailImageUrl;
        }

        public void setSourceDetailImageUrl(String sourceDetailImageUrl) {
            this.sourceDetailImageUrl = sourceDetailImageUrl;
        }

        public String getSourcePackageImageUrl() {
            return sourcePackageImageUrl;
        }

        public void setSourcePackageImageUrl(String sourcePackageImageUrl) {
            this.sourcePackageImageUrl = sourcePackageImageUrl;
        }

        public BigDecimal getTargetPriceMin() {
            return targetPriceMin;
        }

        public void setTargetPriceMin(BigDecimal targetPriceMin) {
            this.targetPriceMin = targetPriceMin;
        }

        public BigDecimal getTargetPriceMax() {
            return targetPriceMax;
        }

        public void setTargetPriceMax(BigDecimal targetPriceMax) {
            this.targetPriceMax = targetPriceMax;
        }

        public Integer getTargetQuantity() {
            return targetQuantity;
        }

        public void setTargetQuantity(Integer targetQuantity) {
            this.targetQuantity = targetQuantity;
        }

        public String getTargetSite() {
            return targetSite;
        }

        public void setTargetSite(String targetSite) {
            this.targetSite = targetSite;
        }

        public String getSpecialRequirement() {
            return specialRequirement;
        }

        public void setSpecialRequirement(String specialRequirement) {
            this.specialRequirement = specialRequirement;
        }

        public String getTargetMaterial() {
            return targetMaterial;
        }

        public void setTargetMaterial(String targetMaterial) {
            this.targetMaterial = targetMaterial;
        }

        public String getTargetPowerMode() {
            return targetPowerMode;
        }

        public void setTargetPowerMode(String targetPowerMode) {
            this.targetPowerMode = targetPowerMode;
        }

        public String getTargetSizeText() {
            return targetSizeText;
        }

        public void setTargetSizeText(String targetSizeText) {
            this.targetSizeText = targetSizeText;
        }

        public String getTargetPackageType() {
            return targetPackageType;
        }

        public void setTargetPackageType(String targetPackageType) {
            this.targetPackageType = targetPackageType;
        }

        public String getDeliveryExpectation() {
            return deliveryExpectation;
        }

        public void setDeliveryExpectation(String deliveryExpectation) {
            this.deliveryExpectation = deliveryExpectation;
        }

        public Long getAssignedBuyerId() {
            return assignedBuyerId;
        }

        public void setAssignedBuyerId(Long assignedBuyerId) {
            this.assignedBuyerId = assignedBuyerId;
        }

        public String getAssignedBuyerName() {
            return assignedBuyerName;
        }

        public void setAssignedBuyerName(String assignedBuyerName) {
            this.assignedBuyerName = assignedBuyerName;
        }

        public Long getPoolId() {
            return poolId;
        }

        public void setPoolId(Long poolId) {
            this.poolId = poolId;
        }

        public String getPoolNo() {
            return poolNo;
        }

        public void setPoolNo(String poolNo) {
            this.poolNo = poolNo;
        }

        public String getPoolStatus() {
            return poolStatus;
        }

        public void setPoolStatus(String poolStatus) {
            this.poolStatus = poolStatus;
        }

        public Integer getPoolCount() {
            return poolCount;
        }

        public void setPoolCount(Integer poolCount) {
            this.poolCount = poolCount;
        }

        public Integer getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(Integer maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public Integer getFinalCandidateCount() {
            return finalCandidateCount;
        }

        public void setFinalCandidateCount(Integer finalCandidateCount) {
            this.finalCandidateCount = finalCandidateCount;
        }

        public Integer getCandidateCount() {
            return candidateCount;
        }

        public void setCandidateCount(Integer candidateCount) {
            this.candidateCount = candidateCount;
        }

        public CandidateCollectionTaskView getCandidateCollectionTask() {
            return candidateCollectionTask;
        }

        public void setCandidateCollectionTask(CandidateCollectionTaskView candidateCollectionTask) {
            this.candidateCollectionTask = candidateCollectionTask;
        }

        public CandidateSummaryView getPreviewCandidate() {
            return previewCandidate;
        }

        public void setPreviewCandidate(CandidateSummaryView previewCandidate) {
            this.previewCandidate = previewCandidate;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class CandidateCollectionTaskView {

        private Long id;

        private String status;

        private Integer progressPercent;

        private String searchMode;

        private Integer selectedImageCount;

        private String searchPath;

        private Integer resultCount;

        private Integer recommendedCount;

        private String message;

        private String startedAt;

        private String finishedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getProgressPercent() {
            return progressPercent;
        }

        public void setProgressPercent(Integer progressPercent) {
            this.progressPercent = progressPercent;
        }

        public String getSearchMode() {
            return searchMode;
        }

        public void setSearchMode(String searchMode) {
            this.searchMode = searchMode;
        }

        public Integer getSelectedImageCount() {
            return selectedImageCount;
        }

        public void setSelectedImageCount(Integer selectedImageCount) {
            this.selectedImageCount = selectedImageCount;
        }

        public String getSearchPath() {
            return searchPath;
        }

        public void setSearchPath(String searchPath) {
            this.searchPath = searchPath;
        }

        public Integer getResultCount() {
            return resultCount;
        }

        public void setResultCount(Integer resultCount) {
            this.resultCount = resultCount;
        }

        public Integer getRecommendedCount() {
            return recommendedCount;
        }

        public void setRecommendedCount(Integer recommendedCount) {
            this.recommendedCount = recommendedCount;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(String startedAt) {
            this.startedAt = startedAt;
        }

        public String getFinishedAt() {
            return finishedAt;
        }

        public void setFinishedAt(String finishedAt) {
            this.finishedAt = finishedAt;
        }
    }

    public static class CandidateSummaryView {

        private Long candidateId;

        private Integer rankNo;

        private Integer totalScore;
        private Integer fitScore;
        private Integer specScore;
        private Integer priceScore;
        private Integer supplierScore;
        private Integer logisticsScore;

        private String offerId;

        private String title;

        private String supplierName;

        private String candidateUrl;

        private String mainImageUrl;

        private String priceText;

        private String moqText;

        private String locationText;

        private String materialText;

        private String powerModeText;

        private String sizeText;

        private String packageText;

        private String deliveryTimelineText;

        private String resultCardText;

        private String detailHighlightText;

        private String attributeSnapshotText;

        private String shippingSnapshotText;

        private String packageSnapshotText;

        private String detailImageUrl;

        private String deliveryImageUrl;

        private String badgesText;

        private String reasonsText;

        private String warningsText;

        public Long getCandidateId() {
            return candidateId;
        }

        public void setCandidateId(Long candidateId) {
            this.candidateId = candidateId;
        }

        public Integer getRankNo() {
            return rankNo;
        }

        public void setRankNo(Integer rankNo) {
            this.rankNo = rankNo;
        }

        public Integer getTotalScore() {
            return totalScore;
        }

        public void setTotalScore(Integer totalScore) {
            this.totalScore = totalScore;
        }

        public Integer getFitScore() {
            return fitScore;
        }

        public void setFitScore(Integer fitScore) {
            this.fitScore = fitScore;
        }

        public Integer getSpecScore() {
            return specScore;
        }

        public void setSpecScore(Integer specScore) {
            this.specScore = specScore;
        }

        public Integer getPriceScore() {
            return priceScore;
        }

        public void setPriceScore(Integer priceScore) {
            this.priceScore = priceScore;
        }

        public Integer getSupplierScore() {
            return supplierScore;
        }

        public void setSupplierScore(Integer supplierScore) {
            this.supplierScore = supplierScore;
        }

        public Integer getLogisticsScore() {
            return logisticsScore;
        }

        public void setLogisticsScore(Integer logisticsScore) {
            this.logisticsScore = logisticsScore;
        }

        public String getOfferId() {
            return offerId;
        }

        public void setOfferId(String offerId) {
            this.offerId = offerId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        public String getCandidateUrl() {
            return candidateUrl;
        }

        public void setCandidateUrl(String candidateUrl) {
            this.candidateUrl = candidateUrl;
        }

        public String getMainImageUrl() {
            return mainImageUrl;
        }

        public void setMainImageUrl(String mainImageUrl) {
            this.mainImageUrl = mainImageUrl;
        }

        public String getPriceText() {
            return priceText;
        }

        public void setPriceText(String priceText) {
            this.priceText = priceText;
        }

        public String getMoqText() {
            return moqText;
        }

        public void setMoqText(String moqText) {
            this.moqText = moqText;
        }

        public String getLocationText() {
            return locationText;
        }

        public void setLocationText(String locationText) {
            this.locationText = locationText;
        }

        public String getMaterialText() {
            return materialText;
        }

        public void setMaterialText(String materialText) {
            this.materialText = materialText;
        }

        public String getPowerModeText() {
            return powerModeText;
        }

        public void setPowerModeText(String powerModeText) {
            this.powerModeText = powerModeText;
        }

        public String getSizeText() {
            return sizeText;
        }

        public void setSizeText(String sizeText) {
            this.sizeText = sizeText;
        }

        public String getPackageText() {
            return packageText;
        }

        public void setPackageText(String packageText) {
            this.packageText = packageText;
        }

        public String getDeliveryTimelineText() {
            return deliveryTimelineText;
        }

        public void setDeliveryTimelineText(String deliveryTimelineText) {
            this.deliveryTimelineText = deliveryTimelineText;
        }

        public String getResultCardText() {
            return resultCardText;
        }

        public void setResultCardText(String resultCardText) {
            this.resultCardText = resultCardText;
        }

        public String getDetailHighlightText() {
            return detailHighlightText;
        }

        public void setDetailHighlightText(String detailHighlightText) {
            this.detailHighlightText = detailHighlightText;
        }

        public String getAttributeSnapshotText() {
            return attributeSnapshotText;
        }

        public void setAttributeSnapshotText(String attributeSnapshotText) {
            this.attributeSnapshotText = attributeSnapshotText;
        }

        public String getShippingSnapshotText() {
            return shippingSnapshotText;
        }

        public void setShippingSnapshotText(String shippingSnapshotText) {
            this.shippingSnapshotText = shippingSnapshotText;
        }

        public String getPackageSnapshotText() {
            return packageSnapshotText;
        }

        public void setPackageSnapshotText(String packageSnapshotText) {
            this.packageSnapshotText = packageSnapshotText;
        }

        public String getDetailImageUrl() {
            return detailImageUrl;
        }

        public void setDetailImageUrl(String detailImageUrl) {
            this.detailImageUrl = detailImageUrl;
        }

        public String getDeliveryImageUrl() {
            return deliveryImageUrl;
        }

        public void setDeliveryImageUrl(String deliveryImageUrl) {
            this.deliveryImageUrl = deliveryImageUrl;
        }

        public String getBadgesText() {
            return badgesText;
        }

        public void setBadgesText(String badgesText) {
            this.badgesText = badgesText;
        }

        public String getReasonsText() {
            return reasonsText;
        }

        public void setReasonsText(String reasonsText) {
            this.reasonsText = reasonsText;
        }

        public String getWarningsText() {
            return warningsText;
        }

        public void setWarningsText(String warningsText) {
            this.warningsText = warningsText;
        }
    }
}
