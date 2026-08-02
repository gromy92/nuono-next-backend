package com.nuono.next.sales;

public interface SalesSyncTaskRepository {

    SalesSyncTaskRecord createQueued(SalesSyncTaskCommand command);

    boolean claimRunning(Long taskId);

    SalesSyncTaskRecord markSucceeded(Long taskId, NoonSalesCsvImportResult result);

    SalesSyncTaskRecord markFailed(Long taskId, String failureReason);

    SalesSyncTaskRecord markWaitingForAuthorization(Long taskId, Long recoveryId);

    java.util.List<SalesSyncTaskRecord> listQueued(int limit);

    SalesSyncTaskRecord findById(Long taskId);
}
