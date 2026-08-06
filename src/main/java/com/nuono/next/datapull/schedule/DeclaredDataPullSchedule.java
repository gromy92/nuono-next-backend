package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One declarative schedule implementation, separated from the catalog wiring. */
final class DeclaredDataPullSchedule implements DataPullSchedule {
    enum WindowRule {
        INCLUSIVE_DATE_RANGE,
        COMPLETE_SNAPSHOT,
        CURRENT_VALID_ITEMS,
        POINT_IN_TIME_RANKING,
        DAILY_RANKING_GAP_TARGETS,
        FULL_THEN_HIGH_WATERMARK
    }

    private final OperationCode operationCode;
    private final List<LocalTime> runTimes;
    private final int jitterBuckets;
    private final WindowRule windowRule;
    private final int fromDaysBefore;
    private final int toDaysBefore;

    DeclaredDataPullSchedule(
            OperationCode operationCode,
            List<LocalTime> runTimes,
            int jitterBuckets,
            WindowRule windowRule,
            int fromDaysBefore,
            int toDaysBefore
    ) {
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        this.runTimes = validatedTimes(runTimes);
        if (jitterBuckets < 1) throw new IllegalArgumentException("jitterBuckets must be positive");
        this.jitterBuckets = jitterBuckets;
        this.windowRule = Objects.requireNonNull(windowRule, "windowRule");
        if (windowRule == WindowRule.INCLUSIVE_DATE_RANGE
                && (toDaysBefore < 0 || fromDaysBefore < toDaysBefore)) {
            throw new IllegalArgumentException("invalid inclusive date-range offsets");
        }
        this.fromDaysBefore = fromDaysBefore;
        this.toDaysBefore = toDaysBefore;
    }

    @Override
    public OperationCode operationCode() { return operationCode; }

    @Override
    public List<DataPullScheduleSlot> missedSlots(
            String scopeKey, Instant lastCompletedSlotExclusive, Instant nowInclusive
    ) {
        String scope = requireScopeKey(scopeKey);
        Instant lastCompleted = Objects.requireNonNull(
                lastCompletedSlotExclusive, "lastCompletedSlotExclusive"
        );
        Instant now = Objects.requireNonNull(nowInclusive, "nowInclusive");
        requireOrdered(lastCompleted, now);
        LocalDate cursor = lastCompleted.atZone(zoneId()).toLocalDate();
        LocalDate lastDate = now.atZone(zoneId()).toLocalDate();
        List<DataPullScheduleSlot> result = new ArrayList<>();
        while (!cursor.isAfter(lastDate)) {
            for (LocalTime runTime : runTimes) {
                ZonedDateTime scheduledAt = at(cursor, runTime, scope);
                Instant instant = scheduledAt.toInstant();
                if (instant.isAfter(lastCompleted) && !instant.isAfter(now)) {
                    result.add(slot(scheduledAt));
                }
            }
            cursor = cursor.plusDays(1);
        }
        return List.copyOf(result);
    }

    @Override
    public ScheduleSlotPage missedSlotsPage(
            String scopeKey,
            Instant lastCompletedSlotExclusive,
            Instant nowInclusive,
            int limit
    ) {
        if (limit < 1 || limit > 64) {
            throw new IllegalArgumentException("slot page limit must be between 1 and 64");
        }
        String scope = requireScopeKey(scopeKey);
        Instant lastCompleted = Objects.requireNonNull(
                lastCompletedSlotExclusive, "lastCompletedSlotExclusive"
        );
        Instant now = Objects.requireNonNull(nowInclusive, "nowInclusive");
        requireOrdered(lastCompleted, now);
        LocalDate cursor = lastCompleted.atZone(zoneId()).toLocalDate();
        LocalDate lastDate = now.atZone(zoneId()).toLocalDate();
        List<DataPullScheduleSlot> result = new ArrayList<>(limit);
        while (!cursor.isAfter(lastDate)) {
            for (LocalTime runTime : runTimes) {
                ZonedDateTime scheduledAt = at(cursor, runTime, scope);
                Instant instant = scheduledAt.toInstant();
                if (!instant.isAfter(lastCompleted) || instant.isAfter(now)) continue;
                if (result.size() == limit) return new ScheduleSlotPage(result, true, limit);
                result.add(slot(scheduledAt));
            }
            cursor = cursor.plusDays(1);
        }
        return new ScheduleSlotPage(result, false, limit);
    }

    @Override
    public Optional<DataPullScheduleSlot> latestMissedSlot(
            String scopeKey, Instant lastCompletedSlotExclusive, Instant nowInclusive
    ) {
        String scope = requireScopeKey(scopeKey);
        Instant lastCompleted = Objects.requireNonNull(
                lastCompletedSlotExclusive, "lastCompletedSlotExclusive"
        );
        Instant now = Objects.requireNonNull(nowInclusive, "nowInclusive");
        requireOrdered(lastCompleted, now);
        LocalDate firstDate = lastCompleted.atZone(zoneId()).toLocalDate();
        LocalDate cursor = now.atZone(zoneId()).toLocalDate();
        while (!cursor.isBefore(firstDate)) {
            for (int index = runTimes.size() - 1; index >= 0; index--) {
                ZonedDateTime scheduledAt = at(cursor, runTimes.get(index), scope);
                Instant instant = scheduledAt.toInstant();
                if (instant.isAfter(lastCompleted) && !instant.isAfter(now)) {
                    return Optional.of(slot(scheduledAt));
                }
            }
            cursor = cursor.minusDays(1);
        }
        return Optional.empty();
    }

    private DataPullScheduleSlot slot(ZonedDateTime scheduledAt) {
        return new DataPullScheduleSlot(operationCode, scheduledAt, businessWindow(scheduledAt));
    }

    private ZonedDateTime at(LocalDate date, LocalTime time, String scope) {
        return date.atTime(time).atZone(zoneId()).plusMinutes(jitterMinutes(scope));
    }

    private DataPullBusinessWindow businessWindow(ZonedDateTime scheduledAt) {
        LocalDate anchor = scheduledAt.toLocalDate();
        switch (windowRule) {
            case INCLUSIVE_DATE_RANGE:
                LocalDate from = anchor.minusDays(fromDaysBefore);
                LocalDate to = anchor.minusDays(toDaysBefore);
                return DataPullBusinessWindow.inclusiveDateRange(
                        key("date-range:" + from + ".." + to), anchor, from, to
                );
            case COMPLETE_SNAPSHOT:
                return DataPullBusinessWindow.currentCompleteSnapshot(
                        key("complete-snapshot:" + anchor), anchor
                );
            case CURRENT_VALID_ITEMS:
                return DataPullBusinessWindow.currentValidItems(
                        key("current-valid-items:" + anchor), anchor
                );
            case POINT_IN_TIME_RANKING:
                return DataPullBusinessWindow.pointInTimeRanking(
                        key("ranking-point:" + scheduledAt.toLocalDateTime()), scheduledAt
                );
            case DAILY_RANKING_GAP_TARGETS:
                return DataPullBusinessWindow.dailyRankingGapTargets(
                        key("ranking-gap-targets:" + anchor), anchor
                );
            case FULL_THEN_HIGH_WATERMARK:
                return DataPullBusinessWindow.initialFullThenHighWatermarkIncremental(
                        key("full-then-high-watermark-incremental:" + anchor), anchor
                );
            default:
                throw new IllegalStateException("unsupported business-window rule");
        }
    }

    private int jitterMinutes(String scopeKey) {
        return Math.floorMod(scopeKey.hashCode(), jitterBuckets);
    }

    private String key(String suffix) { return operationCode.name() + ":" + suffix; }

    private static List<LocalTime> validatedTimes(List<LocalTime> runTimes) {
        List<LocalTime> times = new ArrayList<>(Objects.requireNonNull(runTimes, "runTimes"));
        if (times.isEmpty() || times.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("runTimes must be non-empty");
        }
        times.sort(Comparator.naturalOrder());
        if (new LinkedHashSet<>(times).size() != times.size()) {
            throw new IllegalArgumentException("runTimes must be unique");
        }
        return List.copyOf(times);
    }

    private static String requireScopeKey(String scopeKey) {
        if (scopeKey == null || scopeKey.trim().isEmpty()) {
            throw new IllegalArgumentException("scopeKey must not be blank");
        }
        return scopeKey.trim();
    }

    private static void requireOrdered(Instant start, Instant end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("schedule upper bound predates cursor");
        }
    }
}
