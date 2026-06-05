package com.nuono.next.sales;

import java.time.LocalDate;
import java.util.List;

public interface SalesFactRepository {

    long saveBatch(SalesImportBatch batch);

    void upsert(DailySalesFact fact);

    List<DailySalesFact> list(SalesFactQuery query);

    default LocalDate findLatestFactDate(Long ownerUserId, String storeCode, String siteCode) {
        return null;
    }

    default void saveExceptions(long sourceBatchId, List<SalesImportExceptionRecord> records) {
    }

    default List<SalesImportBatchRecord> listImportBatches(SalesImportBatchQuery query) {
        return List.of();
    }

    default SalesImportBatchRecord findImportBatch(Long batchId) {
        return null;
    }

    default List<SalesImportExceptionRecord> listImportExceptions(Long batchId) {
        return List.of();
    }

    default void markSiteOffersNotListedForEmptyReport(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long updatedBy
    ) {
    }
}
