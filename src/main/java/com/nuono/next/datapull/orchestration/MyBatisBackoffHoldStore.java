package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.RiskShareLevel;
import com.nuono.next.datapull.runtime.SanitizedCode;
import com.nuono.next.infrastructure.mapper.DataPullBackoffHoldMapper;
import java.time.LocalDateTime;
import java.util.Objects;

/** Production Adapter for monotonic, durable provider holds. */
public final class MyBatisBackoffHoldStore implements BackoffHoldStore {

    private final DataPullBackoffHoldMapper mapper;

    public MyBatisBackoffHoldStore(DataPullBackoffHoldMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public void record(
            RiskShareLevel shareLevel,
            DataPullBackoffIdentity identity,
            LocalDateTime blockedUntilUtc,
            String sanitizedCode,
            LocalDateTime observedAtUtc
    ) {
        LocalDateTime observedAt = Objects.requireNonNull(observedAtUtc, "observedAtUtc");
        LocalDateTime blockedUntil = Objects.requireNonNull(blockedUntilUtc, "blockedUntilUtc");
        if (blockedUntil.isBefore(observedAt)) {
            throw new IllegalArgumentException("blockedUntilUtc must not be before observedAtUtc");
        }
        SanitizedCode.require(sanitizedCode);
        int changed = mapper.upsert(DataPullBackoffHoldRow.from(
                shareLevel,
                Objects.requireNonNull(identity, "identity"),
                blockedUntil,
                sanitizedCode,
                observedAt
        ));
        if (changed < 0 || changed > 2) {
            throw new IllegalStateException("backoff hold upsert affected an invalid row count: " + changed);
        }
    }

    @Override
    public boolean isHeld(
            RiskShareLevel shareLevel,
            DataPullBackoffIdentity identity,
            LocalDateTime nowUtc
    ) {
        LocalDateTime now = Objects.requireNonNull(nowUtc, "nowUtc");
        LocalDateTime blockedUntil = mapper.selectBlockedUntil(
                DataPullBackoffHoldKey.from(shareLevel, identity)
        );
        return blockedUntil != null && blockedUntil.isAfter(now);
    }
}
