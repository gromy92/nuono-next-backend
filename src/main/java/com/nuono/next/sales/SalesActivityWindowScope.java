package com.nuono.next.sales;

import java.time.LocalDate;

public class SalesActivityWindowScope {

    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;

    public SalesActivityWindowScope(Long ownerUserId, String storeCode, String siteCode, LocalDate dateFrom, LocalDate dateTo) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
    }

    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public LocalDate getDateFrom() { return dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
}
