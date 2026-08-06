package com.nuono.next.datapull.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataPullScheduleBusinessContractTest {
    private static final String SCOPE = "owner=307|project=PRJ108065|store=STR108065-NSA|site=SA";
    private static final LocalDate T = LocalDate.of(2026, 8, 2);
    private static final DataPullScheduleRegistry CATALOG = new DataPullScheduleRegistry();

    @Test
    void dp01RunsAt2000ForTMinus30ThroughTMinus1() {
        DataPullScheduleSlot slot = onlyDailySlot(schedule(OperationCode.DP01), LocalTime.of(20, 0));

        assertDateRange(slot, T.minusDays(30), T.minusDays(1));
    }

    @Test
    void dp02RunsAt0830PlusStableJitterForTMinus1() {
        DataPullSchedule schedule = schedule(OperationCode.DP02);
        int jitter = Math.floorMod(SCOPE.hashCode(), 11);
        LocalTime expectedTime = LocalTime.of(8, 30).plusMinutes(jitter);
        DataPullScheduleSlot slot = onlyDailySlot(schedule, expectedTime);

        assertDateRange(slot, T.minusDays(1), T.minusDays(1));
    }

    @Test
    void dp03RunsAt2230ForTMinus7ThroughTMinus1() {
        DataPullScheduleSlot slot = onlyDailySlot(
                schedule(OperationCode.DP03),
                LocalTime.of(22, 30)
        );

        assertDateRange(slot, T.minusDays(7), T.minusDays(1));
    }

    @Test
    void dp04RunsAt0300ForCurrentCompleteProductSnapshot() {
        DataPullScheduleSlot slot = onlyDailySlot(schedule(OperationCode.DP04), LocalTime.of(3, 0));

        assertEquals(DataPullBusinessWindow.Kind.CURRENT_COMPLETE_SNAPSHOT, slot.getBusinessWindow().getKind());
        assertCurrentWindow(slot);
    }

    @Test
    void dp05RunsAt0330ForCurrentValidProductDetails() {
        DataPullScheduleSlot slot = onlyDailySlot(
                schedule(OperationCode.DP05),
                LocalTime.of(3, 30)
        );

        assertEquals(DataPullBusinessWindow.Kind.CURRENT_VALID_ITEMS, slot.getBusinessWindow().getKind());
        assertCurrentWindow(slot);
    }

    @Test
    void dp06RunsAt0630ForTMinus1() {
        DataPullScheduleSlot slot = onlyDailySlot(
                schedule(OperationCode.DP06),
                LocalTime.of(6, 30)
        );

        assertDateRange(slot, T.minusDays(1), T.minusDays(1));
    }

    @Test
    void dp07ARunsAt2300ForCurrentCompleteInventorySnapshot() {
        DataPullScheduleSlot slot = onlyDailySlot(
                schedule(OperationCode.DP07A),
                LocalTime.of(23, 0)
        );

        assertEquals(DataPullBusinessWindow.Kind.CURRENT_COMPLETE_SNAPSHOT, slot.getBusinessWindow().getKind());
        assertCurrentWindow(slot);
    }

    @Test
    void dp07BRunsAt2330ForTMinus1() {
        DataPullScheduleSlot slot = onlyDailySlot(
                schedule(OperationCode.DP07B),
                LocalTime.of(23, 30)
        );

        assertDateRange(slot, T.minusDays(1), T.minusDays(1));
    }

    @Test
    void dp08ARunsFourPointInTimeRankingSlotsEachDay() {
        DataPullSchedule schedule = schedule(OperationCode.DP08A);
        Instant previousSlot = at(T.minusDays(1), LocalTime.of(18, 0)).toInstant();
        Instant now = at(T, LocalTime.of(18, 0)).toInstant();

        List<DataPullScheduleSlot> slots = schedule.missedSlots(SCOPE, previousSlot, now);

        assertEquals(
                List.of(LocalTime.MIDNIGHT, LocalTime.of(6, 0), LocalTime.NOON, LocalTime.of(18, 0)),
                slots.stream().map(slot -> slot.getScheduledAt().toLocalTime()).collect(java.util.stream.Collectors.toList())
        );
        for (DataPullScheduleSlot slot : slots) {
            assertEquals(DataPullBusinessWindow.Kind.POINT_IN_TIME_RANKING, slot.getBusinessWindow().getKind());
            assertEquals(slot.getScheduledAt(), slot.getBusinessWindow().getPointInTime());
            assertEquals(T, slot.getBusinessWindow().getAnchorDate());
        }
    }

    @Test
    void dp08BRunsAt0200ForCurrentDaysRankingGapTargets() {
        DataPullScheduleSlot slot = onlyDailySlot(
                schedule(OperationCode.DP08B),
                LocalTime.of(2, 0)
        );

        assertEquals(DataPullBusinessWindow.Kind.DAILY_RANKING_GAP_TARGETS, slot.getBusinessWindow().getKind());
        assertCurrentWindow(slot);
    }

    @Test
    void dp10RunsAt0300ForInitialFullThenHighWatermarkIncrementalSync() {
        DataPullScheduleSlot slot = onlyDailySlot(schedule(OperationCode.DP10), LocalTime.of(3, 0));

        assertEquals(
                DataPullBusinessWindow.Kind.INITIAL_FULL_THEN_HIGH_WATERMARK_INCREMENTAL,
                slot.getBusinessWindow().getKind()
        );
        assertCurrentWindow(slot);
    }

    private DataPullScheduleSlot onlyDailySlot(DataPullSchedule schedule, LocalTime runAt) {
        Instant previousSlot = at(T.minusDays(1), runAt).toInstant();
        Instant currentSlot = at(T, runAt).toInstant();
        List<DataPullScheduleSlot> slots = schedule.missedSlots(SCOPE, previousSlot, currentSlot);
        assertEquals(1, slots.size());
        DataPullScheduleSlot slot = slots.get(0);
        assertEquals(DataPullSchedule.ZONE_ID, slot.getScheduledAt().getZone());
        assertEquals(T, slot.getScheduledAt().toLocalDate());
        assertEquals(runAt, slot.getScheduledAt().toLocalTime());
        assertEquals(schedule.operationCode(), slot.getOperationCode());
        return slot;
    }

    private DataPullSchedule schedule(OperationCode operationCode) {
        return CATALOG.find(operationCode).orElseThrow();
    }

    private void assertDateRange(DataPullScheduleSlot slot, LocalDate expectedFrom, LocalDate expectedTo) {
        assertEquals(DataPullBusinessWindow.Kind.INCLUSIVE_DATE_RANGE, slot.getBusinessWindow().getKind());
        assertEquals(T, slot.getBusinessWindow().getAnchorDate());
        assertEquals(expectedFrom, slot.getBusinessWindow().getDateFromInclusive());
        assertEquals(expectedTo, slot.getBusinessWindow().getDateToInclusive());
        assertNull(slot.getBusinessWindow().getPointInTime());
    }

    private void assertCurrentWindow(DataPullScheduleSlot slot) {
        assertEquals(T, slot.getBusinessWindow().getAnchorDate());
        assertNull(slot.getBusinessWindow().getDateFromInclusive());
        assertNull(slot.getBusinessWindow().getDateToInclusive());
        assertNull(slot.getBusinessWindow().getPointInTime());
    }

    private ZonedDateTime at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(DataPullSchedule.ZONE_ID);
    }
}
