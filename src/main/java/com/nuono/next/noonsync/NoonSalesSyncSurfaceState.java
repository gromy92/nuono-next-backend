package com.nuono.next.noonsync;

public enum NoonSalesSyncSurfaceState {
    READY,
    SYNC_IN_PROGRESS,
    NO_DATA_BACKFILL_REQUIRED,
    BACKFILL_RUNNING,
    BACKFILL_FAILED,
    EMPTY_REPORT,
    MISSING_MAPPING,
    STALE_LATEST_SALES
}
