package com.nuono.next.noonpull;

import java.util.Locale;
import java.util.Set;

/** Shared Ad Manager campaign-state vocabulary. */
final class NoonAdvertisingCampaignStatus {

    private static final Set<String> ACTIVE = Set.of(
            "active", "enabled", "live", "on", "running"
    );
    private static final Set<String> INACTIVE = Set.of(
            "archived", "completed", "deleted", "disabled", "draft", "ended", "inactive",
            "off", "paused", "rejected", "stopped"
    );

    private NoonAdvertisingCampaignStatus() {
    }

    static boolean activeOrFalse(String status) {
        return ACTIVE.contains(normalize(status));
    }

    static boolean requireKnown(String status) {
        String normalized = normalize(status);
        if (ACTIVE.contains(normalized)) {
            return true;
        }
        if (INACTIVE.contains(normalized)) {
            return false;
        }
        throw new NoonAdvertisingContractException("ADS_CAMPAIGN_STATUS_UNKNOWN");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
