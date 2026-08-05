package com.nuono.next.datapull.schedule;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

/** Immutable business-window value carried by a technical schedule slot. */
public final class DataPullBusinessWindow {
    public enum Kind {
        INCLUSIVE_DATE_RANGE,
        CURRENT_COMPLETE_SNAPSHOT,
        CURRENT_VALID_ITEMS,
        POINT_IN_TIME_RANKING,
        DAILY_RANKING_GAP_TARGETS,
        INITIAL_FULL_THEN_HIGH_WATERMARK_INCREMENTAL
    }

    private final Kind kind;
    private final String key;
    private final LocalDate anchorDate;
    private final LocalDate dateFromInclusive;
    private final LocalDate dateToInclusive;
    private final ZonedDateTime pointInTime;

    private DataPullBusinessWindow(
            Kind kind,
            String key,
            LocalDate anchorDate,
            LocalDate dateFromInclusive,
            LocalDate dateToInclusive,
            ZonedDateTime pointInTime
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.key = requireText(key, "key");
        this.anchorDate = Objects.requireNonNull(anchorDate, "anchorDate");
        this.dateFromInclusive = dateFromInclusive;
        this.dateToInclusive = dateToInclusive;
        this.pointInTime = pointInTime;
    }

    public static DataPullBusinessWindow inclusiveDateRange(
            String key,
            LocalDate anchorDate,
            LocalDate dateFromInclusive,
            LocalDate dateToInclusive
    ) {
        Objects.requireNonNull(dateFromInclusive, "dateFromInclusive");
        Objects.requireNonNull(dateToInclusive, "dateToInclusive");
        if (dateFromInclusive.isAfter(dateToInclusive)) {
            throw new IllegalArgumentException("dateFromInclusive must not be after dateToInclusive");
        }
        return new DataPullBusinessWindow(
                Kind.INCLUSIVE_DATE_RANGE,
                key,
                anchorDate,
                dateFromInclusive,
                dateToInclusive,
                null
        );
    }

    public static DataPullBusinessWindow currentCompleteSnapshot(String key, LocalDate anchorDate) {
        return new DataPullBusinessWindow(
                Kind.CURRENT_COMPLETE_SNAPSHOT,
                key,
                anchorDate,
                null,
                null,
                null
        );
    }

    public static DataPullBusinessWindow currentValidItems(String key, LocalDate anchorDate) {
        return new DataPullBusinessWindow(
                Kind.CURRENT_VALID_ITEMS,
                key,
                anchorDate,
                null,
                null,
                null
        );
    }

    public static DataPullBusinessWindow pointInTimeRanking(String key, ZonedDateTime pointInTime) {
        Objects.requireNonNull(pointInTime, "pointInTime");
        return new DataPullBusinessWindow(
                Kind.POINT_IN_TIME_RANKING,
                key,
                pointInTime.toLocalDate(),
                null,
                null,
                pointInTime
        );
    }

    public static DataPullBusinessWindow dailyRankingGapTargets(String key, LocalDate anchorDate) {
        return new DataPullBusinessWindow(
                Kind.DAILY_RANKING_GAP_TARGETS,
                key,
                anchorDate,
                null,
                null,
                null
        );
    }

    public static DataPullBusinessWindow initialFullThenHighWatermarkIncremental(
            String key,
            LocalDate anchorDate
    ) {
        return new DataPullBusinessWindow(
                Kind.INITIAL_FULL_THEN_HIGH_WATERMARK_INCREMENTAL,
                key,
                anchorDate,
                null,
                null,
                null
        );
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public Kind getKind() {
        return kind;
    }

    public String getKey() {
        return key;
    }

    public LocalDate getAnchorDate() {
        return anchorDate;
    }

    public LocalDate getDateFromInclusive() {
        return dateFromInclusive;
    }

    public LocalDate getDateToInclusive() {
        return dateToInclusive;
    }

    public ZonedDateTime getPointInTime() {
        return pointInTime;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DataPullBusinessWindow)) {
            return false;
        }
        DataPullBusinessWindow that = (DataPullBusinessWindow) other;
        return kind == that.kind
                && key.equals(that.key)
                && anchorDate.equals(that.anchorDate)
                && Objects.equals(dateFromInclusive, that.dateFromInclusive)
                && Objects.equals(dateToInclusive, that.dateToInclusive)
                && Objects.equals(pointInTime, that.pointInTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, key, anchorDate, dateFromInclusive, dateToInclusive, pointInTime);
    }
}
