package com.nuono.next.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

final class ProductPublishSnapshotSupport {

    private static final DateTimeFormatter FETCH_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ProductPublishSnapshotSupport() {
    }

    static ProductMasterSnapshotView copySnapshot(ObjectMapper objectMapper, ProductMasterSnapshotView source) {
        if (source == null) {
            return null;
        }
        return objectMapper.convertValue(source, ProductMasterSnapshotView.class);
    }

    static Map<String, Map<String, Object>> siteOfferMap(List<Map<String, Object>> siteOffers) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (siteOffers == null) {
            return map;
        }
        for (Map<String, Object> siteOffer : siteOffers) {
            if (siteOffer == null) {
                continue;
            }
            String storeCode = siteOfferCode(siteOffer);
            if (!StringUtils.hasText(storeCode)) {
                continue;
            }
            map.put(storeCode, new LinkedHashMap<>(siteOffer));
        }
        return map;
    }

    static String siteOfferCode(Map<String, Object> siteOffer) {
        if (siteOffer == null) {
            return null;
        }
        return textValue(siteOffer.get("storeCode"));
    }

    static List<Map<String, Object>> siteOfferComparableList(
            ProductMasterSnapshotView snapshot,
            String siteCode,
            boolean includeUnsupportedFields
    ) {
        List<Map<String, Object>> comparable = new ArrayList<>();
        if (snapshot == null || snapshot.getSiteOffers() == null) {
            return comparable;
        }
        String normalizedSiteCode = normalize(siteCode);
        for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
            String storeCode = siteOfferCode(siteOffer);
            if (StringUtils.hasText(normalizedSiteCode) && !normalizedSiteCode.equalsIgnoreCase(storeCode)) {
                continue;
            }
            comparable.add(siteOfferComparable(siteOffer, includeUnsupportedFields));
        }
        return comparable;
    }

    static Map<String, Object> siteOfferComparable(Map<String, Object> siteOffer, boolean includeUnsupportedFields) {
        Map<String, Object> comparable = new LinkedHashMap<>();
        putIfNotNull(comparable, "storeCode", siteOfferCode(siteOffer));
        putIfNotNull(comparable, "site", siteOffer == null ? null : siteOffer.get("site"));
        putComparableDecimalIfPresent(comparable, "price", siteOffer == null ? null : siteOffer.get("price"));
        putComparableDecimalIfPresent(comparable, "salePrice", siteOffer == null ? null : siteOffer.get("salePrice"));
        putIfNotNull(comparable, "saleStart", normalizeOfferDateForNoon(siteOffer == null ? null : siteOffer.get("saleStart")));
        putIfNotNull(comparable, "saleEnd", normalizeOfferDateForNoon(siteOffer == null ? null : siteOffer.get("saleEnd")));
        putComparableDecimalIfPresent(comparable, "priceMin", siteOffer == null ? null : siteOffer.get("priceMin"));
        putComparableDecimalIfPresent(comparable, "priceMax", siteOffer == null ? null : siteOffer.get("priceMax"));
        putComparableBooleanIfPresent(comparable, "isActive", siteOffer == null ? null : siteOffer.get("isActive"));
        putComparableDecimalIfPresent(comparable, "idWarranty", siteOffer == null ? null : siteOffer.get("idWarranty"));
        putIfNotNull(comparable, "offerNote", siteOffer == null ? null : siteOffer.get("offerNote"));
        if (includeUnsupportedFields) {
            putIfNotNull(comparable, "barcode", siteOffer == null ? null : siteOffer.get("barcode"));
        }
        return comparable;
    }

    static String normalizeOfferDateForNoon(Object value) {
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // Try the next supported date format.
        }
        try {
            return ZonedDateTime.parse(text).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // Try the next supported date format.
        }
        try {
            return LocalDateTime.parse(text).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // Try the next supported date format.
        }
        try {
            return LocalDateTime.parse(text, FETCH_TIME_FORMATTER).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // Try the next supported date format.
        }
        try {
            return LocalDate.parse(text).format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            return text;
        }
    }

    static LocalDate parseLocalDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        try {
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // Try the next supported date format.
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // Try the next supported date format.
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // Fall back to the date prefix below.
        }
        String firstTen = text.length() >= 10 ? text.substring(0, 10) : text;
        try {
            return LocalDate.parse(firstTen, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    static Map<String, Map<String, Object>> keyAttributeMap(List<Map<String, Object>> keyAttributes) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (keyAttributes == null) {
            return map;
        }
        for (Map<String, Object> attribute : keyAttributes) {
            String code = attribute == null ? null : textValue(attribute.get("code"));
            if (StringUtils.hasText(code)) {
                map.put(code, new LinkedHashMap<>(attribute));
            }
        }
        return map;
    }

    static Map<String, Map<String, Object>> variantMap(List<Map<String, Object>> variants) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (variants == null) {
            return map;
        }
        for (Map<String, Object> variant : variants) {
            String childSku = variant == null ? null : textValue(variant.get("childSku"));
            if (StringUtils.hasText(childSku)) {
                map.put(childSku, new LinkedHashMap<>(variant));
            }
        }
        return map;
    }

    static String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    static BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value));
        }
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static boolean truthy(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0D;
        }
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return "1".equals(normalized)
                || "true".equals(normalized)
                || "yes".equals(normalized)
                || "active".equals(normalized)
                || "live".equals(normalized);
    }

    static void putIfNotNull(Map<String, Object> target, String field, Object value) {
        if (value != null) {
            target.put(field, value);
        }
    }

    private static void putComparableDecimalIfPresent(Map<String, Object> target, String field, Object value) {
        if (value == null) {
            return;
        }
        BigDecimal decimal = asBigDecimal(value);
        if (decimal == null) {
            target.put(field, value);
            return;
        }
        target.put(field, decimal.stripTrailingZeros().toPlainString());
    }

    private static void putComparableBooleanIfPresent(Map<String, Object> target, String field, Object value) {
        if (value != null) {
            target.put(field, truthy(value));
        }
    }

}
