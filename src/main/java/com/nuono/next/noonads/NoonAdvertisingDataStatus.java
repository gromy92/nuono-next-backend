package com.nuono.next.noonads;

import java.time.LocalDate;

public class NoonAdvertisingDataStatus {
    private int batchCount;
    private int campaignRowCount;
    private int queryRowCount;
    private LocalDate earliestReportDate;
    private LocalDate latestReportDate;
    private boolean dataAvailable;

    public NoonAdvertisingDataStatus() {
    }

    public NoonAdvertisingDataStatus(
            int batchCount,
            int campaignRowCount,
            int queryRowCount,
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

    public int getBatchCount() { return batchCount; }
    public void setBatchCount(int batchCount) { this.batchCount = batchCount; }
    public int getCampaignRowCount() { return campaignRowCount; }
    public void setCampaignRowCount(int campaignRowCount) { this.campaignRowCount = campaignRowCount; }
    public int getQueryRowCount() { return queryRowCount; }
    public void setQueryRowCount(int queryRowCount) { this.queryRowCount = queryRowCount; }
    public LocalDate getEarliestReportDate() { return earliestReportDate; }
    public void setEarliestReportDate(LocalDate earliestReportDate) { this.earliestReportDate = earliestReportDate; }
    public LocalDate getLatestReportDate() { return latestReportDate; }
    public void setLatestReportDate(LocalDate latestReportDate) { this.latestReportDate = latestReportDate; }
    public boolean isDataAvailable() { return dataAvailable; }
    public void setDataAvailable(boolean dataAvailable) { this.dataAvailable = dataAvailable; }
}
