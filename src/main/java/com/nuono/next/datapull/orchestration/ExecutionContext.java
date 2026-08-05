package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.LocalDateTime;
import java.util.Objects;

/** One already-claimed task epoch and its UTC execution time. */
public final class ExecutionContext {

    private final DataPullTask task;
    private final DataPullScope scope;
    private final LocalDateTime nowUtc;

    public ExecutionContext(DataPullTask task, LocalDateTime nowUtc) {
        this.task = Objects.requireNonNull(task, "task");
        this.scope = DataPullScope.fromTaskSnapshot(task);
        this.nowUtc = Objects.requireNonNull(nowUtc, "nowUtc");
        if (task.getState() != TaskState.RUNNING) {
            throw new IllegalArgumentException("execution context requires a RUNNING task");
        }
    }

    public DataPullTask getTask() {
        return task;
    }

    public DataPullScope getScope() {
        return scope;
    }

    public LocalDateTime getNowUtc() {
        return nowUtc;
    }
}
