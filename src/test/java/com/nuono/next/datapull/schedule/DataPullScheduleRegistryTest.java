package com.nuono.next.datapull.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DataPullScheduleRegistryTest {
    private static final LocalDate T = LocalDate.of(2026, 8, 2);

    @Test
    void immutableCatalogContainsEveryDailyDpAndNoRetiredDp09() {
        DataPullScheduleRegistry catalog = new DataPullScheduleRegistry();

        catalog.requireComplete();
        assertEquals(
                List.of(OperationCode.values()),
                Arrays.stream(OperationCode.values())
                        .map(operation -> catalog.find(operation).orElseThrow().operationCode())
                        .collect(Collectors.toList())
        );
        List<String> registeredNames = Arrays.stream(OperationCode.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        assertFalse(registeredNames.stream().anyMatch(name -> name.startsWith("DP09")));
        assertSame(
                catalog.find(OperationCode.DP01).orElseThrow(),
                catalog.find(OperationCode.DP01).orElseThrow()
        );
    }

    @Test
    void dp02JitterIsStablePerScopeAndAlwaysZeroThroughTenMinutes() {
        DataPullSchedule schedule = new DataPullScheduleRegistry()
                .find(OperationCode.DP02)
                .orElseThrow();

        for (int index = 0; index < 100; index++) {
            String scopeKey = "owner=307|project=P" + index + "|account=A" + index;
            DataPullScheduleSlot first = currentDp02Slot(schedule, scopeKey);
            DataPullScheduleSlot second = currentDp02Slot(schedule, scopeKey);
            int jitter = first.getScheduledAt().getMinute() - 30;

            assertEquals(first.getScheduleSlotKey(), second.getScheduleSlotKey());
            assertEquals(first.getBusinessWindow(), second.getBusinessWindow());
            assertTrue(jitter >= 0 && jitter <= 10);
            assertEquals(Math.floorMod(scopeKey.hashCode(), 11), jitter);
        }
    }

    @Test
    void dp02SlotBecomesDueExactlyAtItsStableJitteredTime() {
        DataPullSchedule schedule = new DataPullScheduleRegistry()
                .find(OperationCode.DP02)
                .orElseThrow();
        String scopeKey = "owner=307|project=PRJ108065|store=STR108065-NSA|site=SA";
        ZonedDateTime due = currentDp02Slot(schedule, scopeKey).getScheduledAt();
        ZonedDateTime previousDue = due.minusDays(1);

        assertEquals(
                due,
                currentDp02Slot(schedule, "  " + scopeKey + "  ").getScheduledAt()
        );
        assertEquals(
                List.of(),
                schedule.missedSlots(
                        scopeKey, previousDue.toInstant(), due.minusNanos(1).toInstant()
                )
        );
        assertEquals(
                due,
                schedule.missedSlots(scopeKey, previousDue.toInstant(), due.toInstant())
                        .get(0)
                        .getScheduledAt()
        );
    }

    private DataPullScheduleSlot currentDp02Slot(DataPullSchedule schedule, String scopeKey) {
        ZonedDateTime previousAfterLatestDue = T.minusDays(1)
                .atTime(LocalTime.of(8, 40))
                .atZone(DataPullSchedule.ZONE_ID);
        ZonedDateTime currentAfterLatestDue = T.atTime(LocalTime.of(8, 40))
                .atZone(DataPullSchedule.ZONE_ID);
        List<DataPullScheduleSlot> slots = schedule.missedSlots(
                scopeKey,
                previousAfterLatestDue.toInstant(),
                currentAfterLatestDue.toInstant()
        );
        assertEquals(1, slots.size());
        return slots.get(0);
    }
}
