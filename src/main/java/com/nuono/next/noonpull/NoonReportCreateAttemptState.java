package com.nuono.next.noonpull;

final class NoonReportCreateAttemptState {
    static final String INTENT = "CREATE_INTENT";
    static final String UNKNOWN_OUTCOME =
            "provider unavailable: report_create_outcome_unknown_readback_unavailable";

    private NoonReportCreateAttemptState() {
    }

    static boolean isUnresolved(NoonPullTaskRecord task) {
        return task != null
                && !hasText(task.getReportExportId())
                && INTENT.equalsIgnoreCase(task.getReportExportStatus());
    }

    static boolean isDefiniteRejection(NoonPullFailureType failureType) {
        return failureType == NoonPullFailureType.AUTH_REQUIRED
                || failureType == NoonPullFailureType.RATE_LIMITED
                || failureType == NoonPullFailureType.CAPTCHA_REQUIRED
                || failureType == NoonPullFailureType.BLOCKED_BY_RISK_CONTROL
                || failureType == NoonPullFailureType.PROVIDER_NOT_CONFIGURED
                || failureType == NoonPullFailureType.INVALID_PROJECT_CODE
                || failureType == NoonPullFailureType.ADS_ADVERTISER_CONTEXT_MISMATCH;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
