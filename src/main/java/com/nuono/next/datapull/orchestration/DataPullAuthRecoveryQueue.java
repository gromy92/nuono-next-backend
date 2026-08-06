package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.persistence.DataPullTask;
import java.time.LocalDateTime;

/** Binds one fenced WAITING_AUTH task to its provider-specific recovery workflow. */
public interface DataPullAuthRecoveryQueue {

    void enqueue(DataPullTask task, long waitingTaskVersion, LocalDateTime committedAtUtc);

    static DataPullAuthRecoveryQueue noop() {
        return (task, waitingTaskVersion, committedAtUtc) -> { };
    }
}
