package com.nuono.next.procurement.aliorder;

import java.time.Duration;

/** Sanitized outcome of one token-refresh HTTP call. */
public final class Ali1688HistoricalOrderAuthorizationRefreshResult {
    private final boolean success;
    private final String failureCode;
    private final Duration retryAfter;

    private Ali1688HistoricalOrderAuthorizationRefreshResult(
            boolean success,
            String failureCode,
            Duration retryAfter
    ) {
        this.success = success;
        this.failureCode = failureCode;
        this.retryAfter = retryAfter;
    }

    public static Ali1688HistoricalOrderAuthorizationRefreshResult success() {
        return new Ali1688HistoricalOrderAuthorizationRefreshResult(true, null, null);
    }

    public static Ali1688HistoricalOrderAuthorizationRefreshResult failure(
            Ali1688HistoricalOrderFailureCode code,
            Duration retryAfter
    ) {
        if (code == null) {
            throw new IllegalArgumentException("refresh failure code is required");
        }
        return new Ali1688HistoricalOrderAuthorizationRefreshResult(
                false,
                code.getCode(),
                retryAfter
        );
    }

    public boolean isSuccess() { return success; }
    public String getFailureCode() { return failureCode; }
    public Duration getRetryAfter() { return retryAfter; }
}
