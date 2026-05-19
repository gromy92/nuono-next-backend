package com.nuono.next.store;

import java.util.ArrayList;
import java.util.List;

public class StoreSyncOverview {

    private String mode;

    private boolean ready;

    private String message;

    private Long selectedOwnerId;

    private StoreSyncSummary summary;

    private List<StoreSyncOwnerOption> ownerOptions = new ArrayList<>();

    private List<StoreSyncStoreView> stores = new ArrayList<>();

    private List<String> syncedRules = new ArrayList<>();

    private List<String> missingCoreTables = new ArrayList<>();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getSelectedOwnerId() {
        return selectedOwnerId;
    }

    public void setSelectedOwnerId(Long selectedOwnerId) {
        this.selectedOwnerId = selectedOwnerId;
    }

    public StoreSyncSummary getSummary() {
        return summary;
    }

    public void setSummary(StoreSyncSummary summary) {
        this.summary = summary;
    }

    public List<StoreSyncOwnerOption> getOwnerOptions() {
        return ownerOptions;
    }

    public void setOwnerOptions(List<StoreSyncOwnerOption> ownerOptions) {
        this.ownerOptions = ownerOptions;
    }

    public List<StoreSyncStoreView> getStores() {
        return stores;
    }

    public void setStores(List<StoreSyncStoreView> stores) {
        this.stores = stores;
    }

    public List<String> getSyncedRules() {
        return syncedRules;
    }

    public void setSyncedRules(List<String> syncedRules) {
        this.syncedRules = syncedRules;
    }

    public List<String> getMissingCoreTables() {
        return missingCoreTables;
    }

    public void setMissingCoreTables(List<String> missingCoreTables) {
        this.missingCoreTables = missingCoreTables;
    }
}
