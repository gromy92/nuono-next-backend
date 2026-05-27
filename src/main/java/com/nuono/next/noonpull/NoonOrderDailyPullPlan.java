package com.nuono.next.noonpull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class NoonOrderDailyPullPlan {
    private final LocalDate targetDate;
    private final LocalTime baseReadyAfterLocalTime;
    private final LocalDateTime scheduledAt;
    private final boolean due;

    public NoonOrderDailyPullPlan(
            LocalDate targetDate,
            LocalTime baseReadyAfterLocalTime,
            LocalDateTime scheduledAt,
            boolean due
    ) {
        this.targetDate = targetDate;
        this.baseReadyAfterLocalTime = baseReadyAfterLocalTime;
        this.scheduledAt = scheduledAt;
        this.due = due;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public LocalTime getBaseReadyAfterLocalTime() {
        return baseReadyAfterLocalTime;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public boolean isDue() {
        return due;
    }
}
