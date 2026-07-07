package com.nuono.next.postsaleprofit;

public class PostSaleProfitRecalculationView {
    private final Long runId;
    private final String status;
    private final int saleCandidateCount;
    private final int batchCandidateCount;
    private final int missingIssueCount;
    private final java.util.List<PostSaleProfitBatchRowView> rows;

    public PostSaleProfitRecalculationView(
            Long runId,
            String status,
            int saleCandidateCount,
            int batchCandidateCount,
            int missingIssueCount
    ) {
        this(runId, status, saleCandidateCount, batchCandidateCount, missingIssueCount, java.util.List.of());
    }

    public PostSaleProfitRecalculationView(
            Long runId,
            String status,
            int saleCandidateCount,
            int batchCandidateCount,
            int missingIssueCount,
            java.util.List<PostSaleProfitBatchRowView> rows
    ) {
        this.runId = runId;
        this.status = status;
        this.saleCandidateCount = saleCandidateCount;
        this.batchCandidateCount = batchCandidateCount;
        this.missingIssueCount = missingIssueCount;
        this.rows = rows == null ? java.util.List.of() : java.util.List.copyOf(rows);
    }

    public Long getRunId() { return runId; }
    public String getStatus() { return status; }
    public int getSaleCandidateCount() { return saleCandidateCount; }
    public int getBatchCandidateCount() { return batchCandidateCount; }
    public int getMissingIssueCount() { return missingIssueCount; }
    public java.util.List<PostSaleProfitBatchRowView> getRows() { return rows; }
}
