package com.nuono.next.productpublicdetail;

import java.time.LocalDateTime;

public class ProductPublicDetailStatusView {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private int candidateCount;
    private int latestSucceededCount;
    private int latestPartialCount;
    private int latestNotFoundCount;
    private int latestFailedCount;
    private int todaySucceededCount;
    private int todayPartialCount;
    private int todayNotFoundCount;
    private int todayFailedCount;
    private ProductPublicDetailTaskView latestTask;
    private LocalDateTime checkedAt;

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public int getCandidateCount() { return candidateCount; }
    public void setCandidateCount(int candidateCount) { this.candidateCount = candidateCount; }
    public int getLatestSucceededCount() { return latestSucceededCount; }
    public void setLatestSucceededCount(int latestSucceededCount) { this.latestSucceededCount = latestSucceededCount; }
    public int getLatestPartialCount() { return latestPartialCount; }
    public void setLatestPartialCount(int latestPartialCount) { this.latestPartialCount = latestPartialCount; }
    public int getLatestNotFoundCount() { return latestNotFoundCount; }
    public void setLatestNotFoundCount(int latestNotFoundCount) { this.latestNotFoundCount = latestNotFoundCount; }
    public int getLatestFailedCount() { return latestFailedCount; }
    public void setLatestFailedCount(int latestFailedCount) { this.latestFailedCount = latestFailedCount; }
    public int getTodaySucceededCount() { return todaySucceededCount; }
    public void setTodaySucceededCount(int todaySucceededCount) { this.todaySucceededCount = todaySucceededCount; }
    public int getTodayPartialCount() { return todayPartialCount; }
    public void setTodayPartialCount(int todayPartialCount) { this.todayPartialCount = todayPartialCount; }
    public int getTodayNotFoundCount() { return todayNotFoundCount; }
    public void setTodayNotFoundCount(int todayNotFoundCount) { this.todayNotFoundCount = todayNotFoundCount; }
    public int getTodayFailedCount() { return todayFailedCount; }
    public void setTodayFailedCount(int todayFailedCount) { this.todayFailedCount = todayFailedCount; }
    public ProductPublicDetailTaskView getLatestTask() { return latestTask; }
    public void setLatestTask(ProductPublicDetailTaskView latestTask) { this.latestTask = latestTask; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
}
