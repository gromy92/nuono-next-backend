package com.nuono.next.noonads;

import java.time.LocalDate;
import org.springframework.util.StringUtils;

public class NoonAdvertisingDashboardQuery {
    private final Long ownerUserId;
    private final String projectCode;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;

    public NoonAdvertisingDashboardQuery(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        this.ownerUserId = ownerUserId;
        this.projectCode = normalize(projectCode);
        this.storeCode = normalize(storeCode);
        this.siteCode = normalize(siteCode);
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public Long getOwnerUserId() { return ownerUserId; }
    public String getProjectCode() { return projectCode; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public LocalDate getDateFrom() { return dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
}
