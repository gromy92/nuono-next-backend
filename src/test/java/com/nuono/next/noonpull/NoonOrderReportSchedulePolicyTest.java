package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class NoonOrderReportSchedulePolicyTest {

    @Test
    void shouldDefaultToTMinusOneAfterShanghai0830WithDeterministicJitter() {
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        NoonOrderReportSchedulePolicy policy = new NoonOrderReportSchedulePolicy(
                Clock.fixed(Instant.parse("2026-05-22T00:40:00Z"), shanghai)
        );

        NoonOrderDailyPullPlan ae = policy.dailyPlan(10002L, "STR245027-NAE", "AE");
        NoonOrderDailyPullPlan sa = policy.dailyPlan(10002L, "STR245027-NSA", "SA");

        assertEquals(LocalDate.of(2026, 5, 21), ae.getTargetDate());
        assertEquals(LocalTime.of(8, 30), ae.getBaseReadyAfterLocalTime());
        assertTrue(ae.isDue());
        assertTrue(ae.getScheduledAt().toLocalTime().isAfter(LocalTime.of(8, 29)));
        assertFalse(ae.getScheduledAt().equals(sa.getScheduledAt()));
    }

    @Test
    void shouldNotRunDailyOrderPullBeforeReadinessWindow() {
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        NoonOrderReportSchedulePolicy policy = new NoonOrderReportSchedulePolicy(
                Clock.fixed(Instant.parse("2026-05-21T23:55:00Z"), shanghai)
        );

        NoonOrderDailyPullPlan plan = policy.dailyPlan(10002L, "STR245027-NAE", "AE");

        assertEquals(LocalDate.of(2026, 5, 21), plan.getTargetDate());
        assertFalse(plan.isDue());
        assertEquals(LocalTime.of(8, 30), plan.getBaseReadyAfterLocalTime());
    }
}
