package com.nuono.next.intransit.autosync;

import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightSyncCommand;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FreightBillFetchResult {
    private boolean success;
    private boolean snapshotComplete;
    private String failureCode;
    private String failureMessage;
    private String revisionDigest;
    private String sourceUpdatedAt;
    private int sourceRowCount;
    private ActualFreightSyncCommand command;
    private List<String> issues = Collections.emptyList();

    public static FreightBillFetchResult success(
            ActualFreightSyncCommand command,
            boolean snapshotComplete,
            int sourceRowCount,
            String revisionDigest,
            List<String> issues
    ) {
        FreightBillFetchResult result = new FreightBillFetchResult();
        result.success = true;
        result.command = command;
        result.snapshotComplete = snapshotComplete;
        result.sourceRowCount = Math.max(0, sourceRowCount);
        result.revisionDigest = revisionDigest;
        result.issues = issues == null ? Collections.emptyList() : new ArrayList<>(issues);
        return result;
    }

    public static FreightBillFetchResult failure(String code, String message) {
        FreightBillFetchResult result = new FreightBillFetchResult();
        result.success = false;
        result.failureCode = code;
        result.failureMessage = message;
        return result;
    }

    public boolean isSuccess() { return success; }
    public boolean isSnapshotComplete() { return snapshotComplete; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public String getRevisionDigest() { return revisionDigest; }
    public String getSourceUpdatedAt() { return sourceUpdatedAt; }
    void setSourceUpdatedAt(String value) { this.sourceUpdatedAt = value; }
    public int getSourceRowCount() { return sourceRowCount; }
    public ActualFreightSyncCommand getCommand() { return command; }
    public List<String> getIssues() { return issues; }
}
