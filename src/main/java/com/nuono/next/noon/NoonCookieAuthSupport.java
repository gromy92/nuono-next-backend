package com.nuono.next.noon;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.util.StringUtils;

final class NoonCookieAuthSupport {
    private NoonCookieAuthSupport() {
    }

    static String fingerprint(String cookie) {
        if (!StringUtils.hasText(cookie)) {
            return "missing";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    digest.digest(cookie.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算 Noon Cookie 指纹。", exception);
        }
    }

    static String safeFailureReason(Throwable throwable) {
        if (throwable == null || !StringUtils.hasText(throwable.getMessage())) {
            return "cookie_rejected";
        }
        String normalized = throwable.getMessage().replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180);
    }

    static NoonSessionGateway.NoonCookieAuthRequiredException authRequired(
            String projectCode,
            String storeCode,
            String reason,
            Throwable cause
    ) {
        String message = "auth_required: Noon Cookie 无效或已过期，任务将进入统一授权等待"
                + "; project=" + firstNonBlank(normalize(projectCode), "unknown")
                + "; store=" + firstNonBlank(normalize(storeCode), "unknown")
                + "; reason=" + firstNonBlank(reason, "cookie_rejected");
        return cause == null
                ? new NoonSessionGateway.NoonCookieAuthRequiredException(message)
                : new NoonSessionGateway.NoonCookieAuthRequiredException(message, cause);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String firstNonBlank(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }
}
