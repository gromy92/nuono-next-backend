package com.nuono.next.competitoranalysis;

import org.springframework.util.StringUtils;

public class CompetitorProductDetailRefreshResult {
    private int attemptedCount;
    private int succeededCount;
    private int failedCount;
    private String firstErrorCode;
    private String firstErrorMessage;

    public static CompetitorProductDetailRefreshResult empty() {
        return new CompetitorProductDetailRefreshResult();
    }

    static CompetitorProductDetailRefreshResult unavailable(String errorCode, String errorMessage) {
        CompetitorProductDetailRefreshResult result = new CompetitorProductDetailRefreshResult();
        result.attemptedCount = 1;
        result.recordFailure(errorCode, errorMessage);
        return result;
    }

    void recordAttempt() {
        attemptedCount++;
    }

    void recordSuccess() {
        succeededCount++;
    }

    void recordFailure(String errorCode, String errorMessage) {
        failedCount++;
        if (!StringUtils.hasText(firstErrorCode)) {
            firstErrorCode = errorCode;
        }
        if (!StringUtils.hasText(firstErrorMessage)) {
            firstErrorMessage = errorMessage;
        }
    }

    public int getAttemptedCount() {
        return attemptedCount;
    }

    public int getSucceededCount() {
        return succeededCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public String getFirstErrorCode() {
        return firstErrorCode;
    }

    public String getFirstErrorMessage() {
        return firstErrorMessage;
    }
}
