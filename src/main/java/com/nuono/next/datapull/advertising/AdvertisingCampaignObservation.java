package com.nuono.next.datapull.advertising;

import java.util.Objects;

/** One valid campaign row reduced to the identity/status needed for first-valid-wins routing. */
public final class AdvertisingCampaignObservation {
    private final AdvertisingCampaignRef campaign;
    private final boolean active;

    public AdvertisingCampaignObservation(AdvertisingCampaignRef campaign, boolean active) {
        this.campaign = Objects.requireNonNull(campaign, "campaign");
        this.active = active;
    }

    public AdvertisingCampaignRef getCampaign() { return campaign; }
    public boolean isActive() { return active; }
}
