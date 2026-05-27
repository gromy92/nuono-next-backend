package com.nuono.next.operationsconfig;

public class OperationConfigBundleDraftRequest {

    private String displayName;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public OperationConfigBundleDraftCommand toCommand() {
        return new OperationConfigBundleDraftCommand(displayName);
    }
}
