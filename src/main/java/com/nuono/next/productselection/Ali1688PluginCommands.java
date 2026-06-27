package com.nuono.next.productselection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Ali1688PluginCommands {

    private Ali1688PluginCommands() {
    }

    public static class CreateAssignmentCommand {
        public String assignmentType;
        public String taskId;
        public String candidateId;
    }

    public static class FailureCommand {
        public String failureCode;
        public String failureMessage;
    }

    public static class CandidateSubmissionCommand {
        public String idempotencyKey;
        public String sourcePageUrl;
        public String assignmentType;
        public String candidateId;
        public String resultStatus;
        public Map<String, Object> resultSnapshot;
        public RawSnapshot rawSnapshot;
        public List<CandidatePayload> candidates = new ArrayList<>();
    }

    public static class RawSnapshot {
        public String assignmentId;
        public Integer candidateCount;
        public String capturedAt;
        public String extractionStatus;
        public String pageUrl;
        public Integer scannedCount;
        public String source;
        public String taskId;
    }

    public static class CandidatePayload {
        public String offerId;
        public String candidateUrl;
        public String title;
        public String supplierName;
        public String priceText;
        public BigDecimal priceMin;
        public BigDecimal priceMax;
        public String moqText;
        public Integer moqValue;
        public String locationText;
        public String mainImageUrl;
        public List<String> imageUrls = new ArrayList<>();
        public Map<String, Object> badges;
        public Map<String, Object> skuSnapshot;
        public Map<String, Object> supplierSnapshot;
        public Map<String, Object> logisticsSnapshot;
    }

    public static class AssignmentResultCommand {
        public String assignmentType;
        public String candidateId;
        public String idempotencyKey;
        public Map<String, Object> resultSnapshot;
        public String resultStatus;
        public String sourcePageUrl;
    }
}
