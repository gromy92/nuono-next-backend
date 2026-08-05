package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable in-process view used only to prefilter one dispatcher pass. */
public final class EmergencyClaimHoldSnapshot {

    private final List<EmergencyClaimHold> activeHolds;
    private final LocalDateTime observedAtUtc;

    private EmergencyClaimHoldSnapshot(
            List<EmergencyClaimHold> activeHolds,
            LocalDateTime observedAtUtc
    ) {
        this.activeHolds = List.copyOf(activeHolds);
        this.observedAtUtc = observedAtUtc;
    }

    static EmergencyClaimHoldSnapshot from(
            Iterable<EmergencyClaimHold> holds,
            LocalDateTime nowUtc
    ) {
        LocalDateTime now = Objects.requireNonNull(nowUtc, "nowUtc");
        List<EmergencyClaimHold> active = new ArrayList<>();
        for (EmergencyClaimHold hold : Objects.requireNonNull(holds, "holds")) {
            EmergencyClaimHold candidate = Objects.requireNonNull(hold, "hold");
            candidate.validateForPlacement();
            if (candidate.getBlockedUntil().isAfter(now)) {
                active.add(candidate.persistedCopy(
                        candidate.getBlockedUntil(),
                        candidate.getSanitizedReason(),
                        candidate.getVersion(),
                        candidate.getUpdatedAt()
                ));
            }
        }
        return new EmergencyClaimHoldSnapshot(active, now);
    }

    public boolean blocksAllClaims() {
        return activeHolds.stream().anyMatch((hold) ->
                hold.getHoldScope() == EmergencyClaimHoldScope.GLOBAL
        );
    }

    public boolean isClaimHeld(OperationCode operationCode, String scopeKey) {
        Objects.requireNonNull(operationCode, "operationCode");
        EmergencyClaimHoldKey.requireScopeKey(scopeKey);
        return activeHolds.stream().anyMatch((hold) ->
                hold.blocks(operationCode, scopeKey, observedAtUtc)
        );
    }
}
