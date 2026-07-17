package com.nuono.next.noon;

import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * Transport facts for a non-successful Noon HTTP response.
 *
 * <p>The raw provider body is deliberately kept out of {@link #getMessage()} so it cannot be
 * accidentally returned by a controller. Callers that classify provider semantics can inspect it
 * explicitly through {@link #getResponseBody()}.</p>
 */
public class NoonHttpException extends IllegalStateException {

    private static final int MAX_RESPONSE_BODY_LENGTH = 65_536;

    private final int statusCode;
    private final String responseBody;
    private final String requestPath;

    public NoonHttpException(int statusCode, String responseBody, String requestPath) {
        super(buildSafeMessage(statusCode, responseBody, requestPath));
        this.statusCode = statusCode;
        this.responseBody = truncate(responseBody);
        this.requestPath = requestPath;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getRequestPath() {
        return requestPath;
    }

    boolean hasStatusCode(int... expectedStatusCodes) {
        if (expectedStatusCodes == null) {
            return false;
        }
        for (int expectedStatusCode : expectedStatusCodes) {
            if (statusCode == expectedStatusCode) {
                return true;
            }
        }
        return false;
    }

    boolean responseBodyContainsAny(String... markers) {
        if (!StringUtils.hasText(responseBody) || markers == null) {
            return false;
        }
        String normalizedBody = responseBody.toLowerCase(Locale.ROOT);
        for (String marker : markers) {
            if (StringUtils.hasText(marker)
                    && normalizedBody.contains(marker.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_RESPONSE_BODY_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_RESPONSE_BODY_LENGTH);
    }

    private static String buildSafeMessage(int statusCode, String responseBody, String requestPath) {
        StringBuilder message = new StringBuilder("Noon HTTP ").append(statusCode);
        String marker = safeFailureMarker(statusCode, responseBody);
        if (marker != null) {
            message.append(" (").append(marker).append(")");
        }
        if (StringUtils.hasText(requestPath)) {
            message.append(" at ").append(requestPath);
        }
        return message.toString();
    }

    private static String safeFailureMarker(int statusCode, String responseBody) {
        String normalized = StringUtils.hasText(responseBody)
                ? responseBody.toLowerCase(Locale.ROOT)
                : "";
        if (statusCode == 429
                || statusCode == 418
                || normalized.contains("too many requests")
                || normalized.contains("ip_channel")) {
            return "rate_limited";
        }
        if (statusCode == 401
                || statusCode == 403
                || normalized.contains("unauthorized")
                || normalized.contains("invalid session")) {
            return "auth_required";
        }
        if (normalized.contains("captcha") || normalized.contains("验证码")) {
            return "captcha_required";
        }
        if (normalized.contains("risk control") || normalized.contains("blocked by risk")) {
            return "blocked_by_risk_control";
        }
        if (statusCode >= 500) {
            return "provider_unavailable";
        }
        return null;
    }
}
