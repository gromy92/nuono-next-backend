package com.nuono.next.intransit.autosync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FreightBillAutoSyncTaskSummary {
    private Long accountId;
    private Long ownerUserId;
    private Long taskId;
    private String sourceSystem;
    private String status;
    private String failureCode;
    private String failureMessage;
    private String revisionDigest;
    private String sourceUpdatedAt;
    private int sourceRowCount;
    private int billCount;
    private int componentCount;
    private int createCount;
    private int updateCount;
    private int unchangedCount;
    private List<FreightBillSyncPreview.Issue> issues = Collections.emptyList();

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long value) { this.accountId = value; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long value) { this.ownerUserId = value; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { this.taskId = value; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String value) { this.sourceSystem = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String value) { this.failureCode = value; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String value) { this.failureMessage = value; }
    public String getRevisionDigest() { return revisionDigest; }
    public void setRevisionDigest(String value) { this.revisionDigest = value; }
    public String getSourceUpdatedAt() { return sourceUpdatedAt; }
    public void setSourceUpdatedAt(String value) { this.sourceUpdatedAt = value; }
    public int getSourceRowCount() { return sourceRowCount; }
    public void setSourceRowCount(int value) { this.sourceRowCount = value; }
    public int getBillCount() { return billCount; }
    public void setBillCount(int value) { this.billCount = value; }
    public int getComponentCount() { return componentCount; }
    public void setComponentCount(int value) { this.componentCount = value; }
    public int getCreateCount() { return createCount; }
    public void setCreateCount(int value) { this.createCount = value; }
    public int getUpdateCount() { return updateCount; }
    public void setUpdateCount(int value) { this.updateCount = value; }
    public int getUnchangedCount() { return unchangedCount; }
    public void setUnchangedCount(int value) { this.unchangedCount = value; }
    public List<FreightBillSyncPreview.Issue> getIssues() { return issues; }
    public void setIssues(List<FreightBillSyncPreview.Issue> value) { this.issues = value == null ? Collections.emptyList() : new ArrayList<>(value); }
}
