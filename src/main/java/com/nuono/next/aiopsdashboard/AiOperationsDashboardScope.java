package com.nuono.next.aiopsdashboard;

import java.time.LocalDate;

public class AiOperationsDashboardScope {

    private final Long ownerUserId;
    private final Long operatorUserId;
    private final String storeCode;
    private final String siteCode;
    private final String datePreset;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;

    public AiOperationsDashboardScope(
            Long ownerUserId,
            Long operatorUserId,
            String storeCode,
            String siteCode,
            String datePreset,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        this.ownerUserId = ownerUserId;
        this.operatorUserId = operatorUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.datePreset = datePreset;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public String getDatePreset() {
        return datePreset;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }
}
