package com.nuono.next.sales;

import java.time.LocalDate;

public class LegacySalesBackfillCommand {

    private final Long ownerUserId;
    private final Long logicalStoreId;
    private final String storeCode;
    private final String siteCode;
    private final Long legacyOwnerUserId;
    private final String legacyStoreCode;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final Long requestedBy;

    public LegacySalesBackfillCommand(
            Long ownerUserId,
            Long logicalStoreId,
            String storeCode,
            String siteCode,
            Long legacyOwnerUserId,
            String legacyStoreCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long requestedBy
    ) {
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.legacyOwnerUserId = legacyOwnerUserId;
        this.legacyStoreCode = legacyStoreCode;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.requestedBy = requestedBy;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public Long getLogicalStoreId() {
        return logicalStoreId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public Long getLegacyOwnerUserId() {
        return legacyOwnerUserId;
    }

    public String getLegacyStoreCode() {
        return legacyStoreCode;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }
}
