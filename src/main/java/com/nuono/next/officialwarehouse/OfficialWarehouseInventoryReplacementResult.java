package com.nuono.next.officialwarehouse;

/** Persisted row counts and identity returned by one inventory replacement transaction. */
public final class OfficialWarehouseInventoryReplacementResult {

    public final long syncBatchId;
    public final String storeCode;
    public final String siteCode;
    public final int pageCount;
    public final int fetchedRows;
    public final int insertedRows;
    public final String syncedAt;

    OfficialWarehouseInventoryReplacementResult(
            long syncBatchId,
            String storeCode,
            String siteCode,
            int pageCount,
            int fetchedRows,
            int insertedRows,
            String syncedAt
    ) {
        this.syncBatchId = syncBatchId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.pageCount = pageCount;
        this.fetchedRows = fetchedRows;
        this.insertedRows = insertedRows;
        this.syncedAt = syncedAt;
    }
}
