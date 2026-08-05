package com.nuono.next.datapull.report;

import java.time.LocalDate;
import java.util.Objects;

/** Strict inclusive date window decoded from the schedule-owned business key. */
public final class NoonReportWindow {
    private final LocalDate dateFrom;
    private final LocalDate dateTo;

    public NoonReportWindow(LocalDate dateFrom, LocalDate dateTo) {
        this.dateFrom = Objects.requireNonNull(dateFrom, "dateFrom");
        this.dateTo = Objects.requireNonNull(dateTo, "dateTo");
        if (dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("report dateFrom must not be after dateTo");
        }
    }

    public LocalDate getDateFrom() { return dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
}
