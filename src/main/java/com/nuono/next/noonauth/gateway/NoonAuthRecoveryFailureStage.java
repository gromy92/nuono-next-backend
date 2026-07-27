package com.nuono.next.noonauth.gateway;

public enum NoonAuthRecoveryFailureStage {
    IDENTITY_PREPARATION,
    MAILBOX_SNAPSHOT,
    OTP_SEND,
    MAILBOX_POLLING,
    OTP_VALIDATION,
    PROJECT_SESSION_CREATE,
    WHOAMI_VALIDATION,
    CATALOG_VALIDATION
}
