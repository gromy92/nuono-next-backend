package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementRequirementConfirmationListView.CandidateSummaryView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProcurementRequirementConfirmationDetailView {

    private String mode;

    private boolean ready;

    private String message;

    private List<String> missingFeatureTables = new ArrayList<>();

    private DemandDetailView demand;

    private PoolView pool;

    private List<CandidateSummaryView> backupCandidates = new ArrayList<>();

    private List<FinalCandidateView> finalCandidates = new ArrayList<>();

    private SummaryView summary = new SummaryView();

    private List<OperationLogView> operationLogs = new ArrayList<>();

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

    public List<String> getMissingFeatureTables() {
        return missingFeatureTables;
    }

    public void setMissingFeatureTables(List<String> missingFeatureTables) {
        this.missingFeatureTables = missingFeatureTables;
    }

    public DemandDetailView getDemand() {
        return demand;
    }

    public void setDemand(DemandDetailView demand) {
        this.demand = demand;
    }

    public PoolView getPool() {
        return pool;
    }

    public void setPool(PoolView pool) {
        this.pool = pool;
    }

    public List<CandidateSummaryView> getBackupCandidates() {
        return backupCandidates;
    }

    public void setBackupCandidates(List<CandidateSummaryView> backupCandidates) {
        this.backupCandidates = backupCandidates;
    }

    public List<FinalCandidateView> getFinalCandidates() {
        return finalCandidates;
    }

    public void setFinalCandidates(List<FinalCandidateView> finalCandidates) {
        this.finalCandidates = finalCandidates;
    }

    public SummaryView getSummary() {
        return summary;
    }

    public void setSummary(SummaryView summary) {
        this.summary = summary;
    }

    public List<OperationLogView> getOperationLogs() {
        return operationLogs;
    }

    public void setOperationLogs(List<OperationLogView> operationLogs) {
        this.operationLogs = operationLogs;
    }

    public static class DemandDetailView {

        private Long demandItemId;

        private Long orderId;

        private Long ownerUserId;

        private String orderNo;

        private String orderTitle;

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

        private String status;

        private Long assignedBuyerId;

        private String assignedBuyerName;

        private Long currentPoolId;

        private String createdAt;

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

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
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

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class PoolView {

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

        private List<PoolItemView> items = new ArrayList<>();

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

        public List<PoolItemView> getItems() {
            return items;
        }

        public void setItems(List<PoolItemView> items) {
            this.items = items;
        }
    }

    public static class PoolItemView {

        private Long poolItemId;

        private Long candidateId;

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

        private CandidateSummaryView candidate;

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

        public CandidateSummaryView getCandidate() {
            return candidate;
        }

        public void setCandidate(CandidateSummaryView candidate) {
            this.candidate = candidate;
        }
    }

    public static class FinalCandidateView {

        private Long id;

        private Long poolItemId;

        private Long candidateId;

        private String finalPickType;

        private Long snapshotId;

        private String decisionNote;

        private Long confirmedBy;

        private String confirmedAt;

        private CandidateSummaryView candidate;

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

        public Long getCandidateId() {
            return candidateId;
        }

        public void setCandidateId(Long candidateId) {
            this.candidateId = candidateId;
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

        public CandidateSummaryView getCandidate() {
            return candidate;
        }

        public void setCandidate(CandidateSummaryView candidate) {
            this.candidate = candidate;
        }
    }

    public static class SummaryView {

        private String summaryText;

        private Long snapshotId;

        public String getSummaryText() {
            return summaryText;
        }

        public void setSummaryText(String summaryText) {
            this.summaryText = summaryText;
        }

        public Long getSnapshotId() {
            return snapshotId;
        }

        public void setSnapshotId(Long snapshotId) {
            this.snapshotId = snapshotId;
        }
    }

    public static class OperationLogView {

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
