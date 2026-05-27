package com.nuono.next.sales;

import java.time.LocalDate;

public class SalesSyncTaskCommand {

    private final Long ownerUserId;
    private final Long logicalStoreId;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final Long requestedBy;
    private final String triggerType;

    public SalesSyncTaskCommand(
            Long ownerUserId,
            Long logicalStoreId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long requestedBy,
            String triggerType
    ) {
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.requestedBy = requestedBy;
        this.triggerType = triggerType;
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

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public String getTriggerType() {
        return triggerType;
    }
}
