package com.nuono.next.procurement.aliorder;

import java.time.Duration;

/** One detail call reduced to success, structured absence, or sanitized failure. */
public class Ali1688HistoricalOrderDetailResult {
    private final Ali1688HistoricalOrderProvider.DetailStatus status;
    private final Ali1688HistoricalOrderProvider.OrderSnapshot order;
    private final String failureCode;
    private final Duration retryAfter;

    protected Ali1688HistoricalOrderDetailResult(
            Ali1688HistoricalOrderProvider.DetailStatus status,
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            String failureCode,
            Duration retryAfter
    ) {
        this.status = status;
        this.order = order;
        this.failureCode = failureCode;
        this.retryAfter = retryAfter;
    }

    public Ali1688HistoricalOrderProvider.DetailStatus getStatus() { return status; }
    public Ali1688HistoricalOrderProvider.OrderSnapshot getOrder() { return order; }
    public String getFailureCode() { return failureCode; }
    public Duration getRetryAfter() { return retryAfter; }
}
