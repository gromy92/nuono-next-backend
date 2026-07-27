package com.nuono.next.noon;

import java.util.Locale;
import org.springframework.util.StringUtils;

/** Structured authentication-failure detection for callers that must not parse provider messages. */
public final class NoonAuthenticationFailureClassifier {

    private NoonAuthenticationFailureClassifier() {
    }

    public static boolean isAuthenticationFailure(Throwable failure) {
        return classify(failure, true);
    }

    /**
     * Returns true only for an explicit HTTP 401 that does not contain permanent credential or
     * Project-scope rejection evidence.
     */
    public static boolean isExplicitAuthenticationRejection(Throwable failure) {
        return classify(failure, false);
    }

    /**
     * Returns true when a failure contains evidence that reauthentication cannot repair, such as
     * invalid credentials or a Project-scope mismatch.
     */
    public static boolean hasPermanentAuthenticationFailureEvidence(Throwable failure) {
        StringBuilder details = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (current instanceof NoonHttpException) {
                append(details, ((NoonHttpException) current).getResponseBody());
            }
            append(details, current.getMessage());
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return isPermanentFailure(details.toString());
    }

    private static boolean classify(Throwable failure, boolean includeTypedSignals) {
        boolean authenticationEvidence = false;
        StringBuilder details = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (includeTypedSignals
                    && (current instanceof NoonSessionGateway.NoonCookieAuthRequiredException
                    || current instanceof NoonAuthenticationRequiredException)) {
                authenticationEvidence = true;
            }
            if (current instanceof NoonHttpException) {
                NoonHttpException httpFailure = (NoonHttpException) current;
                if (httpFailure.getStatusCode() == 401) {
                    authenticationEvidence = true;
                }
                append(details, httpFailure.getResponseBody());
            }
            append(details, current.getMessage());
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return authenticationEvidence && !isPermanentFailure(details.toString());
    }

    private static boolean isPermanentFailure(String details) {
        String normalized = StringUtils.hasText(details)
                ? details.toLowerCase(Locale.ROOT)
                : "";
        return containsAny(
                normalized,
                "invalid username or password",
                "invalid credentials",
                "password validate",
                "invalid password",
                "bad credentials",
                "账号或密码错误",
                "account does not contain current project",
                "account does not include current project",
                "账号不包含当前项目",
                "project_access_denied",
                "project access denied",
                "noon_project_scope_missing",
                "project_scope_missing",
                "project scope missing",
                "current-project mismatch",
                "current project mismatch"
        );
    }

    private static boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static void append(StringBuilder details, String value) {
        if (StringUtils.hasText(value)) {
            details.append(' ').append(value);
        }
    }
}
