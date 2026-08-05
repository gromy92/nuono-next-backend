package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.LocalDateTime;
import java.util.Objects;

/** One authoritative live-fence predicate shared by staging and fact commits. */
public final class Ali1688Dp10FenceGuard {
    private Ali1688Dp10FenceGuard() {
    }

    public static void requireLive(
            DataPullTask task,
            Ali1688Dp10TaskFenceRow row,
            LocalDateTime nowUtc
    ) {
        if (task == null
                || row == null
                || nowUtc == null
                || !Objects.equals(row.getId(), task.getId())
                || row.getOperationCode() != OperationCode.DP10
                || task.getOperationCode() != OperationCode.DP10
                || row.getState() != TaskState.RUNNING
                || task.getFenceEpoch() == null
                || task.getFenceEpoch() <= 0L
                || task.getVersion() == null
                || !Objects.equals(row.getOwnerUserId(), task.getOwnerUserId())
                || !Objects.equals(row.getAccountKey(), task.getAccountKey())
                || !Objects.equals(row.getScopeKey(), task.getScopeKey())
                || !Objects.equals(row.getLeaseOwner(), task.getLeaseOwner())
                || !Objects.equals(row.getFenceEpoch(), task.getFenceEpoch())
                || !Objects.equals(row.getVersion(), task.getVersion())
                || row.getLeaseUntil() == null
                || !row.getLeaseUntil().isAfter(nowUtc)) {
            throw new IllegalStateException("DP10_TASK_FENCE_STALE");
        }
    }
}
