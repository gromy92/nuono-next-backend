package com.nuono.next.noonpull;

public class NoonPullPlanDraft {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private NoonPullType pullType;
    private NoonPullDataDomain dataDomain;
    private NoonPullTriggerMode triggerMode;
    private String scheduleExpression;
    private Integer maxPagesPerRun;
    private Integer maxProductsPerRun;
    private Integer maxDetailFetchesPerRun;
    private Integer maxRequestsPerRun;
    private Integer cooldownSeconds;
    private Integer concurrencyLimit;

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

    public String getScheduleExpression() {
        return scheduleExpression;
    }

    public Integer getMaxPagesPerRun() {
        return maxPagesPerRun;
    }

    public Integer getMaxProductsPerRun() {
        return maxProductsPerRun;
    }

    public Integer getMaxDetailFetchesPerRun() {
        return maxDetailFetchesPerRun;
    }

    public Integer getMaxRequestsPerRun() {
        return maxRequestsPerRun;
    }

    public Integer getCooldownSeconds() {
        return cooldownSeconds;
    }

    public Integer getConcurrencyLimit() {
        return concurrencyLimit;
    }

    public static final class Builder {
        private final NoonPullPlanDraft draft = new NoonPullPlanDraft();

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

        public Builder scheduleExpression(String scheduleExpression) {
            draft.scheduleExpression = scheduleExpression;
            return this;
        }

        public Builder maxPagesPerRun(Integer maxPagesPerRun) {
            draft.maxPagesPerRun = maxPagesPerRun;
            return this;
        }

        public Builder maxProductsPerRun(Integer maxProductsPerRun) {
            draft.maxProductsPerRun = maxProductsPerRun;
            return this;
        }

        public Builder maxDetailFetchesPerRun(Integer maxDetailFetchesPerRun) {
            draft.maxDetailFetchesPerRun = maxDetailFetchesPerRun;
            return this;
        }

        public Builder maxRequestsPerRun(Integer maxRequestsPerRun) {
            draft.maxRequestsPerRun = maxRequestsPerRun;
            return this;
        }

        public Builder cooldownSeconds(Integer cooldownSeconds) {
            draft.cooldownSeconds = cooldownSeconds;
            return this;
        }

        public Builder concurrencyLimit(Integer concurrencyLimit) {
            draft.concurrencyLimit = concurrencyLimit;
            return this;
        }

        public NoonPullPlanDraft build() {
            return draft;
        }
    }
}
