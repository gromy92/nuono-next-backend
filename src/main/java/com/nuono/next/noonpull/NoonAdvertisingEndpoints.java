package com.nuono.next.noonpull;

import org.springframework.util.StringUtils;

/** Normalizes the shared Ad Manager endpoint root. */
final class NoonAdvertisingEndpoints {

    private NoonAdvertisingEndpoints() {
    }

    static String root(String configured, String fallback) {
        String result = StringUtils.hasText(configured) ? configured.trim() : fallback;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
