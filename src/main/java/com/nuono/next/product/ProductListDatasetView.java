package com.nuono.next.product;

import com.nuono.next.store.LocalDbStoreInitializationService;
import java.util.ArrayList;
import java.util.List;

public class ProductListDatasetView {

    private boolean ready;
    private String source;
    private String message;
    private List<String> warnings = new ArrayList<>();
    private Long ownerUserId;
    private String projectName;
    private String projectCode;
    private String storeCode;
    private String initializationStatus;
    private String initializationMessage;
    private String lastInitializedAt;
    private String lastDatasetSyncedAt;
    private Integer totalItems;
    private Integer syncedCount;
    private Integer draftCount;
    private Integer conflictCount;
    private Integer failedCount;
    private Integer liveCount;
    private Integer groupedCount;
    private Integer pendingPriceCount;
    private Integer historyReadyCount;
    private List<LocalDbStoreInitializationService.StoreInitializationProductListItemView> items = new ArrayList<>();

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getInitializationStatus() {
        return initializationStatus;
    }

    public void setInitializationStatus(String initializationStatus) {
        this.initializationStatus = initializationStatus;
    }

    public String getInitializationMessage() {
        return initializationMessage;
    }

    public void setInitializationMessage(String initializationMessage) {
        this.initializationMessage = initializationMessage;
    }

    public String getLastInitializedAt() {
        return lastInitializedAt;
    }

    public void setLastInitializedAt(String lastInitializedAt) {
        this.lastInitializedAt = lastInitializedAt;
    }

    public String getLastDatasetSyncedAt() {
        return lastDatasetSyncedAt;
    }

    public void setLastDatasetSyncedAt(String lastDatasetSyncedAt) {
        this.lastDatasetSyncedAt = lastDatasetSyncedAt;
    }

    public Integer getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
    }

    public Integer getSyncedCount() {
        return syncedCount;
    }

    public void setSyncedCount(Integer syncedCount) {
        this.syncedCount = syncedCount;
    }

    public Integer getDraftCount() {
        return draftCount;
    }

    public void setDraftCount(Integer draftCount) {
        this.draftCount = draftCount;
    }

    public Integer getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(Integer conflictCount) {
        this.conflictCount = conflictCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public Integer getLiveCount() {
        return liveCount;
    }

    public void setLiveCount(Integer liveCount) {
        this.liveCount = liveCount;
    }

    public Integer getGroupedCount() {
        return groupedCount;
    }

    public void setGroupedCount(Integer groupedCount) {
        this.groupedCount = groupedCount;
    }

    public Integer getPendingPriceCount() {
        return pendingPriceCount;
    }

    public void setPendingPriceCount(Integer pendingPriceCount) {
        this.pendingPriceCount = pendingPriceCount;
    }

    public Integer getHistoryReadyCount() {
        return historyReadyCount;
    }

    public void setHistoryReadyCount(Integer historyReadyCount) {
        this.historyReadyCount = historyReadyCount;
    }

    public List<LocalDbStoreInitializationService.StoreInitializationProductListItemView> getItems() {
        return items;
    }

    public void setItems(List<LocalDbStoreInitializationService.StoreInitializationProductListItemView> items) {
        this.items = items;
    }
}
