package com.nuono.next.noonads;

public interface NoonAdvertisingImportRepository {
    Long nextReportBatchId();

    Long nextCampaignFactId();

    Long nextQueryFactId();

    Long findReportBatchId(NoonAdvertisingReportBatch batch);

    void insertReportBatch(NoonAdvertisingReportBatch batch);

    void upsertCampaignFact(NoonAdvertisingCampaignFact fact);

    void upsertQueryFact(NoonAdvertisingQueryFact fact);
}
