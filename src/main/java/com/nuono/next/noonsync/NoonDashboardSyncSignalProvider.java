package com.nuono.next.noonsync;

import org.springframework.stereotype.Service;

@Service
public class NoonDashboardSyncSignalProvider {

    private final NoonBusinessSyncStatusService syncStatusService;

    public NoonDashboardSyncSignalProvider(NoonBusinessSyncStatusService syncStatusService) {
        this.syncStatusService = syncStatusService;
    }

    public NoonDashboardSyncSignal salesSignal(NoonSalesSurfaceSyncInput input) {
        NoonSalesSyncReadModel status = syncStatusService.describeSalesSurface(input);
        if (status.getState() == NoonSalesSyncSurfaceState.SYNC_IN_PROGRESS
                || status.getState() == NoonSalesSyncSurfaceState.BACKFILL_RUNNING) {
            return new NoonDashboardSyncSignal(NoonDashboardSyncSignalState.SYNC_IN_PROGRESS, status);
        }
        if (!status.isDataSufficient()) {
            return new NoonDashboardSyncSignal(NoonDashboardSyncSignalState.DATA_INSUFFICIENT, status);
        }
        return new NoonDashboardSyncSignal(NoonDashboardSyncSignalState.READY, status);
    }
}
