package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

class ProductKeyAttributeBuilder {

    private final ObjectMapper objectMapper;

    ProductKeyAttributeBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<Map<String, Object>> buildKeyAttributes(
            JsonNode fulltypeTemplateRoot,
            JsonNode commonNode,
            JsonNode enNode,
            JsonNode arNode
    ) {
        JsonNode templateRoot = fulltypeTemplateDataNode(fulltypeTemplateRoot);
        Map<String, JsonNode> dictionaryByCode = attributeDictionaryMap(fulltypeTemplateRoot);
        List<Map<String, Object>> attributes = new ArrayList<>();
        Set<String> mandatoryCodes = new LinkedHashSet<>();
        JsonNode mandatoryNode = templateRoot.path("fundamental").path("attribute_class").path("mandatory");
        if (mandatoryNode.isArray()) {
            for (JsonNode node : mandatoryNode) {
                if (node.isTextual()) {
                    mandatoryCodes.add(node.asText());
                }
            }
        }

        JsonNode attributePropertiesNode = templateRoot.path("fundamental").path("attribute_properties");
        Set<String> candidateCodes = new LinkedHashSet<>(mandatoryCodes);
        if (attributePropertiesNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = attributePropertiesNode.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                JsonNode propertyNode = entry.getValue();
                if (propertyNode.path("is_grouping").asInt(0) == 1
                        || propertyNode.path("is_visible_seller").asInt(0) == 1) {
                    candidateCodes.add(entry.getKey());
                }
            }
        }
        for (JsonNode dictionaryNode : dictionaryByCode.values()) {
            String dictionaryCode = text(dictionaryNode, "code");
            if (StringUtils.hasText(dictionaryCode)) {
                candidateCodes.add(dictionaryCode);
            }
        }

        if (candidateCodes.isEmpty()) {
            candidateCodes.add("brand");
            candidateCodes.add("model_name");
            candidateCodes.add("model_number");
            candidateCodes.add("colour_family");
            candidateCodes.add("colour_name");
            candidateCodes.add("item_condition");
        }

        for (String code : candidateCodes) {
            JsonNode propertyNode = attributePropertiesNode.path(code);
            JsonNode dictionaryNode = dictionaryByCode.getOrDefault(normalizeAttributeCode(code), MissingNode.getInstance());
            List<Map<String, Object>> options = extractAttributeOptions(templateRoot, propertyNode, code);
            List<Map<String, Object>> unitOptions = extractAttributeUnitOptions(templateRoot, propertyNode, code);
            if (options.isEmpty()) {
                options = extractDictionaryOptions(dictionaryNode.path("options"));
            }
            if (unitOptions.isEmpty()) {
                unitOptions = extractDictionaryOptions(dictionaryNode.path("unitOptions"));
            }
            Object commonValue = toDisplayValue(commonNode.path(code));
            Object enValue = toDisplayValue(enNode.path(code));
            Object arValue = toDisplayValue(arNode.path(code));
            String unitValue = firstNonBlank(
                    text(commonNode, code + "_unit"),
                    text(enNode, code + "_unit"),
                    text(arNode, code + "_unit")
            );
            boolean required = mandatoryCodes.contains(code);
            if (!required && dictionaryNode.path("required").asBoolean(false)) {
                required = true;
            }
            boolean grouping = propertyNode.path("is_grouping").asInt(0) == 1
                    || dictionaryNode.path("grouping").asBoolean(false);
            boolean visibleSeller = propertyNode.path("is_visible_seller").asInt(0) == 1
                    || dictionaryNode.path("visibleSeller").asBoolean(false);

            if (!required && !grouping && !visibleSeller
                    && commonValue == null && enValue == null && arValue == null) {
                continue;
            }

            Map<String, Object> attribute = new LinkedHashMap<>();
            attribute.put("code", code);
            attribute.put("required", required);
            attribute.put("grouping", grouping);
            attribute.put("visibleSeller", visibleSeller);
            attribute.put("kind", resolveDictionaryInputKind(dictionaryNode, code, propertyNode, options, unitOptions));
            putIfNotBlank(attribute, "labelEn", firstNonBlank(
                    resolveAttributeLabel(propertyNode, code, "en"),
                    text(dictionaryNode, "labelEn")
            ));
            putIfNotBlank(attribute, "labelAr", firstNonBlank(
                    resolveAttributeLabel(propertyNode, code, "ar"),
                    text(dictionaryNode, "labelAr")
            ));
            putIfNotBlank(attribute, "labelZh", text(dictionaryNode, "labelZh"));
            putIfNotBlank(attribute, "groupName", firstNonBlank(
                    text(propertyNode.path("attribute_group_name"), "en"),
                    text(dictionaryNode, "groupName")
            ));
            putIfNotEmpty(attribute, "options", options);
            putIfNotEmpty(attribute, "unitOptions", unitOptions);
            if (!options.isEmpty() || !unitOptions.isEmpty()) {
                attribute.put("dictionarySource", firstNonBlank(text(dictionaryNode, "dictionarySource"), "official-template"));
            }
            putIfNotNull(attribute, "commonValue", commonValue);
            putIfNotNull(attribute, "enValue", enValue);
            putIfNotNull(attribute, "arValue", arValue);
            putIfNotBlank(attribute, "unit", unitValue);
            attributes.add(attribute);
        }

        return attributes;
    }

    private JsonNode fulltypeTemplateDataNode(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return MissingNode.getInstance();
        }
        if (root.path("fundamental").isObject()) {
            return root;
        }
        JsonNode dataCandidate = fulltypeTemplateDataNodeFromContainer(root.path("data"));
        if (!dataCandidate.isMissingNode()) {
            return dataCandidate;
        }
        JsonNode rootCandidate = fulltypeTemplateDataNodeFromContainer(root);
        if (!rootCandidate.isMissingNode()) {
            return rootCandidate;
        }
        return root;
    }

    private JsonNode fulltypeTemplateDataNodeFromContainer(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return MissingNode.getInstance();
        }
        if (node.path("fundamental").isObject()) {
            return node;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode candidate = fulltypeTemplateDataNodeFromContainer(item);
                if (!candidate.isMissingNode()) {
                    return candidate;
                }
            }
            return MissingNode.getInstance();
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                JsonNode candidate = fulltypeTemplateDataNodeFromContainer(iterator.next().getValue());
                if (!candidate.isMissingNode()) {
                    return candidate;
                }
            }
        }
        return MissingNode.getInstance();
    }

    private Map<String, JsonNode> attributeDictionaryMap(JsonNode fulltypeTemplateRoot) {
        Map<String, JsonNode> byCode = new LinkedHashMap<>();
        JsonNode dictionaryNode = fulltypeTemplateRoot == null
                ? MissingNode.getInstance()
                : fulltypeTemplateRoot.path("_nuonoAttributeDictionary");
        if (!dictionaryNode.isArray()) {
            return byCode;
        }
        for (JsonNode fieldNode : dictionaryNode) {
            String code = text(fieldNode, "code");
            String key = normalizeAttributeCode(code);
            if (StringUtils.hasText(key)) {
                byCode.putIfAbsent(key, fieldNode);
            }
        }
        return byCode;
    }

    private String resolveDictionaryInputKind(
            JsonNode dictionaryNode,
            String code,
            JsonNode propertyNode,
            List<Map<String, Object>> options,
            List<Map<String, Object>> unitOptions
    ) {
        String resolvedKind = resolveAttributeInputKind(code, propertyNode, options, unitOptions);
        if (!"text".equals(resolvedKind)) {
            return resolvedKind;
        }
        String dictionaryKind = text(dictionaryNode, "kind");
        if (!StringUtils.hasText(dictionaryKind)) {
            return resolvedKind;
        }
        String normalizedKind = dictionaryKind.trim().toLowerCase();
        if ("select".equals(normalizedKind) || "dimension".equals(normalizedKind) || "textarea".equals(normalizedKind)) {
            return normalizedKind;
        }
        return resolvedKind;
    }

    private List<Map<String, Object>> extractDictionaryOptions(JsonNode node) {
        List<Map<String, Object>> options = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectOptionsFromNode(options, seen, node);
        return options;
    }

    private String resolveAttributeInputKind(
            String code,
            JsonNode propertyNode,
            List<Map<String, Object>> options,
            List<Map<String, Object>> unitOptions
    ) {
        if (!unitOptions.isEmpty() || isDimensionAttribute(code)) {
            return "dimension";
        }
        if (!options.isEmpty()) {
            return "select";
        }
        String valueType = firstNonBlank(
                text(propertyNode, "value_type"),
                text(propertyNode, "data_type"),
                text(propertyNode, "input_type"),
                text(propertyNode, "type")
        );
        if (!StringUtils.hasText(valueType)) {
            return "text";
        }
        String normalizedType = valueType.toLowerCase();
        if (normalizedType.contains("select") || normalizedType.contains("enum") || normalizedType.contains("option")) {
            return "select";
        }
        if (normalizedType.contains("textarea") || normalizedType.contains("rich") || normalizedType.contains("long")) {
            return "textarea";
        }
        return "text";
    }

    private List<Map<String, Object>> extractAttributeOptions(JsonNode templateRoot, JsonNode propertyNode, String code) {
        List<Map<String, Object>> options = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectOptionsFromNode(options, seen, firstExisting(
                propertyNode,
                "options",
                "attribute_options",
                "option_values",
                "values",
                "allowed_values",
                "allowedValues"
        ));
        JsonNode specsNode = firstExisting(propertyNode, "specs", "attribute_specs", "attributeSpecs");
        collectOptionsFromNode(options, seen, firstExisting(
                specsNode,
                "options",
                "attribute_options",
                "option_values",
                "values",
                "allowed_values",
                "allowedValues"
        ));
        JsonNode templateSpecs = firstExisting(
                templateRoot.path("fundamental").path("attribute_specs").path(code),
                "options",
                "attribute_options",
                "option_values",
                "values",
                "allowed_values",
                "allowedValues"
        );
        collectOptionsFromNode(options, seen, templateSpecs);
        return options;
    }

    private List<Map<String, Object>> extractAttributeUnitOptions(JsonNode templateRoot, JsonNode propertyNode, String code) {
        List<Map<String, Object>> unitOptions = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectOptionsFromNode(unitOptions, seen, firstExisting(
                propertyNode,
                "unit_options",
                "unitOptions",
                "units",
                "allowed_units",
                "allowedUnits",
                "measurement_units"
        ));
        JsonNode templateSpecs = templateRoot.path("fundamental").path("attribute_specs").path(code);
        collectOptionsFromNode(unitOptions, seen, firstExisting(
                templateSpecs,
                "unit_options",
                "unitOptions",
                "units",
                "allowed_units",
                "allowedUnits",
                "measurement_units"
        ));
        if (unitOptions.isEmpty() && isDimensionAttribute(code)) {
            String[] fallbackUnits = code.toLowerCase().contains("weight")
                    ? new String[]{"g", "KG", "lb", "lbs"}
                    : new String[]{"mm", "cm", "m", "in", "ft"};
            for (String unit : fallbackUnits) {
                addOption(unitOptions, seen, unit, unit, null, null);
            }
        }
        return unitOptions;
    }

    private void collectOptionsFromNode(List<Map<String, Object>> options, Set<String> seen, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectSingleOption(options, seen, item);
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                JsonNode item = entry.getValue();
                if (item.isValueNode()) {
                    addOption(options, seen, entry.getKey(), item.asText(), null, null);
                } else {
                    collectSingleOption(options, seen, item);
                }
            }
            return;
        }
        collectSingleOption(options, seen, node);
    }

    private void collectSingleOption(List<Map<String, Object>> options, Set<String> seen, JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) {
            return;
        }
        if (item.isValueNode()) {
            String value = item.asText();
            addOption(options, seen, value, value, null, null);
            return;
        }
        String en = firstNonBlank(
                localizedText(item, "option_name", "en"),
                localizedText(item, "name", "en"),
                localizedText(item, "label", "en"),
                text(item, "option_name_en"),
                text(item, "name_en"),
                text(item, "label_en"),
                text(item, "en")
        );
        String ar = firstNonBlank(
                localizedText(item, "option_name", "ar"),
                localizedText(item, "name", "ar"),
                localizedText(item, "label", "ar"),
                text(item, "option_name_ar"),
                text(item, "name_ar"),
                text(item, "label_ar"),
                text(item, "ar")
        );
        String zh = firstNonBlank(
                localizedText(item, "option_name", "zh"),
                localizedText(item, "name", "zh"),
                localizedText(item, "label", "zh"),
                text(item, "option_name_zh"),
                text(item, "name_zh"),
                text(item, "label_zh"),
                text(item, "zh")
        );
        String value = firstNonBlank(
                text(item, "value"),
                text(item, "option_value"),
                text(item, "code"),
                text(item, "option_code"),
                en
        );
        addOption(options, seen, value, firstNonBlank(en, value), ar, zh);
    }

    private void addOption(List<Map<String, Object>> options, Set<String> seen, String value, String en, String ar, String zh) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(en)) {
            return;
        }
        String key = value.trim().toLowerCase();
        if (seen.contains(key)) {
            return;
        }
        seen.add(key);
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("value", value.trim());
        option.put("en", en.trim());
        putIfNotBlank(option, "ar", ar);
        putIfNotBlank(option, "zh", zh);
        options.add(option);
    }

    private String resolveAttributeLabel(JsonNode propertyNode, String code, String lang) {
        return firstNonBlank(
                localizedText(propertyNode, "attribute_name", lang),
                localizedText(propertyNode, "display_name", lang),
                localizedText(propertyNode, "name", lang),
                localizedText(propertyNode, "label", lang),
                text(propertyNode, "attribute_name_" + lang),
                text(propertyNode, "display_name_" + lang),
                text(propertyNode, "name_" + lang),
                text(propertyNode, "label_" + lang),
                "en".equals(lang) ? humanizeAttributeCode(code) : null
        );
    }

    private String localizedText(JsonNode node, String field, String lang) {
        JsonNode valueNode = node != null ? node.path(field) : MissingNode.getInstance();
        if (valueNode.isObject()) {
            return text(valueNode, lang);
        }
        if ("en".equals(lang) && valueNode.isValueNode()) {
            return valueNode.asText();
        }
        return null;
    }

    private boolean isDimensionAttribute(String code) {
        String normalizedCode = normalize(code);
        if (!StringUtils.hasText(normalizedCode)) {
            return false;
        }
        String lowerCaseCode = normalizedCode.toLowerCase();
        return lowerCaseCode.contains("height")
                || lowerCaseCode.contains("length")
                || lowerCaseCode.contains("weight")
                || lowerCaseCode.contains("width")
                || lowerCaseCode.contains("depth");
    }

    private String humanizeAttributeCode(String code) {
        String normalizedCode = normalize(code);
        if (!StringUtils.hasText(normalizedCode)) {
            return null;
        }
        String[] parts = normalizedCode.replace('-', '_').split("_+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return builder.toString();
    }

    private JsonNode firstExisting(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return MissingNode.getInstance();
        }
        for (String fieldName : fieldNames) {
            JsonNode candidate = node.path(fieldName);
            if (!candidate.isMissingNode() && !candidate.isNull()) {
                return candidate;
            }
        }
        return MissingNode.getInstance();
    }

    private Object toDisplayValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return objectMapper.convertValue(node, Object.class);
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private void putIfNotEmpty(Map<String, Object> target, String key, List<?> values) {
        if (values != null && !values.isEmpty()) {
            target.put(key, values);
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode valueNode = node.path(field);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        String value = valueNode.isValueNode() ? valueNode.asText() : valueNode.toString();
        return StringUtils.hasText(value) ? value : null;
    }

    private String firstNonBlank(String... values) {
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

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeAttributeCode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase().replaceAll("[\\s-]+", "_");
    }
}
