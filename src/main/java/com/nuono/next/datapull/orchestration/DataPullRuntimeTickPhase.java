package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderLease;
import java.time.Instant;

/** Immutable output of the leadership/reconciliation scheduler phase. */
final class DataPullRuntimeTickPhase {
    private final DataPullRuntimeLeaderLease lease;
    private final Instant now;
    private final int reconciled;

    private DataPullRuntimeTickPhase(
            DataPullRuntimeLeaderLease lease,
            Instant now,
            int reconciled
    ) {
        this.lease = lease;
        this.now = now;
        this.reconciled = reconciled;
    }

    static DataPullRuntimeTickPhase noLeadership() {
        return new DataPullRuntimeTickPhase(null, null, 0);
    }

    static DataPullRuntimeTickPhase active(
            DataPullRuntimeLeaderLease lease,
            Instant now,
            int reconciled
    ) {
        return new DataPullRuntimeTickPhase(lease, now, reconciled);
    }

    boolean hasLeadership() { return lease != null; }
    DataPullRuntimeLeaderLease lease() { return lease; }
    Instant now() { return now; }
    int reconciled() { return reconciled; }
}
