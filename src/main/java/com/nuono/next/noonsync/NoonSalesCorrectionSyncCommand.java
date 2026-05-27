package com.nuono.next.noonsync;

import java.time.LocalDate;
import java.util.Objects;

public class NoonSalesCorrectionSyncCommand {

    private final NoonSyncScope scope;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final int windowDays;
    private final Long requestedBy;

    public NoonSalesCorrectionSyncCommand(
            NoonSyncScope scope,
            LocalDate dateFrom,
            LocalDate dateTo,
            int windowDays,
            Long requestedBy
    ) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.dateFrom = Objects.requireNonNull(dateFrom, "dateFrom");
        this.dateTo = Objects.requireNonNull(dateTo, "dateTo");
        if (dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("Sales correction dateFrom must not be after dateTo.");
        }
        if (windowDays < NoonSalesCorrectionPolicy.MIN_FIRST_VERSION_WINDOW_DAYS
                || windowDays > NoonSalesCorrectionPolicy.MAX_FIRST_VERSION_WINDOW_DAYS) {
            throw new IllegalArgumentException("Sales correction window must stay within the first-version 30-60 day range.");
        }
        this.windowDays = windowDays;
        this.requestedBy = requestedBy;
    }

    public NoonSyncScope getScope() {
        return scope;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public int getWindowDays() {
        return windowDays;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }
}
