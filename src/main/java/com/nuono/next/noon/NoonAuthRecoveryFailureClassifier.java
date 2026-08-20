package com.nuono.next.noon;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureCode;
import java.util.Locale;
import javax.mail.AuthenticationFailedException;
import org.springframework.util.StringUtils;

final class NoonAuthRecoveryFailureClassifier {
    private NoonAuthRecoveryFailureClassifier() {
    }

    static NoonAuthRecoveryFailureCode classifySendFailure(Throwable throwable) {
        NoonHttpException httpFailure = findNoonHttpException(throwable);
        if (httpFailure != null && httpFailure.hasStatusCode(418)) {
            return NoonAuthRecoveryFailureCode.SEND_RISK_BLOCKED;
        }
        if (httpFailure != null && httpFailure.hasStatusCode(429)) {
            return NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED;
        }
        if (httpFailure != null && httpFailure.hasStatusCode(401, 403)) {
            return NoonAuthRecoveryFailureCode.IDENTITY_AUTH_FAILED;
        }
        String message = throwableMessage(throwable).toLowerCase(Locale.ROOT);
        if (message.contains("418")) {
            return NoonAuthRecoveryFailureCode.SEND_RISK_BLOCKED;
        }
        if (containsRateLimit(message)) {
            return NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED;
        }
        return containsRiskBlock(message)
                ? NoonAuthRecoveryFailureCode.SEND_RISK_BLOCKED
                : NoonAuthRecoveryFailureCode.SEND_RESULT_UNKNOWN;
    }

    static NoonAuthRecoveryFailureCode classifyMailboxFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AuthenticationFailedException) {
                return NoonAuthRecoveryFailureCode.MAILBOX_AUTH_FAILED;
            }
            current = current.getCause();
        }
        String message = throwableMessage(throwable).toLowerCase(Locale.ROOT);
        return message.contains("authentication failed")
                || message.contains("login failed")
                || message.contains("auth code")
                ? NoonAuthRecoveryFailureCode.MAILBOX_AUTH_FAILED
                : NoonAuthRecoveryFailureCode.MAILBOX_UNAVAILABLE;
    }

    static NoonAuthRecoveryFailureCode classifyOtpValidationFailure(Throwable throwable) {
        NoonHttpException httpFailure = findNoonHttpException(throwable);
        if (httpFailure != null && httpFailure.hasStatusCode(418)) {
            return NoonAuthRecoveryFailureCode.SEND_RISK_BLOCKED;
        }
        if (httpFailure != null && httpFailure.hasStatusCode(429)) {
            return NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED;
        }
        String message = throwableMessage(throwable).toLowerCase(Locale.ROOT);
        if (message.contains("418") || containsRiskBlock(message)) {
            return NoonAuthRecoveryFailureCode.SEND_RISK_BLOCKED;
        }
        if (containsRateLimit(message)) {
            return NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED;
        }
        if ((httpFailure != null
                && httpFailure.hasStatusCode(400, 401)
                && httpFailure.responseBodyContainsAny("invalid", "expired", "验证码失效"))
                || message.contains("invalid")
                || message.contains("expired")
                || message.contains("credential")
                || message.contains("验证码")) {
            return NoonAuthRecoveryFailureCode.OTP_INVALID_OR_EXPIRED;
        }
        return NoonAuthRecoveryFailureCode.IDENTITY_AUTH_FAILED;
    }

    static NoonAuthRecoveryFailureCode classifyIdentityFailure(Throwable throwable) {
        String message = throwableMessage(throwable).toLowerCase(Locale.ROOT);
        if (containsRateLimit(message)) {
            return NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED;
        }
        return containsRiskBlock(message)
                ? NoonAuthRecoveryFailureCode.SEND_RISK_BLOCKED
                : NoonAuthRecoveryFailureCode.IDENTITY_AUTH_FAILED;
    }

    static String safeDiagnostic(String operation, Throwable throwable) {
        String message = throwableMessage(throwable).toLowerCase(Locale.ROOT);
        if (containsRateLimit(message)) {
            return operation + ": rate limited";
        }
        if (containsRiskBlock(message)) {
            return operation + ": risk blocked";
        }
        if (message.contains("invalid") || message.contains("expired")) {
            return operation + ": invalid or expired";
        }
        return operation + ": failed";
    }

    static String throwableMessage(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                if (builder.length() > 0) {
                    builder.append(" | ");
                }
                builder.append(current.getMessage());
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    private static NoonHttpException findNoonHttpException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NoonHttpException) {
                return (NoonHttpException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean containsRateLimit(String message) {
        return message.contains("429")
                || message.contains("too many requests")
                || message.contains("rate limit")
                || message.contains("ip_channel");
    }

    private static boolean containsRiskBlock(String message) {
        return message.contains("captcha")
                || message.contains("risk control")
                || message.contains("blocked by risk");
    }
}
