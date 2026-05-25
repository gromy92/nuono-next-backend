package com.nuono.next.logisticsquote;

import java.math.BigDecimal;

public class LogisticsBillingRuleFact {

    private final String naturalKey;
    private final String forwarderCode;
    private final String serviceLineKey;
    private final String cargoCategoryKey;
    private final String ruleName;
    private final String ruleType;
    private final String conditionText;
    private final String structuredField;
    private final String operator;
    private final BigDecimal thresholdValue;
    private final String thresholdUnit;
    private final String actionText;
    private final String severity;
    private final String status;
    private final LogisticsQuoteFactSourceLineage sourceLineage;

    public LogisticsBillingRuleFact(
            String naturalKey,
            String forwarderCode,
            String serviceLineKey,
            String cargoCategoryKey,
            String ruleName,
            String ruleType,
            String conditionText,
            String structuredField,
            String operator,
            BigDecimal thresholdValue,
            String thresholdUnit,
            String actionText,
            String severity,
            String status,
            LogisticsQuoteFactSourceLineage sourceLineage
    ) {
        this.naturalKey = naturalKey;
        this.forwarderCode = forwarderCode;
        this.serviceLineKey = serviceLineKey;
        this.cargoCategoryKey = cargoCategoryKey;
        this.ruleName = ruleName;
        this.ruleType = ruleType;
        this.conditionText = conditionText;
        this.structuredField = structuredField;
        this.operator = operator;
        this.thresholdValue = thresholdValue;
        this.thresholdUnit = thresholdUnit;
        this.actionText = actionText;
        this.severity = severity;
        this.status = status;
        this.sourceLineage = sourceLineage;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public String getForwarderCode() {
        return forwarderCode;
    }

    public String getServiceLineKey() {
        return serviceLineKey;
    }

    public String getCargoCategoryKey() {
        return cargoCategoryKey;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getRuleType() {
        return ruleType;
    }

    public String getConditionText() {
        return conditionText;
    }

    public String getStructuredField() {
        return structuredField;
    }

    public String getOperator() {
        return operator;
    }

    public BigDecimal getThresholdValue() {
        return thresholdValue;
    }

    public String getThresholdUnit() {
        return thresholdUnit;
    }

    public String getActionText() {
        return actionText;
    }

    public String getSeverity() {
        return severity;
    }

    public String getStatus() {
        return status;
    }

    public LogisticsQuoteFactSourceLineage getSourceLineage() {
        return sourceLineage;
    }
}
