package com.nuono.next.datapull.advertising;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Current sealed DP-06 generation for one exact scope and report window. */
public final class AdvertisingGenerationHeadRow {
    private Long ownerUserId;
    private String projectCode;
    private String storeCode;
    private String siteCode;
    private LocalDate reportDate;
    private Long taskId;
    private Long batchId;
    private LocalDateTime scheduleSlot;

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long value) { ownerUserId = value; }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String value) { projectCode = value; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String value) { storeCode = value; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String value) { siteCode = value; }
    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate value) { reportDate = value; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long value) { batchId = value; }
    public LocalDateTime getScheduleSlot() { return scheduleSlot; }
    public void setScheduleSlot(LocalDateTime value) { scheduleSlot = value; }
}
