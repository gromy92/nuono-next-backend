package com.nuono.next.noon;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Converts Noon business capture instants to the canonical Shanghai wall time.
 * Retry, cooldown and elapsed-time calculations must continue to use instants.
 */
public final class NoonShanghaiBusinessTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private NoonShanghaiBusinessTime() {
    }

    public static LocalDateTime now() {
        return now(Clock.systemUTC());
    }

    public static LocalDateTime now(Clock clock) {
        Clock source = clock == null ? Clock.systemUTC() : clock;
        return LocalDateTime.ofInstant(source.instant(), ZONE);
    }
}
