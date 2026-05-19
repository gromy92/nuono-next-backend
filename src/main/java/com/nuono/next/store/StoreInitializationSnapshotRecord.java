package com.nuono.next.store;

import java.time.LocalDateTime;

public class StoreInitializationSnapshotRecord {

    private Long id;
    private Long ownerUserId;
    private String storeCode;
    private String projectCode;
    private String projectName;
    private String status;
    private LocalDateTime lastInitializedAt;
    private String snapshotJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastInitializedAt() {
        return lastInitializedAt;
    }

    public void setLastInitializedAt(LocalDateTime lastInitializedAt) {
        this.lastInitializedAt = lastInitializedAt;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }
}
