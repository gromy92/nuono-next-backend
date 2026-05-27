package com.nuono.next.operationsconfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class OperationCalendarRuleDraftRequest {

    private Long id;
    private List<Long> bossUserIds;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private String ruleName;
    private String activityType;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String recurringExpression;
    private String targetScopeType;
    private String targetScopeValue;
    private BigDecimal factorValue;
    private String factorPurpose;
    private boolean enabled = true;
    private Long bundleVersionId;

    public OperationCalendarRuleDraftCommand toCommand() {
        return new OperationCalendarRuleDraftCommand(
                id,
                bossUserIds,
                ownerUserId,
                storeCode,
                siteCode,
                ruleName,
                activityType,
                dateFrom,
                dateTo,
                recurringExpression,
                targetScopeType,
                targetScopeValue,
                factorValue,
                factorPurpose,
                enabled,
                bundleVersionId
        );
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public List<Long> getBossUserIds() { return bossUserIds; }
    public void setBossUserIds(List<Long> bossUserIds) { this.bossUserIds = bossUserIds; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }
    public LocalDate getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
    public String getRecurringExpression() { return recurringExpression; }
    public void setRecurringExpression(String recurringExpression) { this.recurringExpression = recurringExpression; }
    public String getTargetScopeType() { return targetScopeType; }
    public void setTargetScopeType(String targetScopeType) { this.targetScopeType = targetScopeType; }
    public String getTargetScopeValue() { return targetScopeValue; }
    public void setTargetScopeValue(String targetScopeValue) { this.targetScopeValue = targetScopeValue; }
    public BigDecimal getFactorValue() { return factorValue; }
    public void setFactorValue(BigDecimal factorValue) { this.factorValue = factorValue; }
    public String getFactorPurpose() { return factorPurpose; }
    public void setFactorPurpose(String factorPurpose) { this.factorPurpose = factorPurpose; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Long getBundleVersionId() { return bundleVersionId; }
    public void setBundleVersionId(Long bundleVersionId) { this.bundleVersionId = bundleVersionId; }
}
