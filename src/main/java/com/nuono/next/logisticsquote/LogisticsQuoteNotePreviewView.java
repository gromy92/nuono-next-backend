package com.nuono.next.logisticsquote;

import java.util.ArrayList;
import java.util.List;

public class LogisticsQuoteNotePreviewView {

    private boolean ready;

    private String message;

    private String normalizedNote;

    private List<RulePreviewView> rulePreviews = new ArrayList<>();

    private List<RestrictionPreviewView> restrictionPreviews = new ArrayList<>();

    private List<String> warnings = new ArrayList<>();

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getNormalizedNote() {
        return normalizedNote;
    }

    public void setNormalizedNote(String normalizedNote) {
        this.normalizedNote = normalizedNote;
    }

    public List<RulePreviewView> getRulePreviews() {
        return rulePreviews;
    }

    public void setRulePreviews(List<RulePreviewView> rulePreviews) {
        this.rulePreviews = rulePreviews;
    }

    public List<RestrictionPreviewView> getRestrictionPreviews() {
        return restrictionPreviews;
    }

    public void setRestrictionPreviews(List<RestrictionPreviewView> restrictionPreviews) {
        this.restrictionPreviews = restrictionPreviews;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public static class RulePreviewView {

        private String ruleName;

        private String ruleType;

        private String billingUnit;

        private Double unitPrice;

        private String triggerCondition;

        private String summary;

        public String getRuleName() {
            return ruleName;
        }

        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        public String getRuleType() {
            return ruleType;
        }

        public void setRuleType(String ruleType) {
            this.ruleType = ruleType;
        }

        public String getBillingUnit() {
            return billingUnit;
        }

        public void setBillingUnit(String billingUnit) {
            this.billingUnit = billingUnit;
        }

        public Double getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(Double unitPrice) {
            this.unitPrice = unitPrice;
        }

        public String getTriggerCondition() {
            return triggerCondition;
        }

        public void setTriggerCondition(String triggerCondition) {
            this.triggerCondition = triggerCondition;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }
    }

    public static class RestrictionPreviewView {

        private String restrictionType;

        private String operator;

        private String value;

        private String unit;

        private String severity;

        private String description;

        public String getRestrictionType() {
            return restrictionType;
        }

        public void setRestrictionType(String restrictionType) {
            this.restrictionType = restrictionType;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
