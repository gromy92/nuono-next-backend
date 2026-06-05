package com.nuono.next.orderfinance;

import java.time.LocalDate;

public class OrderFinanceSyncResult {
    private final Long taskId;
    private final String status;
    private final String sourceBatchId;
    private final int importedCount;
    private final int exceptionCount;
    private final String message;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final boolean skipped;

    public OrderFinanceSyncResult(
            Long taskId,
            String status,
            String sourceBatchId,
            int importedCount,
            int exceptionCount,
            String message
    ) {
        this(taskId, status, sourceBatchId, importedCount, exceptionCount, message, null, null, false);
    }

    public OrderFinanceSyncResult(
            Long taskId,
            String status,
            String sourceBatchId,
            int importedCount,
            int exceptionCount,
            String message,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean skipped
    ) {
        this.taskId = taskId;
        this.status = status;
        this.sourceBatchId = sourceBatchId;
        this.importedCount = importedCount;
        this.exceptionCount = exceptionCount;
        this.message = message;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.skipped = skipped;
    }

    public Long getTaskId() { return taskId; }
    public String getStatus() { return status; }
    public String getSourceBatchId() { return sourceBatchId; }
    public int getImportedCount() { return importedCount; }
    public int getExceptionCount() { return exceptionCount; }
    public String getMessage() { return message; }
    public LocalDate getDateFrom() { return dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public boolean isSkipped() { return skipped; }
}
