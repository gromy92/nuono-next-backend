package com.nuono.next.datapull.advertising;

import com.nuono.next.datapull.snapshot.SnapshotCollectionAuthority;
import java.time.LocalDateTime;
import java.util.Objects;

/** Provider-native proof that one dashboard enumerated the whole campaign collection. */
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
        if (collectionAuthority.getProviderAsOfUtc() == null) {
            throw new IllegalArgumentException("campaign authority requires provider as-of time");
        }
        this.complete = complete;
    }

    public static AdvertisingCampaignEnumerationAuthority fromProviderFields(
            String providerGenerationToken,
            LocalDateTime providerAsOfUtc,
            long declaredCampaignCount,
            boolean complete
    ) {
        return new AdvertisingCampaignEnumerationAuthority(
                SnapshotCollectionAuthority.fromProviderToken(
                        SnapshotCollectionAuthority.Kind.COMPLETE_EXPORT,
                        providerGenerationToken,
                        Objects.requireNonNull(providerAsOfUtc, "providerAsOfUtc"),
                        declaredCampaignCount
                ),
                complete
        );
    }

    static AdvertisingCampaignEnumerationAuthority fromPersistedFields(
            String generationTokenSha256,
            LocalDateTime providerAsOfUtc,
            long declaredCampaignCount,
            boolean complete
    ) {
        return new AdvertisingCampaignEnumerationAuthority(
                SnapshotCollectionAuthority.fromPersistedDigest(
                        SnapshotCollectionAuthority.Kind.COMPLETE_EXPORT,
                        generationTokenSha256,
                        Objects.requireNonNull(providerAsOfUtc, "providerAsOfUtc"),
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
