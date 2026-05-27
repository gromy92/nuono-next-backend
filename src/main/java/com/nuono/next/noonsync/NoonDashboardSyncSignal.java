package com.nuono.next.noonsync;

public class NoonDashboardSyncSignal {

    private final NoonDashboardSyncSignalState state;
    private final NoonSalesSyncReadModel salesSyncStatus;

    public NoonDashboardSyncSignal(
            NoonDashboardSyncSignalState state,
            NoonSalesSyncReadModel salesSyncStatus
    ) {
        this.state = state;
        this.salesSyncStatus = salesSyncStatus;
    }

    public NoonDashboardSyncSignalState getState() {
        return state;
    }

    public NoonSalesSyncReadModel getSalesSyncStatus() {
        return salesSyncStatus;
    }
}
