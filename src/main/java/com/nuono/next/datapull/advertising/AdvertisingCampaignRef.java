package com.nuono.next.datapull.advertising;

/** Ordered active-campaign checkpoint used for exactly one query-report call per campaign. */
public final class AdvertisingCampaignRef {
    private final String campaignCode;
    private final String campaignName;

    public AdvertisingCampaignRef(String campaignCode, String campaignName) {
        this.campaignCode = AdvertisingAdvertiser.requireIdentity(campaignCode, "campaignCode");
        this.campaignName = campaignName == null ? "" : campaignName.trim();
    }

    public String getCampaignCode() {
        return campaignCode;
    }

    public String getCampaignName() {
        return campaignName;
    }
}
