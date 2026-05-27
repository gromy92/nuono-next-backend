package com.nuono.next.sales;

public class SalesImportExceptionRecord {

    private final Long id;
    private final Long sourceBatchId;
    private final String sourceFilename;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final int rowNumber;
    private final String exceptionType;
    private final String fieldName;
    private final String sourceValue;
    private final String sourceContext;
    private final String message;
    private final String resolutionHint;

    public SalesImportExceptionRecord(
            Long id,
            Long sourceBatchId,
            String sourceFilename,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            int rowNumber,
            String exceptionType,
            String fieldName,
            String sourceValue,
            String sourceContext,
            String message,
            String resolutionHint
    ) {
        this.id = id;
        this.sourceBatchId = sourceBatchId;
        this.sourceFilename = sourceFilename;
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.rowNumber = rowNumber;
        this.exceptionType = exceptionType;
        this.fieldName = fieldName;
        this.sourceValue = sourceValue;
        this.sourceContext = sourceContext;
        this.message = message;
        this.resolutionHint = resolutionHint;
    }

    public SalesImportExceptionRecord withBatchContext(long batchId, NoonSalesCsvImportCommand command) {
        return new SalesImportExceptionRecord(
                id,
                batchId,
                sourceFilename,
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                rowNumber,
                exceptionType,
                fieldName,
                sourceValue,
                sourceContext,
                message,
                resolutionHint
        );
    }

    public Long getId() {
        return id;
    }

    public Long getSourceBatchId() {
        return sourceBatchId;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getSourceValue() {
        return sourceValue;
    }

    public String getSourceContext() {
        return sourceContext;
    }

    public String getMessage() {
        return message;
    }

    public String getResolutionHint() {
        return resolutionHint;
    }
}
