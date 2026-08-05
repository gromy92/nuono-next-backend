package com.nuono.next.datapull.advertising;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class AdvertisingCheckpoint {
    enum Phase {
        ADVERTISER,
        DASHBOARD,
        CAMPAIGN_QUERY,
        APPLY,
        RESET
    }

    private final Phase phase;
    private final AdvertisingAdvertiser advertiser;
    private final List<AdvertisingCampaignRef> activeCampaigns;
    private final AdvertisingCampaignEnumerationAuthority authority;
    private final int nextCampaignIndex;
    private final int consecutiveRetryAttempt;

    private AdvertisingCheckpoint(
            Phase phase,
            AdvertisingAdvertiser advertiser,
            List<AdvertisingCampaignRef> activeCampaigns,
            AdvertisingCampaignEnumerationAuthority authority,
            int nextCampaignIndex,
            int consecutiveRetryAttempt
    ) {
        this.phase = Objects.requireNonNull(phase, "phase");
        this.advertiser = advertiser;
        this.activeCampaigns = List.copyOf(Objects.requireNonNull(activeCampaigns, "activeCampaigns"));
        this.authority = authority;
        if (nextCampaignIndex < 0 || consecutiveRetryAttempt < 0) {
            throw new IllegalArgumentException("advertising checkpoint counters must not be negative");
        }
        this.nextCampaignIndex = nextCampaignIndex;
        this.consecutiveRetryAttempt = consecutiveRetryAttempt;
        validate();
    }

    static AdvertisingCheckpoint initial() {
        return new AdvertisingCheckpoint(Phase.ADVERTISER, null, List.of(), null, 0, 0);
    }

    static AdvertisingCheckpoint resetting() {
        return new AdvertisingCheckpoint(Phase.RESET, null, List.of(), null, 0, 0);
    }

    static AdvertisingCheckpoint restored(
            Phase phase,
            AdvertisingAdvertiser advertiser,
            List<AdvertisingCampaignRef> campaigns,
            AdvertisingCampaignEnumerationAuthority authority,
            int nextCampaignIndex,
            int retryAttempt
    ) {
        return new AdvertisingCheckpoint(
                phase,
                advertiser,
                campaigns,
                authority,
                nextCampaignIndex,
                retryAttempt
        );
    }

    AdvertisingCheckpoint dashboard(AdvertisingAdvertiser resolvedAdvertiser) {
        return new AdvertisingCheckpoint(
                Phase.DASHBOARD,
                Objects.requireNonNull(resolvedAdvertiser, "resolvedAdvertiser"),
                List.of(),
                null,
                0,
                0
        );
    }

    AdvertisingCheckpoint afterDashboard(
            List<AdvertisingCampaignRef> campaigns,
            AdvertisingCampaignEnumerationAuthority resolvedAuthority
    ) {
        List<AdvertisingCampaignRef> values = List.copyOf(
                Objects.requireNonNull(campaigns, "campaigns")
        );
        return new AdvertisingCheckpoint(
                values.isEmpty() ? Phase.APPLY : Phase.CAMPAIGN_QUERY,
                advertiser,
                values,
                Objects.requireNonNull(resolvedAuthority, "resolvedAuthority"),
                0,
                0
        );
    }

    AdvertisingCheckpoint nextCampaign() {
        int next = Math.addExact(nextCampaignIndex, 1);
        return new AdvertisingCheckpoint(
                next == activeCampaigns.size() ? Phase.APPLY : Phase.CAMPAIGN_QUERY,
                advertiser,
                activeCampaigns,
                authority,
                next,
                0
        );
    }

    AdvertisingCheckpoint retry() {
        int attempt = consecutiveRetryAttempt == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : consecutiveRetryAttempt + 1;
        return new AdvertisingCheckpoint(
                phase,
                advertiser,
                activeCampaigns,
                authority,
                nextCampaignIndex,
                attempt
        );
    }

    private void validate() {
        if (phase == Phase.ADVERTISER || phase == Phase.RESET) {
            require(advertiser == null && activeCampaigns.isEmpty()
                    && authority == null && nextCampaignIndex == 0);
            return;
        }
        require(advertiser != null);
        if (phase == Phase.DASHBOARD) {
            require(activeCampaigns.isEmpty() && authority == null && nextCampaignIndex == 0);
            return;
        }
        require(authority != null && authority.isComplete());
        require(activeCampaigns.size() <= authority.getDeclaredCampaignCount());
        Set<String> identities = new HashSet<>();
        for (AdvertisingCampaignRef campaign : activeCampaigns) {
            require(campaign != null && identities.add(campaign.getCampaignCode()));
        }
        if (phase == Phase.CAMPAIGN_QUERY) {
            require(!activeCampaigns.isEmpty() && nextCampaignIndex < activeCampaigns.size());
            return;
        }
        require(nextCampaignIndex == activeCampaigns.size());
    }

    private void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("invalid advertising checkpoint state");
        }
    }

    Phase getPhase() {
        return phase;
    }

    AdvertisingAdvertiser getAdvertiser() {
        return advertiser;
    }

    List<AdvertisingCampaignRef> getActiveCampaigns() {
        return activeCampaigns;
    }

    AdvertisingCampaignEnumerationAuthority getAuthority() {
        return authority;
    }

    int getNextCampaignIndex() {
        return nextCampaignIndex;
    }

    int getConsecutiveRetryAttempt() {
        return consecutiveRetryAttempt;
    }

    AdvertisingCampaignRef currentCampaign() {
        if (phase != Phase.CAMPAIGN_QUERY) {
            throw new IllegalStateException("checkpoint is not at a campaign query");
        }
        return activeCampaigns.get(nextCampaignIndex);
    }
}
