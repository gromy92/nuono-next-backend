package com.nuono.next.datapull.advertising;

import com.nuono.next.datapull.snapshot.SnapshotCollectionAuthority;
import java.time.LocalDateTime;
import java.util.Objects;

/** Durable authority produced only after two equal complete campaign enumerations. */
public final class AdvertisingCampaignEnumerationAuthority {
    private final SnapshotCollectionAuthority collectionAuthority;
    private final boolean complete;

    private AdvertisingCampaignEnumerationAuthority(
            SnapshotCollectionAuthority collectionAuthority,
            boolean complete
    ) {
        this.collectionAuthority = Objects.requireNonNull(
                collectionAuthority,
                "collectionAuthority"
        );
        if (collectionAuthority.getKind()
                != SnapshotCollectionAuthority.Kind.TWO_PASS_OBSERVATION
                || collectionAuthority.getProviderAsOfUtc() != null) {
            throw new IllegalArgumentException("campaign authority requires a two-pass observation");
        }
        this.complete = complete;
    }

    public static AdvertisingCampaignEnumerationAuthority fromTwoPassObservation(
            String observationDigestSha256,
            long declaredCampaignCount,
            boolean complete
    ) {
        return new AdvertisingCampaignEnumerationAuthority(
                SnapshotCollectionAuthority.fromTwoPassObservation(
                        observationDigestSha256,
                        declaredCampaignCount
                ),
                complete
        );
    }

    static AdvertisingCampaignEnumerationAuthority fromPersistedFields(
            String generationTokenSha256,
            long declaredCampaignCount,
            boolean complete
    ) {
        return new AdvertisingCampaignEnumerationAuthority(
                SnapshotCollectionAuthority.fromPersistedDigest(
                        SnapshotCollectionAuthority.Kind.TWO_PASS_OBSERVATION,
                        generationTokenSha256,
                        null,
                        declaredCampaignCount
                ),
                complete
        );
    }

    public String getGenerationTokenSha256() {
        return collectionAuthority.getGenerationTokenSha256();
    }

    public LocalDateTime getProviderAsOfUtc() {
        return collectionAuthority.getProviderAsOfUtc();
    }

    public long getDeclaredCampaignCount() {
        return collectionAuthority.getDeclaredCollectionCount();
    }

    public boolean isComplete() {
        return complete;
    }

    public SnapshotCollectionAuthority asSnapshotAuthority() {
        return collectionAuthority;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AdvertisingCampaignEnumerationAuthority)) {
            return false;
        }
        AdvertisingCampaignEnumerationAuthority value =
                (AdvertisingCampaignEnumerationAuthority) other;
        return complete == value.complete
                && collectionAuthority.equals(value.collectionAuthority);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collectionAuthority, complete);
    }
}
