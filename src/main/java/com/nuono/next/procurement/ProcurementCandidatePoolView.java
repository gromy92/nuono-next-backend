package com.nuono.next.procurement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProcurementCandidatePoolView {

    private String mode;

    private boolean ready;

    private String message;

    private List<String> missingCoreTables = new ArrayList<>();

    private OrderView order;

    private SummaryView summary;

    private Long selectedDemandItemId;

    private List<DemandItemView> demandItems = new ArrayList<>();

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

    public List<String> getMissingCoreTables() {
        return missingCoreTables;
    }

    public void setMissingCoreTables(List<String> missingCoreTables) {
        this.missingCoreTables = missingCoreTables;
    }

    public OrderView getOrder() {
        return order;
    }

    public void setOrder(OrderView order) {
        this.order = order;
    }

    public SummaryView getSummary() {
        return summary;
    }

    public void setSummary(SummaryView summary) {
        this.summary = summary;
    }

    public Long getSelectedDemandItemId() {
        return selectedDemandItemId;
    }

    public void setSelectedDemandItemId(Long selectedDemandItemId) {
        this.selectedDemandItemId = selectedDemandItemId;
    }

    public List<DemandItemView> getDemandItems() {
        return demandItems;
    }

    public void setDemandItems(List<DemandItemView> demandItems) {
        this.demandItems = demandItems;
    }

    public static class OrderView {

        private Long id;

        private Long ownerUserId;

        private String orderNo;

        private String title;

        private String status;

        private String targetMarket;

        private String priority;

        private String sourceType;

        private Integer itemCount;

        private Integer selectedCandidateCount;

        private String createdAt;

        private String updatedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
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

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getTargetMarket() {
            return targetMarket;
        }

        public void setTargetMarket(String targetMarket) {
            this.targetMarket = targetMarket;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public Integer getItemCount() {
            return itemCount;
        }

        public void setItemCount(Integer itemCount) {
            this.itemCount = itemCount;
        }

        public Integer getSelectedCandidateCount() {
            return selectedCandidateCount;
        }

        public void setSelectedCandidateCount(Integer selectedCandidateCount) {
            this.selectedCandidateCount = selectedCandidateCount;
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

    public static class SummaryView {

        private Integer totalItems;

        private Integer runningTasks;

        private Integer successTasks;

        private Integer failedTasks;

        private Integer recommendedCandidates;

        private Integer reviewCandidates;

        private Integer selectedCandidates;

        public Integer getTotalItems() {
            return totalItems;
        }

        public void setTotalItems(Integer totalItems) {
            this.totalItems = totalItems;
        }

        public Integer getRunningTasks() {
            return runningTasks;
        }

        public void setRunningTasks(Integer runningTasks) {
            this.runningTasks = runningTasks;
        }

        public Integer getSuccessTasks() {
            return successTasks;
        }

        public void setSuccessTasks(Integer successTasks) {
            this.successTasks = successTasks;
        }

        public Integer getFailedTasks() {
            return failedTasks;
        }

        public void setFailedTasks(Integer failedTasks) {
            this.failedTasks = failedTasks;
        }

        public Integer getRecommendedCandidates() {
            return recommendedCandidates;
        }

        public void setRecommendedCandidates(Integer recommendedCandidates) {
            this.recommendedCandidates = recommendedCandidates;
        }

        public Integer getReviewCandidates() {
            return reviewCandidates;
        }

        public void setReviewCandidates(Integer reviewCandidates) {
            this.reviewCandidates = reviewCandidates;
        }

        public Integer getSelectedCandidates() {
            return selectedCandidates;
        }

        public void setSelectedCandidates(Integer selectedCandidates) {
            this.selectedCandidates = selectedCandidates;
        }
    }

    public static class DemandItemView {

        private Long id;

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

        private String structuredFieldSource;

        private String status;

        private Long selectedCandidateId;

        private String createdAt;

        private String updatedAt;

        private TaskView task;

        private List<CandidateView> candidates = new ArrayList<>();

        private List<CandidateGroupView> candidateGroups = new ArrayList<>();

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
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

        public String getStructuredFieldSource() {
            return structuredFieldSource;
        }

        public void setStructuredFieldSource(String structuredFieldSource) {
            this.structuredFieldSource = structuredFieldSource;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Long getSelectedCandidateId() {
            return selectedCandidateId;
        }

        public void setSelectedCandidateId(Long selectedCandidateId) {
            this.selectedCandidateId = selectedCandidateId;
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

        public TaskView getTask() {
            return task;
        }

        public void setTask(TaskView task) {
            this.task = task;
        }

        public List<CandidateView> getCandidates() {
            return candidates;
        }

        public void setCandidates(List<CandidateView> candidates) {
            this.candidates = candidates;
        }

        public List<CandidateGroupView> getCandidateGroups() {
            return candidateGroups;
        }

        public void setCandidateGroups(List<CandidateGroupView> candidateGroups) {
            this.candidateGroups = candidateGroups;
        }
    }

    public static class TaskView {

        private Long id;

        private Long demandItemId;

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

        public Long getDemandItemId() {
            return demandItemId;
        }

        public void setDemandItemId(Long demandItemId) {
            this.demandItemId = demandItemId;
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

    public static class CandidateView {

        private Long id;

        private Long demandItemId;

        private Long taskId;

        private Integer rankNo;

        private String level;

        private Integer totalScore;

        private Integer fitScore;

        private Integer specScore;

        private Integer priceScore;

        private Integer supplierScore;

        private Integer logisticsScore;

        private String candidatePlatform;

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

        private String structuredFieldSource;

        private String mainImageUrl;

        private String detailImageUrl;

        private String deliveryImageUrl;

        private String manualReviewNote;

        private String inquirySummary;

        private String nextAction;

        private List<String> badges = new ArrayList<>();

        private List<String> reasons = new ArrayList<>();

        private List<String> warnings = new ArrayList<>();

        private List<FieldEvidenceView> extractionEvidences = new ArrayList<>();

        private Boolean selected;

        private String decisionStatus;

        private String createdAt;

        private String updatedAt;

        private String badgesText;

        private String reasonsText;

        private String warningsText;

        private String standardizedPriceText;

        private String standardizedMoqText;

        private String standardizedMaterialText;

        private String standardizedPowerModeText;

        private String standardizedSizeText;

        private String standardizedPackageText;

        private String standardizedDeliveryText;

        private List<String> pendingQuestions = new ArrayList<>();

        private String groupKey;

        private String groupLabel;

        private String groupType;

        private Integer groupRank;

        private String inquiryOpeningLine;

        private String inquirySummaryLine;

        private List<String> inquiryQuestions = new ArrayList<>();

        private List<String> quoteChecklist = new ArrayList<>();

        private List<String> sampleChecklist = new ArrayList<>();

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getDemandItemId() {
            return demandItemId;
        }

        public void setDemandItemId(Long demandItemId) {
            this.demandItemId = demandItemId;
        }

        public Long getTaskId() {
            return taskId;
        }

        public void setTaskId(Long taskId) {
            this.taskId = taskId;
        }

        public Integer getRankNo() {
            return rankNo;
        }

        public void setRankNo(Integer rankNo) {
            this.rankNo = rankNo;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
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

        public String getCandidatePlatform() {
            return candidatePlatform;
        }

        public void setCandidatePlatform(String candidatePlatform) {
            this.candidatePlatform = candidatePlatform;
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

        public String getStructuredFieldSource() {
            return structuredFieldSource;
        }

        public void setStructuredFieldSource(String structuredFieldSource) {
            this.structuredFieldSource = structuredFieldSource;
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

        public String getManualReviewNote() {
            return manualReviewNote;
        }

        public void setManualReviewNote(String manualReviewNote) {
            this.manualReviewNote = manualReviewNote;
        }

        public String getInquirySummary() {
            return inquirySummary;
        }

        public void setInquirySummary(String inquirySummary) {
            this.inquirySummary = inquirySummary;
        }

        public String getNextAction() {
            return nextAction;
        }

        public void setNextAction(String nextAction) {
            this.nextAction = nextAction;
        }

        public List<String> getBadges() {
            return badges;
        }

        public void setBadges(List<String> badges) {
            this.badges = badges;
        }

        public List<String> getReasons() {
            return reasons;
        }

        public void setReasons(List<String> reasons) {
            this.reasons = reasons;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public void setWarnings(List<String> warnings) {
            this.warnings = warnings;
        }

        public List<FieldEvidenceView> getExtractionEvidences() {
            return extractionEvidences;
        }

        public void setExtractionEvidences(List<FieldEvidenceView> extractionEvidences) {
            this.extractionEvidences = extractionEvidences;
        }

        public Boolean getSelected() {
            return selected;
        }

        public void setSelected(Boolean selected) {
            this.selected = selected;
        }

        public String getDecisionStatus() {
            return decisionStatus;
        }

        public void setDecisionStatus(String decisionStatus) {
            this.decisionStatus = decisionStatus;
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

        public String getStandardizedPriceText() {
            return standardizedPriceText;
        }

        public void setStandardizedPriceText(String standardizedPriceText) {
            this.standardizedPriceText = standardizedPriceText;
        }

        public String getStandardizedMoqText() {
            return standardizedMoqText;
        }

        public void setStandardizedMoqText(String standardizedMoqText) {
            this.standardizedMoqText = standardizedMoqText;
        }

        public String getStandardizedMaterialText() {
            return standardizedMaterialText;
        }

        public void setStandardizedMaterialText(String standardizedMaterialText) {
            this.standardizedMaterialText = standardizedMaterialText;
        }

        public String getStandardizedPowerModeText() {
            return standardizedPowerModeText;
        }

        public void setStandardizedPowerModeText(String standardizedPowerModeText) {
            this.standardizedPowerModeText = standardizedPowerModeText;
        }

        public String getStandardizedSizeText() {
            return standardizedSizeText;
        }

        public void setStandardizedSizeText(String standardizedSizeText) {
            this.standardizedSizeText = standardizedSizeText;
        }

        public String getStandardizedPackageText() {
            return standardizedPackageText;
        }

        public void setStandardizedPackageText(String standardizedPackageText) {
            this.standardizedPackageText = standardizedPackageText;
        }

        public String getStandardizedDeliveryText() {
            return standardizedDeliveryText;
        }

        public void setStandardizedDeliveryText(String standardizedDeliveryText) {
            this.standardizedDeliveryText = standardizedDeliveryText;
        }

        public List<String> getPendingQuestions() {
            return pendingQuestions;
        }

        public void setPendingQuestions(List<String> pendingQuestions) {
            this.pendingQuestions = pendingQuestions;
        }

        public String getGroupKey() {
            return groupKey;
        }

        public void setGroupKey(String groupKey) {
            this.groupKey = groupKey;
        }

        public String getGroupLabel() {
            return groupLabel;
        }

        public void setGroupLabel(String groupLabel) {
            this.groupLabel = groupLabel;
        }

        public String getGroupType() {
            return groupType;
        }

        public void setGroupType(String groupType) {
            this.groupType = groupType;
        }

        public Integer getGroupRank() {
            return groupRank;
        }

        public void setGroupRank(Integer groupRank) {
            this.groupRank = groupRank;
        }

        public String getInquiryOpeningLine() {
            return inquiryOpeningLine;
        }

        public void setInquiryOpeningLine(String inquiryOpeningLine) {
            this.inquiryOpeningLine = inquiryOpeningLine;
        }

        public String getInquirySummaryLine() {
            return inquirySummaryLine;
        }

        public void setInquirySummaryLine(String inquirySummaryLine) {
            this.inquirySummaryLine = inquirySummaryLine;
        }

        public List<String> getInquiryQuestions() {
            return inquiryQuestions;
        }

        public void setInquiryQuestions(List<String> inquiryQuestions) {
            this.inquiryQuestions = inquiryQuestions;
        }

        public List<String> getQuoteChecklist() {
            return quoteChecklist;
        }

        public void setQuoteChecklist(List<String> quoteChecklist) {
            this.quoteChecklist = quoteChecklist;
        }

        public List<String> getSampleChecklist() {
            return sampleChecklist;
        }

        public void setSampleChecklist(List<String> sampleChecklist) {
            this.sampleChecklist = sampleChecklist;
        }
    }

    public static class CandidateGroupView {

        private String groupKey;

        private String groupLabel;

        private String groupType;

        private String summary;

        private String representativeTitle;

        private String representativeSupplierName;

        private String mainImageUrl;

        private Integer candidateCount;

        private Integer supplierCount;

        private Integer bestScore;

        private Long bestCandidateId;

        private List<Long> candidateIds = new ArrayList<>();

        private List<String> tags = new ArrayList<>();

        public String getGroupKey() {
            return groupKey;
        }

        public void setGroupKey(String groupKey) {
            this.groupKey = groupKey;
        }

        public String getGroupLabel() {
            return groupLabel;
        }

        public void setGroupLabel(String groupLabel) {
            this.groupLabel = groupLabel;
        }

        public String getGroupType() {
            return groupType;
        }

        public void setGroupType(String groupType) {
            this.groupType = groupType;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getRepresentativeTitle() {
            return representativeTitle;
        }

        public void setRepresentativeTitle(String representativeTitle) {
            this.representativeTitle = representativeTitle;
        }

        public String getRepresentativeSupplierName() {
            return representativeSupplierName;
        }

        public void setRepresentativeSupplierName(String representativeSupplierName) {
            this.representativeSupplierName = representativeSupplierName;
        }

        public String getMainImageUrl() {
            return mainImageUrl;
        }

        public void setMainImageUrl(String mainImageUrl) {
            this.mainImageUrl = mainImageUrl;
        }

        public Integer getCandidateCount() {
            return candidateCount;
        }

        public void setCandidateCount(Integer candidateCount) {
            this.candidateCount = candidateCount;
        }

        public Integer getSupplierCount() {
            return supplierCount;
        }

        public void setSupplierCount(Integer supplierCount) {
            this.supplierCount = supplierCount;
        }

        public Integer getBestScore() {
            return bestScore;
        }

        public void setBestScore(Integer bestScore) {
            this.bestScore = bestScore;
        }

        public Long getBestCandidateId() {
            return bestCandidateId;
        }

        public void setBestCandidateId(Long bestCandidateId) {
            this.bestCandidateId = bestCandidateId;
        }

        public List<Long> getCandidateIds() {
            return candidateIds;
        }

        public void setCandidateIds(List<Long> candidateIds) {
            this.candidateIds = candidateIds;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    public static class FieldEvidenceView {

        private String fieldKey;

        private String fieldLabel;

        private String fieldValue;

        private String sourceType;

        private String sourceLabel;

        private String evidenceText;

        public String getFieldKey() {
            return fieldKey;
        }

        public void setFieldKey(String fieldKey) {
            this.fieldKey = fieldKey;
        }

        public String getFieldLabel() {
            return fieldLabel;
        }

        public void setFieldLabel(String fieldLabel) {
            this.fieldLabel = fieldLabel;
        }

        public String getFieldValue() {
            return fieldValue;
        }

        public void setFieldValue(String fieldValue) {
            this.fieldValue = fieldValue;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public String getSourceLabel() {
            return sourceLabel;
        }

        public void setSourceLabel(String sourceLabel) {
            this.sourceLabel = sourceLabel;
        }

        public String getEvidenceText() {
            return evidenceText;
        }

        public void setEvidenceText(String evidenceText) {
            this.evidenceText = evidenceText;
        }
    }
}
