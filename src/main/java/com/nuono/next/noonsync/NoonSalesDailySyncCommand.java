package com.nuono.next.noonsync;

import java.time.LocalDate;
import java.util.Objects;

public class NoonSalesDailySyncCommand {

    private final NoonSyncScope scope;
    private final LocalDate latestAvailableSalesDay;
    private final Long requestedBy;

    public NoonSalesDailySyncCommand(
            NoonSyncScope scope,
            LocalDate latestAvailableSalesDay,
            Long requestedBy
    ) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.latestAvailableSalesDay = Objects.requireNonNull(latestAvailableSalesDay, "latestAvailableSalesDay");
        this.requestedBy = requestedBy;
    }

    public NoonSyncScope getScope() {
        return scope;
    }

    public LocalDate getLatestAvailableSalesDay() {
        return latestAvailableSalesDay;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public boolean isRollingCorrection() {
        return false;
    }
}
