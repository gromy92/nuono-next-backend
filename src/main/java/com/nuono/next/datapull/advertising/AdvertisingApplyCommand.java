package com.nuono.next.datapull.advertising;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable DP-06 generation identity plus the exact task fence allowed to advance it. */
public final class AdvertisingApplyCommand {
    private final long taskId;
    private final long fenceEpoch;
    private final String leaseOwner;
    private final long ownerUserId;
    private final String projectCode;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate reportDate;
    private final LocalDateTime scheduleSlot;
    private final String businessWindowKey;
    private final AdvertisingCampaignEnumerationAuthority authority;
    private final List<AdvertisingCampaignRef> activeCampaigns;
    private final int campaignPageCount;

    public AdvertisingApplyCommand(
            long taskId,
            long fenceEpoch,
            String leaseOwner,
            LocalDateTime scheduleSlot,
            AdvertisingPullRequest request,
            String businessWindowKey,
            AdvertisingCampaignEnumerationAuthority authority,
            List<AdvertisingCampaignRef> activeCampaigns,
            int campaignPageCount
    ) {
        if (taskId <= 0L || fenceEpoch <= 0L) {
            throw new IllegalArgumentException("taskId and fenceEpoch must be positive");
        }
        this.taskId = taskId;
        this.fenceEpoch = fenceEpoch;
        this.leaseOwner = AdvertisingAdvertiser.requireIdentity(leaseOwner, "leaseOwner");
        AdvertisingPullRequest scope = Objects.requireNonNull(request, "request");
        this.ownerUserId = scope.getOwnerUserId();
        this.projectCode = scope.getProjectCode();
        this.storeCode = scope.getStoreCode();
        this.siteCode = scope.getSiteCode();
        this.reportDate = scope.getReportDate();
        this.scheduleSlot = Objects.requireNonNull(scheduleSlot, "scheduleSlot");
        this.businessWindowKey = AdvertisingAdvertiser.requireIdentity(
                businessWindowKey,
                "businessWindowKey"
        );
        this.authority = Objects.requireNonNull(authority, "authority");
        this.activeCampaigns = List.copyOf(Objects.requireNonNull(
                activeCampaigns,
                "activeCampaigns"
        ));
        if (campaignPageCount < 1) {
            throw new IllegalArgumentException("campaignPageCount must be positive");
        }
        this.campaignPageCount = campaignPageCount;
        validateCampaigns();
    }

    public long getTaskId() { return taskId; }
    public long getFenceEpoch() { return fenceEpoch; }
    public String getLeaseOwner() { return leaseOwner; }
    public long getOwnerUserId() { return ownerUserId; }
    public String getProjectCode() { return projectCode; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public LocalDate getReportDate() { return reportDate; }
    public LocalDateTime getScheduleSlot() { return scheduleSlot; }
    public String getBusinessWindowKey() { return businessWindowKey; }
    public AdvertisingCampaignEnumerationAuthority getAuthority() { return authority; }
    public List<AdvertisingCampaignRef> getActiveCampaigns() { return activeCampaigns; }
    public int getActiveCampaignCount() { return activeCampaigns.size(); }
    public int getCampaignPageCount() { return campaignPageCount; }
    public int getLastPage() {
        return Math.addExact(campaignPageCount, activeCampaigns.size());
    }

    private void validateCampaigns() {
        if (!authority.isComplete()
                || activeCampaigns.size() > authority.getDeclaredCampaignCount()) {
            throw new IllegalArgumentException("advertising campaign authority is incomplete");
        }
        Set<String> identities = new HashSet<>();
        for (AdvertisingCampaignRef campaign : activeCampaigns) {
            AdvertisingCampaignRef value = Objects.requireNonNull(campaign, "activeCampaign");
            if (!identities.add(value.getCampaignCode())) {
                throw new IllegalArgumentException("active campaign identities must be unique");
            }
        }
    }
}
