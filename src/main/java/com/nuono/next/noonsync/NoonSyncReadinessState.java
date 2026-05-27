package com.nuono.next.noonsync;

public enum NoonSyncReadinessState {
    NO_SYNC_NEEDED,
    INITIALIZATION_NEEDED,
    BACKFILL_NEEDED,
    BLOCKED
}
