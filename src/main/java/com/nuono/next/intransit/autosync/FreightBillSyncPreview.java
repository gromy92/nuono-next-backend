package com.nuono.next.intransit.autosync;

import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightSyncCommand;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FreightBillSyncPreview {
    private boolean committable;
    private String revisionDigest;
    private int billCount;
    private int componentCount;
    private int createCount;
    private int updateCount;
    private int unchangedCount;
    private ActualFreightSyncCommand changedCommand;
    private List<Issue> issues = Collections.emptyList();

    public boolean isCommittable() { return committable; }
    public void setCommittable(boolean value) { this.committable = value; }
    public String getRevisionDigest() { return revisionDigest; }
    public void setRevisionDigest(String value) { this.revisionDigest = value; }
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
    public ActualFreightSyncCommand getChangedCommand() { return changedCommand; }
    public void setChangedCommand(ActualFreightSyncCommand value) { this.changedCommand = value; }
    public List<Issue> getIssues() { return issues; }
    public void setIssues(List<Issue> value) { this.issues = value == null ? Collections.emptyList() : new ArrayList<>(value); }

    public static class Issue {
        private final String code;
        private final String billNo;
        private final String batchReferenceNo;
        private final String message;

        public Issue(String code, String billNo, String batchReferenceNo, String message) {
            this.code = code;
            this.billNo = billNo;
            this.batchReferenceNo = batchReferenceNo;
            this.message = message;
        }

        public String getCode() { return code; }
        public String getBillNo() { return billNo; }
        public String getBatchReferenceNo() { return batchReferenceNo; }
        public String getMessage() { return message; }
    }
}
