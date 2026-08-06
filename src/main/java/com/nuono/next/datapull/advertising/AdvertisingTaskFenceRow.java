package com.nuono.next.datapull.advertising;

import java.time.LocalDateTime;

/** Locked runtime task evidence read inside the DP-06 fact transaction. */
public class AdvertisingTaskFenceRow {
    private Long taskId;
    private String operationCode;
    private Long ownerUserId;
    private String projectCode;
    private String storeCode;
    private String siteCode;
    private String businessWindowKey;
    private LocalDateTime scheduleSlot;
    private Long fenceEpoch;
    private String state;
    private String leaseOwner;
    private Boolean leaseValid;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getOperationCode() { return operationCode; }
    public void setOperationCode(String value) { operationCode = value; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long value) { ownerUserId = value; }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String value) { projectCode = value; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String value) { storeCode = value; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String value) { siteCode = value; }
    public String getBusinessWindowKey() { return businessWindowKey; }
    public void setBusinessWindowKey(String value) { businessWindowKey = value; }
    public LocalDateTime getScheduleSlot() { return scheduleSlot; }
    public void setScheduleSlot(LocalDateTime value) { scheduleSlot = value; }
    public Long getFenceEpoch() { return fenceEpoch; }
    public void setFenceEpoch(Long fenceEpoch) { this.fenceEpoch = fenceEpoch; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public Boolean getLeaseValid() { return leaseValid; }
    public void setLeaseValid(Boolean leaseValid) { this.leaseValid = leaseValid; }
}
