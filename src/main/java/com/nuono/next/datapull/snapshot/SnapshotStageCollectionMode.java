package com.nuono.next.datapull.snapshot;

/** Fail-closed binding between a staged generation and its authority strategy. */
final class SnapshotStageCollectionMode {
    private SnapshotStageCollectionMode() {
    }

    static String validate(
            SnapshotStageAggregateRow aggregate,
            SnapshotStagePageCandidate<?> page,
            Integer maxStagedPage
    ) {
        SnapshotPage.AuthorityMode incoming = page.getAuthorityMode();
        SnapshotPage.AuthorityMode persisted = restore(aggregate.getCollectionMode());
        if (persisted == null && (page.getPageNo() != 1 || maxStagedPage != null)) {
            return "SNAPSHOT_AUTHORITY_MODE_LATE_BINDING";
        }
        if (persisted != null && persisted != incoming) {
            return "SNAPSHOT_AUTHORITY_MODE_DRIFT";
        }
        if (incoming == SnapshotPage.AuthorityMode.PROVIDER_AUTHORITY
                && page.getAuthority() == null) {
            return "SNAPSHOT_AUTHORITY_MISSING";
        }
        if (incoming == SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED
                && page.getAuthority() != null) {
            return "SNAPSHOT_TWO_PASS_AUTHORITY_CONFLICT";
        }
        return null;
    }

    private static SnapshotPage.AuthorityMode restore(String value) {
        if (value == null) return null;
        try {
            return SnapshotPage.AuthorityMode.valueOf(value);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("invalid persisted snapshot authority mode", invalid);
        }
    }
}
