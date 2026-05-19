package com.nuono.next.procurement;

import java.math.BigDecimal;

public class ProcurementRequirementConfirmationRecords {

    public static class OperatorContextRow {

        private Long userId;
        private String accountNo;
        private String realName;
        private String userRole;
        private Long roleId;
        private String roleName;
        private String roleCode;
        private Integer userLevel;
        private Integer roleLevel;
        private Integer status;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getAccountNo() {
            return accountNo;
        }

        public void setAccountNo(String accountNo) {
            this.accountNo = accountNo;
        }

        public String getRealName() {
            return realName;
        }

        public void setRealName(String realName) {
            this.realName = realName;
        }

        public String getUserRole() {
            return userRole;
        }

        public void setUserRole(String userRole) {
            this.userRole = userRole;
        }

        public Long getRoleId() {
            return roleId;
        }

        public void setRoleId(Long roleId) {
            this.roleId = roleId;
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public String getRoleCode() {
            return roleCode;
        }

        public void setRoleCode(String roleCode) {
            this.roleCode = roleCode;
        }

        public Integer getUserLevel() {
            return userLevel;
        }

        public void setUserLevel(Integer userLevel) {
            this.userLevel = userLevel;
        }

        public Integer getRoleLevel() {
            return roleLevel;
        }

        public void setRoleLevel(Integer roleLevel) {
            this.roleLevel = roleLevel;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }

    public static class DemandListRow {

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
        private Long matchTaskId;
        private String matchTaskStatus;
        private Integer matchTaskProgressPercent;
        private String matchTaskSearchMode;
        private Integer matchTaskSelectedImageCount;
        private String matchTaskSearchPath;
        private Integer matchTaskResultCount;
        private Integer matchTaskRecommendedCount;
        private String matchTaskMessage;
        private String matchTaskStartedAt;
        private String matchTaskFinishedAt;
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

        public Long getMatchTaskId() {
            return matchTaskId;
        }

        public void setMatchTaskId(Long matchTaskId) {
            this.matchTaskId = matchTaskId;
        }

        public String getMatchTaskStatus() {
            return matchTaskStatus;
        }

        public void setMatchTaskStatus(String matchTaskStatus) {
            this.matchTaskStatus = matchTaskStatus;
        }

        public Integer getMatchTaskProgressPercent() {
            return matchTaskProgressPercent;
        }

        public void setMatchTaskProgressPercent(Integer matchTaskProgressPercent) {
            this.matchTaskProgressPercent = matchTaskProgressPercent;
        }

        public String getMatchTaskSearchMode() {
            return matchTaskSearchMode;
        }

        public void setMatchTaskSearchMode(String matchTaskSearchMode) {
            this.matchTaskSearchMode = matchTaskSearchMode;
        }

        public Integer getMatchTaskSelectedImageCount() {
            return matchTaskSelectedImageCount;
        }

        public void setMatchTaskSelectedImageCount(Integer matchTaskSelectedImageCount) {
            this.matchTaskSelectedImageCount = matchTaskSelectedImageCount;
        }

        public String getMatchTaskSearchPath() {
            return matchTaskSearchPath;
        }

        public void setMatchTaskSearchPath(String matchTaskSearchPath) {
            this.matchTaskSearchPath = matchTaskSearchPath;
        }

        public Integer getMatchTaskResultCount() {
            return matchTaskResultCount;
        }

        public void setMatchTaskResultCount(Integer matchTaskResultCount) {
            this.matchTaskResultCount = matchTaskResultCount;
        }

        public Integer getMatchTaskRecommendedCount() {
            return matchTaskRecommendedCount;
        }

        public void setMatchTaskRecommendedCount(Integer matchTaskRecommendedCount) {
            this.matchTaskRecommendedCount = matchTaskRecommendedCount;
        }

        public String getMatchTaskMessage() {
            return matchTaskMessage;
        }

        public void setMatchTaskMessage(String matchTaskMessage) {
            this.matchTaskMessage = matchTaskMessage;
        }

        public String getMatchTaskStartedAt() {
            return matchTaskStartedAt;
        }

        public void setMatchTaskStartedAt(String matchTaskStartedAt) {
            this.matchTaskStartedAt = matchTaskStartedAt;
        }

        public String getMatchTaskFinishedAt() {
            return matchTaskFinishedAt;
        }

        public void setMatchTaskFinishedAt(String matchTaskFinishedAt) {
            this.matchTaskFinishedAt = matchTaskFinishedAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class DemandDetailRow extends DemandListRow {

        private Integer lineNo;
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
        private Long currentPoolId;
        private String createdAt;

        public Integer getLineNo() {
            return lineNo;
        }

        public void setLineNo(Integer lineNo) {
            this.lineNo = lineNo;
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

        public Long getCurrentPoolId() {
            return currentPoolId;
        }

        public void setCurrentPoolId(Long currentPoolId) {
            this.currentPoolId = currentPoolId;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static class CandidateRow {

        private Long candidateId;
        private Integer rankNo;
        private Integer totalScore;
        private Integer fitScore;
        private Integer specScore;
        private Integer priceScore;
        private Integer supplierScore;
        private Integer logisticsScore;
        private String candidateUrl;
        private String title;
        private String supplierName;
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
        private String mainImageUrl;
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

        public String getCandidateUrl() {
            return candidateUrl;
        }

        public void setCandidateUrl(String candidateUrl) {
            this.candidateUrl = candidateUrl;
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

        public String getMainImageUrl() {
            return mainImageUrl;
        }

        public void setMainImageUrl(String mainImageUrl) {
            this.mainImageUrl = mainImageUrl;
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

    public static class PoolRow {

        private Long poolId;
        private String poolNo;
        private String status;
        private Integer poolCount;
        private Integer maxPoolSize;
        private Integer candidateSourceLimit;
        private Long currentSnapshotId;
        private String autoCreatedAt;
        private String inquiryStartedAt;
        private String inquiryFinishedAt;
        private String finalConfirmedAt;
        private String summaryReadyAt;
        private String summaryText;
        private Long summaryInputSnapshotId;

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

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
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

        public Integer getCandidateSourceLimit() {
            return candidateSourceLimit;
        }

        public void setCandidateSourceLimit(Integer candidateSourceLimit) {
            this.candidateSourceLimit = candidateSourceLimit;
        }

        public Long getCurrentSnapshotId() {
            return currentSnapshotId;
        }

        public void setCurrentSnapshotId(Long currentSnapshotId) {
            this.currentSnapshotId = currentSnapshotId;
        }

        public String getAutoCreatedAt() {
            return autoCreatedAt;
        }

        public void setAutoCreatedAt(String autoCreatedAt) {
            this.autoCreatedAt = autoCreatedAt;
        }

        public String getInquiryStartedAt() {
            return inquiryStartedAt;
        }

        public void setInquiryStartedAt(String inquiryStartedAt) {
            this.inquiryStartedAt = inquiryStartedAt;
        }

        public String getInquiryFinishedAt() {
            return inquiryFinishedAt;
        }

        public void setInquiryFinishedAt(String inquiryFinishedAt) {
            this.inquiryFinishedAt = inquiryFinishedAt;
        }

        public String getFinalConfirmedAt() {
            return finalConfirmedAt;
        }

        public void setFinalConfirmedAt(String finalConfirmedAt) {
            this.finalConfirmedAt = finalConfirmedAt;
        }

        public String getSummaryReadyAt() {
            return summaryReadyAt;
        }

        public void setSummaryReadyAt(String summaryReadyAt) {
            this.summaryReadyAt = summaryReadyAt;
        }

        public String getSummaryText() {
            return summaryText;
        }

        public void setSummaryText(String summaryText) {
            this.summaryText = summaryText;
        }

        public Long getSummaryInputSnapshotId() {
            return summaryInputSnapshotId;
        }

        public void setSummaryInputSnapshotId(Long summaryInputSnapshotId) {
            this.summaryInputSnapshotId = summaryInputSnapshotId;
        }
    }

    public static class PoolLockRow {

        private Long poolId;
        private Long ownerUserId;
        private Long demandItemId;
        private String poolNo;
        private String status;
        private Integer poolCount;
        private Integer maxPoolSize;
        private Integer candidateSourceLimit;

        public Long getPoolId() {
            return poolId;
        }

        public void setPoolId(Long poolId) {
            this.poolId = poolId;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public Long getDemandItemId() {
            return demandItemId;
        }

        public void setDemandItemId(Long demandItemId) {
            this.demandItemId = demandItemId;
        }

        public String getPoolNo() {
            return poolNo;
        }

        public void setPoolNo(String poolNo) {
            this.poolNo = poolNo;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
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

        public Integer getCandidateSourceLimit() {
            return candidateSourceLimit;
        }

        public void setCandidateSourceLimit(Integer candidateSourceLimit) {
            this.candidateSourceLimit = candidateSourceLimit;
        }
    }

    public static class PoolItemLockRow {

        private Long poolItemId;
        private Long poolId;
        private Long ownerUserId;
        private Long demandItemId;
        private Long candidateId;
        private Integer sourceRankNo;
        private Integer poolRankNo;
        private String status;
        private String joinSource;
        private Long inquiryTaskId;

        public Long getPoolItemId() {
            return poolItemId;
        }

        public void setPoolItemId(Long poolItemId) {
            this.poolItemId = poolItemId;
        }

        public Long getPoolId() {
            return poolId;
        }

        public void setPoolId(Long poolId) {
            this.poolId = poolId;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public Long getDemandItemId() {
            return demandItemId;
        }

        public void setDemandItemId(Long demandItemId) {
            this.demandItemId = demandItemId;
        }

        public Long getCandidateId() {
            return candidateId;
        }

        public void setCandidateId(Long candidateId) {
            this.candidateId = candidateId;
        }

        public Integer getSourceRankNo() {
            return sourceRankNo;
        }

        public void setSourceRankNo(Integer sourceRankNo) {
            this.sourceRankNo = sourceRankNo;
        }

        public Integer getPoolRankNo() {
            return poolRankNo;
        }

        public void setPoolRankNo(Integer poolRankNo) {
            this.poolRankNo = poolRankNo;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getJoinSource() {
            return joinSource;
        }

        public void setJoinSource(String joinSource) {
            this.joinSource = joinSource;
        }

        public Long getInquiryTaskId() {
            return inquiryTaskId;
        }

        public void setInquiryTaskId(Long inquiryTaskId) {
            this.inquiryTaskId = inquiryTaskId;
        }
    }

    public static class PoolItemRow extends CandidateRow {

        private Long poolItemId;
        private Integer sourceRankNo;
        private Integer poolRankNo;
        private String status;
        private String joinSource;
        private Long inquiryTaskId;
        private String joinedAt;
        private String firstSentAt;
        private String noReplyDeadlineAt;
        private String lastFollowUpAt;
        private String lastReplyAt;
        private String closedAt;
        private String removedAt;
        private Long removedBy;
        private String removeReason;
        private String quotePriceText;
        private String quoteMoqText;
        private String quoteDeliveryText;
        private String replySummary;
        private String riskNote;
        private String inquiryTaskStatus;
        private String inquiryExecutionStage;
        private String plannedChannel;
        private String activeChannel;
        private String channelFallbackReason;
        private String externalInquiryId;
        private String externalInquiryUrl;
        private String externalResultStatus;
        private String replySource;
        private String replyParseStatus;
        private String replyParseError;

        public Long getPoolItemId() {
            return poolItemId;
        }

        public void setPoolItemId(Long poolItemId) {
            this.poolItemId = poolItemId;
        }

        public Integer getSourceRankNo() {
            return sourceRankNo;
        }

        public void setSourceRankNo(Integer sourceRankNo) {
            this.sourceRankNo = sourceRankNo;
        }

        public Integer getPoolRankNo() {
            return poolRankNo;
        }

        public void setPoolRankNo(Integer poolRankNo) {
            this.poolRankNo = poolRankNo;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getJoinSource() {
            return joinSource;
        }

        public void setJoinSource(String joinSource) {
            this.joinSource = joinSource;
        }

        public Long getInquiryTaskId() {
            return inquiryTaskId;
        }

        public void setInquiryTaskId(Long inquiryTaskId) {
            this.inquiryTaskId = inquiryTaskId;
        }

        public String getJoinedAt() {
            return joinedAt;
        }

        public void setJoinedAt(String joinedAt) {
            this.joinedAt = joinedAt;
        }

        public String getFirstSentAt() {
            return firstSentAt;
        }

        public void setFirstSentAt(String firstSentAt) {
            this.firstSentAt = firstSentAt;
        }

        public String getNoReplyDeadlineAt() {
            return noReplyDeadlineAt;
        }

        public void setNoReplyDeadlineAt(String noReplyDeadlineAt) {
            this.noReplyDeadlineAt = noReplyDeadlineAt;
        }

        public String getLastFollowUpAt() {
            return lastFollowUpAt;
        }

        public void setLastFollowUpAt(String lastFollowUpAt) {
            this.lastFollowUpAt = lastFollowUpAt;
        }

        public String getLastReplyAt() {
            return lastReplyAt;
        }

        public void setLastReplyAt(String lastReplyAt) {
            this.lastReplyAt = lastReplyAt;
        }

        public String getClosedAt() {
            return closedAt;
        }

        public void setClosedAt(String closedAt) {
            this.closedAt = closedAt;
        }

        public String getRemovedAt() {
            return removedAt;
        }

        public void setRemovedAt(String removedAt) {
            this.removedAt = removedAt;
        }

        public Long getRemovedBy() {
            return removedBy;
        }

        public void setRemovedBy(Long removedBy) {
            this.removedBy = removedBy;
        }

        public String getRemoveReason() {
            return removeReason;
        }

        public void setRemoveReason(String removeReason) {
            this.removeReason = removeReason;
        }

        public String getQuotePriceText() {
            return quotePriceText;
        }

        public void setQuotePriceText(String quotePriceText) {
            this.quotePriceText = quotePriceText;
        }

        public String getQuoteMoqText() {
            return quoteMoqText;
        }

        public void setQuoteMoqText(String quoteMoqText) {
            this.quoteMoqText = quoteMoqText;
        }

        public String getQuoteDeliveryText() {
            return quoteDeliveryText;
        }

        public void setQuoteDeliveryText(String quoteDeliveryText) {
            this.quoteDeliveryText = quoteDeliveryText;
        }

        public String getReplySummary() {
            return replySummary;
        }

        public void setReplySummary(String replySummary) {
            this.replySummary = replySummary;
        }

        public String getRiskNote() {
            return riskNote;
        }

        public void setRiskNote(String riskNote) {
            this.riskNote = riskNote;
        }

        public String getInquiryTaskStatus() {
            return inquiryTaskStatus;
        }

        public void setInquiryTaskStatus(String inquiryTaskStatus) {
            this.inquiryTaskStatus = inquiryTaskStatus;
        }

        public String getInquiryExecutionStage() {
            return inquiryExecutionStage;
        }

        public void setInquiryExecutionStage(String inquiryExecutionStage) {
            this.inquiryExecutionStage = inquiryExecutionStage;
        }

        public String getPlannedChannel() {
            return plannedChannel;
        }

        public void setPlannedChannel(String plannedChannel) {
            this.plannedChannel = plannedChannel;
        }

        public String getActiveChannel() {
            return activeChannel;
        }

        public void setActiveChannel(String activeChannel) {
            this.activeChannel = activeChannel;
        }

        public String getChannelFallbackReason() {
            return channelFallbackReason;
        }

        public void setChannelFallbackReason(String channelFallbackReason) {
            this.channelFallbackReason = channelFallbackReason;
        }

        public String getExternalInquiryId() {
            return externalInquiryId;
        }

        public void setExternalInquiryId(String externalInquiryId) {
            this.externalInquiryId = externalInquiryId;
        }

        public String getExternalInquiryUrl() {
            return externalInquiryUrl;
        }

        public void setExternalInquiryUrl(String externalInquiryUrl) {
            this.externalInquiryUrl = externalInquiryUrl;
        }

        public String getExternalResultStatus() {
            return externalResultStatus;
        }

        public void setExternalResultStatus(String externalResultStatus) {
            this.externalResultStatus = externalResultStatus;
        }

        public String getReplySource() {
            return replySource;
        }

        public void setReplySource(String replySource) {
            this.replySource = replySource;
        }

        public String getReplyParseStatus() {
            return replyParseStatus;
        }

        public void setReplyParseStatus(String replyParseStatus) {
            this.replyParseStatus = replyParseStatus;
        }

        public String getReplyParseError() {
            return replyParseError;
        }

        public void setReplyParseError(String replyParseError) {
            this.replyParseError = replyParseError;
        }
    }

    public static class FinalCandidateRow extends CandidateRow {

        private Long id;
        private Long poolItemId;
        private String finalPickType;
        private Long snapshotId;
        private String decisionNote;
        private Long confirmedBy;
        private String confirmedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getPoolItemId() {
            return poolItemId;
        }

        public void setPoolItemId(Long poolItemId) {
            this.poolItemId = poolItemId;
        }

        public String getFinalPickType() {
            return finalPickType;
        }

        public void setFinalPickType(String finalPickType) {
            this.finalPickType = finalPickType;
        }

        public Long getSnapshotId() {
            return snapshotId;
        }

        public void setSnapshotId(Long snapshotId) {
            this.snapshotId = snapshotId;
        }

        public String getDecisionNote() {
            return decisionNote;
        }

        public void setDecisionNote(String decisionNote) {
            this.decisionNote = decisionNote;
        }

        public Long getConfirmedBy() {
            return confirmedBy;
        }

        public void setConfirmedBy(Long confirmedBy) {
            this.confirmedBy = confirmedBy;
        }

        public String getConfirmedAt() {
            return confirmedAt;
        }

        public void setConfirmedAt(String confirmedAt) {
            this.confirmedAt = confirmedAt;
        }
    }

    public static class OperationLogRow {

        private Long id;
        private Long poolId;
        private Long poolItemId;
        private Long candidateId;
        private String offerId;
        private String operationType;
        private Long operatorUserId;
        private String operatorRole;
        private String beforeStatus;
        private String afterStatus;
        private Long snapshotId;
        private String operationReason;
        private String detailJson;
        private String createdAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getPoolId() {
            return poolId;
        }

        public void setPoolId(Long poolId) {
            this.poolId = poolId;
        }

        public Long getPoolItemId() {
            return poolItemId;
        }

        public void setPoolItemId(Long poolItemId) {
            this.poolItemId = poolItemId;
        }

        public Long getCandidateId() {
            return candidateId;
        }

        public void setCandidateId(Long candidateId) {
            this.candidateId = candidateId;
        }

        public String getOfferId() {
            return offerId;
        }

        public void setOfferId(String offerId) {
            this.offerId = offerId;
        }

        public String getOperationType() {
            return operationType;
        }

        public void setOperationType(String operationType) {
            this.operationType = operationType;
        }

        public Long getOperatorUserId() {
            return operatorUserId;
        }

        public void setOperatorUserId(Long operatorUserId) {
            this.operatorUserId = operatorUserId;
        }

        public String getOperatorRole() {
            return operatorRole;
        }

        public void setOperatorRole(String operatorRole) {
            this.operatorRole = operatorRole;
        }

        public String getBeforeStatus() {
            return beforeStatus;
        }

        public void setBeforeStatus(String beforeStatus) {
            this.beforeStatus = beforeStatus;
        }

        public String getAfterStatus() {
            return afterStatus;
        }

        public void setAfterStatus(String afterStatus) {
            this.afterStatus = afterStatus;
        }

        public Long getSnapshotId() {
            return snapshotId;
        }

        public void setSnapshotId(Long snapshotId) {
            this.snapshotId = snapshotId;
        }

        public String getOperationReason() {
            return operationReason;
        }

        public void setOperationReason(String operationReason) {
            this.operationReason = operationReason;
        }

        public String getDetailJson() {
            return detailJson;
        }

        public void setDetailJson(String detailJson) {
            this.detailJson = detailJson;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }
}
