package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

public class ProductPublishReadbackReconciler {

    private static final int MAX_VERIFY_ATTEMPTS = 3;
    private static final Set<String> READBACK_ONLY_STATUSES = Set.of(
            "submitted",
            "verifying",
            "pending_effective",
            "write_unknown",
            "verify_timeout"
    );

    private final ObjectMapper objectMapper;

    public ProductPublishReadbackReconciler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProductPublishReadbackReconciliation reconcile(
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView publishableDraft,
            ProductMasterSnapshotView noonCurrent,
            String siteCode,
            int verifyAttempt
    ) {
        if (publishedChangesMatch(baseline, publishableDraft, noonCurrent, siteCode)) {
            return ProductPublishReadbackReconciliation.synced();
        }
        return verifyAttempt >= MAX_VERIFY_ATTEMPTS
                ? ProductPublishReadbackReconciliation.pendingManualCheck()
                : ProductPublishReadbackReconciliation.pendingEffective();
    }

    public ProductPublishReadbackReconciliation timeoutDecision() {
        return ProductPublishReadbackReconciliation.verifyTimeout();
    }

    public boolean isReadbackOnlyStatus(String status) {
        return READBACK_ONLY_STATUSES.contains(ProductPublishSnapshotSupport.normalize(status));
    }

    public boolean publishedChangesMatch(
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView publishableDraft,
            ProductMasterSnapshotView noonCurrent,
            String siteCode
    ) {
        return publishChangedFieldsMatch(
                toPublishComparableScopedJson(baseline, siteCode),
                toPublishComparableScopedJson(publishableDraft, siteCode),
                toPublishComparableScopedJson(noonCurrent, siteCode)
        );
    }

    private JsonNode toPublishComparableScopedJson(ProductMasterSnapshotView snapshot, String siteCode) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("shared", objectMapper.valueToTree(sharedComparableView(snapshot)));
        node.set("siteOffers", objectMapper.valueToTree(
                ProductPublishSnapshotSupport.siteOfferComparableList(snapshot, siteCode, false)
        ));
        return node;
    }

    private boolean publishChangedFieldsMatch(JsonNode baseline, JsonNode draft, JsonNode noonCurrent) {
        JsonNode baselineNode = comparableNode(baseline);
        JsonNode draftNode = comparableNode(draft);
        JsonNode noonNode = comparableNode(noonCurrent);
        if (baselineNode.equals(draftNode)) {
            return true;
        }
        if (baselineNode.isObject() || draftNode.isObject() || noonNode.isObject()) {
            Set<String> fieldNames = new LinkedHashSet<>();
            collectFieldNames(baselineNode, fieldNames);
            collectFieldNames(draftNode, fieldNames);
            collectFieldNames(noonNode, fieldNames);
            for (String fieldName : fieldNames) {
                if (!publishChangedFieldsMatch(
                        baselineNode.path(fieldName),
                        draftNode.path(fieldName),
                        noonNode.path(fieldName)
                )) {
                    return false;
                }
            }
            return true;
        }
        if (baselineNode.isArray() || draftNode.isArray() || noonNode.isArray()) {
            int maxSize = Math.max(baselineNode.size(), Math.max(draftNode.size(), noonNode.size()));
            for (int index = 0; index < maxSize; index++) {
                if (!publishChangedFieldsMatch(
                        baselineNode.path(index),
                        draftNode.path(index),
                        noonNode.path(index)
                )) {
                    return false;
                }
            }
            return true;
        }
        return draftNode.equals(noonNode);
    }

    private void collectFieldNames(JsonNode node, Set<String> fieldNames) {
        if (node == null || !node.isObject()) {
            return;
        }
        Iterator<String> iterator = node.fieldNames();
        while (iterator.hasNext()) {
            fieldNames.add(iterator.next());
        }
    }

    private JsonNode comparableNode(JsonNode node) {
        return node == null || node.isMissingNode() ? MissingNode.getInstance() : node;
    }

    private Map<String, Object> sharedComparableView(ProductMasterSnapshotView snapshot) {
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("identity", publishComparableIdentity(snapshot));
        comparable.put("taxonomy", publishComparableTaxonomy(snapshot));
        comparable.put("content", publishComparableContent(snapshot));
        comparable.put("keyAttributes", publishComparableKeyAttributes(snapshot));
        comparable.put("group", publishComparableGroup(snapshot));
        comparable.put("variants", publishComparableVariants(snapshot));
        return comparable;
    }

    private Map<String, Object> publishComparableIdentity(ProductMasterSnapshotView snapshot) {
        Map<String, Object> identity = snapshot != null && snapshot.getIdentity() != null
                ? snapshot.getIdentity()
                : Map.of();
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("brand", ProductPublishSnapshotSupport.textValue(identity.get("brand")));
        return comparable;
    }

    private Map<String, Object> publishComparableTaxonomy(ProductMasterSnapshotView snapshot) {
        Map<String, Object> taxonomy = snapshot != null && snapshot.getTaxonomy() != null
                ? snapshot.getTaxonomy()
                : Map.of();
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("family", ProductPublishSnapshotSupport.textValue(taxonomy.get("family")));
        comparable.put("productType", ProductPublishSnapshotSupport.textValue(taxonomy.get("productType")));
        comparable.put("productSubtype", ProductPublishSnapshotSupport.textValue(taxonomy.get("productSubtype")));
        comparable.put("productFulltype", ProductPublishSnapshotSupport.textValue(taxonomy.get("productFulltype")));
        comparable.put("grade", ProductPublishSnapshotSupport.textValue(taxonomy.get("grade")));
        comparable.put("itemCondition", ProductPublishSnapshotSupport.textValue(taxonomy.get("itemCondition")));
        return comparable;
    }

    private Map<String, Object> publishComparableContent(ProductMasterSnapshotView snapshot) {
        Map<String, Object> content = snapshot != null && snapshot.getContent() != null
                ? snapshot.getContent()
                : Map.of();
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("titleEn", ProductPublishSnapshotSupport.textValue(content.get("titleEn")));
        comparable.put("titleAr", ProductPublishSnapshotSupport.textValue(content.get("titleAr")));
        comparable.put("descriptionEn", ProductPublishSnapshotSupport.textValue(content.get("descriptionEn")));
        comparable.put("descriptionAr", ProductPublishSnapshotSupport.textValue(content.get("descriptionAr")));
        comparable.put("highlightsEn", stringList(content.get("highlightsEn")));
        comparable.put("highlightsAr", stringList(content.get("highlightsAr")));
        comparable.put("images", stringList(content.get("images")));
        return comparable;
    }

    private List<Map<String, Object>> publishComparableKeyAttributes(ProductMasterSnapshotView snapshot) {
        List<Map<String, Object>> source = snapshot != null && snapshot.getKeyAttributes() != null
                ? snapshot.getKeyAttributes()
                : List.of();
        List<Map<String, Object>> comparable = new ArrayList<>();
        for (Map<String, Object> attribute : source) {
            if (attribute == null) {
                continue;
            }
            String code = normalizeAttributeCode(ProductPublishSnapshotSupport.textValue(attribute.get("code")));
            if (!StringUtils.hasText(code)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", code);
            row.put("commonValue", attribute.get("commonValue"));
            row.put("enValue", attribute.get("enValue"));
            row.put("arValue", attribute.get("arValue"));
            row.put("unit", ProductPublishSnapshotSupport.textValue(attribute.get("unit")));
            comparable.add(row);
        }
        comparable.sort((left, right) ->
                ProductPublishSnapshotSupport.textValue(left.get("code"))
                        .compareTo(ProductPublishSnapshotSupport.textValue(right.get("code"))));
        return comparable;
    }

    private Map<String, Object> publishComparableGroup(ProductMasterSnapshotView snapshot) {
        return publishComparableGroup(snapshot != null ? snapshot.getGroup() : null);
    }

    private Map<String, Object> publishComparableGroup(Map<String, Object> group) {
        Map<String, Object> comparable = groupStructureComparable(group);
        comparable.put("memberAxisValues", groupMemberAxisValuesComparable(group, "en"));
        comparable.put("memberAxisValuesAr", groupMemberAxisValuesComparable(group, "ar"));
        return comparable;
    }

    private Map<String, Object> groupStructureComparable(Map<String, Object> group) {
        Map<String, Object> comparable = new LinkedHashMap<>();
        if (group == null) {
            return comparable;
        }
        comparable.put("skuGroup", ProductPublishSnapshotSupport.textValue(group.get("skuGroup")));
        comparable.put("groupRef", ProductPublishSnapshotSupport.textValue(group.get("groupRef")));
        comparable.put("groupRefCanonical", ProductPublishSnapshotSupport.textValue(group.get("groupRefCanonical")));
        comparable.put("conditionsBrand", ProductPublishSnapshotSupport.textValue(group.get("conditionsBrand")));
        comparable.put("conditionsFulltype", ProductPublishSnapshotSupport.textValue(group.get("conditionsFulltype")));

        List<Map<String, Object>> axes = new ArrayList<>();
        for (Map<String, Object> axis : recordListValue(group.get("axes"))) {
            String axisCode = ProductPublishSnapshotSupport.firstNonBlank(
                    ProductPublishSnapshotSupport.textValue(axis.get("axisCode")),
                    ProductPublishSnapshotSupport.textValue(axis.get("axis_code"))
            );
            if (!StringUtils.hasText(axisCode)) {
                continue;
            }
            Map<String, Object> axisComparable = new LinkedHashMap<>();
            axisComparable.put("axisCode", axisCode);
            axisComparable.put("axisName", ProductPublishSnapshotSupport.firstNonBlank(
                    ProductPublishSnapshotSupport.textValue(axis.get("axisName")),
                    ProductPublishSnapshotSupport.textValue(axis.get("axis_name"))
            ));
            axes.add(axisComparable);
        }
        axes.sort((left, right) ->
                ProductPublishSnapshotSupport.textValue(left.get("axisCode"))
                        .compareTo(ProductPublishSnapshotSupport.textValue(right.get("axisCode"))));
        comparable.put("axes", axes);

        List<String> memberSkuParents = new ArrayList<>();
        for (Map<String, Object> member : recordListValue(group.get("members"))) {
            String skuParent = groupMemberSkuParent(member);
            if (StringUtils.hasText(skuParent)) {
                memberSkuParents.add(skuParent);
            }
        }
        memberSkuParents.sort(String::compareTo);
        comparable.put("memberSkuParents", memberSkuParents);
        return comparable;
    }

    private List<Map<String, Object>> groupMemberAxisValuesComparable(Map<String, Object> group, String lang) {
        List<Map<String, Object>> comparable = new ArrayList<>();
        if (group == null) {
            return comparable;
        }
        List<String> axisCodes = groupAxisCodes(recordListValue(group.get("axes")));
        if (axisCodes.isEmpty()) {
            return comparable;
        }
        for (Map<String, Object> member : recordListValue(group.get("members"))) {
            String skuParent = groupMemberSkuParent(member);
            if (!StringUtils.hasText(skuParent)) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (String axisCode : axisCodes) {
                values.put(axisCode, groupMemberAxisValue(member, axisCode, lang));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skuParent", skuParent);
            row.put("values", values);
            comparable.add(row);
        }
        comparable.sort((left, right) ->
                ProductPublishSnapshotSupport.textValue(left.get("skuParent"))
                        .compareTo(ProductPublishSnapshotSupport.textValue(right.get("skuParent"))));
        return comparable;
    }

    private List<Map<String, Object>> publishComparableVariants(ProductMasterSnapshotView snapshot) {
        List<Map<String, Object>> source = snapshot != null && snapshot.getVariants() != null
                ? snapshot.getVariants()
                : List.of();
        List<Map<String, Object>> comparable = new ArrayList<>();
        for (Map<String, Object> variant : source) {
            if (variant == null) {
                continue;
            }
            String childSku = ProductPublishSnapshotSupport.textValue(variant.get("childSku"));
            if (!StringUtils.hasText(childSku)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("childSku", childSku);
            row.put("sizeEn", ProductPublishSnapshotSupport.textValue(variant.get("sizeEn")));
            row.put("sizeAr", ProductPublishSnapshotSupport.textValue(variant.get("sizeAr")));
            comparable.add(row);
        }
        comparable.sort((left, right) ->
                ProductPublishSnapshotSupport.textValue(left.get("childSku"))
                        .compareTo(ProductPublishSnapshotSupport.textValue(right.get("childSku"))));
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

    @SuppressWarnings("unchecked")
    private String groupMemberAxisValue(Map<String, Object> member, String axisCode, String lang) {
        if (member == null || !StringUtils.hasText(axisCode)) {
            return null;
        }
        String normalizedLang = ProductPublishSnapshotSupport.normalize(lang);
        if ("ar".equalsIgnoreCase(normalizedLang)) {
            Map<String, Object> axisValuesAr = member.get("axisValuesAr") instanceof Map<?, ?>
                    ? (Map<String, Object>) member.get("axisValuesAr")
                    : Map.of();
            String axisSpecificArValue = ProductPublishSnapshotSupport.firstNonBlank(
                    ProductPublishSnapshotSupport.textValue(axisValuesAr.get(axisCode)),
                    ProductPublishSnapshotSupport.textValue(member.get(axisCode + "Ar"))
            );
            if (StringUtils.hasText(axisSpecificArValue)) {
                return axisSpecificArValue;
            }
            if (!axisValuesAr.isEmpty()) {
                return null;
            }
            return ProductPublishSnapshotSupport.textValue(member.get("axisValueAr"));
        }
        Map<String, Object> axisValues = member.get("axisValues") instanceof Map<?, ?>
                ? (Map<String, Object>) member.get("axisValues")
                : Map.of();
        String axisSpecificValue = ProductPublishSnapshotSupport.firstNonBlank(
                ProductPublishSnapshotSupport.textValue(member.get(axisCode)),
                ProductPublishSnapshotSupport.textValue(axisValues.get(axisCode))
        );
        if (StringUtils.hasText(axisSpecificValue)) {
            return axisSpecificValue;
        }
        if (!axisValues.isEmpty()) {
            return null;
        }
        return ProductPublishSnapshotSupport.textValue(member.get("axisValue"));
    }

    private String groupMemberSkuParent(Map<String, Object> member) {
        if (member == null) {
            return null;
        }
        return ProductPublishSnapshotSupport.firstNonBlank(
                ProductPublishSnapshotSupport.textValue(member.get("skuParent")),
                ProductPublishSnapshotSupport.textValue(member.get("parentSku")),
                ProductPublishSnapshotSupport.textValue(member.get("sku")),
                ProductPublishSnapshotSupport.textValue(member.get("childSku")),
                ProductPublishSnapshotSupport.textValue(member.get("partnerSku"))
        );
    }

    private List<String> groupAxisCodes(List<Map<String, Object>> axes) {
        List<String> axisCodes = new ArrayList<>();
        if (axes == null) {
            return axisCodes;
        }
        for (Map<String, Object> axis : axes) {
            String axisCode = ProductPublishSnapshotSupport.firstNonBlank(
                    ProductPublishSnapshotSupport.textValue(axis.get("axisCode")),
                    ProductPublishSnapshotSupport.textValue(axis.get("axis_code"))
            );
            if (StringUtils.hasText(axisCode)) {
                axisCodes.add(axisCode);
            }
        }
        return axisCodes;
    }

    private List<String> stringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value == null) {
            return values;
        }
        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                String text = ProductPublishSnapshotSupport.textValue(item);
                if (StringUtils.hasText(text)) {
                    values.add(text);
                }
            }
            return values;
        }
        String text = ProductPublishSnapshotSupport.textValue(value);
        if (StringUtils.hasText(text)) {
            values.add(text);
        }
        return values;
    }

    private String normalizeAttributeCode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase().replaceAll("[\\s-]+", "_");
    }
}
