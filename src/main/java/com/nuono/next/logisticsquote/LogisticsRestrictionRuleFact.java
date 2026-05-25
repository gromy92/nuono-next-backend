package com.nuono.next.logisticsquote;

public class LogisticsRestrictionRuleFact {

    private final String naturalKey;
    private final String forwarderCode;
    private final String serviceLineKey;
    private final String restrictionType;
    private final String itemText;
    private final String requirementText;
    private final String applicabilityScope;
    private final String severity;
    private final boolean manualConfirmRequired;
    private final String status;
    private final LogisticsQuoteFactSourceLineage sourceLineage;

    public LogisticsRestrictionRuleFact(
            String naturalKey,
            String forwarderCode,
            String serviceLineKey,
            String restrictionType,
            String itemText,
            String requirementText,
            String applicabilityScope,
            String severity,
            boolean manualConfirmRequired,
            String status,
            LogisticsQuoteFactSourceLineage sourceLineage
    ) {
        this.naturalKey = naturalKey;
        this.forwarderCode = forwarderCode;
        this.serviceLineKey = serviceLineKey;
        this.restrictionType = restrictionType;
        this.itemText = itemText;
        this.requirementText = requirementText;
        this.applicabilityScope = applicabilityScope;
        this.severity = severity;
        this.manualConfirmRequired = manualConfirmRequired;
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

    public String getRestrictionType() {
        return restrictionType;
    }

    public String getItemText() {
        return itemText;
    }

    public String getRequirementText() {
        return requirementText;
    }

    public String getApplicabilityScope() {
        return applicabilityScope;
    }

    public String getSeverity() {
        return severity;
    }

    public boolean isManualConfirmRequired() {
        return manualConfirmRequired;
    }

    public boolean isHardRestriction() {
        return "hard".equalsIgnoreCase(severity);
    }

    public boolean isWarning() {
        return "warning".equalsIgnoreCase(severity);
    }

    public boolean isInformational() {
        return "info".equalsIgnoreCase(severity);
    }

    public String getStatus() {
        return status;
    }

    public LogisticsQuoteFactSourceLineage getSourceLineage() {
        return sourceLineage;
    }
}
