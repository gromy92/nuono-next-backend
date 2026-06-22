package com.nuono.next.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

class ProductPublishUnsupportedChangesDetector {

    private final ObjectMapper objectMapper;

    ProductPublishUnsupportedChangesDetector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ProductPublishUnsupportedChanges detect(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        ProductPublishUnsupportedChanges unsupportedChanges = new ProductPublishUnsupportedChanges();

        if (!objectMapper.valueToTree(groupDefinitionComparable(draft.getGroup()))
                .equals(objectMapper.valueToTree(groupDefinitionComparable(baseline.getGroup())))) {
            unsupportedChanges.setGroupChanged(true);
        }
        Map<String, Map<String, Object>> draftVariants = variantMap(draft.getVariants());
        Map<String, Map<String, Object>> baselineVariants = variantMap(baseline.getVariants());
        if (!draftVariants.keySet().equals(baselineVariants.keySet())) {
            unsupportedChanges.setVariantStructureChanged(true);
        }

        Map<String, Map<String, Object>> draftAttributes = keyAttributeMap(draft.getKeyAttributes());
        Map<String, Map<String, Object>> baselineAttributes = keyAttributeMap(baseline.getKeyAttributes());
        Set<String> allAttributeCodes = new LinkedHashSet<>();
        allAttributeCodes.addAll(draftAttributes.keySet());
        allAttributeCodes.addAll(baselineAttributes.keySet());
        for (String code : allAttributeCodes) {
            Map<String, Object> draftAttribute = draftAttributes.get(code);
            Map<String, Object> baselineAttribute = baselineAttributes.get(code);
            if (objectMapper.valueToTree(draftAttribute).equals(objectMapper.valueToTree(baselineAttribute))) {
                continue;
            }
            if (draftAttribute == null || baselineAttribute == null || isCoreAttribute(code) || isBarcodeAttribute(code)) {
                unsupportedChanges.getUnsupportedAttributeCodes().add(code);
                continue;
            }
            if (!isScalarAttributeValue(attributeValue(draftAttribute, "commonValue"))
                    || !isScalarAttributeValue(attributeValue(draftAttribute, "enValue"))
                    || !isScalarAttributeValue(attributeValue(draftAttribute, "arValue"))
                    || !isScalarAttributeValue(attributeValue(baselineAttribute, "commonValue"))
                    || !isScalarAttributeValue(attributeValue(baselineAttribute, "enValue"))
                    || !isScalarAttributeValue(attributeValue(baselineAttribute, "arValue"))) {
                unsupportedChanges.getUnsupportedAttributeCodes().add(code);
            }
        }

        Map<String, Map<String, Object>> draftOffers = siteOfferMap(draft.getSiteOffers());
        Map<String, Map<String, Object>> baselineOffers = siteOfferMap(baseline.getSiteOffers());
        Set<String> relevantSiteCodes = new LinkedHashSet<>();
        if (StringUtils.hasText(currentSiteCode)) {
            relevantSiteCodes.add(currentSiteCode);
        } else {
            relevantSiteCodes.addAll(draftOffers.keySet());
        }
        for (String siteCode : relevantSiteCodes) {
            Map<String, Object> draftOffer = draftOffers.get(siteCode);
            Map<String, Object> baselineOffer = baselineOffers.get(siteCode);
            if (draftOffer == null || baselineOffer == null) {
                continue;
            }
            for (String field : new String[]{"barcode"}) {
                if (!objectMapper.valueToTree(draftOffer.get(field)).equals(objectMapper.valueToTree(baselineOffer.get(field)))) {
                    unsupportedChanges.markUnsupportedSiteField(siteCode, field);
                }
            }
        }

        return unsupportedChanges;
    }

    List<String> validateWriteCoverage(ProductPublishUnsupportedChanges unsupportedChanges) {
        List<String> errors = new ArrayList<>();
        if (unsupportedChanges == null) {
            return errors;
        }
        errors.addAll(unsupportedChanges.getPublishBlockers());
        if (unsupportedChanges.isGroupChanged()) {
            errors.add("Group 换组或轴定义当前暂未开放 Noon 写回；本期支持已有成员 Group 轴属性值、新增未分组商品和 Unlink。");
        }
        if (unsupportedChanges.isVariantStructureChanged()) {
            errors.add("尺码新增、删除或 Child SKU 变更当前没有 Noon 写回适配，请撤回这类修改后再发布。");
        }
        for (String code : unsupportedChanges.getUnsupportedAttributeCodes()) {
            errors.add("关键属性 " + code + " 当前没有 Noon 写回适配，请撤回这类修改后再发布。");
        }
        for (Map.Entry<String, Set<String>> entry : unsupportedChanges.getUnsupportedSiteFields().entrySet()) {
            errors.add(entry.getKey() + " 的 " + String.join("、", entry.getValue()) + " 当前没有 Noon 写回适配，或属于 Noon 只读/汇总字段。");
        }
        return errors;
    }

    private Map<String, Object> groupDefinitionComparable(Map<String, Object> group) {
        Map<String, Object> comparable = new LinkedHashMap<>();
        if (group == null) {
            return comparable;
        }
        comparable.put("skuGroup", textValue(group.get("skuGroup")));
        comparable.put("groupRef", textValue(group.get("groupRef")));
        comparable.put("groupRefCanonical", textValue(group.get("groupRefCanonical")));
        comparable.put("conditionsBrand", textValue(group.get("conditionsBrand")));
        comparable.put("conditionsFulltype", textValue(group.get("conditionsFulltype")));

        List<Map<String, Object>> axes = new ArrayList<>();
        for (Map<String, Object> axis : recordListValue(group.get("axes"))) {
            String axisCode = textValue(firstNonNull(axis.get("axisCode"), axis.get("axis_code")));
            if (!StringUtils.hasText(axisCode)) {
                continue;
            }
            Map<String, Object> axisComparable = new LinkedHashMap<>();
            axisComparable.put("axisCode", axisCode);
            axisComparable.put("axisName", textValue(firstNonNull(axis.get("axisName"), axis.get("axis_name"))));
            axes.add(axisComparable);
        }
        axes.sort((left, right) -> textValue(left.get("axisCode")).compareTo(textValue(right.get("axisCode"))));
        comparable.put("axes", axes);
        return comparable;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> recordListValue(Object value) {
        if (!(value instanceof List<?>)) {
            return List.of();
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map<?, ?>) {
                records.add(new LinkedHashMap<>((Map<String, Object>) item));
            }
        }
        return records;
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Map<String, Map<String, Object>> keyAttributeMap(List<Map<String, Object>> keyAttributes) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (keyAttributes == null) {
            return map;
        }
        for (Map<String, Object> attribute : keyAttributes) {
            String code = textValue(attribute.get("code"));
            if (StringUtils.hasText(code)) {
                map.put(code, new LinkedHashMap<>(attribute));
            }
        }
        return map;
    }

    private Map<String, Map<String, Object>> variantMap(List<Map<String, Object>> variants) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (variants == null) {
            return map;
        }
        for (Map<String, Object> variant : variants) {
            String childSku = textValue(variant.get("childSku"));
            if (StringUtils.hasText(childSku)) {
                map.put(childSku, new LinkedHashMap<>(variant));
            }
        }
        return map;
    }

    private Map<String, Map<String, Object>> siteOfferMap(List<Map<String, Object>> siteOffers) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (siteOffers == null) {
            return map;
        }
        for (Map<String, Object> siteOffer : siteOffers) {
            map.put(siteOfferCode(siteOffer), new LinkedHashMap<>(siteOffer));
        }
        return map;
    }

    private boolean isCoreAttribute(String code) {
        return "brand".equals(code)
                || "family".equals(code)
                || "product_type".equals(code)
                || "product_subtype".equals(code)
                || "product_fulltype".equals(code)
                || "item_condition".equals(code)
                || "grade".equals(code)
                || "product_title".equals(code)
                || "long_description".equals(code);
    }

    private boolean isBarcodeAttribute(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        String normalized = code.trim().toLowerCase();
        if (normalized.contains("barcode")) {
            return true;
        }
        for (String token : normalized.split("[^a-z0-9]+")) {
            if ("gtin".equals(token) || "ean".equals(token) || "upc".equals(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean isScalarAttributeValue(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean;
    }

    private Object attributeValue(Map<String, Object> attribute, String field) {
        return attribute == null ? null : attribute.get(field);
    }

    private String siteOfferCode(Map<String, Object> siteOffer) {
        return textValue(siteOffer.get("storeCode"));
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }
}
