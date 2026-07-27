package com.nuono.next.noon;

import java.net.URI;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class NoonSessionResponseClassifier {

    private NoonSessionResponseClassifier() {
    }

    static boolean isAuthExpiredResponse(
            int statusCode,
            String responseBody,
            String requestPath,
            String redirectLocation
    ) {
        if (isExactTransientStatus(statusCode)) {
            return false;
        }
        if (statusCode == 401 || statusCode == 403) {
            return true;
        }
        if (isRedirectStatus(statusCode) && isWhoamiPath(requestPath)) {
            return true;
        }
        if (isRedirectStatus(statusCode) && isNoonLoginRedirect(redirectLocation)) {
            return true;
        }
        if (!StringUtils.hasText(responseBody)) {
            return false;
        }
        String normalized = responseBody.toLowerCase(Locale.ROOT);
        return normalized.contains("unauthorized")
                || normalized.contains("invalid session")
                || normalized.contains("signin");
    }

    private static boolean isExactTransientStatus(int statusCode) {
        return statusCode == 408
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }

    private static boolean isNoonLoginRedirect(String redirectLocation) {
        if (!StringUtils.hasText(redirectLocation)) {
            return false;
        }
        try {
            String host = URI.create(redirectLocation.trim()).getHost();
            return "login.noon.partners".equalsIgnoreCase(host)
                    || "login-alt.noon.partners".equalsIgnoreCase(host);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isRedirectStatus(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private static boolean isWhoamiPath(String requestPath) {
        if (!StringUtils.hasText(requestPath)) {
            return false;
        }
        String normalized = requestPath.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith("/whoami") || normalized.contains("/auth-v1/whoami");
    }
}
