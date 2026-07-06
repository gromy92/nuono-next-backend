package com.nuono.next.salesforecast;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SalesForecastCalendarFactorImpactView {

    private final String ruleName;
    private final String activityType;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final String targetScopeType;
    private final String targetScopeValue;
    private final BigDecimal factorValue;
    private final String matchedScopeLabel;
    private final int affectedDays30;
    private final int affectedDays60;
    private final int affectedDays90;

    public SalesForecastCalendarFactorImpactView(
            String ruleName,
            String activityType,
            LocalDate dateFrom,
            LocalDate dateTo,
            String targetScopeType,
            String targetScopeValue,
            BigDecimal factorValue,
            String matchedScopeLabel,
            int affectedDays30,
            int affectedDays60,
            int affectedDays90
    ) {
        this.ruleName = ruleName;
        this.activityType = activityType;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.targetScopeType = targetScopeType;
        this.targetScopeValue = targetScopeValue;
        this.factorValue = factorValue;
        this.matchedScopeLabel = matchedScopeLabel;
        this.affectedDays30 = affectedDays30;
        this.affectedDays60 = affectedDays60;
        this.affectedDays90 = affectedDays90;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getActivityType() {
        return activityType;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public String getTargetScopeType() {
        return targetScopeType;
    }

    public String getTargetScopeValue() {
        return targetScopeValue;
    }

    public BigDecimal getFactorValue() {
        return factorValue;
    }

    public String getMatchedScopeLabel() {
        return matchedScopeLabel;
    }

    public int getAffectedDays30() {
        return affectedDays30;
    }

    public int getAffectedDays60() {
        return affectedDays60;
    }

    public int getAffectedDays90() {
        return affectedDays90;
    }
}
