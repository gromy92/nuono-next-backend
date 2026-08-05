package com.nuono.next.competitoranalysis.noon;

import java.time.Duration;

public class NoonSearchProviderException extends RuntimeException {
    private final String errorCode;
    private final Integer providerHttpStatus;
    private final String sourceUrl;
    private final String responseHash;
    private final Duration retryAfter;

    public NoonSearchProviderException(
            String errorCode,
            String message,
            Integer providerHttpStatus,
            String sourceUrl,
            String responseHash
    ) {
        this(errorCode, message, providerHttpStatus, sourceUrl, responseHash, null);
    }

    public NoonSearchProviderException(
            String errorCode,
            String message,
            Integer providerHttpStatus,
            String sourceUrl,
            String responseHash,
            Duration retryAfter
    ) {
        super(message);
        this.errorCode = errorCode;
        this.providerHttpStatus = providerHttpStatus;
        this.sourceUrl = sourceUrl;
        this.responseHash = responseHash;
        this.retryAfter = retryAfter == null || retryAfter.isNegative() ? null : retryAfter;
    }

    public String getErrorCode() { return errorCode; }
    public Integer getProviderHttpStatus() { return providerHttpStatus; }
    public String getSourceUrl() { return sourceUrl; }
    public String getResponseHash() { return responseHash; }
    public Duration getRetryAfter() { return retryAfter; }
}
