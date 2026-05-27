package com.nuono.next.operationsconfig;

import java.time.LocalDateTime;

public class OperationConfigVersionAuditView {
    private final Long operatorUserId;
    private final String operatorLabel;
    private final String operation;
    private final String fromStatus;
    private final String toStatus;
    private final String reason;
    private final LocalDateTime operatedAt;

    public OperationConfigVersionAuditView(
            Long operatorUserId,
            String operatorLabel,
            String operation,
            String fromStatus,
            String toStatus,
            String reason,
            LocalDateTime operatedAt
    ) {
        this.operatorUserId = operatorUserId;
        this.operatorLabel = operatorLabel;
        this.operation = operation;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.operatedAt = operatedAt;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public String getOperatorLabel() {
        return operatorLabel;
    }

    public String getOperation() {
        return operation;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getOperatedAt() {
        return operatedAt;
    }
}
