package com.nuono.next.product;

public class ProductMasterActionCommand extends ProductMasterFetchCommand {

    private String action;

    private String currentSiteCode;

    private ProductMasterSnapshotView snapshot;

    private String syncMergePolicy;

    private String publishConflictResolution;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getCurrentSiteCode() {
        return currentSiteCode;
    }

    public void setCurrentSiteCode(String currentSiteCode) {
        this.currentSiteCode = currentSiteCode;
    }

    public ProductMasterSnapshotView getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(ProductMasterSnapshotView snapshot) {
        this.snapshot = snapshot;
    }

    public String getSyncMergePolicy() {
        return syncMergePolicy;
    }

    public void setSyncMergePolicy(String syncMergePolicy) {
        this.syncMergePolicy = syncMergePolicy;
    }

    public String getPublishConflictResolution() {
        return publishConflictResolution;
    }

    public void setPublishConflictResolution(String publishConflictResolution) {
        this.publishConflictResolution = publishConflictResolution;
    }
}
