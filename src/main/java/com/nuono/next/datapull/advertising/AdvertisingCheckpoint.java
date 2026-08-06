package com.nuono.next.datapull.advertising;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Recoverable DP06 cursor: enumerate twice, promote, then query first-valid active campaigns. */
final class AdvertisingCheckpoint {
    enum Phase {
        ADVERTISER,
        CAMPAIGN_FETCH,
        CAMPAIGN_VERIFY,
        CAMPAIGN_COMPARE,
        CAMPAIGN_PROMOTE,
        CAMPAIGN_QUERY,
        APPLY,
        RESET
    }

    private final Phase phase;
    private final AdvertisingAdvertiser advertiser;
    private final List<AdvertisingCampaignObservation> campaigns;
    private final AdvertisingCampaignEnumerationAuthority authority;
    private final int nextCampaignPage;
    private final int campaignPageCount;
    private final int nextCampaignIndex;
    private final Long declaredCampaignCount;
    private final int consecutiveRetryAttempt;

    private AdvertisingCheckpoint(
            Phase phase,
            AdvertisingAdvertiser advertiser,
            List<AdvertisingCampaignObservation> campaigns,
            AdvertisingCampaignEnumerationAuthority authority,
            int nextCampaignPage,
            int campaignPageCount,
            int nextCampaignIndex,
            Long declaredCampaignCount,
            int consecutiveRetryAttempt
    ) {
        this.phase = Objects.requireNonNull(phase, "phase");
        this.advertiser = advertiser;
        this.campaigns = uniqueCampaigns(campaigns);
        this.authority = authority;
        if (nextCampaignPage < 0 || campaignPageCount < 0 || nextCampaignIndex < 0
                || consecutiveRetryAttempt < 0
                || (declaredCampaignCount != null && declaredCampaignCount < 0L)) {
            throw new IllegalArgumentException("advertising checkpoint counters are invalid");
        }
        this.nextCampaignPage = nextCampaignPage;
        this.campaignPageCount = campaignPageCount;
        this.nextCampaignIndex = nextCampaignIndex;
        this.declaredCampaignCount = declaredCampaignCount;
        this.consecutiveRetryAttempt = consecutiveRetryAttempt;
        validate();
    }

    static AdvertisingCheckpoint initial() {
        return empty(Phase.ADVERTISER);
    }

    static AdvertisingCheckpoint resetting() {
        return empty(Phase.RESET);
    }

    private static AdvertisingCheckpoint empty(Phase phase) {
        return new AdvertisingCheckpoint(
                phase, null, List.of(), null, 0, 0, 0, null, 0
        );
    }

    static AdvertisingCheckpoint restored(
            Phase phase,
            AdvertisingAdvertiser advertiser,
            List<AdvertisingCampaignObservation> campaigns,
            AdvertisingCampaignEnumerationAuthority authority,
            int nextCampaignPage,
            int campaignPageCount,
            int nextCampaignIndex,
            Long declaredCampaignCount,
            int retryAttempt
    ) {
        return new AdvertisingCheckpoint(
                phase, advertiser, campaigns, authority, nextCampaignPage,
                campaignPageCount, nextCampaignIndex, declaredCampaignCount, retryAttempt
        );
    }

    AdvertisingCheckpoint campaigns(AdvertisingAdvertiser resolvedAdvertiser) {
        return new AdvertisingCheckpoint(
                Phase.CAMPAIGN_FETCH,
                Objects.requireNonNull(resolvedAdvertiser, "resolvedAdvertiser"),
                List.of(), null, 1, 0, 0, null, 0
        );
    }

    AdvertisingCheckpoint afterCampaignPage(AdvertisingCampaignPage page) {
        AdvertisingCampaignPage value = requireCampaignPage(page);
        List<AdvertisingCampaignObservation> merged = mergeCampaigns(
                campaigns, value.getObservations()
        );
        boolean last = value.getPageNo() == value.getTotalPages();
        return new AdvertisingCheckpoint(
                last ? Phase.CAMPAIGN_VERIFY : Phase.CAMPAIGN_FETCH,
                advertiser,
                merged,
                null,
                last ? 1 : Math.incrementExact(nextCampaignPage),
                value.getTotalPages(),
                0,
                value.getDeclaredCampaignCount(),
                0
        );
    }

    AdvertisingCampaignPage requireCampaignPage(AdvertisingCampaignPage page) {
        AdvertisingCampaignPage value = Objects.requireNonNull(page, "page");
        if ((phase != Phase.CAMPAIGN_FETCH && phase != Phase.CAMPAIGN_VERIFY)
                || value.getPageNo() != nextCampaignPage
                || (campaignPageCount > 0 && value.getTotalPages() != campaignPageCount)
                || (declaredCampaignCount != null
                        && value.getDeclaredCampaignCount() != declaredCampaignCount)) {
            throw new IllegalArgumentException("advertising campaign page extent drift");
        }
        return value;
    }

    AdvertisingCheckpoint afterVerifiedPage(boolean complete) {
        if (phase != Phase.CAMPAIGN_VERIFY) {
            throw new IllegalStateException("checkpoint is not verifying campaigns");
        }
        if (complete && nextCampaignPage != campaignPageCount) {
            throw new IllegalArgumentException("campaign verification ended before last page");
        }
        return new AdvertisingCheckpoint(
                complete ? Phase.CAMPAIGN_COMPARE : Phase.CAMPAIGN_VERIFY,
                advertiser,
                campaigns,
                null,
                complete ? 0 : Math.incrementExact(nextCampaignPage),
                campaignPageCount,
                0,
                declaredCampaignCount,
                0
        );
    }

    AdvertisingCheckpoint promote() {
        if (phase != Phase.CAMPAIGN_COMPARE) {
            throw new IllegalStateException("checkpoint is not ready for promotion");
        }
        return copy(Phase.CAMPAIGN_PROMOTE, null, 0, 0, 0);
    }

    AdvertisingCheckpoint promoted(AdvertisingCampaignEnumerationAuthority value) {
        AdvertisingCampaignEnumerationAuthority proof = Objects.requireNonNull(value, "authority");
        if (phase != Phase.CAMPAIGN_PROMOTE || !proof.isComplete()
                || proof.getDeclaredCampaignCount() != declaredCampaignCount) {
            throw new IllegalArgumentException("campaign promotion authority drift");
        }
        List<AdvertisingCampaignRef> active = activeCampaigns();
        return new AdvertisingCheckpoint(
                active.isEmpty() ? Phase.APPLY : Phase.CAMPAIGN_QUERY,
                advertiser, campaigns, proof, 0, campaignPageCount, 0,
                declaredCampaignCount, 0
        );
    }

    AdvertisingCheckpoint nextCampaign() {
        int next = Math.addExact(nextCampaignIndex, 1);
        return new AdvertisingCheckpoint(
                next == activeCampaigns().size() ? Phase.APPLY : Phase.CAMPAIGN_QUERY,
                advertiser, campaigns, authority, 0, campaignPageCount, next,
                declaredCampaignCount, 0
        );
    }

    AdvertisingCheckpoint retry() {
        int attempt = consecutiveRetryAttempt == Integer.MAX_VALUE
                ? Integer.MAX_VALUE : consecutiveRetryAttempt + 1;
        return new AdvertisingCheckpoint(
                phase, advertiser, campaigns, authority, nextCampaignPage,
                campaignPageCount, nextCampaignIndex, declaredCampaignCount, attempt
        );
    }

    private AdvertisingCheckpoint copy(
            Phase nextPhase,
            AdvertisingCampaignEnumerationAuthority nextAuthority,
            int nextPage,
            int nextIndex,
            int retry
    ) {
        return new AdvertisingCheckpoint(
                nextPhase, advertiser, campaigns, nextAuthority, nextPage,
                campaignPageCount, nextIndex, declaredCampaignCount, retry
        );
    }

    private void validate() {
        if (phase == Phase.ADVERTISER || phase == Phase.RESET) {
            require(advertiser == null && campaigns.isEmpty() && authority == null
                    && nextCampaignPage == 0 && campaignPageCount == 0
                    && nextCampaignIndex == 0 && declaredCampaignCount == null);
            return;
        }
        require(advertiser != null);
        if (phase == Phase.CAMPAIGN_FETCH) {
            require(authority == null && nextCampaignPage >= 1 && nextCampaignIndex == 0);
            require((campaignPageCount == 0 && nextCampaignPage == 1
                    && declaredCampaignCount == null)
                    || (campaignPageCount >= nextCampaignPage
                    && declaredCampaignCount != null));
            return;
        }
        require(campaignPageCount >= 1 && declaredCampaignCount != null);
        if (phase == Phase.CAMPAIGN_VERIFY) {
            require(authority == null && nextCampaignPage >= 1
                    && nextCampaignPage <= campaignPageCount && nextCampaignIndex == 0);
            return;
        }
        if (phase == Phase.CAMPAIGN_COMPARE || phase == Phase.CAMPAIGN_PROMOTE) {
            require(authority == null && nextCampaignPage == 0 && nextCampaignIndex == 0);
            return;
        }
        require(authority != null && authority.isComplete()
                && authority.getDeclaredCampaignCount() == declaredCampaignCount
                && nextCampaignPage == 0);
        int activeCount = activeCampaigns().size();
        if (phase == Phase.CAMPAIGN_QUERY) {
            require(activeCount > 0 && nextCampaignIndex < activeCount);
        } else {
            require(phase == Phase.APPLY && nextCampaignIndex == activeCount);
        }
    }

    private static List<AdvertisingCampaignObservation> mergeCampaigns(
            List<AdvertisingCampaignObservation> current,
            List<AdvertisingCampaignObservation> incoming
    ) {
        Map<String, AdvertisingCampaignObservation> first = new LinkedHashMap<>();
        for (AdvertisingCampaignObservation item : current) {
            first.put(item.getCampaign().getCampaignCode(), item);
        }
        for (AdvertisingCampaignObservation item : incoming) {
            AdvertisingCampaignObservation value = Objects.requireNonNull(item, "campaign");
            first.putIfAbsent(value.getCampaign().getCampaignCode(), value);
        }
        return List.copyOf(first.values());
    }

    private static List<AdvertisingCampaignObservation> uniqueCampaigns(
            List<AdvertisingCampaignObservation> values
    ) {
        List<AdvertisingCampaignObservation> source = List.copyOf(
                Objects.requireNonNull(values, "campaigns")
        );
        if (mergeCampaigns(List.of(), source).size() != source.size()) {
            throw new IllegalArgumentException("checkpoint campaign identities must be unique");
        }
        return source;
    }

    private void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("invalid advertising checkpoint state");
    }

    Phase getPhase() { return phase; }
    AdvertisingAdvertiser getAdvertiser() { return advertiser; }
    List<AdvertisingCampaignObservation> getCampaigns() { return campaigns; }
    AdvertisingCampaignEnumerationAuthority getAuthority() { return authority; }
    int getNextCampaignPage() { return nextCampaignPage; }
    int getCampaignPageCount() { return campaignPageCount; }
    int getNextCampaignIndex() { return nextCampaignIndex; }
    Long getDeclaredCampaignCount() { return declaredCampaignCount; }
    int getConsecutiveRetryAttempt() { return consecutiveRetryAttempt; }

    List<AdvertisingCampaignRef> activeCampaigns() {
        return campaigns.stream()
                .filter(AdvertisingCampaignObservation::isActive)
                .map(AdvertisingCampaignObservation::getCampaign)
                .collect(Collectors.toUnmodifiableList());
    }

    AdvertisingCampaignRef currentCampaign() {
        if (phase != Phase.CAMPAIGN_QUERY) {
            throw new IllegalStateException("checkpoint is not at a campaign query");
        }
        return activeCampaigns().get(nextCampaignIndex);
    }
}
