package com.nuono.next.productlisting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

final class ProductListingVerifiedAttributeEvidence {
    private static final Set<String> VALUE_FIELDS = Set.of("commonValue", "enValue", "arValue");
    private static final Set<String> NON_FACT_ATTRIBUTE_CODES = Set.of(
            "product_title",
            "family",
            "product_type",
            "product_subtype",
            "product_fulltype"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ProductListingVerifiedAttributeEvidence() {
    }

    static List<Map<String, Object>> selectedAttributes(List<Map<String, Object>> attributes) {
        List<Map<String, Object>> selected = new ArrayList<>();
        for (Map<String, Object> attribute : attributes == null ? List.<Map<String, Object>>of() : attributes) {
            if (attribute == null) {
                continue;
            }
            if (!isProtectedFactEvidence(attribute)) {
                continue;
            }
            Map<String, Object> evidence = new LinkedHashMap<>();
            putText(evidence, "code", attribute.get("code"));
            for (String field : VALUE_FIELDS) {
                putText(evidence, field, attribute.get(field));
            }
            if (evidence.keySet().stream().anyMatch(VALUE_FIELDS::contains)) {
                selected.add(evidence);
            }
        }
        return selected;
    }

    static boolean isProtectedFactEvidence(Map<String, Object> attribute) {
        String code = text(attribute == null ? null : attribute.get("code")).toLowerCase(Locale.ROOT);
        return !NON_FACT_ATTRIBUTE_CODES.contains(code);
    }

    static boolean containsSource(List<Map<String, Object>> attributes, String sourceText) {
        String expected = normalize(sourceText);
        if (!StringUtils.hasText(expected)) {
            return false;
        }
        List<Map<String, Object>> selected = selectedAttributes(attributes);
        if (selected.stream()
                .flatMap(attribute -> VALUE_FIELDS.stream().map(attribute::get))
                .map(ProductListingVerifiedAttributeEvidence::text)
                .map(ProductListingVerifiedAttributeEvidence::normalize)
                .anyMatch(value -> value.contains(expected))) {
            return true;
        }
        Map<String, Object> fragment = parseSelectedValueFragment(sourceText);
        return !fragment.isEmpty() && selected.stream().anyMatch(attribute -> matches(attribute, fragment));
    }

    private static boolean matches(Map<String, Object> attribute, Map<String, Object> fragment) {
        for (Map.Entry<String, Object> entry : fragment.entrySet()) {
            String actual = normalize(text(attribute.get(entry.getKey())));
            String expected = normalize(text(entry.getValue()));
            if (!StringUtils.hasText(actual) || !StringUtils.hasText(expected) || !actual.contains(expected)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> parseSelectedValueFragment(String sourceText) {
        String candidate = text(sourceText);
        if (!candidate.contains(":")) {
            return Map.of();
        }
        if (!candidate.startsWith("{")) {
            candidate = "{" + candidate + "}";
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(
                    candidate,
                    new TypeReference<Map<String, Object>>() { }
            );
            if (parsed.isEmpty() || !VALUE_FIELDS.containsAll(parsed.keySet())) {
                return Map.of();
            }
            boolean scalarValues = parsed.values().stream().allMatch(value ->
                    value instanceof String || value instanceof Number || value instanceof Boolean
            );
            return scalarValues ? parsed : Map.of();
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private static void putText(Map<String, Object> target, String key, Object value) {
        String text = text(value);
        if (StringUtils.hasText(text)) {
            target.put(key, text);
        }
    }

    private static String normalize(String value) {
        return Normalizer.normalize(text(value), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{Zs}\\u060C\\u061B\\u061F]+", " ")
                .replaceAll("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]", "")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
