package com.nuono.next.datapull.orchestration;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Test-only Adapter mirroring durable monotonic hold placement. */
public final class InMemoryEmergencyClaimHoldStore implements EmergencyClaimHoldStore {

    private final Map<String, EmergencyClaimHold> holdsByKey = new HashMap<>();

    @Override
    public synchronized void place(EmergencyClaimHold hold) {
        EmergencyClaimHold candidate = Objects.requireNonNull(hold, "hold");
        candidate.validateForPlacement();
        EmergencyClaimHold existing = holdsByKey.get(candidate.getHoldKey());
        if (existing == null) {
            holdsByKey.put(
                    candidate.getHoldKey(),
                    candidate.persistedCopy(
                            candidate.getBlockedUntil(),
                            candidate.getSanitizedReason(),
                            0L,
                            candidate.getUpdatedAt()
                    )
            );
            return;
        }
        boolean extendsHold = !candidate.getBlockedUntil().isBefore(existing.getBlockedUntil());
        LocalDateTime blockedUntil = extendsHold
                ? candidate.getBlockedUntil()
                : existing.getBlockedUntil();
        String reason = extendsHold
                ? candidate.getSanitizedReason()
                : existing.getSanitizedReason();
        LocalDateTime updatedAt = candidate.getUpdatedAt().isAfter(existing.getUpdatedAt())
                ? candidate.getUpdatedAt()
                : existing.getUpdatedAt();
        holdsByKey.put(
                candidate.getHoldKey(),
                candidate.persistedCopy(
                        blockedUntil,
                        reason,
                        Math.addExact(existing.getVersion(), 1L),
                        updatedAt
                )
        );
    }

    @Override
    public synchronized EmergencyClaimHoldSnapshot activeAt(LocalDateTime nowUtc) {
        return EmergencyClaimHoldSnapshot.from(holdsByKey.values(), nowUtc);
    }

    synchronized Optional<EmergencyClaimHold> snapshot(String holdKey) {
        EmergencyClaimHold hold = holdsByKey.get(holdKey);
        return hold == null
                ? Optional.empty()
                : Optional.of(hold.persistedCopy(
                        hold.getBlockedUntil(),
                        hold.getSanitizedReason(),
                        hold.getVersion(),
                        hold.getUpdatedAt()
                ));
    }
}
