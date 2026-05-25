package com.nuono.next.noonpull;

import java.time.Duration;
import java.time.LocalDate;

public class NoonPullSmokeEvidence {
    private String targetEnvironment;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private NoonPullDataDomain dataDomain;
    private String targetIdentity;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Integer rowOrItemCount;
    private Long taskId;
    private String sourceBatchId;
    private Duration elapsed;
    private LocalDate latestFactDate;
    private String qualityState;
    private String failureClassification;

    public static Builder builder() {
        return new Builder();
    }

    public String getTargetEnvironment() {
        return targetEnvironment;
    }

    public NoonPullDataDomain getDataDomain() {
        return dataDomain;
    }

    public String getQualityState() {
        return qualityState;
    }

    public String getFailureClassification() {
        return failureClassification;
    }

    public boolean isUsableEvidence() {
        return targetEnvironment != null
                && dataDomain != null
                && taskId != null
                && rowOrItemCount != null
                && ((sourceBatchId != null && qualityState != null) || failureClassification != null);
    }

    public static class Builder {
        private final NoonPullSmokeEvidence evidence = new NoonPullSmokeEvidence();

        public Builder targetEnvironment(String targetEnvironment) {
            evidence.targetEnvironment = targetEnvironment;
            return this;
        }

        public Builder ownerUserId(Long ownerUserId) {
            evidence.ownerUserId = ownerUserId;
            return this;
        }

        public Builder storeCode(String storeCode) {
            evidence.storeCode = storeCode;
            return this;
        }

        public Builder siteCode(String siteCode) {
            evidence.siteCode = siteCode;
            return this;
        }

        public Builder dataDomain(NoonPullDataDomain dataDomain) {
            evidence.dataDomain = dataDomain;
            return this;
        }

        public Builder targetIdentity(String targetIdentity) {
            evidence.targetIdentity = targetIdentity;
            return this;
        }

        public Builder dateFrom(LocalDate dateFrom) {
            evidence.dateFrom = dateFrom;
            return this;
        }

        public Builder dateTo(LocalDate dateTo) {
            evidence.dateTo = dateTo;
            return this;
        }

        public Builder rowOrItemCount(Integer rowOrItemCount) {
            evidence.rowOrItemCount = rowOrItemCount;
            return this;
        }

        public Builder taskId(Long taskId) {
            evidence.taskId = taskId;
            return this;
        }

        public Builder sourceBatchId(String sourceBatchId) {
            evidence.sourceBatchId = sourceBatchId;
            return this;
        }

        public Builder elapsed(Duration elapsed) {
            evidence.elapsed = elapsed;
            return this;
        }

        public Builder latestFactDate(LocalDate latestFactDate) {
            evidence.latestFactDate = latestFactDate;
            return this;
        }

        public Builder qualityState(String qualityState) {
            evidence.qualityState = qualityState;
            return this;
        }

        public Builder failureClassification(String failureClassification) {
            evidence.failureClassification = failureClassification;
            return this;
        }

        public NoonPullSmokeEvidence build() {
            return evidence;
        }
    }
}
