package com.nuono.next.productanalysis;

import java.time.LocalDate;

public class ProductLifecycleAnalysisRecalculationView {

    private final Long jobId;
    private final String status;
    private final String message;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate anchorDate;
    private final int processedCount;
    private final int changedCount;
    private final int heldCount;
    private final int dataInsufficientCount;

    public ProductLifecycleAnalysisRecalculationView(
            Long jobId,
            String status,
            String message,
            String storeCode,
            String siteCode,
            LocalDate anchorDate,
            int processedCount,
            int changedCount,
            int heldCount,
            int dataInsufficientCount
    ) {
        this.jobId = jobId;
        this.status = status;
        this.message = message;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.anchorDate = anchorDate;
        this.processedCount = processedCount;
        this.changedCount = changedCount;
        this.heldCount = heldCount;
        this.dataInsufficientCount = dataInsufficientCount;
    }

    public Long getJobId() {
        return jobId;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public LocalDate getAnchorDate() {
        return anchorDate;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public int getChangedCount() {
        return changedCount;
    }

    public int getHeldCount() {
        return heldCount;
    }

    public int getDataInsufficientCount() {
        return dataInsufficientCount;
    }
}
