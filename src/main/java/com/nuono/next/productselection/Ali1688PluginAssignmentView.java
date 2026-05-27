package com.nuono.next.productselection;

public class Ali1688PluginAssignmentView {

    public String assignmentId;
    public String assignmentCode;
    public String taskId;
    public String sourceCollectionId;
    public String taskNo;
    public String status;
    public String sourceImageUrl;
    public String sourceTitle;
    public String sourceTitleCn;
    public String sourceUrl;
    public String pageUrl;
    public String storeId;
    public String storeName;
    public String storeCode;
    public String createdAt;
    public String expiresAt;
    public String startedAt;
    public String finishedAt;
    public String failureCode;
    public String failureMessage;
    public Integer submittedCandidateCount;
    public Integer acceptedCandidateCount;
    public Integer rejectedCandidateCount;
    public Boolean current;
    public String message;

    @Override
    public String toString() {
        return "Ali1688PluginAssignmentView{"
                + "assignmentId='" + assignmentId + '\''
                + ", taskId='" + taskId + '\''
                + ", sourceCollectionId='" + sourceCollectionId + '\''
                + ", taskNo='" + taskNo + '\''
                + ", status='" + status + '\''
                + ", expiresAt='" + expiresAt + '\''
                + ", current=" + current
                + '}';
    }
}
