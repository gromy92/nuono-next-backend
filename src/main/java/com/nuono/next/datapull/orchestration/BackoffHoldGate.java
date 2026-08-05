package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.RiskShareLevel;
import java.time.LocalDateTime;

/** Read-only Seam for durable exact/account/egress backoff holds. */
public interface BackoffHoldGate {

    boolean isHeld(
            RiskShareLevel shareLevel,
            DataPullBackoffIdentity identity,
            LocalDateTime nowUtc
    );
}
