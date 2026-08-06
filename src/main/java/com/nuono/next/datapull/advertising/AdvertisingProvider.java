package com.nuono.next.datapull.advertising;

import com.nuono.next.datapull.runtime.ProviderOutcome;

/**
 * DP-06 provider Seam. Every method performs exactly one remote Ad Manager action.
 */
public interface AdvertisingProvider {
    ProviderOutcome<AdvertisingAdvertiser> resolveAdvertiser(AdvertisingPullRequest request);

    ProviderOutcome<AdvertisingDashboard> fetchDashboard(
            AdvertisingPullRequest request,
            AdvertisingAdvertiser advertiser
    );

    ProviderOutcome<AdvertisingQueryReport> fetchCampaignQueries(
            AdvertisingPullRequest request,
            AdvertisingAdvertiser advertiser,
            AdvertisingCampaignRef campaign
    );
}
