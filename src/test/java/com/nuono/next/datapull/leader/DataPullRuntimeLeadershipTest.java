package com.nuono.next.datapull.leader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataPullRuntimeLeadershipTest {

    private static final Duration LEASE = Duration.ofSeconds(120);

    @Test
    void onlyOneJvmLeadsAndLiveRenewalKeepsItsEpoch() {
        MutableDatabaseLeaderStore database = new MutableDatabaseLeaderStore();
        DataPullRuntimeLeadership first = leadership(database, "jvm-a");
        DataPullRuntimeLeadership second = leadership(database, "jvm-b");

        DataPullRuntimeLeaderLease initial = first.acquireOrRenew().orElseThrow();

        assertFalse(second.acquireOrRenew().isPresent());
        assertEquals(initial.getEpoch(), first.acquireOrRenew().orElseThrow().getEpoch());
        assertTrue(first.isLeader());
        assertFalse(second.isLeader());
    }

    @Test
    void expiredLeaseAllowsTakeoverWithANewEpochAndRejectsTheOldToken() {
        MutableDatabaseLeaderStore database = new MutableDatabaseLeaderStore();
        DataPullRuntimeLeadership first = leadership(database, "jvm-a");
        DataPullRuntimeLeadership second = leadership(database, "jvm-b");
        DataPullRuntimeLeaderLease stale = first.acquireOrRenew().orElseThrow();

        database.advance(LEASE.plusSeconds(1));
        DataPullRuntimeLeaderLease current = second.acquireOrRenew().orElseThrow();

        assertEquals(stale.getEpoch() + 1L, current.getEpoch());
        assertFalse(first.isCurrent(stale));
        assertFalse(database.release(stale));
        assertTrue(second.isCurrent(current));
        assertFalse(first.acquireOrRenew().isPresent());
    }

    @Test
    void releaseIsAnOptimizationAndTheNextOwnerStillAdvancesEpoch() {
        MutableDatabaseLeaderStore database = new MutableDatabaseLeaderStore();
        DataPullRuntimeLeadership first = leadership(database, "jvm-a");
        DataPullRuntimeLeadership second = leadership(database, "jvm-b");
        long firstEpoch = first.acquireOrRenew().orElseThrow().getEpoch();

        first.releaseIfOwned();
        DataPullRuntimeLeaderLease takeover = second.acquireOrRenew().orElseThrow();

        assertEquals(firstEpoch + 1L, takeover.getEpoch());
        assertTrue(second.isLeader());
    }

    private DataPullRuntimeLeadership leadership(
            DataPullRuntimeLeaderStore store,
            String owner
    ) {
        return new DataPullRuntimeLeadership(store, owner, LEASE);
    }

    private static final class MutableDatabaseLeaderStore
            implements DataPullRuntimeLeaderStore {
        private LocalDateTime now = LocalDateTime.of(2026, 8, 3, 4, 0);
        private String owner;
        private long epoch;
        private LocalDateTime leaseUntil;

        @Override
        public synchronized Optional<DataPullRuntimeLeaderLease> acquireOrRenew(
                String candidate,
                Duration duration
        ) {
            boolean live = leaseUntil != null && leaseUntil.isAfter(now);
            if (live && !candidate.equals(owner)) return Optional.empty();
            if (!live || !candidate.equals(owner)) epoch++;
            owner = candidate;
            leaseUntil = now.plus(duration);
            return Optional.of(token());
        }

        @Override
        public synchronized boolean isCurrent(DataPullRuntimeLeaderLease lease) {
            return leaseUntil != null && leaseUntil.isAfter(now)
                    && lease.getOwner().equals(owner) && lease.getEpoch() == epoch;
        }

        @Override
        public synchronized boolean release(DataPullRuntimeLeaderLease lease) {
            if (!isCurrent(lease)) return false;
            owner = null;
            leaseUntil = null;
            return true;
        }

        void advance(Duration duration) { now = now.plus(duration); }

        private DataPullRuntimeLeaderLease token() {
            return new DataPullRuntimeLeaderLease(owner, epoch, leaseUntil, now);
        }
    }
}
