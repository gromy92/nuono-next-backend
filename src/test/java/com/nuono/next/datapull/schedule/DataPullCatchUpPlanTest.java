package com.nuono.next.datapull.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataPullCatchUpPlanTest {

    private static final String SCOPE = "owner=307|project=PRJ108065|site=SA";
    private static final DataPullScheduleRegistry CATALOG = new DataPullScheduleRegistry();

    @Test
    void allCurrentOperationsReduceThreeNewSlotsToTheLatest() {
        List<DataPullSchedule> schedules = List.of(
                schedule(OperationCode.DP04),
                schedule(OperationCode.DP05),
                schedule(OperationCode.DP07A)
        );

        for (DataPullSchedule schedule : schedules) {
            List<DataPullScheduleSlot> slots = threeDailySlots(schedule);
            DataPullCatchUpPlan plan = DataPullCatchUpPlan.from(
                    schedule.operationCode(), slots
            );

            assertEquals(DataPullCatchUpPlan.Strategy.LATEST_CURRENT, plan.getStrategy());
            assertEquals(1, plan.getTaskSlots().size());
            assertEquals(
                    slots.get(2).getScheduleSlotKey(),
                    plan.getTaskSlots().get(0).getScheduleSlotKey()
            );
        }
    }

    @Test
    void dp03NewRollingSlotsAreUnionedBeforePersistence() {
        DataPullSchedule schedule = schedule(OperationCode.DP03);
        List<DataPullScheduleSlot> slots = threeDailySlots(schedule);

        DataPullCatchUpPlan plan = DataPullCatchUpPlan.from(schedule.operationCode(), slots);

        assertEquals(DataPullCatchUpPlan.Strategy.ROLLING_DATE_UNION, plan.getStrategy());
        assertEquals(1, plan.getTaskSlots().size());
        assertEquals(
                "DP03:date-range:2026-07-26..2026-08-03",
                plan.getTaskSlots().get(0).getBusinessWindow().getKey()
        );
    }

    @Test
    void dp08aPreservesEveryMissedPointInTimeSlotAcrossDays() {
        DataPullSchedule schedule = schedule(OperationCode.DP08A);
        Instant previous = LocalDate.of(2026, 8, 1)
                .atStartOfDay(DataPullSchedule.ZONE_ID).toInstant();
        Instant now = LocalDate.of(2026, 8, 4)
                .atTime(13, 0).atZone(DataPullSchedule.ZONE_ID).toInstant();

        DataPullCatchUpPlan plan = DataPullCatchUpPlan.from(
                schedule.operationCode(),
                schedule.missedSlots(SCOPE, previous, now),
                now
        );

        assertEquals(DataPullCatchUpPlan.Strategy.EXACT_WINDOWS, plan.getStrategy());
        assertEquals(14, plan.getTaskSlots().size());
        assertEquals(
                List.of(LocalTime.MIDNIGHT, LocalTime.of(6, 0), LocalTime.NOON,
                        LocalTime.of(18, 0)),
                plan.getTaskSlots().subList(3, 7).stream()
                        .map(slot -> slot.getScheduledAt().toLocalTime())
                        .collect(java.util.stream.Collectors.toList())
        );
        assertEquals(
                LocalDate.of(2026, 8, 4).atTime(12, 0),
                plan.getTaskSlots().get(13).getScheduledAt().toLocalDateTime()
        );
    }

    @Test
    void dp08bPreservesEachDueBusinessDateAcrossAnOutage() {
        DataPullSchedule schedule = schedule(OperationCode.DP08B);
        Instant previous = LocalDate.of(2026, 8, 1)
                .atTime(2, 0).atZone(DataPullSchedule.ZONE_ID).toInstant();
        Instant now = LocalDate.of(2026, 8, 4)
                .atTime(2, 30).atZone(DataPullSchedule.ZONE_ID).toInstant();

        DataPullCatchUpPlan plan = DataPullCatchUpPlan.from(
                schedule.operationCode(),
                schedule.missedSlots(SCOPE, previous, now),
                now
        );

        assertEquals(DataPullCatchUpPlan.Strategy.EXACT_WINDOWS, plan.getStrategy());
        assertEquals(
                List.of(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3),
                        LocalDate.of(2026, 8, 4)),
                plan.getTaskSlots().stream()
                        .map(slot -> slot.getBusinessWindow().getAnchorDate())
                        .collect(java.util.stream.Collectors.toList())
        );
    }

    private List<DataPullScheduleSlot> threeDailySlots(DataPullSchedule schedule) {
        LocalTime runAt = runAt(schedule);
        ZonedDateTime previous = LocalDate.of(2026, 8, 1)
                .atTime(runAt)
                .atZone(DataPullSchedule.ZONE_ID);
        ZonedDateTime now = LocalDate.of(2026, 8, 4)
                .atTime(runAt.plusMinutes(10))
                .atZone(DataPullSchedule.ZONE_ID);
        return schedule.missedSlots(SCOPE, previous.toInstant(), now.toInstant());
    }

    private LocalTime runAt(DataPullSchedule schedule) {
        switch (schedule.operationCode()) {
            case DP04:
            case DP07A:
                return schedule.operationCode().name().equals("DP04")
                        ? LocalTime.of(3, 0) : LocalTime.of(23, 0);
            case DP05:
                return LocalTime.of(3, 30);
            case DP03:
                return LocalTime.of(22, 30);
            default:
                throw new IllegalArgumentException("unsupported test schedule");
        }
    }

    private static DataPullSchedule schedule(OperationCode operationCode) {
        return CATALOG.find(operationCode).orElseThrow();
    }
}
