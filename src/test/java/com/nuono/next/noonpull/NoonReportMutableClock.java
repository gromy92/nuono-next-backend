package com.nuono.next.noonpull;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

final class NoonReportMutableClock extends Clock {
    private Instant instant;
    private final ZoneId zone;

    NoonReportMutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    void setInstant(Instant instant) {
        this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new NoonReportMutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
