package com.nuono.next.datapull.orchestration;

import com.nuono.next.infrastructure.mapper.DataPullEmergencyClaimHoldMapper;
import java.time.LocalDateTime;
import java.util.Objects;

/** Production Adapter for durable, expiring emergency claim holds. */
public final class MyBatisEmergencyClaimHoldStore implements EmergencyClaimHoldStore {

    private final DataPullEmergencyClaimHoldMapper mapper;

    public MyBatisEmergencyClaimHoldStore(DataPullEmergencyClaimHoldMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public void place(EmergencyClaimHold hold) {
        EmergencyClaimHold candidate = Objects.requireNonNull(hold, "hold");
        candidate.validateForPlacement();
        int changed = mapper.upsert(candidate);
        if (changed < 1 || changed > 2) {
            throw new IllegalStateException(
                    "emergency claim hold upsert affected an invalid row count: " + changed
            );
        }
    }

    @Override
    public EmergencyClaimHoldSnapshot activeAt(LocalDateTime nowUtc) {
        LocalDateTime now = Objects.requireNonNull(nowUtc, "nowUtc");
        return EmergencyClaimHoldSnapshot.from(mapper.selectActive(now), now);
    }
}
