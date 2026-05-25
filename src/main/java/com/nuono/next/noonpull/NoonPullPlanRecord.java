package com.nuono.next.noonpull;

import java.time.LocalDateTime;

public class NoonPullPlanRecord {
    private Long id;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private NoonPullType pullType;
    private NoonPullDataDomain dataDomain;
    private NoonPullTriggerMode triggerMode;
    private String scheduleExpression;
    private boolean enabled;
    private boolean paused;
    private String pauseReason;
    private LocalDateTime latestSuccessAt;
    private LocalDateTime latestFailureAt;
    private String latestFailureType;
    private LocalDateTime nextRetryAt;
    private Integer maxPagesPerRun;
    private Integer maxProductsPerRun;
    private Integer maxDetailFetchesPerRun;
    private Integer maxRequestsPerRun;
    private Integer cooldownSeconds;
    private Integer concurrencyLimit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public NoonPullPlanRecord copy() {
        NoonPullPlanRecord copy = new NoonPullPlanRecord();
        copy.id = id;
        copy.ownerUserId = ownerUserId;
        copy.storeCode = storeCode;
        copy.siteCode = siteCode;
        copy.pullType = pullType;
        copy.dataDomain = dataDomain;
        copy.triggerMode = triggerMode;
        copy.scheduleExpression = scheduleExpression;
        copy.enabled = enabled;
        copy.paused = paused;
        copy.pauseReason = pauseReason;
        copy.latestSuccessAt = latestSuccessAt;
        copy.latestFailureAt = latestFailureAt;
        copy.latestFailureType = latestFailureType;
        copy.nextRetryAt = nextRetryAt;
        copy.maxPagesPerRun = maxPagesPerRun;
        copy.maxProductsPerRun = maxProductsPerRun;
        copy.maxDetailFetchesPerRun = maxDetailFetchesPerRun;
        copy.maxRequestsPerRun = maxRequestsPerRun;
        copy.cooldownSeconds = cooldownSeconds;
        copy.concurrencyLimit = concurrencyLimit;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        return copy;
    }

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

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public NoonPullType getPullType() {
        return pullType;
    }

    public void setPullType(NoonPullType pullType) {
        this.pullType = pullType;
    }

    public NoonPullDataDomain getDataDomain() {
        return dataDomain;
    }

    public void setDataDomain(NoonPullDataDomain dataDomain) {
        this.dataDomain = dataDomain;
    }

    public NoonPullTriggerMode getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(NoonPullTriggerMode triggerMode) {
        this.triggerMode = triggerMode;
    }

    public String getScheduleExpression() {
        return scheduleExpression;
    }

    public void setScheduleExpression(String scheduleExpression) {
        this.scheduleExpression = scheduleExpression;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public String getPauseReason() {
        return pauseReason;
    }

    public void setPauseReason(String pauseReason) {
        this.pauseReason = pauseReason;
    }

    public LocalDateTime getLatestSuccessAt() {
        return latestSuccessAt;
    }

    public void setLatestSuccessAt(LocalDateTime latestSuccessAt) {
        this.latestSuccessAt = latestSuccessAt;
    }

    public LocalDateTime getLatestFailureAt() {
        return latestFailureAt;
    }

    public void setLatestFailureAt(LocalDateTime latestFailureAt) {
        this.latestFailureAt = latestFailureAt;
    }

    public String getLatestFailureType() {
        return latestFailureType;
    }

    public void setLatestFailureType(String latestFailureType) {
        this.latestFailureType = latestFailureType;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Integer getMaxPagesPerRun() {
        return maxPagesPerRun;
    }

    public void setMaxPagesPerRun(Integer maxPagesPerRun) {
        this.maxPagesPerRun = maxPagesPerRun;
    }

    public Integer getMaxProductsPerRun() {
        return maxProductsPerRun;
    }

    public void setMaxProductsPerRun(Integer maxProductsPerRun) {
        this.maxProductsPerRun = maxProductsPerRun;
    }

    public Integer getMaxDetailFetchesPerRun() {
        return maxDetailFetchesPerRun;
    }

    public void setMaxDetailFetchesPerRun(Integer maxDetailFetchesPerRun) {
        this.maxDetailFetchesPerRun = maxDetailFetchesPerRun;
    }

    public Integer getMaxRequestsPerRun() {
        return maxRequestsPerRun;
    }

    public void setMaxRequestsPerRun(Integer maxRequestsPerRun) {
        this.maxRequestsPerRun = maxRequestsPerRun;
    }

    public Integer getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(Integer cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public Integer getConcurrencyLimit() {
        return concurrencyLimit;
    }

    public void setConcurrencyLimit(Integer concurrencyLimit) {
        this.concurrencyLimit = concurrencyLimit;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
