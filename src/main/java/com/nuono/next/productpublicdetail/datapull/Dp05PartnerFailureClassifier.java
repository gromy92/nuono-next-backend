package com.nuono.next.productpublicdetail.datapull;

import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noon.NoonRequestPacingException;
import com.nuono.next.noon.NoonTransientTransportFailurePolicy;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class Dp05PartnerFailureClassifier {
    private Dp05PartnerFailureClassifier() {
    }

    static ProviderOutcome<Dp05ProviderValue> classify(RuntimeException failure) {
        NoonRequestPacingException pacing = find(failure, NoonRequestPacingException.class);
        if (pacing != null) {
            return ProviderOutcome.transientFailure(
                    "DP05_PARTNER_LOCAL_PACING",
                    pacing.getRetryAfter()
            );
        }
        NoonHttpException http = find(failure, NoonHttpException.class);
        if (http != null) {
            if (http.getStatusCode() == 403) {
                return ProviderOutcome.riskControl(
                        "DP05_PARTNER_RISK_CONTROL",
                        http.getRetryAfter(),
                        RiskShareLevel.EXACT
                );
            }
            if (http.getStatusCode() == 429 || http.getStatusCode() == 418) {
                return ProviderOutcome.riskControl(
                        "DP05_PARTNER_RATE_LIMITED",
                        http.getRetryAfter(),
                        RiskShareLevel.EXACT
                );
            }
            if (http.getStatusCode() == 401) {
                return ProviderOutcome.authRequired("DP05_PARTNER_AUTH_REQUIRED");
            }
        }
        if (NoonAuthenticationFailureClassifier.isAuthenticationFailure(failure)) {
            return ProviderOutcome.authRequired("DP05_PARTNER_AUTH_REQUIRED");
        }
        if (NoonTransientTransportFailurePolicy.isRetryable(failure)
                || isTransientText(normalize(failure.getMessage()))) {
            return ProviderOutcome.transientFailure(
                    "DP05_PARTNER_TRANSIENT",
                    http == null ? null : http.getRetryAfter()
            );
        }
        return classifyText(failure.getMessage());
    }

    static ProviderOutcome<Dp05ProviderValue> classifyText(String value) {
        String normalized = normalize(value);
        if (isRiskText(normalized)) {
            return ProviderOutcome.riskControl("DP05_PARTNER_RISK_CONTROL");
        }
        if (containsAny(normalized, "AUTH REQUIRED", "UNAUTHORIZED", "INVALID SESSION", "LOGIN REQUIRED")) {
            return ProviderOutcome.authRequired("DP05_PARTNER_AUTH_REQUIRED");
        }
        if (isTransientText(normalized)) {
            return ProviderOutcome.transientFailure("DP05_PARTNER_TRANSIENT");
        }
        return ProviderOutcome.contractError("DP05_PARTNER_UNCLASSIFIED_FAILURE");
    }

    private static boolean isRiskText(String value) {
        return containsAny(value, "HTTP 403", " 403", "FORBIDDEN", "429", "RATE LIMITED", "TOO MANY REQUESTS", "IP_CHANNEL",
                "CAPTCHA", "RISK CONTROL", "BLOCKED BY RISK", "EDGESUITE", "ACCESS DENIED");
    }

    private static boolean isTransientText(String value) {
        return containsAny(value, "TIMEOUT", "TIMED OUT", "CONNECTION RESET", "EOF", "HTTP 407",
                "HTTP 408", "HTTP 500", "HTTP 502", "HTTP 503", "HTTP 504", "PROVIDER UNAVAILABLE");
    }

    private static <T extends Throwable> T find(Throwable failure, Class<T> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private static boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
