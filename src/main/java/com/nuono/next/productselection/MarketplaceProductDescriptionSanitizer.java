package com.nuono.next.productselection;

import java.util.Locale;
import org.springframework.util.StringUtils;

final class MarketplaceProductDescriptionSanitizer {

    private MarketplaceProductDescriptionSanitizer() {
    }

    static String preferIncoming(String value, String fallback) {
        String incoming = usable(value);
        return StringUtils.hasText(incoming) ? incoming : usable(fallback);
    }

    private static String usable(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        String normalized = trimmed
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replace('`', '\'')
                .replaceAll("[\\u064B-\\u065F]", "")
                .replaceAll("[.!؟]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        if ("we're always here to help".equals(normalized)
                || "were always here to help".equals(normalized)
                || "نحن دائما جاهزون لمساعدتك".equals(normalized)) {
            return "";
        }
        return trimmed;
    }
}
