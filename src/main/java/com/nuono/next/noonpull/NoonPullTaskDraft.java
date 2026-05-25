package com.nuono.next.noonpull;

import java.time.LocalDate;

public class NoonPullTaskDraft {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private NoonPullType pullType;
    private NoonPullDataDomain dataDomain;
    private NoonPullTriggerMode triggerMode;
    private String targetIdentity;
    private LocalDate targetDateFrom;
    private LocalDate targetDateTo;

    public static Builder builder() {
        return new Builder();
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public NoonPullType getPullType() {
        return pullType;
    }

    public NoonPullDataDomain getDataDomain() {
        return dataDomain;
    }

    public NoonPullTriggerMode getTriggerMode() {
        return triggerMode;
    }

    public String getTargetIdentity() {
        return targetIdentity;
    }

    public LocalDate getTargetDateFrom() {
        return targetDateFrom;
    }

    public LocalDate getTargetDateTo() {
        return targetDateTo;
    }

    public static final class Builder {
        private final NoonPullTaskDraft draft = new NoonPullTaskDraft();

        public Builder ownerUserId(Long ownerUserId) {
            draft.ownerUserId = ownerUserId;
            return this;
        }

        public Builder storeCode(String storeCode) {
            draft.storeCode = storeCode;
            return this;
        }

        public Builder siteCode(String siteCode) {
            draft.siteCode = siteCode;
            return this;
        }

        public Builder pullType(NoonPullType pullType) {
            draft.pullType = pullType;
            return this;
        }

        public Builder dataDomain(NoonPullDataDomain dataDomain) {
            draft.dataDomain = dataDomain;
            return this;
        }

        public Builder triggerMode(NoonPullTriggerMode triggerMode) {
            draft.triggerMode = triggerMode;
            return this;
        }

        public Builder targetIdentity(String targetIdentity) {
            draft.targetIdentity = targetIdentity;
            return this;
        }

        public Builder targetDateFrom(LocalDate targetDateFrom) {
            draft.targetDateFrom = targetDateFrom;
            return this;
        }

        public Builder targetDateTo(LocalDate targetDateTo) {
            draft.targetDateTo = targetDateTo;
            return this;
        }

        public NoonPullTaskDraft build() {
            return draft;
        }
    }
}
