package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.List;

public class Ali1688CollectionView {

    public String id;
    public String taskId;
    public String sourceCollectionId;
    public String sourceCollectionNo;
    public String storeId;
    public String storeName;
    public String storeCode;
    public String sourcePlatform;
    public String sourceTitle;
    public String sourceTitleCn;
    public String sourceUrl;
    public String pageUrl;
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
    public String startedAt;
    public String finishedAt;
    public String message;
    public boolean canGenerateProcurementOrder;
    public List<SpecValue> sourceSpecs = new ArrayList<>();
    public List<Ali1688CandidatePreview> candidates = new ArrayList<>();

    public static class Ali1688CandidatePreview {
        public String id;
        public Integer rankNo;
        public Integer selectedRankNo;
        public String level;
        public String offerId;
        public String title;
        public String supplierName;
        public String candidateUrl;
        public String priceText;
        public String moqText;
        public String locationText;
        public String imageUrl;
        public List<String> imageUrls = new ArrayList<>();
        public Integer ruleScore;
        public Integer totalScore;
        public String scoreStatus;
        public ScoreBreakdown scoreBreakdown = new ScoreBreakdown();
        public String aiAssessmentStatus;
        public String procurementInquiryStatus;
        public List<SpecValue> matchedSpecs = new ArrayList<>();
        public List<String> reasons = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
    }

    public static class SpecValue {
        public String name;
        public String value;

        public SpecValue() {
        }

        public SpecValue(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    public static class ScoreBreakdown {
        public Integer matchScore;
        public Integer specScore;
        public Integer priceScore;
        public Integer moqScore;
        public Integer supplierScore;
        public Integer deliveryScore;
    }
}
