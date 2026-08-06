package com.nuono.next.datapull.advertising;

import com.nuono.next.noonads.NoonAdvertisingCampaignFact;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Complete dashboard: raw campaign rows plus the first occurrence of each active identity. */
public final class AdvertisingDashboard {
    private final List<NoonAdvertisingCampaignFact> campaignFacts;
    private final List<AdvertisingCampaignRef> activeCampaigns;
    private final AdvertisingCampaignEnumerationAuthority authority;
    private final int businessSkippedCampaignCount;

    public AdvertisingDashboard(
            List<NoonAdvertisingCampaignFact> campaignFacts,
            List<AdvertisingCampaignRef> activeCampaigns,
            AdvertisingCampaignEnumerationAuthority authority
    ) {
        this(campaignFacts, activeCampaigns, authority, 0);
    }

    public AdvertisingDashboard(
            List<NoonAdvertisingCampaignFact> campaignFacts,
            List<AdvertisingCampaignRef> activeCampaigns,
            AdvertisingCampaignEnumerationAuthority authority,
            int businessSkippedCampaignCount
    ) {
        this.campaignFacts = List.copyOf(Objects.requireNonNull(campaignFacts, "campaignFacts"));
        this.activeCampaigns = firstByIdentity(Objects.requireNonNull(
                activeCampaigns,
                "activeCampaigns"
        ));
        this.authority = Objects.requireNonNull(authority, "authority");
        this.businessSkippedCampaignCount = businessSkippedCampaignCount;
        validate();
    }

    private void validate() {
        if (!authority.isComplete()) {
            throw new IllegalArgumentException("dashboard campaign enumeration must be complete");
        }
        if (businessSkippedCampaignCount < 0
                || authority.getDeclaredCampaignCount() != getSourceCampaignCount()) {
            throw new IllegalArgumentException("dashboard campaign count must match provider authority");
        }
        Map<String, NoonAdvertisingCampaignFact> factsByCode = new LinkedHashMap<>();
        for (NoonAdvertisingCampaignFact fact : campaignFacts) {
            NoonAdvertisingCampaignFact value = Objects.requireNonNull(fact, "campaign fact");
            String code = AdvertisingAdvertiser.requireIdentity(
                    value.getCampaignCode(),
                    "campaignFact.campaignCode"
            );
            factsByCode.putIfAbsent(code, value);
        }
        int activeWithoutFact = 0;
        for (AdvertisingCampaignRef campaign : activeCampaigns) {
            AdvertisingCampaignRef value = Objects.requireNonNull(campaign, "active campaign");
            if (!factsByCode.containsKey(value.getCampaignCode())) {
                activeWithoutFact = Math.incrementExact(activeWithoutFact);
            }
        }
        if (activeWithoutFact > businessSkippedCampaignCount) {
            throw new IllegalArgumentException("active campaign lacks dashboard source accounting");
        }
    }

    private List<AdvertisingCampaignRef> firstByIdentity(
            List<AdvertisingCampaignRef> campaigns
    ) {
        Map<String, AdvertisingCampaignRef> first = new LinkedHashMap<>();
        for (AdvertisingCampaignRef campaign : campaigns) {
            AdvertisingCampaignRef value = Objects.requireNonNull(campaign, "active campaign");
            first.putIfAbsent(value.getCampaignCode(), value);
        }
        return List.copyOf(first.values());
    }

    public List<NoonAdvertisingCampaignFact> getCampaignFacts() {
        return campaignFacts;
    }

    public List<AdvertisingCampaignRef> getActiveCampaigns() {
        return activeCampaigns;
    }

    public AdvertisingCampaignEnumerationAuthority getAuthority() {
        return authority;
    }

    public int getBusinessSkippedCampaignCount() {
        return businessSkippedCampaignCount;
    }

    public int getSourceCampaignCount() {
        return Math.addExact(campaignFacts.size(), businessSkippedCampaignCount);
    }
}
