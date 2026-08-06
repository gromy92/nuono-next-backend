package com.nuono.next.datapull.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DataPullScheduleCatchUpTest {
    private static final String SCOPE = "owner=307|project=PRJ108065|store=STR108065-NSA|site=SA";
    private static final DataPullScheduleRegistry CATALOG = new DataPullScheduleRegistry();

    @Test
    void catchesUpEveryDailySlotAfterLastCompletionThroughNow() {
        DataPullSchedule schedule = schedule(OperationCode.DP04);
        Instant lastCompleted = at("2026-08-01", "03:00").toInstant();
        Instant now = at("2026-08-04", "03:00").toInstant();

        List<DataPullScheduleSlot> slots = schedule.missedSlots(SCOPE, lastCompleted, now);

        assertEquals(
                List.of(
                        LocalDate.of(2026, 8, 2),
                        LocalDate.of(2026, 8, 3),
                        LocalDate.of(2026, 8, 4)
                ),
                slots.stream()
                        .map(slot -> slot.getScheduledAt().toLocalDate())
                        .collect(Collectors.toList())
        );
    }

    @Test
    void doesNotCreateTodaysSlotBeforeItsDueTime() {
        DataPullSchedule schedule = schedule(OperationCode.DP06);
        Instant lastCompleted = at("2026-08-01", "06:30").toInstant();
        Instant now = at("2026-08-02", "06:29").toInstant();

        assertEquals(List.of(), schedule.missedSlots(SCOPE, lastCompleted, now));
    }

    @Test
    void catchesUpEachIntradayDp08ASlotWithoutWaitingForTheNextDay() {
        DataPullSchedule schedule = schedule(OperationCode.DP08A);
        Instant lastCompleted = at("2026-08-01", "18:00").toInstant();
        Instant now = at("2026-08-02", "12:00").toInstant();

        List<DataPullScheduleSlot> slots = schedule.missedSlots(SCOPE, lastCompleted, now);

        assertEquals(
                List.of(LocalTime.MIDNIGHT, LocalTime.of(6, 0), LocalTime.NOON),
                slots.stream()
                        .map(slot -> slot.getScheduledAt().toLocalTime())
                        .collect(Collectors.toList())
        );
    }

    @Test
    void producesTheSameSlotAndBusinessWindowKeysWhenCatchUpIsRecomputed() {
        DataPullSchedule schedule = schedule(OperationCode.DP01);
        Instant lastCompleted = at("2026-08-01", "20:00").toInstant();
        Instant firstNow = at("2026-08-02", "20:00").toInstant();
        Instant laterNow = at("2026-08-02", "23:59").toInstant();

        DataPullScheduleSlot first = schedule.missedSlots(SCOPE, lastCompleted, firstNow).get(0);
        DataPullScheduleSlot recomputed = schedule.missedSlots(SCOPE, lastCompleted, laterNow).get(0);

        assertEquals(first.getScheduleSlotKey(), recomputed.getScheduleSlotKey());
        assertEquals(first.getBusinessWindow().getKey(), recomputed.getBusinessWindow().getKey());
        assertEquals(first.getBusinessWindow(), recomputed.getBusinessWindow());
    }

    @Test
    void rejectsInvalidCatchUpBoundsAndBlankScope() {
        DataPullSchedule schedule = schedule(OperationCode.DP01);
        Instant earlier = at("2026-08-01", "20:00").toInstant();
        Instant later = at("2026-08-02", "20:00").toInstant();

        assertThrows(IllegalArgumentException.class, () -> schedule.missedSlots(SCOPE, later, earlier));
        assertThrows(IllegalArgumentException.class, () -> schedule.missedSlots(" ", earlier, later));
    }

    @Test
    void boundedPagesPreserveEveryExactWindowAndLatestLookup() {
        DataPullSchedule schedule = schedule(OperationCode.DP08A);
        Instant start = at("2026-07-01", "18:00").toInstant();
        Instant end = at("2026-08-02", "18:00").toInstant();
        List<DataPullScheduleSlot> expected = schedule.missedSlots(SCOPE, start, end);
        List<DataPullScheduleSlot> actual = new ArrayList<>();
        Instant cursor = start;
        int pages = 0;
        while (true) {
            ScheduleSlotPage page = schedule.missedSlotsPage(SCOPE, cursor, end, 64);
            pages++;
            actual.addAll(page.getSlots());
            if (!page.hasMore()) break;
            cursor = page.getSlots().get(page.getSlots().size() - 1)
                    .getScheduledAt().toInstant();
        }

        assertTrue(pages > 1);
        assertEquals(
                expected.stream().map(DataPullScheduleSlot::getScheduleSlotKey)
                        .collect(Collectors.toList()),
                actual.stream().map(DataPullScheduleSlot::getScheduleSlotKey)
                        .collect(Collectors.toList())
        );
        assertEquals(
                expected.get(expected.size() - 1).getScheduleSlotKey(),
                schedule.latestMissedSlot(SCOPE, start, end).orElseThrow()
                        .getScheduleSlotKey()
        );
    }

    private ZonedDateTime at(String date, String time) {
        return LocalDate.parse(date)
                .atTime(LocalTime.parse(time))
                .atZone(DataPullSchedule.ZONE_ID);
    }

    private DataPullSchedule schedule(OperationCode operationCode) {
        return CATALOG.find(operationCode).orElseThrow();
    }
}
