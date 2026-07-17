package com.nuono.next.noonauth.gateway;

public enum NoonAuthRecoveryFailureCode {
    OTP_NOT_FOUND(true, false),
    OTP_INVALID_OR_EXPIRED(true, false),
    SEND_RESULT_UNKNOWN(true, false),
    SEND_RATE_LIMITED(false, false),
    SEND_RISK_BLOCKED(false, true),
    MAILBOX_AUTH_FAILED(false, true),
    MAILBOX_UNAVAILABLE(false, false),
    IDENTITY_AUTH_FAILED(false, true),
    INTERNAL_FAILURE(false, false);

    private final boolean resendEligible;
    private final boolean manualHold;

    NoonAuthRecoveryFailureCode(boolean resendEligible, boolean manualHold) {
        this.resendEligible = resendEligible;
        this.manualHold = manualHold;
    }

    public boolean isResendEligible() {
        return resendEligible;
    }

    public boolean isManualHold() {
        return manualHold;
    }
}
