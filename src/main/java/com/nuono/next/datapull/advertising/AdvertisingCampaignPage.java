package com.nuono.next.datapull.advertising;

import com.nuono.next.datapull.snapshot.SnapshotPage;
import java.util.List;
import java.util.Objects;

/** One structurally closed page from Noon Ads campaign metrics enumeration. */
public final class AdvertisingCampaignPage {
    private final int pageNo;
    private final int totalPages;
    private final long declaredCampaignCount;
    private final List<AdvertisingStagedFact> facts;
    private final List<AdvertisingCampaignObservation> observations;
    private final List<String> businessSkippedFingerprints;

    public AdvertisingCampaignPage(
            int pageNo,
            int totalPages,
            long declaredCampaignCount,
            List<AdvertisingStagedFact> facts,
            List<AdvertisingCampaignObservation> observations,
            List<String> businessSkippedFingerprints
    ) {
        if (pageNo < 1 || totalPages < pageNo || declaredCampaignCount < 0L) {
            throw new IllegalArgumentException("advertising campaign page extent is invalid");
        }
        this.pageNo = pageNo;
        this.totalPages = totalPages;
        this.declaredCampaignCount = declaredCampaignCount;
        this.facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
        this.observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
        this.businessSkippedFingerprints = List.copyOf(Objects.requireNonNull(
                businessSkippedFingerprints,
                "businessSkippedFingerprints"
        ));
        if (this.observations.size() != this.facts.size()) {
            throw new IllegalArgumentException("every valid campaign fact needs one observation");
        }
    }

    public SnapshotPage<AdvertisingStagedFact> asTwoPassSnapshotPage() {
        boolean last = pageNo == totalPages;
        return SnapshotPage.twoPassRequired(
                pageNo,
                last ? null : Math.incrementExact(pageNo),
                last,
                totalPages,
                facts,
                Math.addExact(facts.size(), businessSkippedFingerprints.size()),
                businessSkippedFingerprints.size(),
                businessSkippedFingerprints
        );
    }

    public int getPageNo() { return pageNo; }
    public int getTotalPages() { return totalPages; }
    public long getDeclaredCampaignCount() { return declaredCampaignCount; }
    public List<AdvertisingStagedFact> getFacts() { return facts; }
    public List<AdvertisingCampaignObservation> getObservations() { return observations; }
    public int getBusinessSkippedItemCount() { return businessSkippedFingerprints.size(); }
    public int getSourceItemCount() {
        return Math.addExact(facts.size(), businessSkippedFingerprints.size());
    }
}
