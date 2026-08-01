package com.nuono.next.noonauth;

import java.util.Locale;

public final class NoonAuthRecoveryTargetPolicy {

    private NoonAuthRecoveryTargetPolicy() {
    }

    public static boolean hasCompleteBusinessIdentity(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode
    ) {
        String project = normalize(projectCode);
        String store = normalize(storeCode);
        String site = normalize(siteCode);
        return ownerUserId != null
                && project != null
                && store != null
                && site != null
                && !project.equalsIgnoreCase(store)
                && !store.toUpperCase(Locale.ROOT).startsWith("PRJ");
    }

    public static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    public static String normalizeSite(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
