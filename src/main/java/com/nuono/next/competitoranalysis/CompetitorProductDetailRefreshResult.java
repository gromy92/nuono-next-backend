package com.nuono.next.competitoranalysis;

import org.springframework.util.StringUtils;

public class CompetitorProductDetailRefreshResult {
    private int attemptedCount;
    private int succeededCount;
    private int failedCount;
    private String firstErrorCode;
    private String firstErrorMessage;
    private String riskErrorCode;
    private String riskErrorMessage;

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
        if (!StringUtils.hasText(riskErrorCode) && isRiskBackoffFailure(errorCode)) {
            riskErrorCode = errorCode;
            riskErrorMessage = errorMessage;
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

    public boolean hasRiskBackoffFailure() {
        return StringUtils.hasText(riskErrorCode);
    }

    public String getRiskErrorCode() {
        return riskErrorCode;
    }

    public String getRiskErrorMessage() {
        return riskErrorMessage;
    }

    private boolean isRiskBackoffFailure(String errorCode) {
        return "RATE_LIMITED".equalsIgnoreCase(errorCode)
                || "BLOCKED_BY_RISK_CONTROL".equalsIgnoreCase(errorCode)
                || "CAPTCHA_REQUIRED".equalsIgnoreCase(errorCode);
    }
}
