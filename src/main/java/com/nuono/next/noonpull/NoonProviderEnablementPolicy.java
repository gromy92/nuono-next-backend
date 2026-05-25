package com.nuono.next.noonpull;

public class NoonProviderEnablementPolicy {
    private final boolean enabled;
    private final String disabledFailureType;

    private NoonProviderEnablementPolicy(boolean enabled, String disabledFailureType) {
        this.enabled = enabled;
        this.disabledFailureType = disabledFailureType;
    }

    public static NoonProviderEnablementPolicy disabledByDefault() {
        return new NoonProviderEnablementPolicy(false, "provider_not_configured");
    }

    public static NoonProviderEnablementPolicy enabled() {
        return new NoonProviderEnablementPolicy(true, null);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String disabledFailureType() {
        return disabledFailureType;
    }
}
