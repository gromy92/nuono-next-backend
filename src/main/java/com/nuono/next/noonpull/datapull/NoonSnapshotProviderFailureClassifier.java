package com.nuono.next.noonpull.datapull;

import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noon.NoonRequestPacingException;
import com.nuono.next.noon.NoonTransientTransportFailurePolicy;
import java.util.Locale;
import org.springframework.util.StringUtils;

/** Secret-free failure classification shared by the Noon complete-snapshot adapters. */
public final class NoonSnapshotProviderFailureClassifier {

    private NoonSnapshotProviderFailureClassifier() {
    }

    public static <T> ProviderOutcome<T> classify(Throwable failure, String operationPrefix) {
        String prefix = requirePrefix(operationPrefix);
        NoonRequestPacingException pacing = cause(
                failure,
                NoonRequestPacingException.class
        );
        if (pacing != null) {
            return ProviderOutcome.transientFailure(
                    prefix + "_LOCAL_PACING",
                    pacing.getRetryAfter()
            );
        }
        NoonHttpException http = cause(failure, NoonHttpException.class);
        if (http != null) {
            int status = http.getStatusCode();
            if (status == 403 || status == 418 || status == 429 || isRiskText(http.getResponseBody())) {
                return ProviderOutcome.riskControl(
                        prefix + "_RISK_CONTROL",
                        http.getRetryAfter(),
                        RiskShareLevel.EXACT
                );
            }
            if (status == 401) {
                return ProviderOutcome.authRequired(prefix + "_AUTH_REQUIRED");
            }
            if (status == 408 || status == 425 || status >= 500) {
                return ProviderOutcome.transientFailure(
                        prefix + "_TRANSIENT",
                        http.getRetryAfter()
                );
            }
            return ProviderOutcome.contractError(prefix + "_HTTP_CONTRACT_ERROR");
        }
        if (NoonAuthenticationFailureClassifier.isAuthenticationFailure(failure)) {
            return ProviderOutcome.authRequired(prefix + "_AUTH_REQUIRED");
        }
        if (NoonTransientTransportFailurePolicy.isRetryable(failure)) {
            return ProviderOutcome.transientFailure(prefix + "_TRANSIENT");
        }

        String messages = messages(failure);
        if (isRiskText(messages)) {
            return ProviderOutcome.riskControl(
                    prefix + "_RISK_CONTROL",
                    null,
                    RiskShareLevel.EXACT
            );
        }
        if (containsAny(
                messages,
                "auth required",
                "unauthorized",
                "invalid session",
                "login required",
                "signin"
        )) {
            return ProviderOutcome.authRequired(prefix + "_AUTH_REQUIRED");
        }
        if (containsAny(
                messages,
                "timeout",
                "timed out",
                "connection reset",
                "connection refused",
                "unexpected eof",
                "provider unavailable"
        )) {
            return ProviderOutcome.transientFailure(prefix + "_TRANSIENT");
        }
        if (containsAny(
                messages,
                "not configured",
                "mapping failed",
                "contract",
                "container",
                "incomplete",
                "malformed",
                "invalid",
                "unsupported",
                "metadata conflict",
                "evidence is missing",
                "exceeds declared",
                "must be an object",
                "scope mismatch",
                "missing noon"
        )) {
            return ProviderOutcome.contractError(prefix + "_CONTRACT_ERROR");
        }
        // Unknown provider/system failures are retried at the exact task checkpoint. They are
        // never converted into an empty page or a deterministic single-item business skip.
        return ProviderOutcome.transientFailure(prefix + "_UNTYPED_FAILURE");
    }

    private static boolean isRiskText(String value) {
        String normalized = normalize(value);
        return containsAny(
                normalized,
                "captcha",
                "\u9a8c\u8bc1\u7801",
                "risk control",
                "blocked by risk",
                "rate limited",
                "too many requests",
                "ip_channel",
                "device restricted",
                "region restricted"
        );
    }

    private static String messages(Throwable failure) {
        StringBuilder result = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                result.append(' ').append(current.getMessage());
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return normalize(result.toString());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... markers) {
        String normalized = normalize(value);
        for (String marker : markers) {
            if (normalized.contains(marker)) {
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
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return null;
    }

    private static String requirePrefix(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw new IllegalArgumentException("operationPrefix must be a sanitized code prefix");
        }
        return value;
    }
}
