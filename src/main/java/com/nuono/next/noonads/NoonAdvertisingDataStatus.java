package com.nuono.next.noonads;

import java.time.LocalDate;

public class NoonAdvertisingDataStatus {
    private long batchCount;
    private long campaignRowCount;
    private long queryRowCount;
    private LocalDate earliestReportDate;
    private LocalDate latestReportDate;
    private boolean dataAvailable;

    public NoonAdvertisingDataStatus() {
    }

    public NoonAdvertisingDataStatus(
            long batchCount,
            long campaignRowCount,
            long queryRowCount,
            LocalDate earliestReportDate,
            LocalDate latestReportDate,
            boolean dataAvailable
    ) {
        this.batchCount = batchCount;
        this.campaignRowCount = campaignRowCount;
        this.queryRowCount = queryRowCount;
        this.earliestReportDate = earliestReportDate;
        this.latestReportDate = latestReportDate;
        this.dataAvailable = dataAvailable;
    }

    public long getBatchCount() { return batchCount; }
    public void setBatchCount(long batchCount) { this.batchCount = batchCount; }
    public long getCampaignRowCount() { return campaignRowCount; }
    public void setCampaignRowCount(long campaignRowCount) { this.campaignRowCount = campaignRowCount; }
    public long getQueryRowCount() { return queryRowCount; }
    public void setQueryRowCount(long queryRowCount) { this.queryRowCount = queryRowCount; }
    public LocalDate getEarliestReportDate() { return earliestReportDate; }
    public void setEarliestReportDate(LocalDate earliestReportDate) { this.earliestReportDate = earliestReportDate; }
    public LocalDate getLatestReportDate() { return latestReportDate; }
    public void setLatestReportDate(LocalDate latestReportDate) { this.latestReportDate = latestReportDate; }
    public boolean isDataAvailable() { return dataAvailable; }
    public void setDataAvailable(boolean dataAvailable) { this.dataAvailable = dataAvailable; }
}
