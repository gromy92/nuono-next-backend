package com.nuono.next.competitoranalysis.noon;

public class NoonSearchProviderException extends RuntimeException {
    private final String errorCode;
    private final Integer providerHttpStatus;
    private final String sourceUrl;
    private final String responseHash;

    public NoonSearchProviderException(
            String errorCode,
            String message,
            Integer providerHttpStatus,
            String sourceUrl,
            String responseHash
    ) {
        super(message);
        this.errorCode = errorCode;
        this.providerHttpStatus = providerHttpStatus;
        this.sourceUrl = sourceUrl;
        this.responseHash = responseHash;
    }

    public String getErrorCode() { return errorCode; }
    public Integer getProviderHttpStatus() { return providerHttpStatus; }
    public String getSourceUrl() { return sourceUrl; }
    public String getResponseHash() { return responseHash; }
}
