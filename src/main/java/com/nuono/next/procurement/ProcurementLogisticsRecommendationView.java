package com.nuono.next.procurement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProcurementLogisticsRecommendationView {

    private String mode;
    private boolean ready;
    private String status;
    private String message;
    private Long ownerUserId;
    private Long demandItemId;
    private String transportMode;
    private String destinationCountry;
    private String destinationNode;
    private String forwarderCode;
    private String forwarderName;
    private String serviceLineKey;
    private String serviceLineName;
    private String matchedCargoCategoryKey;
    private String matchedCargoCategoryName;
    private BigDecimal estimatedBaseCost;
    private BigDecimal estimatedCost;
    private BigDecimal chargeableUnit;
    private String currency;
    private String billingUnit;
    private String leadTimeText;
    private String winnerReason;
    private String calculationBreakdown;
    private List<RiskPromptView> riskPrompts = new ArrayList<>();
    private List<EvidenceView> sourceEvidence = new ArrayList<>();
    private List<OptionView> comparedOptions = new ArrayList<>();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getDemandItemId() {
        return demandItemId;
    }

    public void setDemandItemId(Long demandItemId) {
        this.demandItemId = demandItemId;
    }

    public String getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(String transportMode) {
        this.transportMode = transportMode;
    }

    public String getDestinationCountry() {
        return destinationCountry;
    }

    public void setDestinationCountry(String destinationCountry) {
        this.destinationCountry = destinationCountry;
    }

    public String getDestinationNode() {
        return destinationNode;
    }

    public void setDestinationNode(String destinationNode) {
        this.destinationNode = destinationNode;
    }

    public String getForwarderCode() {
        return forwarderCode;
    }

    public void setForwarderCode(String forwarderCode) {
        this.forwarderCode = forwarderCode;
    }

    public String getForwarderName() {
        return forwarderName;
    }

    public void setForwarderName(String forwarderName) {
        this.forwarderName = forwarderName;
    }

    public String getServiceLineKey() {
        return serviceLineKey;
    }

    public void setServiceLineKey(String serviceLineKey) {
        this.serviceLineKey = serviceLineKey;
    }

    public String getServiceLineName() {
        return serviceLineName;
    }

    public void setServiceLineName(String serviceLineName) {
        this.serviceLineName = serviceLineName;
    }

    public String getMatchedCargoCategoryKey() {
        return matchedCargoCategoryKey;
    }

    public void setMatchedCargoCategoryKey(String matchedCargoCategoryKey) {
        this.matchedCargoCategoryKey = matchedCargoCategoryKey;
    }

    public String getMatchedCargoCategoryName() {
        return matchedCargoCategoryName;
    }

    public void setMatchedCargoCategoryName(String matchedCargoCategoryName) {
        this.matchedCargoCategoryName = matchedCargoCategoryName;
    }

    public BigDecimal getEstimatedBaseCost() {
        return estimatedBaseCost;
    }

    public void setEstimatedBaseCost(BigDecimal estimatedBaseCost) {
        this.estimatedBaseCost = estimatedBaseCost;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public BigDecimal getChargeableUnit() {
        return chargeableUnit;
    }

    public void setChargeableUnit(BigDecimal chargeableUnit) {
        this.chargeableUnit = chargeableUnit;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getBillingUnit() {
        return billingUnit;
    }

    public void setBillingUnit(String billingUnit) {
        this.billingUnit = billingUnit;
    }

    public String getLeadTimeText() {
        return leadTimeText;
    }

    public void setLeadTimeText(String leadTimeText) {
        this.leadTimeText = leadTimeText;
    }

    public String getWinnerReason() {
        return winnerReason;
    }

    public void setWinnerReason(String winnerReason) {
        this.winnerReason = winnerReason;
    }

    public String getCalculationBreakdown() {
        return calculationBreakdown;
    }

    public void setCalculationBreakdown(String calculationBreakdown) {
        this.calculationBreakdown = calculationBreakdown;
    }

    public List<RiskPromptView> getRiskPrompts() {
        return riskPrompts;
    }

    public void setRiskPrompts(List<RiskPromptView> riskPrompts) {
        this.riskPrompts = riskPrompts == null ? new ArrayList<>() : riskPrompts;
    }

    public List<EvidenceView> getSourceEvidence() {
        return sourceEvidence;
    }

    public void setSourceEvidence(List<EvidenceView> sourceEvidence) {
        this.sourceEvidence = sourceEvidence == null ? new ArrayList<>() : sourceEvidence;
    }

    public List<OptionView> getComparedOptions() {
        return comparedOptions;
    }

    public void setComparedOptions(List<OptionView> comparedOptions) {
        this.comparedOptions = comparedOptions == null ? new ArrayList<>() : comparedOptions;
    }

    public static class EvidenceView {

        private String sourceType;
        private Long sourceTaskId;
        private Long sourceResultId;
        private Long sourceVersionId;
        private Long sourceVersionItemId;
        private String sourceFileName;
        private String sourceLocator;

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public Long getSourceTaskId() {
            return sourceTaskId;
        }

        public void setSourceTaskId(Long sourceTaskId) {
            this.sourceTaskId = sourceTaskId;
        }

        public Long getSourceResultId() {
            return sourceResultId;
        }

        public void setSourceResultId(Long sourceResultId) {
            this.sourceResultId = sourceResultId;
        }

        public Long getSourceVersionId() {
            return sourceVersionId;
        }

        public void setSourceVersionId(Long sourceVersionId) {
            this.sourceVersionId = sourceVersionId;
        }

        public Long getSourceVersionItemId() {
            return sourceVersionItemId;
        }

        public void setSourceVersionItemId(Long sourceVersionItemId) {
            this.sourceVersionItemId = sourceVersionItemId;
        }

        public String getSourceFileName() {
            return sourceFileName;
        }

        public void setSourceFileName(String sourceFileName) {
            this.sourceFileName = sourceFileName;
        }

        public String getSourceLocator() {
            return sourceLocator;
        }

        public void setSourceLocator(String sourceLocator) {
            this.sourceLocator = sourceLocator;
        }
    }

    public static class RiskPromptView {

        private String severity;
        private String message;
        private boolean manualConfirmRequired;

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public boolean isManualConfirmRequired() {
            return manualConfirmRequired;
        }

        public void setManualConfirmRequired(boolean manualConfirmRequired) {
            this.manualConfirmRequired = manualConfirmRequired;
        }
    }

    public static class OptionView {

        private String forwarderCode;
        private String forwarderName;
        private String serviceLineKey;
        private String serviceLineName;
        private String cargoCategoryKey;
        private String cargoCategoryName;
        private BigDecimal estimatedBaseCost;
        private BigDecimal estimatedCost;
        private BigDecimal chargeableUnit;
        private String currency;
        private String billingUnit;
        private String reason;

        public String getForwarderCode() {
            return forwarderCode;
        }

        public void setForwarderCode(String forwarderCode) {
            this.forwarderCode = forwarderCode;
        }

        public String getForwarderName() {
            return forwarderName;
        }

        public void setForwarderName(String forwarderName) {
            this.forwarderName = forwarderName;
        }

        public String getServiceLineKey() {
            return serviceLineKey;
        }

        public void setServiceLineKey(String serviceLineKey) {
            this.serviceLineKey = serviceLineKey;
        }

        public String getServiceLineName() {
            return serviceLineName;
        }

        public void setServiceLineName(String serviceLineName) {
            this.serviceLineName = serviceLineName;
        }

        public String getCargoCategoryKey() {
            return cargoCategoryKey;
        }

        public void setCargoCategoryKey(String cargoCategoryKey) {
            this.cargoCategoryKey = cargoCategoryKey;
        }

        public String getCargoCategoryName() {
            return cargoCategoryName;
        }

        public void setCargoCategoryName(String cargoCategoryName) {
            this.cargoCategoryName = cargoCategoryName;
        }

        public BigDecimal getEstimatedBaseCost() {
            return estimatedBaseCost;
        }

        public void setEstimatedBaseCost(BigDecimal estimatedBaseCost) {
            this.estimatedBaseCost = estimatedBaseCost;
        }

        public BigDecimal getEstimatedCost() {
            return estimatedCost;
        }

        public void setEstimatedCost(BigDecimal estimatedCost) {
            this.estimatedCost = estimatedCost;
        }

        public BigDecimal getChargeableUnit() {
            return chargeableUnit;
        }

        public void setChargeableUnit(BigDecimal chargeableUnit) {
            this.chargeableUnit = chargeableUnit;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getBillingUnit() {
            return billingUnit;
        }

        public void setBillingUnit(String billingUnit) {
            this.billingUnit = billingUnit;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
