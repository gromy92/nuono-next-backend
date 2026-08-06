package com.nuono.next.noonpull;

import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import com.nuono.next.noon.NoonAuthenticationRequiredException;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noon.NoonRequestPacingException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.springframework.util.StringUtils;

/** Classifies only verified DP-06 provider failures into durable runtime outcomes. */
final class NoonAdvertisingOutcomeClassifier {

    <T> ProviderOutcome<T> classify(RuntimeException failure, String fallbackCode) {
        NoonRequestPacingException pacing = cause(
                failure,
                NoonRequestPacingException.class
        );
        if (pacing != null) {
            return ProviderOutcome.transientFailure(
                    "ADS_LOCAL_PACING",
                    pacing.getRetryAfter()
            );
        }
        NoonHttpException http = cause(failure, NoonHttpException.class);
        if (http != null && http.getStatusCode() == 403) {
            return risk(http.getRetryAfter());
        }
        NoonAdvertisingContractException contract = cause(
                failure,
                NoonAdvertisingContractException.class
        );
        if (contract != null) {
            return ProviderOutcome.contractError(contract.getSanitizedCode());
        }
        if (cause(failure, NoonAuthenticationRequiredException.class) != null) {
            return ProviderOutcome.authRequired("ADS_AUTH_REQUIRED");
        }
        if (cause(failure, NoonAdvertisingRiskException.class) != null) {
            return risk();
        }
        if (http != null) {
            return classifyHttp(http);
        }
        if (hasNetworkCause(failure)) {
            return ProviderOutcome.transientFailure("ADS_PROVIDER_TRANSIENT");
        }
        String message = messages(failure);
        if (containsAny(message, "http 403", " 403", "forbidden", "captcha", "risk control",
                "blocked by risk", "rate limited", "too many requests", "ip_channel")) {
            return risk();
        }
        if (containsAny(message, "auth required", "unauthorized", "invalid session",
                "login required", "signin")) {
            return ProviderOutcome.authRequired("ADS_AUTH_REQUIRED");
        }
        if (message.contains("ads advertiser context mismatch")) {
            return ProviderOutcome.contractError("ADS_ADVERTISER_CONTEXT_MISMATCH");
        }
        if (containsAny(message, "not configured", "missing noon")) {
            return ProviderOutcome.contractError("ADS_PROVIDER_NOT_CONFIGURED");
        }
        if (containsAny(message, "timeout", "timed out", "connection reset", "connection refused",
                "unexpected eof")) {
            return ProviderOutcome.transientFailure("ADS_PROVIDER_TRANSIENT");
        }
        return ProviderOutcome.transientFailure(fallbackCode);
    }

    private <T> ProviderOutcome<T> classifyHttp(NoonHttpException http) {
        String body = http.getResponseBody() == null
                ? ""
                : http.getResponseBody().toLowerCase(Locale.ROOT);
        if (http.getStatusCode() == 403 || http.getStatusCode() == 418
                || http.getStatusCode() == 429
                || containsAny(body, "captcha", "risk control", "blocked by risk",
                "too many requests", "ip_channel")) {
            return risk(http.getRetryAfter());
        }
        if (http.getStatusCode() == 401
                || containsAny(body, "unauthorized", "invalid session", "login required")) {
            return ProviderOutcome.authRequired("ADS_AUTH_REQUIRED");
        }
        if (http.getStatusCode() == 408 || http.getStatusCode() == 425
                || http.getStatusCode() >= 500) {
            return ProviderOutcome.transientFailure(
                    "ADS_PROVIDER_TRANSIENT",
                    http.getRetryAfter()
            );
        }
        return ProviderOutcome.contractError("ADS_HTTP_CONTRACT_ERROR");
    }

    private <T> ProviderOutcome<T> risk() {
        return risk(null);
    }

    private <T> ProviderOutcome<T> risk(Duration retryAfter) {
        return ProviderOutcome.riskControl(
                "ADS_RISK_CONTROL",
                retryAfter,
                RiskShareLevel.EXACT
        );
    }

    private boolean hasNetworkCause(Throwable failure) {
        return cause(failure, SocketTimeoutException.class) != null
                || cause(failure, TimeoutException.class) != null
                || cause(failure, ConnectException.class) != null
                || cause(failure, SocketException.class) != null
                || cause(failure, IOException.class) != null;
    }

    private String messages(Throwable failure) {
        StringBuilder result = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                result.append(' ').append(current.getMessage());
            }
            current = current.getCause();
        }
        return result.toString().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value != null && value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private <T extends Throwable> T cause(Throwable failure, Class<T> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
