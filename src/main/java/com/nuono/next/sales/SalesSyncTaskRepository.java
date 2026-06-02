package com.nuono.next.sales;

import java.util.Optional;

public interface SalesSyncTaskRepository {

    SalesSyncTaskRecord createQueued(SalesSyncTaskCommand command);

    Optional<SalesSyncTaskRecord> findReusableExportTask(SalesSyncTaskCommand command);

    SalesSyncTaskRecord markRunning(Long taskId);

    SalesSyncTaskRecord markExportStatus(Long taskId, NoonSalesReportExportStatus status);

    SalesSyncTaskRecord markSucceeded(Long taskId, NoonSalesCsvImportResult result);

    SalesSyncTaskRecord markFailed(Long taskId, String failureReason);

    SalesSyncTaskRecord findById(Long taskId);
}
