package com.nuono.next.datapull.advertising;

import java.time.LocalDateTime;

/** Aggregate-only raw stage evidence; item payloads are verified later in bounded chunks. */
public final class AdvertisingStageManifestRow {
    private Long taskId;
    private Long activeFenceEpoch;
    private Integer declaredTotalPages;
    private Integer knownLastPage;
    private String poisonCode;
    private String authorityKind;
    private String authorityTokenSha256;
    private LocalDateTime snapshotAsOfUtc;
    private Long declaredCampaignCount;
    private Long pageCount;
    private Integer firstPage;
    private Integer lastPage;
    private Long dashboardItemCount;
    private Long dashboardSourceItemCount;
    private Long dashboardBusinessSkippedItemCount;
    private Long stagedItemCount;
    private Long sourceItemCount;
    private Long businessSkippedItemCount;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public Long getActiveFenceEpoch() { return activeFenceEpoch; }
    public void setActiveFenceEpoch(Long value) { activeFenceEpoch = value; }
    public Integer getDeclaredTotalPages() { return declaredTotalPages; }
    public void setDeclaredTotalPages(Integer value) { declaredTotalPages = value; }
    public Integer getKnownLastPage() { return knownLastPage; }
    public void setKnownLastPage(Integer value) { knownLastPage = value; }
    public String getPoisonCode() { return poisonCode; }
    public void setPoisonCode(String value) { poisonCode = value; }
    public String getAuthorityKind() { return authorityKind; }
    public void setAuthorityKind(String value) { authorityKind = value; }
    public String getAuthorityTokenSha256() { return authorityTokenSha256; }
    public void setAuthorityTokenSha256(String value) { authorityTokenSha256 = value; }
    public LocalDateTime getSnapshotAsOfUtc() { return snapshotAsOfUtc; }
    public void setSnapshotAsOfUtc(LocalDateTime value) { snapshotAsOfUtc = value; }
    public Long getDeclaredCampaignCount() { return declaredCampaignCount; }
    public void setDeclaredCampaignCount(Long value) { declaredCampaignCount = value; }
    public Long getPageCount() { return pageCount; }
    public void setPageCount(Long value) { pageCount = value; }
    public Integer getFirstPage() { return firstPage; }
    public void setFirstPage(Integer value) { firstPage = value; }
    public Integer getLastPage() { return lastPage; }
    public void setLastPage(Integer value) { lastPage = value; }
    public Long getDashboardItemCount() { return dashboardItemCount; }
    public void setDashboardItemCount(Long value) { dashboardItemCount = value; }
    public Long getDashboardSourceItemCount() { return dashboardSourceItemCount; }
    public void setDashboardSourceItemCount(Long value) { dashboardSourceItemCount = value; }
    public Long getDashboardBusinessSkippedItemCount() {
        return dashboardBusinessSkippedItemCount;
    }
    public void setDashboardBusinessSkippedItemCount(Long value) {
        dashboardBusinessSkippedItemCount = value;
    }
    public Long getStagedItemCount() { return stagedItemCount; }
    public void setStagedItemCount(Long value) { stagedItemCount = value; }
    public Long getSourceItemCount() { return sourceItemCount; }
    public void setSourceItemCount(Long value) { sourceItemCount = value; }
    public Long getBusinessSkippedItemCount() { return businessSkippedItemCount; }
    public void setBusinessSkippedItemCount(Long value) { businessSkippedItemCount = value; }
}
