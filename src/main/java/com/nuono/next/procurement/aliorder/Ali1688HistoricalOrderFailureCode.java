package com.nuono.next.procurement.aliorder;

import java.util.Arrays;

public enum Ali1688HistoricalOrderFailureCode {
    AUTH_REQUIRED("auth_required", false, true),
    PROVIDER_NOT_CONFIGURED("provider_not_configured", false, true),
    PROVIDER_UNAVAILABLE("provider_unavailable", true, false),
    RATE_LIMITED("rate_limited", true, false),
    BLOCKED_BY_RISK_CONTROL("blocked_by_risk_control", false, true),
    MISSING_FIELDS("missing_fields", true, false),
    PARTIAL_SUCCESS("partial_success", true, false),
    UNEXPECTED_RESPONSE("unexpected_response", true, false);

    private final String code;
    private final boolean retryable;
    private final boolean requiresManualAction;

    Ali1688HistoricalOrderFailureCode(String code, boolean retryable, boolean requiresManualAction) {
        this.code = code;
        this.retryable = retryable;
        this.requiresManualAction = requiresManualAction;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isRequiresManualAction() {
        return requiresManualAction;
    }

    public static Ali1688HistoricalOrderFailureCode fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNEXPECTED_RESPONSE;
        }
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElse(UNEXPECTED_RESPONSE);
    }
}
