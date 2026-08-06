package com.nuono.next.datapull.runtime;

/** Technical execution state only; no business-readiness state belongs here. */
public enum TaskState {
    QUEUED,
    RUNNING,
    WAITING_REMOTE,
    WAITING_BACKOFF,
    WAITING_AUTH,
    SUCCEEDED,
    FAILED,
    /** Terminal only when an atomic catch-up compaction replaces a never-started task. */
    SUPERSEDED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == SUPERSEDED;
    }
}
