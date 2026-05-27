package com.nuono.next.noonsync;

import java.time.LocalDate;
import java.util.Objects;

public class NoonSyncTarget {

    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final String targetIdentity;

    private NoonSyncTarget(LocalDate dateFrom, LocalDate dateTo, String targetIdentity) {
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.targetIdentity = targetIdentity;
    }

    public static NoonSyncTarget dateRange(LocalDate dateFrom, LocalDate dateTo) {
        return new NoonSyncTarget(dateFrom, dateTo, null);
    }

    public static NoonSyncTarget identity(String targetIdentity) {
        return new NoonSyncTarget(null, null, targetIdentity);
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public String getTargetIdentity() {
        return targetIdentity;
    }

    boolean overlaps(NoonSyncTarget other) {
        if (other == null) {
            return false;
        }
        if (dateFrom != null && dateTo != null && other.dateFrom != null && other.dateTo != null) {
            return !dateTo.isBefore(other.dateFrom) && !other.dateTo.isBefore(dateFrom);
        }
        if (targetIdentity != null && other.targetIdentity != null) {
            return targetIdentity.equals(other.targetIdentity);
        }
        return Objects.equals(this, other);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NoonSyncTarget)) {
            return false;
        }
        NoonSyncTarget that = (NoonSyncTarget) other;
        return Objects.equals(dateFrom, that.dateFrom)
                && Objects.equals(dateTo, that.dateTo)
                && Objects.equals(targetIdentity, that.targetIdentity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dateFrom, dateTo, targetIdentity);
    }
}
