package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

public final class NoonProductListFieldSupport {
    private static final List<String> PSKU_CODE_FIELDS = List.of(
            "psku_code",
            "pskuCode",
            "noon_partner_psku_code",
            "noonPartnerPskuCode",
            "partner_psku_code",
            "partnerPskuCode"
    );
    private static final List<String> NESTED_CONTAINER_FIELDS = List.of(
            "offer",
            "product",
            "catalog",
            "sku",
            "identity",
            "psku"
    );
    private static final List<String> NESTED_VALUE_FIELDS = List.of(
            "psku_code",
            "pskuCode",
            "noon_partner_psku_code",
            "noonPartnerPskuCode",
            "partner_psku_code",
            "partnerPskuCode",
            "code",
            "value"
    );

    private NoonProductListFieldSupport() {
    }

    public static String pskuCode(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        String direct = firstMapText(item, PSKU_CODE_FIELDS);
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        for (String field : NESTED_CONTAINER_FIELDS) {
            Object nested = item.get(field);
            if (nested instanceof Map<?, ?>) {
                Map<?, ?> nestedMap = (Map<?, ?>) nested;
                String value = firstMapText(nestedMap, NESTED_VALUE_FIELDS);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    public static String pskuCode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String direct = firstJsonText(node, PSKU_CODE_FIELDS);
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        for (String field : NESTED_CONTAINER_FIELDS) {
            JsonNode nested = node.path(field);
            if (!nested.isMissingNode() && !nested.isNull() && nested.isObject()) {
                String value = firstJsonText(nested, NESTED_VALUE_FIELDS);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String firstMapText(Map<?, ?> item, List<String> fields) {
        if (item == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            Object value = item.get(field);
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private static String firstJsonText(JsonNode node, List<String> fields) {
        if (node == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isMissingNode() || value.isNull() || value.isContainerNode()) {
                continue;
            }
            String text = text(value.asText());
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }
}
