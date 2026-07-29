package com.nuono.next.competitoranalysis.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.util.StringUtils;

final class NoonVisibleTagSupport {
    private static final List<String> CONTAINER_FIELDS = List.of(
            "badges",
            "labels",
            "flags",
            "tags",
            "tag",
            "promo_tags",
            "promotion_tags",
            "logistics_tags"
    );
    private static final List<String> LABEL_FIELDS = List.of(
            "label",
            "text",
            "name",
            "title",
            "value",
            "badge"
    );

    private final ObjectMapper objectMapper;

    NoonVisibleTagSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String resolve(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Map<String, String> labels = new TreeMap<>();
        for (String field : CONTAINER_FIELDS) {
            JsonNode value = node.path(field);
            if (hasContent(value)) {
                collect(value, labels, 0);
            }
        }
        if (labels.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(labels.values());
        } catch (Exception ignored) {
            return null;
        }
    }

    private void collect(
            JsonNode node,
            Map<String, String> labels,
            int depth
    ) {
        if (node == null || depth > 5
                || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            addLabel(labels, compact(node.asText()));
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collect(item, labels, depth + 1));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        boolean foundNamedLabel = false;
        for (String field : LABEL_FIELDS) {
            JsonNode value = node.path(field);
            if (value.isTextual() || value.isArray()) {
                foundNamedLabel = true;
                collect(value, labels, depth + 1);
            }
        }
        if (!foundNamedLabel) {
            node.elements().forEachRemaining(value -> {
                if (value.isArray() || value.isObject()) {
                    collect(value, labels, depth + 1);
                }
            });
        }
    }

    private void addLabel(Map<String, String> labels, String label) {
        if (StringUtils.hasText(label)
                && label.length() <= 120
                && !isSponsored(label)) {
            labels.putIfAbsent(label.toLowerCase(Locale.ROOT), label);
        }
    }

    private boolean isSponsored(String value) {
        String normalized = compact(value).toLowerCase(Locale.ROOT);
        return normalized.equals("ad")
                || normalized.equals("ads")
                || normalized.equals("pla")
                || normalized.equals("sponsored")
                || normalized.contains("sponsor")
                || normalized.contains("advert")
                || normalized.contains("product listing ad");
    }

    private boolean hasContent(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isArray() || node.isObject()) {
            return node.size() > 0;
        }
        if (node.isTextual()) {
            return StringUtils.hasText(compact(node.asText()));
        }
        return node.isNumber() || node.isBoolean();
    }

    private String compact(String value) {
        return StringUtils.hasText(value)
                ? value.replace('\u00A0', ' ')
                        .replaceAll("\\s+", " ")
                        .trim()
                : null;
    }
}
