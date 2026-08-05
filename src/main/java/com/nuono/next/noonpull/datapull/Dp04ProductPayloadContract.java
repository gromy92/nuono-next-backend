package com.nuono.next.noonpull.datapull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** Whitelist, canonical copy, and target-column limits for one DP-04 provider row. */
final class Dp04ProductPayloadContract {

    private static final List<String> ROOT_FIELDS = List.of(
            "csku_parent", "zsku_parent", "sku_parent", "skuParent", "catalog_sku",
            "sku", "zsku_child", "child_sku", "partner_sku",
            "psku_code", "pskuCode", "noon_partner_psku_code", "noonPartnerPskuCode",
            "partner_psku_code", "partnerPskuCode", "offer_code",
            "title", "brand_code", "brand", "image", "product_fulltype",
            "barcode", "gtin", "ean", "upc", "partner_barcodes",
            "base_price", "original_price", "sale_price", "price", "currency",
            "live_status", "seller_status", "status_code",
            "fbn_stock", "supermall_stock", "fbp_stock",
            "content", "offer", "product", "catalog", "identity", "psku"
    );
    private static final List<String> CONTENT_FIELDS = List.of("title", "brand", "image");
    private static final List<String> IDENTITY_FIELDS = List.of(
            "psku_code", "pskuCode", "noon_partner_psku_code", "noonPartnerPskuCode",
            "partner_psku_code", "partnerPskuCode", "code", "value"
    );

    private Dp04ProductPayloadContract() {
    }

    static Map<String, Object> sanitize(Map<String, Object> payload, boolean rejectUnknown) {
        if (payload == null) {
            throw new IllegalArgumentException("DP-04 product payload is required");
        }
        if (rejectUnknown) {
            for (String key : payload.keySet()) {
                if (!ROOT_FIELDS.contains(key)) {
                    throw new IllegalArgumentException("DP-04 product payload contains an unknown field");
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : ROOT_FIELDS) {
            if (!payload.containsKey(field) || payload.get(field) == null) {
                continue;
            }
            Object value = payload.get(field);
            if ("content".equals(field)) {
                result.put(field, sanitizeContainer(value, CONTENT_FIELDS, rejectUnknown));
            } else if (isIdentityContainer(field) && value instanceof Map<?, ?>) {
                result.put(field, sanitizeContainer(value, IDENTITY_FIELDS, rejectUnknown));
            } else if ("partner_barcodes".equals(field)) {
                result.put(field, sanitizeScalarList(value));
            } else {
                result.put(field, sanitizeScalar(value));
            }
        }
        return result;
    }

    static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
        }
        return copy;
    }

    static String productIdentity(Map<String, Object> payload) {
        return firstText(
                payload,
                "csku_parent",
                "zsku_parent",
                "sku_parent",
                "skuParent",
                "catalog_sku"
        );
    }

    static String text(Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return StringUtils.hasText(result) ? result : null;
    }

    static boolean fitsTargetColumns(Map<String, Object> payload) {
        return fieldsFit(payload, 100,
                "csku_parent", "zsku_parent", "sku_parent", "skuParent", "catalog_sku",
                "sku", "zsku_child", "child_sku", "partner_sku", "psku_code", "pskuCode",
                "noon_partner_psku_code", "noonPartnerPskuCode", "partner_psku_code",
                "partnerPskuCode", "offer_code", "barcode", "gtin", "ean", "upc")
                && fieldsFit(payload, 500, "title")
                && fieldsFit(payload, 200, "brand_code", "brand", "product_fulltype")
                && fieldsFit(payload, 1000, "image")
                && fieldsFit(payload, 10, "currency")
                && fieldsFit(payload, 50, "live_status", "seller_status", "status_code")
                && nestedFieldsFit(payload.get("content"), 500, "title")
                && nestedFieldsFit(payload.get("content"), 200, "brand")
                && nestedFieldsFit(payload.get("content"), 1000, "image")
                && identityContainersFit(payload)
                && Dp04NumericPayloadContract.fieldsFit(payload)
                && scalarListFits(payload.get("partner_barcodes"), 100);
    }

    static void requireNumericSyntax(Map<String, Object> payload) {
        Dp04NumericPayloadContract.requireSyntax(payload);
    }

    private static boolean identityContainersFit(Map<String, Object> payload) {
        for (String field : List.of("offer", "product", "catalog", "sku", "identity", "psku")) {
            if (!nestedFieldsFit(payload.get(field), 100, IDENTITY_FIELDS.toArray(String[]::new))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdentityContainer(String field) {
        return "offer".equals(field)
                || "product".equals(field)
                || "catalog".equals(field)
                || "sku".equals(field)
                || "identity".equals(field)
                || "psku".equals(field);
    }

    private static Map<String, Object> sanitizeContainer(
            Object value,
            List<String> allowedFields,
            boolean rejectUnknown
    ) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("DP-04 nested payload must be an object");
        }
        Map<?, ?> source = (Map<?, ?>) value;
        for (Object key : source.keySet()) {
            if (!(key instanceof String)
                    || (rejectUnknown && !allowedFields.contains(key))) {
                throw new IllegalArgumentException("DP-04 nested payload contains an unknown field");
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : allowedFields) {
            if (source.containsKey(field) && source.get(field) != null) {
                result.put(field, sanitizeScalar(source.get(field)));
            }
        }
        return result;
    }

    private static List<Object> sanitizeScalarList(Object value) {
        if (!(value instanceof Iterable<?>)) {
            return List.of(sanitizeScalar(value));
        }
        List<Object> result = new ArrayList<>();
        for (Object item : (Iterable<?>) value) {
            if (item != null) {
                result.add(sanitizeScalar(item));
            }
        }
        return List.copyOf(result);
    }

    private static Object sanitizeScalar(Object value) {
        Objects.requireNonNull(value, "DP-04 scalar value");
        if (value instanceof String || value instanceof Boolean
                || value instanceof BigDecimal || value instanceof BigInteger
                || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return value;
        }
        if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException("DP-04 numeric value must be finite");
            }
            return new BigDecimal(String.valueOf(value));
        }
        throw new IllegalArgumentException("DP-04 payload contains an unsupported value");
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?>) {
            return deepCopy((Map<String, Object>) value);
        }
        if (value instanceof List<?>) {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<?>) value) {
                copy.add(deepCopyValue(item));
            }
            return copy;
        }
        return value;
    }

    private static String firstText(Map<String, Object> map, String... fields) {
        for (String field : fields) {
            String value = text(map.get(field));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean fieldsFit(Map<String, Object> payload, int max, String... fields) {
        for (String field : fields) {
            if (!scalarFits(payload.get(field), max)) {
                return false;
            }
        }
        return true;
    }

    private static boolean nestedFieldsFit(Object value, int max, String... fields) {
        return !(value instanceof Map<?, ?>) || fieldsFit(castStringMap(value), max, fields);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castStringMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static boolean scalarListFits(Object value, int max) {
        if (!(value instanceof Iterable<?>)) {
            return scalarFits(value, max);
        }
        for (Object item : (Iterable<?>) value) {
            if (!scalarFits(item, max)) {
                return false;
            }
        }
        return true;
    }

    private static boolean scalarFits(Object value, int max) {
        if (value == null || value instanceof Map<?, ?>) {
            return true;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.length() <= max && normalized.indexOf('\0') < 0;
    }
}
