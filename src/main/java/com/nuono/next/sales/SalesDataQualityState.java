package com.nuono.next.sales;

public class SalesDataQualityState {

    private final String code;
    private final String message;
    private final Long sourceBatchId;

    public SalesDataQualityState(String code, String message, Long sourceBatchId) {
        this.code = code;
        this.message = message;
        this.sourceBatchId = sourceBatchId;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Long getSourceBatchId() {
        return sourceBatchId;
    }
}
