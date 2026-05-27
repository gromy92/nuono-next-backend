package com.nuono.next.salesforecast;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class SalesForecastRunStatusView {

    private final Long runId;
    private final String status;
    private final String failureReason;
    private final LocalDate sourceDataDate;
    private final LocalDateTime calculatedAt;
    private final int resultCount;

    public SalesForecastRunStatusView(
            Long runId,
            String status,
            String failureReason,
            LocalDate sourceDataDate,
            LocalDateTime calculatedAt,
            int resultCount
    ) {
        this.runId = runId;
        this.status = status;
        this.failureReason = failureReason;
        this.sourceDataDate = sourceDataDate;
        this.calculatedAt = calculatedAt;
        this.resultCount = resultCount;
    }

    public static SalesForecastRunStatusView succeeded(SalesForecastRunRecord run) {
        return new SalesForecastRunStatusView(
                run.getId(),
                run.getStatus(),
                null,
                run.getSourceDataDate(),
                run.getCalculatedAt(),
                run.getResultCount()
        );
    }

    public static SalesForecastRunStatusView failed(String failureReason) {
        return new SalesForecastRunStatusView(null, "failed", failureReason, null, null, 0);
    }

    public Long getRunId() {
        return runId;
    }

    public String getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDate getSourceDataDate() {
        return sourceDataDate;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public int getResultCount() {
        return resultCount;
    }
}
