package com.nuono.next.datapull.orchestration;

import java.time.LocalDateTime;

/** Deep Interface for expiring technical holds on new DP claims. */
public interface EmergencyClaimHoldStore {

    void place(EmergencyClaimHold hold);

    EmergencyClaimHoldSnapshot activeAt(LocalDateTime nowUtc);
}
