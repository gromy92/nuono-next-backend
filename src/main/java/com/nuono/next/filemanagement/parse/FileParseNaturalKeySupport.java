package com.nuono.next.filemanagement.parse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

final class FileParseNaturalKeySupport {

    private FileParseNaturalKeySupport() {
    }

    static String buildNaturalKey(String itemType, Map<String, Object> payload) {
        if ("logistics_channel_rule".equals(itemType)) {
            return buildLogisticsNaturalKey(payload);
        }
        if ("commission_rule".equals(itemType)) {
            return buildCommissionNaturalKey(payload);
        }
        return null;
    }

    private static String buildCommissionNaturalKey(Map<String, Object> payload) {
        String country = normalizeUpper(text(payload.get("country")));
        String categoryName = normalizeText(text(payload.get("categoryName")));
        if (!StringUtils.hasText(country)
                || !StringUtils.hasText(categoryName)) {
            return null;
        }
        return String.join("|",
                country,
                normalizeCategoryIdentity(payload),
                normalizeBrandRestriction(text(payload.get("brandRestriction"))),
                normalizeRange(payload),
                normalizeUpper(text(payload.get("amountCurrency"))),
                normalizeText(text(payload.get("effectiveDate")))
        );
    }

    private static String buildLogisticsNaturalKey(Map<String, Object> payload) {
        String channelKey = normalizeText(text(payload.get("channelKey")));
        String feeItem = normalizeText(text(payload.get("feeItem")));
        if (!StringUtils.hasText(channelKey) || !StringUtils.hasText(feeItem)) {
            return null;
        }
        return String.join("|",
                channelKey,
                normalizeUpper(text(payload.get("country"))),
                normalizeText(text(payload.get("city"))),
                normalizeText(text(payload.get("shippingMethod"))),
                feeItem
        );
    }

    static String naturalKeyHash(String itemType, String naturalKey) {
        return sha256(itemType + "|" + naturalKey);
    }

    static String matchKey(String itemType, Map<String, Object> payload, String fallbackNaturalKeyHash) {
        String naturalKey = buildNaturalKey(itemType, payload);
        if (StringUtils.hasText(naturalKey)) {
            return itemType + "|" + naturalKey;
        }
        return itemType + "|" + (StringUtils.hasText(fallbackNaturalKeyHash) ? fallbackNaturalKeyHash : "");
    }

    private static String normalizeRange(Map<String, Object> payload) {
        String min = normalizeNumber(text(payload.get("amountMin")));
        String max = normalizeNumber(text(payload.get("amountMax")));
        String currency = normalizeUpper(text(payload.get("amountCurrency")));
        if (StringUtils.hasText(min) || StringUtils.hasText(max)) {
            String minInclusive = normalizeBoolean(text(payload.get("amountMinInclusive")));
            String maxInclusive = normalizeBoolean(text(payload.get("amountMaxInclusive")));
            return "MIN:" + (StringUtils.hasText(min) ? min : "*")
                    + ":" + minInclusive
                    + "|MAX:" + (StringUtils.hasText(max) ? max : "*")
                    + ":" + maxInclusive
                    + "|CUR:" + currency;
        }
        String label = normalizeText(text(payload.get("amountRangeLabel")));
        if (!StringUtils.hasText(label) || "全部".equals(label) || "ALL".equalsIgnoreCase(label)) {
            return "ALL";
        }
        return label.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String normalizeNumber(String value) {
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        try {
            return new java.math.BigDecimal(text).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            return text;
        }
    }

    private static String normalizeBoolean(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Boolean.parseBoolean(value.trim()) ? "1" : "0";
    }

    private static String normalizeUpper(String value) {
        String text = normalizeText(value);
        return StringUtils.hasText(text) ? text.toUpperCase(Locale.ROOT) : "";
    }

    private static String normalizeCategoryIdentity(Map<String, Object> payload) {
        String categoryPath = normalizeText(text(payload.get("categoryPath")));
        if (StringUtils.hasText(categoryPath)) {
            return normalizeCategoryPath(categoryPath);
        }
        String parentCategoryName = normalizeText(text(payload.get("parentCategoryName")));
        String categoryName = normalizeText(text(payload.get("categoryName")));
        if (StringUtils.hasText(parentCategoryName)
                && StringUtils.hasText(categoryName)
                && !parentCategoryName.equalsIgnoreCase(categoryName)
                && !categoryName.toLowerCase(Locale.ROOT).startsWith(parentCategoryName.toLowerCase(Locale.ROOT) + " >")) {
            return normalizeCategoryPath(parentCategoryName + " > " + categoryName);
        }
        return normalizeCategoryPath(categoryName);
    }

    private static String normalizeCategoryPath(String value) {
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replaceAll("\\s*>\\s*", " > ");
    }

    private static String normalizeBrandRestriction(String value) {
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return "全部";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if ("all".equals(lower)
                || "all brands".equals(lower)
                || "any brand".equals(lower)
                || "no restriction".equals(lower)
                || "全部".equals(text)
                || "所有品牌".equals(text)
                || "不限品牌".equals(text)) {
            return "全部";
        }
        if (lower.contains("generic")) {
            return "Generic brand";
        }
        if (lower.contains("other brand")) {
            return "All other brands";
        }
        return text;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
