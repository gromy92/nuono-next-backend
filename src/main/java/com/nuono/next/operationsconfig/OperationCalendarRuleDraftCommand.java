package com.nuono.next.operationsconfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class OperationCalendarRuleDraftCommand {

    private final Long id;
    private final List<Long> bossUserIds;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String ruleName;
    private final String activityType;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final String recurringExpression;
    private final String targetScopeType;
    private final String targetScopeValue;
    private final BigDecimal factorValue;
    private final String factorPurpose;
    private final boolean enabled;
    private final Long bundleVersionId;

    public OperationCalendarRuleDraftCommand(
            Long id,
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleName,
            String activityType,
            LocalDate dateFrom,
            LocalDate dateTo,
            String recurringExpression,
            String targetScopeType,
            String targetScopeValue,
            BigDecimal factorValue,
            String factorPurpose,
            boolean enabled
    ) {
        this(
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
                null
        );
    }

    public OperationCalendarRuleDraftCommand(
            Long id,
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleName,
            String activityType,
            LocalDate dateFrom,
            LocalDate dateTo,
            String recurringExpression,
            String targetScopeType,
            String targetScopeValue,
            BigDecimal factorValue,
            String factorPurpose,
            boolean enabled,
            Long bundleVersionId
    ) {
        this.id = id;
        this.bossUserIds = bossUserIds;
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.ruleName = ruleName;
        this.activityType = activityType;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.recurringExpression = recurringExpression;
        this.targetScopeType = targetScopeType;
        this.targetScopeValue = targetScopeValue;
        this.factorValue = factorValue;
        this.factorPurpose = factorPurpose;
        this.enabled = enabled;
        this.bundleVersionId = bundleVersionId;
    }

    public OperationCalendarRuleDraftCommand withIdAndFactor(Long nextId, BigDecimal nextFactorValue) {
        return new OperationCalendarRuleDraftCommand(
                nextId,
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
                nextFactorValue,
                factorPurpose,
                enabled,
                bundleVersionId
        );
    }

    public OperationCalendarRuleDraftCommand withNameAndEnabled(String nextRuleName, boolean nextEnabled) {
        return new OperationCalendarRuleDraftCommand(
                id,
                bossUserIds,
                ownerUserId,
                storeCode,
                siteCode,
                nextRuleName,
                activityType,
                dateFrom,
                dateTo,
                recurringExpression,
                targetScopeType,
                targetScopeValue,
                factorValue,
                factorPurpose,
                nextEnabled,
                bundleVersionId
        );
    }

    public OperationCalendarRuleDraftCommand withBundleVersionId(Long nextBundleVersionId) {
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
                nextBundleVersionId
        );
    }

    public Long getId() { return id; }
    public List<Long> getBossUserIds() { return bossUserIds; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public String getRuleName() { return ruleName; }
    public String getActivityType() { return activityType; }
    public LocalDate getDateFrom() { return dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public String getRecurringExpression() { return recurringExpression; }
    public String getTargetScopeType() { return targetScopeType; }
    public String getTargetScopeValue() { return targetScopeValue; }
    public BigDecimal getFactorValue() { return factorValue; }
    public String getFactorPurpose() { return factorPurpose; }
    public boolean isEnabled() { return enabled; }
    public Long getBundleVersionId() { return bundleVersionId; }
}
