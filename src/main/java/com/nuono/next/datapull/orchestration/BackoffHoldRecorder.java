package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.RiskShareLevel;
import java.time.LocalDateTime;

/** Write Seam for a durable, non-blocking provider hold. */
public interface BackoffHoldRecorder {

    void record(
            RiskShareLevel shareLevel,
            DataPullBackoffIdentity identity,
            LocalDateTime blockedUntilUtc,
            String sanitizedCode,
            LocalDateTime observedAtUtc
    );
}
