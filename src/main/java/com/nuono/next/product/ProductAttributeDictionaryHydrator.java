package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

class ProductAttributeDictionaryHydrator {

    private final ProductAttributeTemplateService productAttributeTemplateService;

    ProductAttributeDictionaryHydrator(ProductAttributeTemplateService productAttributeTemplateService) {
        this.productAttributeTemplateService = productAttributeTemplateService;
    }

    void hydrateWorkbenchAttributeDictionary(
            Long ownerUserId,
            String storeCode,
            ProductWorkbenchRecord record,
            List<String> warnings
    ) {
        if (record == null) {
            return;
        }
        hydrateSnapshotAttributeDictionary(ownerUserId, storeCode, record.getBaselineSnapshot(), warnings);
        hydrateSnapshotAttributeDictionary(ownerUserId, storeCode, record.getDraftSnapshot(), warnings);
    }

    void hydrateSnapshotAttributeDictionary(
            Long ownerUserId,
            String storeCode,
            ProductMasterSnapshotView snapshot,
            List<String> warnings
    ) {
        if (snapshot == null || snapshot.getKeyAttributes() == null || snapshot.getKeyAttributes().isEmpty()) {
            return;
        }
        String projectCode = firstNonBlank(
                textValue(snapshot.getStoreContext().get("projectCode")),
                storeCode
        );
        String resolvedStoreCode = firstNonBlank(
                textValue(snapshot.getStoreContext().get("storeCode")),
                storeCode
        );
        String productFulltype = textValue(snapshot.getTaxonomy().get("productFulltype"));
        if (!StringUtils.hasText(projectCode)
                || !StringUtils.hasText(resolvedStoreCode)
                || !StringUtils.hasText(productFulltype)) {
            return;
        }
        JsonNode templateRoot = productAttributeTemplateService.loadTemplate(
                null,
                projectCode,
                resolvedStoreCode,
                productFulltype,
                ownerUserId,
                warnings
        );
        Map<String, JsonNode> dictionaryByCode = attributeDictionaryMap(templateRoot);
        if (dictionaryByCode.isEmpty()) {
            return;
        }
        for (Map<String, Object> attribute : snapshot.getKeyAttributes()) {
            String code = textValue(attribute.get("code"));
            JsonNode dictionaryNode = dictionaryByCode.get(normalizeAttributeCode(code));
            if (dictionaryNode == null || dictionaryNode.isMissingNode()) {
                continue;
            }
            List<Map<String, Object>> options = extractDictionaryOptions(dictionaryNode.path("options"));
            List<Map<String, Object>> unitOptions = extractDictionaryOptions(dictionaryNode.path("unitOptions"));
            if (!options.isEmpty() && !hasListValue(attribute.get("options"))) {
                attribute.put("options", options);
            }
            if (!unitOptions.isEmpty() && !hasListValue(attribute.get("unitOptions"))) {
                attribute.put("unitOptions", unitOptions);
            }
            if (StringUtils.hasText(text(dictionaryNode, "labelEn"))
                    && !StringUtils.hasText(textValue(attribute.get("labelEn")))) {
                attribute.put("labelEn", text(dictionaryNode, "labelEn"));
            }
            if (StringUtils.hasText(text(dictionaryNode, "labelAr"))
                    && !StringUtils.hasText(textValue(attribute.get("labelAr")))) {
                attribute.put("labelAr", text(dictionaryNode, "labelAr"));
            }
            if (StringUtils.hasText(text(dictionaryNode, "labelZh"))
                    && !StringUtils.hasText(textValue(attribute.get("labelZh")))) {
                attribute.put("labelZh", text(dictionaryNode, "labelZh"));
            }
            if (StringUtils.hasText(text(dictionaryNode, "groupName"))
                    && !StringUtils.hasText(textValue(attribute.get("groupName")))) {
                attribute.put("groupName", text(dictionaryNode, "groupName"));
            }
            if (!options.isEmpty() || !unitOptions.isEmpty()) {
                attribute.put("dictionarySource", firstNonBlank(text(dictionaryNode, "dictionarySource"), "official-template"));
            }
            String currentKind = normalize(textValue(attribute.get("kind")));
            if (!unitOptions.isEmpty()) {
                attribute.put("kind", "dimension");
            } else if (!options.isEmpty()) {
                attribute.put("kind", "select");
            } else if (!StringUtils.hasText(currentKind) && StringUtils.hasText(text(dictionaryNode, "kind"))) {
                attribute.put("kind", text(dictionaryNode, "kind"));
            }
        }
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

    private List<Map<String, Object>> extractDictionaryOptions(JsonNode node) {
        List<Map<String, Object>> options = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectOptionsFromNode(options, seen, node);
        return options;
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

    private boolean hasListValue(Object value) {
        return value instanceof List<?> && !((List<?>) value).isEmpty();
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.path(field) : MissingNode.getInstance();
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isValueNode()) {
            return value.asText();
        }
        return value.toString();
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeAttributeCode(String value) {
        String normalized = normalize(value);
        return StringUtils.hasText(normalized) ? normalized.toLowerCase() : null;
    }
}
