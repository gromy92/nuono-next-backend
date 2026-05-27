package com.nuono.next.noonsync;

import com.nuono.next.sales.SalesSyncTaskRecord;

public class NoonSalesSyncRunResult {

    private final NoonSyncTask foundationTask;
    private final SalesSyncTaskRecord salesTask;

    public NoonSalesSyncRunResult(NoonSyncTask foundationTask, SalesSyncTaskRecord salesTask) {
        this.foundationTask = foundationTask;
        this.salesTask = salesTask;
    }

    public NoonSyncTask getFoundationTask() {
        return foundationTask;
    }

    public SalesSyncTaskRecord getSalesTask() {
        return salesTask;
    }
}
