package com.nuono.next.noonpull;

import java.util.Locale;

public enum NoonPullFailureType {
    PROVIDER_UNAVAILABLE,
    PROVIDER_NOT_CONFIGURED,
    INVALID_PROJECT_CODE,
    AUTH_REQUIRED,
    EMPTY_REPORT,
    EMPTY_REPORT_PENDING_CONFIRMATION,
    REPORT_NOT_READY,
    PROVIDER_RETENTION_LIMIT,
    MISSING_COLUMNS,
    MAPPING_FAILED,
    TIMEOUT,
    RATE_LIMITED,
    BLOCKED_BY_RISK_CONTROL,
    CAPTCHA_REQUIRED,
    PARTIAL_SUCCESS,
    UNKNOWN_FAILURE;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
