package com.nuono.next.datapull.advertising;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Locked durable cursor and accounting for one invisible DP-06 fact generation. */
public final class AdvertisingGenerationRow {
    private Long taskId;
    private Long activeFenceEpoch;
    private String state;
    private Long ownerUserId;
    private String projectCode;
    private String storeCode;
    private String siteCode;
    private LocalDate reportDate;
    private LocalDateTime scheduleSlot;
    private String businessWindowKey;
    private String authorityTokenSha256;
    private String activeCampaignDigestSha256;
    private LocalDateTime providerAsOfUtc;
    private Long declaredCampaignCount;
    private Integer activeCampaignCount;
    private Integer lastPage;
    private Long stagedCampaignItemCount;
    private Long campaignBusinessSkippedItemCount;
    private Long stagedItemCount;
    private Long sourceItemCount;
    private Long businessSkippedItemCount;
    private Integer cursorPageNo;
    private Integer cursorItemOrdinal;
    private Long processedItemCount;
    private Long campaignFactCount;
    private Long queryFactCount;
    private Long identitySkippedItemCount;
    private Long campaignIdentitySkippedItemCount;
    private Integer queryPageProofCount;
    private Integer matchedActiveCampaignCount;
    private Long batchId;
    private Long campaignIdStart;
    private Long queryIdStart;
    private String digestChainSha256;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public Long getActiveFenceEpoch() { return activeFenceEpoch; }
    public void setActiveFenceEpoch(Long value) { activeFenceEpoch = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long value) { ownerUserId = value; }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String value) { projectCode = value; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String value) { storeCode = value; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String value) { siteCode = value; }
    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate value) { reportDate = value; }
    public LocalDateTime getScheduleSlot() { return scheduleSlot; }
    public void setScheduleSlot(LocalDateTime value) { scheduleSlot = value; }
    public String getBusinessWindowKey() { return businessWindowKey; }
    public void setBusinessWindowKey(String value) { businessWindowKey = value; }
    public String getAuthorityTokenSha256() { return authorityTokenSha256; }
    public void setAuthorityTokenSha256(String value) { authorityTokenSha256 = value; }
    public String getActiveCampaignDigestSha256() { return activeCampaignDigestSha256; }
    public void setActiveCampaignDigestSha256(String value) { activeCampaignDigestSha256 = value; }
    public LocalDateTime getProviderAsOfUtc() { return providerAsOfUtc; }
    public void setProviderAsOfUtc(LocalDateTime value) { providerAsOfUtc = value; }
    public Long getDeclaredCampaignCount() { return declaredCampaignCount; }
    public void setDeclaredCampaignCount(Long value) { declaredCampaignCount = value; }
    public Integer getActiveCampaignCount() { return activeCampaignCount; }
    public void setActiveCampaignCount(Integer value) { activeCampaignCount = value; }
    public Integer getLastPage() { return lastPage; }
    public void setLastPage(Integer value) { lastPage = value; }
    public Long getStagedCampaignItemCount() { return stagedCampaignItemCount; }
    public void setStagedCampaignItemCount(Long value) { stagedCampaignItemCount = value; }
    public Long getCampaignBusinessSkippedItemCount() {
        return campaignBusinessSkippedItemCount;
    }
    public void setCampaignBusinessSkippedItemCount(Long value) {
        campaignBusinessSkippedItemCount = value;
    }
    public Long getStagedItemCount() { return stagedItemCount; }
    public void setStagedItemCount(Long value) { stagedItemCount = value; }
    public Long getSourceItemCount() { return sourceItemCount; }
    public void setSourceItemCount(Long value) { sourceItemCount = value; }
    public Long getBusinessSkippedItemCount() { return businessSkippedItemCount; }
    public void setBusinessSkippedItemCount(Long value) { businessSkippedItemCount = value; }
    public Integer getCursorPageNo() { return cursorPageNo; }
    public void setCursorPageNo(Integer value) { cursorPageNo = value; }
    public Integer getCursorItemOrdinal() { return cursorItemOrdinal; }
    public void setCursorItemOrdinal(Integer value) { cursorItemOrdinal = value; }
    public Long getProcessedItemCount() { return processedItemCount; }
    public void setProcessedItemCount(Long value) { processedItemCount = value; }
    public Long getCampaignFactCount() { return campaignFactCount; }
    public void setCampaignFactCount(Long value) { campaignFactCount = value; }
    public Long getQueryFactCount() { return queryFactCount; }
    public void setQueryFactCount(Long value) { queryFactCount = value; }
    public Long getIdentitySkippedItemCount() { return identitySkippedItemCount; }
    public void setIdentitySkippedItemCount(Long value) { identitySkippedItemCount = value; }
    public Long getCampaignIdentitySkippedItemCount() {
        return campaignIdentitySkippedItemCount;
    }
    public void setCampaignIdentitySkippedItemCount(Long value) {
        campaignIdentitySkippedItemCount = value;
    }
    public Integer getQueryPageProofCount() { return queryPageProofCount; }
    public void setQueryPageProofCount(Integer value) { queryPageProofCount = value; }
    public Integer getMatchedActiveCampaignCount() { return matchedActiveCampaignCount; }
    public void setMatchedActiveCampaignCount(Integer value) { matchedActiveCampaignCount = value; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long value) { batchId = value; }
    public Long getCampaignIdStart() { return campaignIdStart; }
    public void setCampaignIdStart(Long value) { campaignIdStart = value; }
    public Long getQueryIdStart() { return queryIdStart; }
    public void setQueryIdStart(Long value) { queryIdStart = value; }
    public String getDigestChainSha256() { return digestChainSha256; }
    public void setDigestChainSha256(String value) { digestChainSha256 = value; }
}
