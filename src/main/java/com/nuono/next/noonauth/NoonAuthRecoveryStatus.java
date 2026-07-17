package com.nuono.next.noonauth;

import java.util.EnumSet;

public enum NoonAuthRecoveryStatus {
    WAITING_PREDECESSOR,
    COALESCING,
    AUTHENTICATING,
    WAITING_EMAIL,
    VALIDATING,
    APPLYING_PROJECTS,
    RECOVERING_PULLS,
    WAITING_COOLDOWN,
    MANUAL_HOLD,
    COMPLETED,
    FAILED_FINAL,
    CANCELLED;

    private static final EnumSet<NoonAuthRecoveryStatus> TERMINAL = EnumSet.of(
            COMPLETED,
            FAILED_FINAL,
            CANCELLED
    );

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean isActive() {
        return !isTerminal();
    }
}
