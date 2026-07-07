package com.nuono.next.postsaleprofit;

import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.RecalculationRunRow;

public class PostSaleProfitLatestRunView {
    private final boolean available;
    private final Long runId;
    private final String storeCode;
    private final String siteCode;
    private final String dateFrom;
    private final String dateTo;
    private final String status;
    private final Integer orderLineCount;
    private final Integer missingIssueCount;
    private final String finishedAt;

    private PostSaleProfitLatestRunView(
            boolean available,
            Long runId,
            String storeCode,
            String siteCode,
            String dateFrom,
            String dateTo,
            String status,
            Integer orderLineCount,
            Integer missingIssueCount,
            String finishedAt
    ) {
        this.available = available;
        this.runId = runId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.status = status;
        this.orderLineCount = orderLineCount;
        this.missingIssueCount = missingIssueCount;
        this.finishedAt = finishedAt;
    }

    public static PostSaleProfitLatestRunView unavailable() {
        return new PostSaleProfitLatestRunView(false, null, null, null, null, null, null, null, null, null);
    }

    public static PostSaleProfitLatestRunView from(RecalculationRunRow row) {
        if (row == null || row.dateFrom == null || row.dateTo == null) {
            return unavailable();
        }
        return new PostSaleProfitLatestRunView(
                true,
                row.id,
                row.storeCode,
                row.siteCode,
                row.dateFrom.toString(),
                row.dateTo.toString(),
                row.status,
                row.orderLineCount,
                row.missingIssueCount,
                row.finishedAt == null ? null : row.finishedAt.toString()
        );
    }

    public boolean isAvailable() { return available; }
    public Long getRunId() { return runId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public String getDateFrom() { return dateFrom; }
    public String getDateTo() { return dateTo; }
    public String getStatus() { return status; }
    public Integer getOrderLineCount() { return orderLineCount; }
    public Integer getMissingIssueCount() { return missingIssueCount; }
    public String getFinishedAt() { return finishedAt; }
}
