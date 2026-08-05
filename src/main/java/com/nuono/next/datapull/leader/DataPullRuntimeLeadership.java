package com.nuono.next.datapull.leader;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Process-local control surface backed by the database owner/epoch lease. */
public final class DataPullRuntimeLeadership {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataPullRuntimeLeadership.class);

    private final DataPullRuntimeLeaderStore store;
    private final String owner;
    private final Duration leaseDuration;
    private final AtomicReference<DataPullRuntimeLeaderLease> current = new AtomicReference<>();

    public DataPullRuntimeLeadership(
            DataPullRuntimeLeaderStore store,
            String owner,
            Duration leaseDuration
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.owner = DataPullRuntimeLeaderLease.requireOwner(owner);
        this.leaseDuration = requirePositiveSeconds(leaseDuration);
    }

    public Optional<DataPullRuntimeLeaderLease> acquireOrRenew() {
        try {
            Optional<DataPullRuntimeLeaderLease> acquired = store.acquireOrRenew(
                    owner,
                    leaseDuration
            );
            current.set(acquired.orElse(null));
            return acquired;
        } catch (RuntimeException failure) {
            current.set(null);
            throw failure;
        }
    }

    public boolean isLeader() {
        DataPullRuntimeLeaderLease lease = current.get();
        return lease != null && isCurrent(lease);
    }

    public boolean isCurrent(DataPullRuntimeLeaderLease lease) {
        DataPullRuntimeLeaderLease expected = Objects.requireNonNull(lease, "lease");
        DataPullRuntimeLeaderLease local = current.get();
        if (local == null || local.getEpoch() != expected.getEpoch()
                || !local.getOwner().equals(expected.getOwner())) {
            return false;
        }
        boolean valid = store.isCurrent(expected);
        if (!valid) {
            current.compareAndSet(local, null);
        }
        return valid;
    }

    public void releaseIfOwned() {
        DataPullRuntimeLeaderLease lease = current.getAndSet(null);
        if (lease == null) return;
        try {
            store.release(lease);
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "DP leader release failed; database lease expiry remains the safety boundary errorType={}",
                    failure.getClass().getSimpleName()
            );
        }
    }

    public String owner() { return owner; }

    private static Duration requirePositiveSeconds(Duration value) {
        Duration duration = Objects.requireNonNull(value, "leaseDuration");
        if (duration.isZero() || duration.isNegative() || duration.getNano() != 0) {
            throw new IllegalArgumentException("leader lease duration must be positive whole seconds");
        }
        if (duration.getSeconds() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("leader lease duration exceeds database bound");
        }
        return duration;
    }
}
