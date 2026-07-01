package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProductMasterWorkbenchView extends ProductMasterSnapshotView {

    private ProductMasterSnapshotView baselineSnapshot;

    private ProductMasterSnapshotView draftSnapshot;

    private String syncStatus;

    private String lastSyncedAt;

    private String note;

    private String currentZCode;

    private List<Map<String, Object>> recentActions = new ArrayList<>();

    private List<Map<String, Object>> keyContentHistory = new ArrayList<>();

    private Integer pendingKeyContentHistoryCount;

    private String pendingKeyContentHistoryVisibleAfter;

    private ProductListSummaryView listSummary;

    private Map<String, Object> publishConflict;

    private ProductPublishTaskView publishTask;

    public ProductMasterSnapshotView getBaselineSnapshot() {
        return baselineSnapshot;
    }

    public void setBaselineSnapshot(ProductMasterSnapshotView baselineSnapshot) {
        this.baselineSnapshot = baselineSnapshot;
    }

    public ProductMasterSnapshotView getDraftSnapshot() {
        return draftSnapshot;
    }

    public void setDraftSnapshot(ProductMasterSnapshotView draftSnapshot) {
        this.draftSnapshot = draftSnapshot;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(String lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCurrentZCode() {
        return currentZCode;
    }

    public void setCurrentZCode(String currentZCode) {
        this.currentZCode = currentZCode;
    }

    public List<Map<String, Object>> getRecentActions() {
        return recentActions;
    }

    public void setRecentActions(List<Map<String, Object>> recentActions) {
        this.recentActions = recentActions;
    }

    public List<Map<String, Object>> getKeyContentHistory() {
        return keyContentHistory;
    }

    public void setKeyContentHistory(List<Map<String, Object>> keyContentHistory) {
        this.keyContentHistory = keyContentHistory;
    }

    public Integer getPendingKeyContentHistoryCount() {
        return pendingKeyContentHistoryCount;
    }

    public void setPendingKeyContentHistoryCount(Integer pendingKeyContentHistoryCount) {
        this.pendingKeyContentHistoryCount = pendingKeyContentHistoryCount;
    }

    public String getPendingKeyContentHistoryVisibleAfter() {
        return pendingKeyContentHistoryVisibleAfter;
    }

    public void setPendingKeyContentHistoryVisibleAfter(String pendingKeyContentHistoryVisibleAfter) {
        this.pendingKeyContentHistoryVisibleAfter = pendingKeyContentHistoryVisibleAfter;
    }

    public ProductListSummaryView getListSummary() {
        return listSummary;
    }

    public void setListSummary(ProductListSummaryView listSummary) {
        this.listSummary = listSummary;
    }

    public Map<String, Object> getPublishConflict() {
        return publishConflict;
    }

    public void setPublishConflict(Map<String, Object> publishConflict) {
        this.publishConflict = publishConflict;
    }

    public ProductPublishTaskView getPublishTask() {
        return publishTask;
    }

    public void setPublishTask(ProductPublishTaskView publishTask) {
        this.publishTask = publishTask;
    }
}
