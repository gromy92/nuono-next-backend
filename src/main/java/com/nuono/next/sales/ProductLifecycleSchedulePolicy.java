package com.nuono.next.sales;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class ProductLifecycleSchedulePolicy {

    private static final int CADENCE_DAYS = 3;

    private final Clock clock;

    public ProductLifecycleSchedulePolicy() {
        this(Clock.systemDefaultZone());
    }

    ProductLifecycleSchedulePolicy(Clock clock) {
        this.clock = clock;
    }

    public int getCadenceDays() {
        return CADENCE_DAYS;
    }

    public LocalDate defaultAnchorDate() {
        return LocalDate.now(clock).minusDays(1);
    }
}
