package com.nuono.next.noon;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class NoonTransientTransportFailurePolicy {

    private static final Set<Integer> RETRYABLE_HTTP_STATUSES =
            Set.of(408, 500, 502, 503, 504);
    private static final Pattern HTTP_STATUS_PATTERN =
            Pattern.compile("\\bHTTP(?:/\\d(?:\\.\\d)?)?\\s+(\\d{3})\\b", Pattern.CASE_INSENSITIVE);

    private NoonTransientTransportFailurePolicy() {
    }

    static boolean shouldRefresh(boolean proxyEnabled, Throwable failure) {
        return proxyEnabled
                && (isRetryable(failure) || hasHttpStatus(failure, 407));
    }

    public static boolean isRetryable(Throwable failure) {
        boolean approvedHttpStatus = false;
        Throwable current = failure;
        while (current != null) {
            if (current instanceof NoonHttpException) {
                int status = ((NoonHttpException) current).getStatusCode();
                if (!RETRYABLE_HTTP_STATUSES.contains(status)) {
                    return false;
                }
                approvedHttpStatus = true;
            }
            String message = current.getMessage();
            if (StringUtils.hasText(message)) {
                Matcher statusMatcher = HTTP_STATUS_PATTERN.matcher(message);
                while (statusMatcher.find()) {
                    int status = Integer.parseInt(statusMatcher.group(1));
                    if (!RETRYABLE_HTTP_STATUSES.contains(status)) {
                        return false;
                    }
                    approvedHttpStatus = true;
                }
            }
            current = current.getCause();
        }
        return approvedHttpStatus
                || NoonProjectTransientFailureClassifier.classify(failure).isPresent();
    }

    private static boolean hasHttpStatus(Throwable failure, int expectedStatus) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof NoonHttpException
                    && ((NoonHttpException) current).getStatusCode() == expectedStatus) {
                return true;
            }
            String message = current.getMessage();
            if (StringUtils.hasText(message)) {
                Matcher statusMatcher = HTTP_STATUS_PATTERN.matcher(message);
                while (statusMatcher.find()) {
                    if (Integer.parseInt(statusMatcher.group(1)) == expectedStatus) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }

    static String safeDeterministicAuthMarker(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        String normalized = responseBody.toLowerCase(Locale.ROOT);
        if (containsAny(
                normalized,
                "account does not contain current project",
                "account does not include current project",
                "账号不包含当前项目",
                "project_access_denied"
        )) {
            return "; project_access_denied";
        }
        if (containsAny(
                normalized,
                "invalid username or password",
                "invalid credentials",
                "credentials invalid",
                "invalid password",
                "password validate",
                "bad credentials",
                "账号或密码错误"
        )) {
            return "; bad credentials";
        }
        return "";
    }

    private static boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizedMessage(String message) {
        return message == null ? "" : message.toLowerCase(Locale.ROOT);
    }
}
