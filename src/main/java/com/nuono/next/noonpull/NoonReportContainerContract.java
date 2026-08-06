package com.nuono.next.noonpull;

import java.util.Locale;
import org.springframework.util.StringUtils;

/** Container identity primitives shared before row-level business parsing. */
final class NoonReportContainerContract {
    private NoonReportContainerContract() { }

    static String recognizedSite(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("SA".equals(normalized) || "KSA".equals(normalized)
                || "SAUDI ARABIA".equals(normalized)) {
            return "SA";
        }
        if ("AE".equals(normalized) || "UAE".equals(normalized)
                || "UNITED ARAB EMIRATES".equals(normalized)) {
            return "AE";
        }
        return null;
    }

    static String siteForCurrency(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String currency = value.trim().toUpperCase(Locale.ROOT);
        if ("SAR".equals(currency)) {
            return "SA";
        }
        return "AED".equals(currency) ? "AE" : null;
    }

    static boolean mismatch(String expected, String actual) {
        return StringUtils.hasText(actual)
                && StringUtils.hasText(expected)
                && !actual.equals(expected);
    }

    static boolean hasBlank(String... values) {
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                return true;
            }
        }
        return false;
    }
}
