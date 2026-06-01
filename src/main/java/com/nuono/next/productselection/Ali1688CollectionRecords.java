package com.nuono.next.productselection;

import java.math.BigDecimal;

public final class Ali1688CollectionRecords {

    private Ali1688CollectionRecords() {
    }

    public static class TaskRecord {
        public Long id;
        public Long sourceCollectionId;
        public String currentTaskKey;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String taskNo;
        public String status;
        public Integer progressPercent;
        public String searchMode;
        public String sourceImageUrl;
        public Integer selectedImageCount;
        public Integer scannedCount;
        public Integer candidateCount;
        public Integer recommendedCount;
        public String failureCode;
        public String failureMessage;
        public String officialSearchUrl;
        public String searchImageId;
        public String searchImageIdListJson;
        public String rawSearchSnapshotJson;
        public String startedAt;
        public String finishedAt;
        public String lockedAt;
        public String lockedBy;
        public Integer attemptCount;
        public Long createdBy;
        public Long updatedBy;
        public String sourceCollectionNo;
        public String sourcePlatform;
        public String sourceTitle;
        public String sourceTitleCn;
        public String sourceUrl;
        public String pageUrl;
        public String storeName;
        public String storeCode;
    }

    public static class CandidateRecord {
        public Long id;
        public Long taskId;
        public Long sourceCollectionId;
        public Long ownerUserId;
        public Long logicalStoreId;
        public Integer rankNo;
        public Integer selectedRankNo;
        public String level;
        public String offerId;
        public String candidateUrl;
        public String candidateUrlHash;
        public String activeCandidateKey;
        public String title;
        public String supplierName;
        public String priceText;
        public BigDecimal priceMin;
        public BigDecimal priceMax;
        public String moqText;
        public Integer moqValue;
        public String locationText;
        public String mainImageUrl;
        public String imageUrlsJson;
        public String badgesJson;
        public String skuSnapshotJson;
        public String supplierSnapshotJson;
        public String logisticsSnapshotJson;
        public Integer ruleScore;
        public Integer totalScore;
        public Integer matchScore;
        public Integer specScore;
        public Integer priceScore;
        public Integer moqScore;
        public Integer supplierScore;
        public Integer deliveryScore;
        public String scoreStatus;
        public String scoreVersion;
        public String scoreDetailJson;
        public String aiAssessmentStatus;
        public Long createdBy;
        public Long updatedBy;
    }

    public static class AiAssessmentRecord {
        public Long id;
        public Long taskId;
        public Long candidateId;
        public String status;
        public String featureCode;
        public String operationCode;
        public String promptVersion;
        public String schemaVersion;
        public String modelName;
        public String inputHash;
        public String inputSnapshotJson;
        public String outputJson;
        public Integer matchScore;
        public Integer specScore;
        public String riskLevel;
        public String failureCode;
        public String failureMessage;
        public String startedAt;
        public String finishedAt;
        public String lockedAt;
        public String lockedBy;
        public Integer attemptCount;
        public String nextRunAt;
        public Long createdBy;
        public Long updatedBy;
    }

    public static class PluginAssignmentRecord {
        public Long id;
        public String assignmentCode;
        public String assignmentType;
        public Long taskId;
        public Long candidateId;
        public Long sourceCollectionId;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String currentAssignmentKey;
        public String status;
        public String idempotencyKey;
        public String resultStatus;
        public String resultSnapshotJson;
        public String failureCode;
        public String failureMessage;
        public Integer submittedCandidateCount;
        public Integer acceptedCandidateCount;
        public Integer rejectedCandidateCount;
        public String createdAt;
        public String expiresAt;
        public String startedAt;
        public String finishedAt;
        public Long createdBy;
        public Long updatedBy;
        public String taskNo;
        public String sourceImageUrl;
        public String sourceTitle;
        public String sourceTitleCn;
        public String sourceUrl;
        public String pageUrl;
        public String storeName;
        public String storeCode;
        public String candidateTitle;
        public String candidateUrl;
        public String offerId;
    }

    public static class DetailEnrichmentSnapshotRecord {
        public Long id;
        public Long assignmentId;
        public Long taskId;
        public Long candidateId;
        public Long sourceCollectionId;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String snapshotSource;
        public String collectedAt;
        public String pageUrl;
        public String detailTitle;
        public String mainImageUrlsJson;
        public String detailImageUrlsJson;
        public String imageUrlsJson;
        public String skuOptionsJson;
        public String moqText;
        public String supplierName;
        public String locationText;
        public String listPriceText;
        public String serviceLabelsJson;
        public String salesLabelsJson;
        public String rawEvidenceSnippetsJson;
        public String rawSnapshotJson;
        public Long createdBy;
        public Long updatedBy;
    }

    public static class PricePreviewSnapshotRecord {
        public Long id;
        public Long assignmentId;
        public Long taskId;
        public Long candidateId;
        public Long sourceCollectionId;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String snapshotSource;
        public String resultStatus;
        public String failureCode;
        public String failureMessage;
        public String collectedAt;
        public String skuOptionsJson;
        public Integer quantity;
        public String unitPriceText;
        public String shippingText;
        public String discountText;
        public String totalPriceText;
        public String currency;
        public String regionText;
        public String safetyMode;
        public String sideEffectPolicy;
        public String rawSnapshotJson;
        public Long createdBy;
        public Long updatedBy;
    }
}
