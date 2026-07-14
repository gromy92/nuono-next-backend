package com.nuono.next.web;

import java.util.Map;

public final class ApiProblemResponse {

    private final String code;
    private final String message;
    private final String category;
    private final String operation;
    private final boolean retryable;
    private final boolean partialSuccess;
    private final String reference;
    private final Map<String, Object> details;

    public ApiProblemResponse(ApiProblemException exception) {
        this.code = exception.getCode();
        this.message = exception.getMessage();
        this.category = exception.getCategory();
        this.operation = exception.getOperation();
        this.retryable = exception.isRetryable();
        this.partialSuccess = exception.isPartialSuccess();
        this.reference = exception.getReference();
        this.details = exception.getDetails();
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getCategory() {
        return category;
    }

    public String getOperation() {
        return operation;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isPartialSuccess() {
        return partialSuccess;
    }

    public String getReference() {
        return reference;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
