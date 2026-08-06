package com.nuono.next.datapull.persistence;

import java.util.Objects;

/** One immutable task proposal plus optional never-started compaction policy. */
public final class DataPullTaskBatchProposal {
    private final DataPullTask task;
    private final DataPullTaskCatchUpMode catchUpMode;

    public DataPullTaskBatchProposal(DataPullTask task, DataPullTaskCatchUpMode catchUpMode) {
        this.task = Objects.requireNonNull(task, "task");
        this.catchUpMode = catchUpMode;
    }

    public DataPullTask getTask() { return task; }
    public DataPullTaskCatchUpMode getCatchUpMode() { return catchUpMode; }
}
