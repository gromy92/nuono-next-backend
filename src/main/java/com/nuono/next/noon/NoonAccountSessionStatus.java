package com.nuono.next.noon;

/**
 * The state of Nuono's single configured Noon account.
 *
 * <p>This is deliberately account-scoped. A Project consumes the session created by the account;
 * it never owns an OTP lifecycle of its own.</p>
 */
public enum NoonAccountSessionStatus {
    UNKNOWN,
    ACTIVE,
    MANUAL_OTP_REQUIRED,
    OTP_SENT,
    MANUAL_ACTION_REQUIRED
}
