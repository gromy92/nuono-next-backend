package com.nuono.next.product;

import java.util.Locale;
import org.springframework.util.StringUtils;

public final class ProductSourceTypeSupport {

    public static final String SELF_BUILT = "SELF_BUILT";
    public static final String FOLLOW_SELL = "FOLLOW_SELL";

    private ProductSourceTypeSupport() {
    }

    public static String normalize(String productSourceType) {
        String value = trim(productSourceType);
        if (FOLLOW_SELL.equalsIgnoreCase(value)) {
            return FOLLOW_SELL;
        }
        if (SELF_BUILT.equalsIgnoreCase(value)) {
            return SELF_BUILT;
        }
        return null;
    }

    public static String resolve(String productSourceType, String childSku, String skuParent) {
        String normalized = normalize(productSourceType);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        if (startsWithCatalogPrefix(childSku, "N") || startsWithCatalogPrefix(skuParent, "N")) {
            return FOLLOW_SELL;
        }
        return SELF_BUILT;
    }

    public static boolean isFollowSell(String productSourceType) {
        return FOLLOW_SELL.equals(normalize(productSourceType));
    }

    private static boolean startsWithCatalogPrefix(String value, String prefix) {
        String normalized = trim(value);
        return StringUtils.hasText(normalized)
                && normalized.toUpperCase(Locale.ROOT).startsWith(prefix);
    }

    private static String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
