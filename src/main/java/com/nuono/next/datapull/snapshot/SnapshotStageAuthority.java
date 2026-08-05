package com.nuono.next.datapull.snapshot;

import java.time.LocalDateTime;
import java.util.Objects;

/** Fail-closed merge and restore rules for a staged provider collection identity. */
final class SnapshotStageAuthority {
    private SnapshotStageAuthority() {
    }

    static Decision merge(
            SnapshotStageAggregateRow aggregate,
            SnapshotStagePageCandidate<?> page,
            Integer maxStagedPage
    ) {
        SnapshotCollectionAuthority persisted;
        try {
            persisted = restore(aggregate);
        } catch (RuntimeException invalid) {
            return Decision.reject("SNAPSHOT_AUTHORITY_STATE_INVALID");
        }
        SnapshotCollectionAuthority incoming = page.getAuthority();
        if (persisted == null) {
            if (incoming == null) {
                return Decision.accept(null);
            }
            if (page.getPageNo() != 1 || maxStagedPage != null) {
                return Decision.reject("SNAPSHOT_AUTHORITY_LATE_BINDING");
            }
            return Decision.accept(incoming);
        }
        if (incoming == null) {
            return Decision.reject("SNAPSHOT_AUTHORITY_MISSING");
        }
        if (persisted.getKind() != incoming.getKind()) {
            return Decision.reject("SNAPSHOT_AUTHORITY_KIND_DRIFT");
        }
        if (!persisted.getGenerationTokenSha256().equals(
                incoming.getGenerationTokenSha256()
        )) {
            return Decision.reject("SNAPSHOT_AUTHORITY_GENERATION_DRIFT");
        }
        if (persisted.getDeclaredCollectionCount()
                != incoming.getDeclaredCollectionCount()) {
            return Decision.reject("SNAPSHOT_AUTHORITY_EXTENT_DRIFT");
        }
        if (!Objects.equals(
                persisted.getProviderAsOfUtc(), incoming.getProviderAsOfUtc()
        )) {
            return Decision.reject("SNAPSHOT_AUTHORITY_AS_OF_DRIFT");
        }
        return Decision.accept(persisted);
    }

    static SnapshotCollectionAuthority restore(SnapshotStageAggregateRow aggregate) {
        Objects.requireNonNull(aggregate, "aggregate");
        String kind = aggregate.getAuthorityKind();
        String digest = aggregate.getAuthorityTokenSha256();
        Long count = aggregate.getDeclaredCollectionCount();
        LocalDateTime asOf = aggregate.getSnapshotAsOfUtc();
        if (kind == null && digest == null && count == null && asOf == null) {
            return null;
        }
        if (kind == null || digest == null || count == null) {
            throw new IllegalStateException("partial snapshot authority envelope");
        }
        return SnapshotCollectionAuthority.fromPersistedDigest(
                SnapshotCollectionAuthority.Kind.valueOf(kind),
                digest,
                asOf,
                count
        );
    }

    static final class Decision {
        private final String rejectionCode;
        private final SnapshotCollectionAuthority authority;

        private Decision(String rejectionCode, SnapshotCollectionAuthority authority) {
            this.rejectionCode = rejectionCode;
            this.authority = authority;
        }

        static Decision reject(String code) {
            return new Decision(Objects.requireNonNull(code, "code"), null);
        }

        static Decision accept(SnapshotCollectionAuthority authority) {
            return new Decision(null, authority);
        }

        boolean isAccepted() {
            return rejectionCode == null;
        }

        String getRejectionCode() {
            return rejectionCode;
        }

        SnapshotCollectionAuthority getAuthority() {
            return authority;
        }
    }
}
