package com.nuono.next.nooncompleteness;

public enum NoonDataGapStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    WAITING_RETRY,
    PENDING_CONFIRMATION,
    CONFIRMED_EMPTY,
    SKIPPED,
    PAUSED,
    CANCELLED,
    PROVIDER_RETENTION_LIMIT
}

