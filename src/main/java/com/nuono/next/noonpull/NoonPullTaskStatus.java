package com.nuono.next.noonpull;

public enum NoonPullTaskStatus {
    QUEUED,
    RUNNING,
    BLOCKED_AUTH,
    SUCCEEDED,
    FAILED,
    PARTIAL,
    SKIPPED,
    CANCELLED
}
