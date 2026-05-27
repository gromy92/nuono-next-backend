package com.nuono.next.sales;

import java.time.LocalDate;
import java.util.Objects;

public class SalesImportBatchQuery {

    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;

    public SalesImportBatchQuery(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
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

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof SalesImportBatchQuery)) {
            return false;
        }
        SalesImportBatchQuery that = (SalesImportBatchQuery) object;
        return Objects.equals(ownerUserId, that.ownerUserId)
                && Objects.equals(storeCode, that.storeCode)
                && Objects.equals(siteCode, that.siteCode)
                && Objects.equals(dateFrom, that.dateFrom)
                && Objects.equals(dateTo, that.dateTo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerUserId, storeCode, siteCode, dateFrom, dateTo);
    }
}
