package com.nuono.next.sales;

public interface SalesSyncTaskRepository {

    SalesSyncTaskRecord createQueued(SalesSyncTaskCommand command);

    SalesSyncTaskRecord markRunning(Long taskId);

    SalesSyncTaskRecord markSucceeded(Long taskId, NoonSalesCsvImportResult result);

    SalesSyncTaskRecord markFailed(Long taskId, String failureReason);

    SalesSyncTaskRecord findById(Long taskId);
}
