package com.nuono.next.noonads;

import java.util.Locale;
import org.springframework.util.StringUtils;

/** Stable legacy CSV header normalization shared independently of row mapping. */
final class NoonAdvertisingHeaderContract {

    private NoonAdvertisingHeaderContract() {}

    static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String camelSeparated = value.trim().replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        return camelSeparated.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
