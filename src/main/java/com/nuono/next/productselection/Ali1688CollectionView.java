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
    public String detailCompletionStatus;
    public String detailCompletionMessage;
    public FieldCompleteness fieldCompleteness = new FieldCompleteness();
    public GatewayStatus gatewayStatus = new GatewayStatus();
    public Boolean pluginAssistAvailable;
    public Ali1688PluginAssignmentView pluginAssignment;
    public boolean canGenerateProcurementOrder;
    public Integer inquiryEligibleCount;
    public Integer inquiryBlockedCount;
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
        public String listPriceHintText;
        public String priceState;
        public String confirmedPriceText;
        public Ali1688RealPriceSnapshot realPriceSnapshot;
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
        public Boolean autoInquiryEligible;
        public Ali1688InquiryEligibilityView inquiryEligibility;
        public Ali1688CandidateGateView gate;
        public List<String> reasons = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
    }

    public static class ScoreBreakdown {
        public Integer matchScore;
        public Integer specScore;
        public Integer priceScore;
        public Integer moqScore;
        public Integer supplierScore;
        public Integer deliveryScore;
    }

    public static class FieldCompleteness {
        public int candidateCount;
        public int nonFallbackTitleCount;
        public int supplierNameCount;
        public int priceTextCount;
        public int moqTextCount;
        public int locationTextCount;
        public int normalizedDetailUrlCount;
    }

    public static class GatewayStatus {
        public String gatewayServiceKind;
        public String sessionState;
        public Boolean runtimeReady;
        public Boolean captchaAutoSolveEnabled;
        public String userFacingStatus;
        public String userFacingMessage;
    }
}
