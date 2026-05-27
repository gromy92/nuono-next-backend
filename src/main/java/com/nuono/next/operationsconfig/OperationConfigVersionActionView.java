package com.nuono.next.operationsconfig;

public class OperationConfigVersionActionView {
    private final String action;
    private final String label;
    private final boolean enabled;
    private final String disabledReason;

    public OperationConfigVersionActionView(String action, String label, boolean enabled, String disabledReason) {
        this.action = action;
        this.label = label;
        this.enabled = enabled;
        this.disabledReason = disabledReason;
    }

    public String getAction() {
        return action;
    }

    public String getLabel() {
        return label;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisabledReason() {
        return disabledReason;
    }
}
