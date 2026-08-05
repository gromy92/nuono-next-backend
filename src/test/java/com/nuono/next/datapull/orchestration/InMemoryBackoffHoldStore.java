package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.RiskShareLevel;
import com.nuono.next.datapull.runtime.SanitizedCode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Test-only Adapter with monotonic hold extension. */
public final class InMemoryBackoffHoldStore implements BackoffHoldStore {

    private final Map<String, LocalDateTime> blockedUntilByKey = new HashMap<>();

    @Override
    public synchronized void record(
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
        String key = DataPullBackoffHoldKey.from(shareLevel, identity);
        blockedUntilByKey.merge(
                key,
                blockedUntil,
                (existing, candidate) -> existing.isAfter(candidate) ? existing : candidate
        );
    }

    @Override
    public synchronized boolean isHeld(
            RiskShareLevel shareLevel,
            DataPullBackoffIdentity identity,
            LocalDateTime nowUtc
    ) {
        LocalDateTime now = Objects.requireNonNull(nowUtc, "nowUtc");
        LocalDateTime blockedUntil = blockedUntilByKey.get(
                DataPullBackoffHoldKey.from(shareLevel, identity)
        );
        return blockedUntil != null && blockedUntil.isAfter(now);
    }
}
