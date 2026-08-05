package com.nuono.next.datapull.snapshot;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;

/** Shared task-envelope validation kept outside the snapshot state machine. */
final class SnapshotEngineTaskGuard {
    private SnapshotEngineTaskGuard() {
    }

    static String validate(DataPullTask task, OperationCode operationCode) {
        if (task == null) return "SNAPSHOT_TASK_REQUIRED";
        if (task.getOperationCode() != operationCode) return "SNAPSHOT_OPERATION_MISMATCH";
        if (task.getState() != TaskState.RUNNING) return "SNAPSHOT_TASK_NOT_RUNNING";
        if (task.getId() == null || task.getId() < 1L
                || task.getFenceEpoch() == null || task.getFenceEpoch() < 1L) {
            return "SNAPSHOT_TASK_FENCE_INVALID";
        }
        if (!stable(task.getProviderChannel())
                || !stable(task.getAccountKey())
                || !stable(task.getScopeKey())
                || !stable(task.getBusinessWindowKey())) {
            return "SNAPSHOT_TASK_CONTEXT_INVALID";
        }
        return null;
    }

    private static boolean stable(String value) {
        return value != null && !value.isEmpty() && value.equals(value.trim());
    }
}
