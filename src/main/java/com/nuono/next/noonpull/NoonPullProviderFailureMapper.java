package com.nuono.next.noonpull;

import java.util.Locale;
import org.springframework.util.StringUtils;

final class NoonPullProviderFailureMapper {
    private NoonPullProviderFailureMapper() {
    }

    static NoonInterfacePullException map(String stage, RuntimeException exception) {
        if (exception instanceof NoonInterfacePullException) {
            return (NoonInterfacePullException) exception;
        }
        String message = StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
        String normalized = message.toLowerCase(Locale.ROOT);
        String prefix = classifyPrefix(normalized);
        return new NoonInterfacePullException(prefix + ": " + stage + " failed: " + safe(message), exception);
    }

    static NoonInterfacePullException explicit(String stage, String message) {
        String safeMessage = safe(message);
        return new NoonInterfacePullException(classifyPrefix(safeMessage.toLowerCase(Locale.ROOT))
                + ": " + stage + " failed: " + safeMessage);
    }

    private static String classifyPrefix(String normalized) {
        if (normalized.contains("not configured")
                || normalized.contains("provider_not_configured")
                || normalized.contains("export is not configured")
                || normalized.contains("missing noon")) {
            return "provider not configured";
        }
        if (normalized.contains("401")
                || normalized.contains("403")
                || normalized.contains("unauthorized")
                || normalized.contains("invalid session")
                || normalized.contains("signin")
                || normalized.contains("login required")) {
            return "auth required";
        }
        if (normalized.contains("429")
                || normalized.contains("too many requests")
                || normalized.contains("rate limited")
                || normalized.contains("ip_channel")
                || normalized.contains("teapot")) {
            return "rate limited";
        }
        if (normalized.contains("captcha") || normalized.contains("验证码")) {
            return "captcha required";
        }
        if (normalized.contains("risk control") || normalized.contains("blocked by risk")) {
            return "blocked by risk control";
        }
        if (normalized.contains("timeout") || normalized.contains("timed out")) {
            return "timeout";
        }
        if (normalized.contains("503") || normalized.contains("502") || normalized.contains("500")) {
            return "provider unavailable";
        }
        return "provider unavailable";
    }

    private static String safe(String message) {
        if (!StringUtils.hasText(message)) {
            return "unknown provider failure";
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        normalized = normalized.replaceAll("(?i)(cookie|password|api[-_ ]?key|authorization|bearer|token)=([^;\\s]+)", "$1=<redacted>");
        normalized = normalized.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+", "$1<redacted>");
        return normalized.length() > 240 ? normalized.substring(0, 240) + "..." : normalized;
    }
}
