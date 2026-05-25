package com.nuono.next.nooncompleteness;

public enum NoonDataHistoryStatus {
    UNKNOWN,
    NOT_REQUIRED,
    INCOMPLETE,
    BACKFILL_RUNNING,
    COMPLETE,
    CONFIRMED_EMPTY,
    PROVIDER_RETENTION_LIMIT,
    FAILED,
    PAUSED,
    NOT_INTEGRATED
}

