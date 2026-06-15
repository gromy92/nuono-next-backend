package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ProductWorkbenchRecord {

    private ProductMasterSnapshotView baselineSnapshot;

    private ProductMasterSnapshotView draftSnapshot;

    private String syncStatus;

    private String lastSyncedAt;

    private String note;

    private List<Map<String, Object>> recentActions = new ArrayList<>();

    private List<Map<String, Object>> keyContentHistory = new ArrayList<>();

    private Integer pendingKeyContentHistoryCount;

    private String pendingKeyContentHistoryVisibleAfter;

    private ProductPublishTaskView publishTask;

    ProductMasterSnapshotView getBaselineSnapshot() {
        return baselineSnapshot;
    }

    void setBaselineSnapshot(ProductMasterSnapshotView baselineSnapshot) {
        this.baselineSnapshot = baselineSnapshot;
    }

    ProductMasterSnapshotView getDraftSnapshot() {
        return draftSnapshot;
    }

    void setDraftSnapshot(ProductMasterSnapshotView draftSnapshot) {
        this.draftSnapshot = draftSnapshot;
    }

    String getSyncStatus() {
        return syncStatus;
    }

    void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    String getLastSyncedAt() {
        return lastSyncedAt;
    }

    void setLastSyncedAt(String lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    String getNote() {
        return note;
    }

    void setNote(String note) {
        this.note = note;
    }

    List<Map<String, Object>> getRecentActions() {
        return recentActions;
    }

    void setRecentActions(List<Map<String, Object>> recentActions) {
        this.recentActions = recentActions == null ? new ArrayList<>() : recentActions;
    }

    List<Map<String, Object>> getKeyContentHistory() {
        return keyContentHistory;
    }

    void setKeyContentHistory(List<Map<String, Object>> keyContentHistory) {
        this.keyContentHistory = keyContentHistory == null ? new ArrayList<>() : keyContentHistory;
    }

    Integer getPendingKeyContentHistoryCount() {
        return pendingKeyContentHistoryCount;
    }

    void setPendingKeyContentHistoryCount(Integer pendingKeyContentHistoryCount) {
        this.pendingKeyContentHistoryCount = pendingKeyContentHistoryCount;
    }

    String getPendingKeyContentHistoryVisibleAfter() {
        return pendingKeyContentHistoryVisibleAfter;
    }

    void setPendingKeyContentHistoryVisibleAfter(String pendingKeyContentHistoryVisibleAfter) {
        this.pendingKeyContentHistoryVisibleAfter = pendingKeyContentHistoryVisibleAfter;
    }

    ProductPublishTaskView getPublishTask() {
        return publishTask;
    }

    void setPublishTask(ProductPublishTaskView publishTask) {
        this.publishTask = publishTask;
    }
}
