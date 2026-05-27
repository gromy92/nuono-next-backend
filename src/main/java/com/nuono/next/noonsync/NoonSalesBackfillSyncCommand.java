package com.nuono.next.noonsync;

import java.time.LocalDate;
import java.util.Objects;

public class NoonSalesBackfillSyncCommand {

    private final NoonSyncScope scope;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final NoonSalesBackfillReason reason;
    private final Long requestedBy;

    public NoonSalesBackfillSyncCommand(
            NoonSyncScope scope,
            LocalDate dateFrom,
            LocalDate dateTo,
            NoonSalesBackfillReason reason,
            Long requestedBy
    ) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.dateFrom = Objects.requireNonNull(dateFrom, "dateFrom");
        this.dateTo = Objects.requireNonNull(dateTo, "dateTo");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.requestedBy = requestedBy;
        if (dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("Sales backfill dateFrom must not be after dateTo.");
        }
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

    public NoonSalesBackfillReason getReason() {
        return reason;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }
}
