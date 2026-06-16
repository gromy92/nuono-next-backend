package com.nuono.next.noonpull;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NoonPullFailurePolicy {
    private final Clock clock;

    public NoonPullFailurePolicy() {
        this(Clock.systemUTC());
    }

    public NoonPullFailurePolicy(Clock clock) {
        this.clock = clock;
    }

    public NoonPullFailureType classify(String rawFailure) {
        String value = normalize(rawFailure);
        if (!StringUtils.hasText(value)) {
            return NoonPullFailureType.UNKNOWN_FAILURE;
        }
        if (value.contains("not configured") || value.contains("provider_not_configured")) {
            return NoonPullFailureType.PROVIDER_NOT_CONFIGURED;
        }
        if (value.contains("invalid project code") || value.contains("invalid_project_code")) {
            return NoonPullFailureType.INVALID_PROJECT_CODE;
        }
        if (value.contains("auth required") || value.contains("auth_required") || value.contains("401")
                || value.contains("unauthorized")) {
            return NoonPullFailureType.AUTH_REQUIRED;
        }
        if (value.contains("empty report pending confirmation")
                || value.contains("empty_report_pending_confirmation")) {
            return NoonPullFailureType.EMPTY_REPORT_PENDING_CONFIRMATION;
        }
        if (value.contains("empty report") || value.contains("empty_report")) {
            return NoonPullFailureType.EMPTY_REPORT;
        }
        if (value.contains("report not ready") || value.contains("report_not_ready") || value.contains("not ready")) {
            return NoonPullFailureType.REPORT_NOT_READY;
        }
        if (value.contains("retention") || value.contains("history_unavailable")) {
            return NoonPullFailureType.PROVIDER_RETENTION_LIMIT;
        }
        if (value.contains("missing columns") || value.contains("missing column") || value.contains("missing_columns")) {
            return NoonPullFailureType.MISSING_COLUMNS;
        }
        if (value.contains("mapping failed") || value.contains("mapping_failed")) {
            return NoonPullFailureType.MAPPING_FAILED;
        }
        if (value.contains("timeout") || value.contains("timed out")) {
            return NoonPullFailureType.TIMEOUT;
        }
        if (value.contains("429") || value.contains("too many requests") || value.contains("rate limited")
                || value.contains("rate_limited")) {
            return NoonPullFailureType.RATE_LIMITED;
        }
        if (value.contains("captcha") || value.contains("验证码")) {
            return NoonPullFailureType.CAPTCHA_REQUIRED;
        }
        if (value.contains("risk control") || value.contains("blocked_by_risk_control")
                || value.contains("blocked by risk")) {
            return NoonPullFailureType.BLOCKED_BY_RISK_CONTROL;
        }
        if (value.contains("partial success") || value.contains("partial_success")) {
            return NoonPullFailureType.PARTIAL_SUCCESS;
        }
        if (value.contains("provider unavailable") || value.contains("provider_unavailable")
                || value.contains("http 503") || value.contains("503")) {
            return NoonPullFailureType.PROVIDER_UNAVAILABLE;
        }
        return NoonPullFailureType.UNKNOWN_FAILURE;
    }

    public NoonPullFailureDecision decide(NoonPullFailureType failureType, int attempt) {
        NoonPullFailureType type = failureType == null ? NoonPullFailureType.UNKNOWN_FAILURE : failureType;
        int safeAttempt = Math.max(1, attempt);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());

        switch (type) {
            case TIMEOUT:
            case PROVIDER_UNAVAILABLE:
                return new NoonPullFailureDecision(
                        type,
                        NoonPullRetryAction.RETRY,
                        true,
                        false,
                        false,
                        now.plusMinutes(transientBackoffMinutes(safeAttempt)),
                        type.code() + ": retry with bounded exponential backoff"
                );
            case REPORT_NOT_READY:
                return new NoonPullFailureDecision(
                        type,
                        NoonPullRetryAction.DELAY,
                        true,
                        false,
                        false,
                        now.plusMinutes(60),
                        type.code() + ": retry later today without marking report empty"
                );
            case EMPTY_REPORT_PENDING_CONFIRMATION:
                return new NoonPullFailureDecision(
                        type,
                        NoonPullRetryAction.DELAY,
                        true,
                        false,
                        false,
                        now.plusHours(6),
                        type.code() + ": completed empty export remains inside the late sales confirmation window"
                );
            case RATE_LIMITED:
            case BLOCKED_BY_RISK_CONTROL:
            case CAPTCHA_REQUIRED:
                return new NoonPullFailureDecision(
                        type,
                        NoonPullRetryAction.PAUSE,
                        false,
                        true,
                        true,
                        now.plusHours(6),
                        type.code() + ": pause or cool down; manual/operator action required"
                );
            case AUTH_REQUIRED:
            case MISSING_COLUMNS:
            case PROVIDER_RETENTION_LIMIT:
            case PROVIDER_NOT_CONFIGURED:
                return new NoonPullFailureDecision(
                        type,
                        NoonPullRetryAction.MANUAL_ACTION,
                        false,
                        true,
                        false,
                        null,
                        type.code() + ": non-retryable until configuration, authorization, schema or data-window issue is fixed"
                );
            case INVALID_PROJECT_CODE:
                return new NoonPullFailureDecision(
                        type,
                        NoonPullRetryAction.DELAY,
                        true,
                        false,
                        false,
                        now.plusMinutes(transientBackoffMinutes(safeAttempt)),
                        type.code() + ": retry with cooldown; Noon may return this transiently for valid report scopes"
                );
            case EMPTY_REPORT:
            case PARTIAL_SUCCESS:
                return new NoonPullFailureDecision(
                        type,
                        NoonPullRetryAction.NONE,
                        false,
                        false,
                        false,
                        null,
                        type.code() + ": terminal data-quality state"
                );
            case MAPPING_FAILED:
            case UNKNOWN_FAILURE:
            default:
                return new NoonPullFailureDecision(
                        type,
                        NoonPullRetryAction.MANUAL_ACTION,
                        false,
                        true,
                        false,
                        null,
                        type.code() + ": manual diagnosis required"
                );
        }
    }

    private int transientBackoffMinutes(int attempt) {
        int exponential = 5 * (1 << Math.min(4, attempt - 1));
        int jitter = Math.min(7, attempt * 2);
        return Math.min(120, exponential + jitter);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
