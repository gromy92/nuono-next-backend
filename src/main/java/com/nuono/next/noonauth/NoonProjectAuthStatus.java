package com.nuono.next.noonauth;

public enum NoonProjectAuthStatus {
    HEALTHY,
    REAUTH_REQUIRED,
    RECOVERING,
    MANUAL_HOLD,
    RECOVERY_DISABLED;

    public boolean blocksProviderCalls() {
        return this == REAUTH_REQUIRED || this == RECOVERING || this == MANUAL_HOLD;
    }
}
