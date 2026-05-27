package com.nuono.next.sales;

import java.math.BigDecimal;

public class SalesActivityWindowRequest {

    private Long id;
    private String storeCode;
    private String siteCode;
    private String name;
    private String activityType;
    private String categoryScope;
    private String dateFrom;
    private String dateTo;
    private BigDecimal factor;
    private boolean enabled = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }
    public String getCategoryScope() { return categoryScope; }
    public void setCategoryScope(String categoryScope) { this.categoryScope = categoryScope; }
    public String getDateFrom() { return dateFrom; }
    public void setDateFrom(String dateFrom) { this.dateFrom = dateFrom; }
    public String getDateTo() { return dateTo; }
    public void setDateTo(String dateTo) { this.dateTo = dateTo; }
    public BigDecimal getFactor() { return factor; }
    public void setFactor(BigDecimal factor) { this.factor = factor; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
