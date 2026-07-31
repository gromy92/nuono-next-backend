package com.nuono.next.noonpull;

import java.util.Locale;
import org.springframework.util.StringUtils;

final class NoonProductListActiveStateSupport {

    private NoonProductListActiveStateSupport() {
    }

    static Boolean resolve(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("live".equals(normalized)
                || "active".equals(normalized)
                || "true".equals(normalized)
                || "1".equals(normalized)
                || "yes".equals(normalized)
                || "enabled".equals(normalized)
                || "on".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)
                || "0".equals(normalized)
                || "no".equals(normalized)
                || "disabled".equals(normalized)
                || normalized.contains("not")
                || normalized.contains("off")
                || normalized.contains("inactive")) {
            return false;
        }
        return null;
    }
}
