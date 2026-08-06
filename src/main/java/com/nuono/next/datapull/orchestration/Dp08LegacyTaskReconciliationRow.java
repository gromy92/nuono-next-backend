package com.nuono.next.datapull.orchestration;

/** One active legacy DP08 task or search-run row from a single read-only snapshot. */
public final class Dp08LegacyTaskReconciliationRow {
    private String recordKind;
    private Long recordId;
    private Long taskId;
    private String taskType;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private String naturalKey;
    private String status;
    private String payloadJson;
    private Long watchProductId;
    private String triggerMode;

    public String getRecordKind() {
        return recordKind;
    }

    public void setRecordKind(String recordKind) {
        this.recordKind = recordKind;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
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

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public void setNaturalKey(String naturalKey) {
        this.naturalKey = naturalKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Long getWatchProductId() {
        return watchProductId;
    }

    public void setWatchProductId(Long watchProductId) {
        this.watchProductId = watchProductId;
    }

    public String getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(String triggerMode) {
        this.triggerMode = triggerMode;
    }
}
