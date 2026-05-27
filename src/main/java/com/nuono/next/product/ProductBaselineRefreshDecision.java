package com.nuono.next.product;

public class ProductBaselineRefreshDecision {

    private final ProductMasterSnapshotView baselineSnapshot;
    private final ProductMasterSnapshotView draftSnapshot;
    private final String syncStatus;
    private final String note;

    public ProductBaselineRefreshDecision(
            ProductMasterSnapshotView baselineSnapshot,
            ProductMasterSnapshotView draftSnapshot,
            String syncStatus,
            String note
    ) {
        this.baselineSnapshot = baselineSnapshot;
        this.draftSnapshot = draftSnapshot;
        this.syncStatus = syncStatus;
        this.note = note;
    }

    public ProductMasterSnapshotView getBaselineSnapshot() {
        return baselineSnapshot;
    }

    public ProductMasterSnapshotView getDraftSnapshot() {
        return draftSnapshot;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public String getNote() {
        return note;
    }
}
