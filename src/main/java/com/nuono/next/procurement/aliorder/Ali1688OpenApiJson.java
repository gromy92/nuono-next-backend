package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/** Small JSON Adapter shared by order mapping and response-contract proof. */
final class Ali1688OpenApiJson {
    private final ObjectMapper objectMapper;

    Ali1688OpenApiJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    JsonNode read(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (Exception invalid) {
            throw new IllegalStateException("invalid 1688 JSON response", invalid);
        }
    }

    JsonNode unwrapResult(JsonNode root) {
        if (root == null || root.isNull()) return objectMapper.createObjectNode();
        JsonNode result = root.get("result");
        return result == null || result.isNull() ? root : result;
    }

    JsonNode firstObject(JsonNode node, String... names) {
        if (node == null || node.isNull()) return null;
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isObject()) return value;
        }
        return null;
    }

    List<JsonNode> arrayValues(JsonNode node, String... names) {
        List<JsonNode> values = new ArrayList<>();
        if (node == null || node.isNull()) return values;
        if (node.isArray()) {
            node.forEach(values::add);
            return values;
        }
        for (String name : names) {
            JsonNode candidate = node.get(name);
            if (candidate != null && candidate.isArray()) {
                candidate.forEach(values::add);
                return values;
            }
        }
        return values;
    }

    String text(JsonNode node, String... names) {
        if (node == null || names == null) return null;
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) continue;
            if (value.isArray()) {
                for (JsonNode item : value) {
                    if (item != null
                            && !item.isNull()
                            && item.isValueNode()
                            && StringUtils.hasText(item.asText())) {
                        return item.asText().trim();
                    }
                }
            } else if (StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    Integer integer(JsonNode node, String... names) {
        String value = text(node, names);
        if (!StringUtils.hasText(value)) return null;
        try {
            return new BigDecimal(value.trim()).intValueExact();
        } catch (NumberFormatException | ArithmeticException ignored) {
            return null;
        }
    }

    Long longInteger(JsonNode node, String... names) {
        String value = text(node, names);
        if (!StringUtils.hasText(value)) return null;
        try {
            return new BigDecimal(value.trim()).longValueExact();
        } catch (NumberFormatException | ArithmeticException ignored) {
            return null;
        }
    }

    String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ignored) {
            return null;
        }
    }
}
