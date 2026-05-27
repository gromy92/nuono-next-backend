package com.nuono.next.noonsync;

import java.time.LocalDate;

public class NoonSyncAdminDiagnosticFilter {

    private final NoonSyncDataDomain dataDomain;
    private final NoonSyncTaskStatus status;
    private final NoonSyncTriggerMode triggerMode;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;

    public NoonSyncAdminDiagnosticFilter(
            NoonSyncDataDomain dataDomain,
            NoonSyncTaskStatus status,
            NoonSyncTriggerMode triggerMode,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        this.dataDomain = dataDomain;
        this.status = status;
        this.triggerMode = triggerMode;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
    }

    public static NoonSyncAdminDiagnosticFilter empty() {
        return new NoonSyncAdminDiagnosticFilter(null, null, null, null, null);
    }

    public NoonSyncDataDomain getDataDomain() {
        return dataDomain;
    }

    public NoonSyncTaskStatus getStatus() {
        return status;
    }

    public NoonSyncTriggerMode getTriggerMode() {
        return triggerMode;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }
}
