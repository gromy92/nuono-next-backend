package com.nuono.next.logisticsquote;

public class LogisticsQuoteOperationPriceAdjustmentCommand {

    private String targetType;

    private Long targetId;

    private String numericField;

    private Double adjustedValue;

    private String reason;

    private Long operatorUserId;

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getNumericField() {
        return numericField;
    }

    public void setNumericField(String numericField) {
        this.numericField = numericField;
    }

    public Double getAdjustedValue() {
        return adjustedValue;
    }

    public void setAdjustedValue(Double adjustedValue) {
        this.adjustedValue = adjustedValue;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}
