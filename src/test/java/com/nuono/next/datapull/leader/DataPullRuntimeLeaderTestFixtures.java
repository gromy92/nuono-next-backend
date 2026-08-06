package com.nuono.next.datapull.leader;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/** Small test seam for components that are not themselves testing election. */
public final class DataPullRuntimeLeaderTestFixtures {

    private DataPullRuntimeLeaderTestFixtures() { }

    public static DataPullRuntimeLeadership alwaysLeader(
            String owner,
            LocalDateTime databaseTime
    ) {
        return new DataPullRuntimeLeadership(
                new AlwaysLeaderStore(databaseTime),
                owner,
                Duration.ofSeconds(120)
        );
    }

    public static DataPullRuntimeLeaderLease lease(String owner, LocalDateTime databaseTime) {
        return new DataPullRuntimeLeaderLease(
                owner,
                1L,
                databaseTime.plusSeconds(120),
                databaseTime
        );
    }

    private static final class AlwaysLeaderStore implements DataPullRuntimeLeaderStore {
        private final LocalDateTime databaseTime;

        private AlwaysLeaderStore(LocalDateTime databaseTime) {
            this.databaseTime = databaseTime;
        }

        @Override
        public Optional<DataPullRuntimeLeaderLease> acquireOrRenew(
                String owner,
                Duration leaseDuration
        ) {
            return Optional.of(new DataPullRuntimeLeaderLease(
                    owner, 1L, databaseTime.plus(leaseDuration), databaseTime
            ));
        }

        @Override public boolean isCurrent(DataPullRuntimeLeaderLease lease) { return true; }
        @Override public boolean release(DataPullRuntimeLeaderLease lease) { return true; }
    }
}
