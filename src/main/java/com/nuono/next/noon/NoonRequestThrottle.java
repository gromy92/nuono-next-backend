package com.nuono.next.noon;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Serial request pacing for one authenticated Noon session. */
final class NoonRequestThrottle {

    private final long minimumIntervalMillis;
    private final LongSupplier currentTimeMillis;
    private volatile long lastCompletedAtMillis;

    NoonRequestThrottle(long minimumIntervalMillis) {
        this(minimumIntervalMillis, System::currentTimeMillis);
    }

    NoonRequestThrottle(long minimumIntervalMillis, LongSupplier currentTimeMillis) {
        this.minimumIntervalMillis = Math.max(0L, minimumIntervalMillis);
        this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis");
    }

    void await() {
        Duration remaining = remainingDelay();
        if (remaining.isZero()) {
            return;
        }
        try {
            Thread.sleep(remaining.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "请求 Noon 前被中断：" + exception.getMessage(),
                    exception
            );
        }
    }

    void requireReady() {
        Duration remaining = remainingDelay();
        if (!remaining.isZero()) {
            throw new NoonRequestPacingException(remaining);
        }
    }

    void beforeRequest(boolean waitForPacing) {
        if (waitForPacing) {
            await();
        } else {
            requireReady();
        }
    }

    void markCompleted() {
        lastCompletedAtMillis = currentTimeMillis.getAsLong();
    }

    private Duration remainingDelay() {
        if (minimumIntervalMillis <= 0L || lastCompletedAtMillis <= 0L) {
            return Duration.ZERO;
        }
        long now = currentTimeMillis.getAsLong();
        long elapsedMillis = now >= lastCompletedAtMillis
                ? now - lastCompletedAtMillis
                : 0L;
        if (elapsedMillis >= minimumIntervalMillis) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(minimumIntervalMillis - elapsedMillis);
    }
}
