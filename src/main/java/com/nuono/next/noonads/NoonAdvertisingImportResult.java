package com.nuono.next.noonads;

import java.time.LocalDate;

public class NoonAdvertisingImportResult {
    private final Long batchId;
    private final int campaignRowCount;
    private final int queryRowCount;
    private final LocalDate reportDateFrom;
    private final LocalDate reportDateTo;

    public NoonAdvertisingImportResult(
            Long batchId,
            int campaignRowCount,
            int queryRowCount,
            LocalDate reportDateFrom,
            LocalDate reportDateTo
    ) {
        this.batchId = batchId;
        this.campaignRowCount = campaignRowCount;
        this.queryRowCount = queryRowCount;
        this.reportDateFrom = reportDateFrom;
        this.reportDateTo = reportDateTo;
    }

    public Long getBatchId() { return batchId; }
    public int getCampaignRowCount() { return campaignRowCount; }
    public int getQueryRowCount() { return queryRowCount; }
    public LocalDate getReportDateFrom() { return reportDateFrom; }
    public LocalDate getReportDateTo() { return reportDateTo; }
}
