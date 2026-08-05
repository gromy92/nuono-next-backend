package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import org.springframework.util.StringUtils;

/** Provider-row scalar parsing and inventory bucket normalization. */
final class OfficialWarehouseFbnInventoryFields {
    private OfficialWarehouseFbnInventoryFields() {
    }

    static String stockBucket(String inventoryType, String reasonCode) {
        String type = normalize(inventoryType);
        String reason = normalize(reasonCode);
        if ("SALEABLE".equals(type)) return "SELLABLE";
        if ("GRADED_RETURNS_CIR".equals(type) && "CUSTOMER_RETURN".equals(reason)) {
            return "RETURNED";
        }
        if ("INBOUND_PS".equals(type)) return "RECEIVING_EXCEPTION";
        if ("DAMAGED".equals(type)) return "DAMAGED";
        if ("EXPIRED".equals(type)) return "QUALITY_HOLD";
        if ("LOST".equals(type)) return "LOST";
        return "PENDING_CONFIRMATION";
    }

    static String text(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) return null;
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = trimToNull(value.asText(null));
                if (text != null) return text;
            }
        }
        return null;
    }

    static Integer integer(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) return null;
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) return null;
        if ((value.isInt() || value.isLong()) && value.canConvertToInt()) {
            return value.asInt();
        }
        if (value.isIntegralNumber()) return null;
        try {
            String text = trimToNull(value.asText(null));
            return text == null ? null : Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) return trimmed;
        }
        return null;
    }

    static String normalizeDateTime(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) return null;
        String withoutUtc = trimmed.toUpperCase(Locale.ROOT).endsWith(" UTC")
                ? trimmed.substring(0, trimmed.length() - 4).trim() : trimmed;
        return withoutUtc.replaceFirst("^(\\d{4}-\\d{2}-\\d{2}),\\s*", "$1 ");
    }

    private static String normalize(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "" : trimmed.replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
