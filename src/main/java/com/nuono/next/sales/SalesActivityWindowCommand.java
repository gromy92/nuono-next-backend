package com.nuono.next.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SalesActivityWindowCommand {

    private final Long id;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String name;
    private final String activityType;
    private final String categoryScope;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final BigDecimal factor;
    private final boolean enabled;
    private final Long operatorUserId;

    public SalesActivityWindowCommand(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String name,
            String activityType,
            String categoryScope,
            LocalDate dateFrom,
            LocalDate dateTo,
            BigDecimal factor,
            boolean enabled,
            Long operatorUserId
    ) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.name = name;
        this.activityType = activityType;
        this.categoryScope = categoryScope;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.factor = factor;
        this.enabled = enabled;
        this.operatorUserId = operatorUserId;
    }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public String getName() { return name; }
    public String getActivityType() { return activityType; }
    public String getCategoryScope() { return categoryScope; }
    public LocalDate getDateFrom() { return dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public BigDecimal getFactor() { return factor; }
    public boolean isEnabled() { return enabled; }
    public Long getOperatorUserId() { return operatorUserId; }
}
