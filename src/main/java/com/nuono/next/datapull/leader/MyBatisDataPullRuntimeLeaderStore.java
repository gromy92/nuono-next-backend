package com.nuono.next.datapull.leader;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.infrastructure.mapper.DataPullRuntimeLeaderMapper;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** MyBatis Adapter whose lease decisions are based exclusively on MySQL time. */
public final class MyBatisDataPullRuntimeLeaderStore implements DataPullRuntimeLeaderStore {

    private final DataPullRuntimeLeaderMapper mapper;

    public MyBatisDataPullRuntimeLeaderStore(DataPullRuntimeLeaderMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public Optional<DataPullRuntimeLeaderLease> acquireOrRenew(
            String owner,
            Duration leaseDuration
    ) {
        String identity = DataPullRuntimeLeaderLease.requireOwner(owner);
        int seconds = Math.toIntExact(requirePositiveSeconds(leaseDuration));
        requireAtMostOne(mapper.acquireOrRenew(identity, seconds), "leader acquire");
        DataPullRuntimeLeaderRow row = mapper.selectOwnedLive(identity);
        return row == null ? Optional.empty() : Optional.of(row.toLease());
    }

    @Override
    public boolean isCurrent(DataPullRuntimeLeaderLease lease) {
        Objects.requireNonNull(lease, "lease");
        int count = mapper.countCurrent(lease.getOwner(), lease.getEpoch());
        if (count < 0 || count > 1) {
            throw new IllegalStateException("leader validation returned an invalid row count");
        }
        return count == 1;
    }

    @Override
    public boolean release(DataPullRuntimeLeaderLease lease) {
        Objects.requireNonNull(lease, "lease");
        int changed = mapper.release(lease.getOwner(), lease.getEpoch());
        requireAtMostOne(changed, "leader release");
        return changed == 1;
    }

    private static long requirePositiveSeconds(Duration value) {
        Duration duration = Objects.requireNonNull(value, "leaseDuration");
        if (duration.isZero() || duration.isNegative() || duration.getNano() != 0) {
            throw new IllegalArgumentException("leader lease duration must be positive whole seconds");
        }
        return duration.getSeconds();
    }

    private static void requireAtMostOne(int changed, String action) {
        if (changed < 0 || changed > 1) {
            throw new IllegalStateException(action + " affected an invalid number of rows: " + changed);
        }
    }
}
