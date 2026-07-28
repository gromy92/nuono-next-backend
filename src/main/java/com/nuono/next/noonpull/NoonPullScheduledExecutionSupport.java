package com.nuono.next.noonpull;

import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

final class NoonPullScheduledExecutionSupport {
    private static final Set<String> COMPLETE_STATUSES =
            Set.of("COMPLETE", "COMPLETED", "SUCCESS", "READY", "DONE");
    private static final Set<String> FAILED_STATUSES =
            Set.of("FAILED", "FAILURE", "ERROR", "CANCELLED", "CANCELED");

    private NoonPullScheduledExecutionSupport() {
    }

    static boolean isFbnExportComplete(String status) {
        return hasStatus(COMPLETE_STATUSES, status);
    }

    static boolean isFbnExportFailed(String status) {
        return hasStatus(FAILED_STATUSES, status);
    }

    static String valueOrUnknown(String value) {
        return StringUtils.hasText(value) ? value.trim() : "unknown";
    }

    static String deriveProjectCode(String storeCode) {
        if (!StringUtils.hasText(storeCode)) {
            return null;
        }
        String normalized = storeCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("PRJ")) {
            return normalized;
        }
        if (!normalized.startsWith("STR")) {
            return null;
        }
        int dashIndex = normalized.indexOf('-');
        String partnerId = dashIndex > 3 ? normalized.substring(3, dashIndex) : normalized.substring(3);
        return partnerId.isBlank() ? null : "PRJ" + partnerId;
    }

    private static boolean hasStatus(Set<String> statuses, String status) {
        return StringUtils.hasText(status)
                && statuses.contains(status.trim().toUpperCase(Locale.ROOT));
    }
}
