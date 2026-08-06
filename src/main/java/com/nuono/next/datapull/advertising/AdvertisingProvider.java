package com.nuono.next.datapull.advertising;

import com.nuono.next.datapull.runtime.ProviderOutcome;

/**
 * DP-06 provider Seam. Every method performs exactly one remote Ad Manager action.
 */
public interface AdvertisingProvider {
    ProviderOutcome<AdvertisingAdvertiser> resolveAdvertiser(AdvertisingPullRequest request);

    ProviderOutcome<AdvertisingCampaignPage> fetchCampaignPage(
            AdvertisingPullRequest request,
            AdvertisingAdvertiser advertiser,
            int pageNo
    );

    ProviderOutcome<AdvertisingQueryReport> fetchCampaignQueries(
            AdvertisingPullRequest request,
            AdvertisingAdvertiser advertiser,
            AdvertisingCampaignRef campaign
    );
}
