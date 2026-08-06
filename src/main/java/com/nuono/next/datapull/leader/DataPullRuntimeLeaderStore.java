package com.nuono.next.datapull.leader;

import java.time.Duration;
import java.util.Optional;

/** Persistence Interface for the singleton DP scheduler leader lease. */
public interface DataPullRuntimeLeaderStore {

    Optional<DataPullRuntimeLeaderLease> acquireOrRenew(String owner, Duration leaseDuration);

    boolean isCurrent(DataPullRuntimeLeaderLease lease);

    boolean release(DataPullRuntimeLeaderLease lease);
}
