package com.nuono.next.operationsconfig;

public class OperationConfigVersionDisableRequest {
    private String reason;

    public OperationConfigVersionDisableRequest() {
    }

    public OperationConfigVersionDisableRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
