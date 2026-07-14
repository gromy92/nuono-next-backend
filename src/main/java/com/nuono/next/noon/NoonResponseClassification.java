package com.nuono.next.noon;

import java.util.List;

public final class NoonResponseClassification {

    private final String code;
    private final NoonFailureCategory category;
    private final String operation;
    private final String userMessage;
    private final int apiStatus;
    private final int providerStatus;
    private final boolean retryable;
    private final List<String> affectedPskuCodes;

    public NoonResponseClassification(
            String code,
            NoonFailureCategory category,
            String operation,
            String userMessage,
            int apiStatus,
            int providerStatus,
            boolean retryable,
            List<String> affectedPskuCodes
    ) {
        this.code = code;
        this.category = category;
        this.operation = operation;
        this.userMessage = userMessage;
        this.apiStatus = apiStatus;
        this.providerStatus = providerStatus;
        this.retryable = retryable;
        this.affectedPskuCodes = affectedPskuCodes == null ? List.of() : List.copyOf(affectedPskuCodes);
    }

    public String getCode() {
        return code;
    }

    public NoonFailureCategory getCategory() {
        return category;
    }

    public String getOperation() {
        return operation;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public int getApiStatus() {
        return apiStatus;
    }

    public int getProviderStatus() {
        return providerStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public List<String> getAffectedPskuCodes() {
        return affectedPskuCodes;
    }
}
