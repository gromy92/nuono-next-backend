package com.nuono.next.noon;

import java.time.Duration;
import java.util.Objects;

/** Local, pre-request pacing signal for a DP-owned one-shot transport attempt. */
public final class NoonRequestPacingException extends IllegalStateException {
    private final Duration retryAfter;

    public NoonRequestPacingException(Duration retryAfter) {
        super("Noon local request pacing is active.");
        Duration value = Objects.requireNonNull(retryAfter, "retryAfter");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("retryAfter must be positive");
        }
        this.retryAfter = value;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
