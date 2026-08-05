package com.nuono.next.datapull.report;

import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noon.NoonRequestPacingException;
import com.nuono.next.noon.NoonTransientTransportFailurePolicy;
import java.util.Locale;
import org.springframework.util.StringUtils;

/** Secret-free classification for legacy report-provider exceptions. */
final class NoonReportOutcomeClassifier {
    private NoonReportOutcomeClassifier() {
    }

    static <T> ProviderOutcome<T> ambiguousCreate() {
        return ProviderOutcome.unknownOutcome("REPORT_CREATE_OUTCOME_UNKNOWN");
    }

    static <T> ProviderOutcome<T> createFailure(RuntimeException failure) {
        ProviderOutcome<T> classified = readFailure(failure);
        if (classified.getType() == ProviderOutcomeType.RISK_CONTROL
                || classified.getType() == ProviderOutcomeType.AUTH_REQUIRED
                || (classified.getType() == ProviderOutcomeType.TRANSIENT
                    && "REPORT_PROVIDER_LOCAL_PACING".equals(classified.getSanitizedCode()))
                || (classified.getType() == ProviderOutcomeType.CONTRACT_ERROR
                    && "REPORT_PROVIDER_CONTRACT_ERROR".equals(classified.getSanitizedCode()))) {
            return classified;
        }
        return ambiguousCreate();
    }

    static <T> ProviderOutcome<T> readFailure(RuntimeException failure) {
        NoonRequestPacingException pacing = cause(
                failure,
                NoonRequestPacingException.class
        );
        if (pacing != null) {
            return ProviderOutcome.transientFailure(
                    "REPORT_PROVIDER_LOCAL_PACING",
                    pacing.getRetryAfter()
            );
        }
        NoonHttpException http = cause(failure, NoonHttpException.class);
        String signal = signal(failure);
        if (isRisk(signal)) {
            return ProviderOutcome.riskControl(
                    "REPORT_PROVIDER_RISK_CONTROL",
                    http == null ? null : http.getRetryAfter(),
                    RiskShareLevel.EXACT
            );
        }
        if (NoonAuthenticationFailureClassifier.isAuthenticationFailure(failure)
                || containsAny(signal, "AUTH REQUIRED", "UNAUTHORIZED", "LOGIN REQUIRED")) {
            return ProviderOutcome.authRequired("REPORT_PROVIDER_AUTH_REQUIRED");
        }
        if (NoonTransientTransportFailurePolicy.isRetryable(failure)
                || isTransient(signal)) {
            return ProviderOutcome.transientFailure(
                    "REPORT_PROVIDER_TRANSIENT",
                    http == null ? null : http.getRetryAfter()
            );
        }
        if (containsAny(signal, "NOT CONFIGURED", "MAPPING FAILED", "MISSING COLUMN")) {
            return ProviderOutcome.contractError("REPORT_PROVIDER_CONTRACT_ERROR");
        }
        // A message that does not match a deterministic Adapter contract is not evidence
        // that the immutable provider response is bad. Keep it retryable at the same handle.
        return ProviderOutcome.transientFailure("REPORT_PROVIDER_UNTYPED_FAILURE");
    }

    static <T> ProviderOutcome<T> downloadFailure(RuntimeException failure) {
        String signal = signal(failure);
        if (containsAny(signal, "REQUEST HAS EXPIRED", "EXPIREDTOKEN", "EXPIRED TOKEN",
                "SIGNATUREDOESNOTMATCH", "SIGNATURE DOES NOT MATCH")) {
            return ProviderOutcome.notFound("REPORT_DOWNLOAD_LOCATOR_EXPIRED");
        }
        return readFailure(failure);
    }

    private static String signal(Throwable failure) {
        StringBuilder value = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                value.append(' ').append(current.getMessage());
            }
            if (current instanceof NoonHttpException
                    && StringUtils.hasText(((NoonHttpException) current).getResponseBody())) {
                value.append(' ').append(((NoonHttpException) current).getResponseBody());
            }
            current = current.getCause();
        }
        return value.toString().toUpperCase(Locale.ROOT);
    }

    private static boolean isRisk(String value) {
        return containsAny(value, "HTTP 403", " 403", "HTTP 429", " 429", "HTTP 418", "CAPTCHA", "IP_CHANNEL",
                "RATE LIMITED", "RISK CONTROL", "BLOCKED BY RISK", "EDGESUITE", "ACCESS DENIED",
                "DEVICE BLOCK", "GEO RESTRICT", "REGION RESTRICT", "FORBIDDEN BY IP");
    }

    private static boolean isTransient(String value) {
        return containsAny(value, "TIMEOUT", "TIMED OUT", "CONNECTION RESET", "EOF",
                "HTTP 407", "HTTP 408", "HTTP 500", "HTTP 502", "HTTP 503", "HTTP 504",
                "PROVIDER UNAVAILABLE", "RECEIVED NO BYTES", "REPORT_ARTIFACT_EMPTY_DOWNLOAD");
    }

    private static boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static <T extends Throwable> T cause(Throwable failure, Class<T> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        return null;
    }
}
