package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

class ProductSnapshotSectionBuilder {

    private final ObjectMapper objectMapper;

    ProductSnapshotSectionBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Map<String, Object> buildIdentity(
            JsonNode productNode,
            JsonNode commonNode,
            JsonNode pricingRoot,
            String skuParent,
            String partnerSku,
            String pskuCode
    ) {
        Map<String, Object> identity = new LinkedHashMap<>();
        JsonNode pricingItem = firstDataItem(pricingRoot);
        putIfNotBlank(identity, "skuParent", skuParent);
        putIfNotBlank(identity, "parentSku", text(productNode, "parent_sku"));
        putIfNotBlank(identity, "partnerSku", firstNonBlank(partnerSku, text(pricingItem, "psku")));
        putIfNotBlank(identity, "pskuCode", pskuCode);
        putIfNotBlank(identity, "brand", text(commonNode, "brand"));
        putIfNotBlank(identity, "barcode", firstNonBlank(
                text(commonNode, "barcode"),
                text(commonNode, "gtin"),
                text(commonNode, "ean"),
                text(commonNode, "upc"),
                text(pricingItem, "barcode"),
                text(pricingItem, "gtin"),
                text(pricingItem, "ean"),
                text(pricingItem, "upc")
        ));

        putIfNotBlank(identity, "childSku", text(pricingItem, "sku"));
        putIfNotBlank(identity, "offerCode", text(pricingItem, "offer_code"));
        putIfNotBlank(identity, "productSourceType", ProductSourceTypeSupport.resolve(
                textValue(identity.get("productSourceType")),
                textValue(identity.get("childSku")),
                skuParent
        ));
        putIfNotNull(identity, "variantCount", productNode.path("variants").size());
        return identity;
    }

    Map<String, Object> buildTaxonomy(JsonNode commonNode) {
        Map<String, Object> taxonomy = new LinkedHashMap<>();
        putIfNotBlank(taxonomy, "family", text(commonNode, "family"));
        putIfNotBlank(taxonomy, "familyNameEn", noonText(commonNode, "family_option_name_en"));
        putIfNotBlank(taxonomy, "familyNameAr", noonText(commonNode, "family_option_name_ar"));
        putIfNotBlank(taxonomy, "productType", text(commonNode, "product_type"));
        putIfNotBlank(taxonomy, "productTypeNameEn", noonText(commonNode, "product_type_option_name_en"));
        putIfNotBlank(taxonomy, "productTypeNameAr", noonText(commonNode, "product_type_option_name_ar"));
        putIfNotBlank(taxonomy, "productSubtype", text(commonNode, "product_subtype"));
        putIfNotBlank(taxonomy, "productSubtypeNameEn", noonText(commonNode, "product_subtype_option_name_en"));
        putIfNotBlank(taxonomy, "productSubtypeNameAr", noonText(commonNode, "product_subtype_option_name_ar"));
        putIfNotBlank(taxonomy, "productFulltype", text(commonNode, "product_fulltype"));
        putIfNotBlank(taxonomy, "grade", text(commonNode, "grade"));
        putIfNotBlank(taxonomy, "itemCondition", text(commonNode, "item_condition"));
        return taxonomy;
    }

    Map<String, Object> buildContent(JsonNode commonNode, JsonNode enNode, JsonNode arNode) {
        Map<String, Object> content = new LinkedHashMap<>();
        putIfNotBlank(content, "titleEn", text(enNode, "product_title"));
        putIfNotBlank(content, "titleAr", text(arNode, "product_title"));
        putIfNotBlank(content, "fullTitleEn", text(enNode, "full_product_title"));
        putIfNotBlank(content, "fullTitleAr", text(arNode, "full_product_title"));
        putIfNotBlank(content, "descriptionEn", text(enNode, "long_description"));
        putIfNotBlank(content, "descriptionAr", text(arNode, "long_description"));
        List<String> highlightsEn = collectOrderedText(enNode, "feature_bullet_", 5);
        List<String> highlightsAr = collectOrderedText(arNode, "feature_bullet_", 5);
        List<String> images = collectImages(commonNode);
        putIfNotNull(content, "imageCount", images.size());
        putIfNotEmpty(content, "images", images);
        putIfNotEmpty(content, "highlightsEn", highlightsEn);
        putIfNotEmpty(content, "highlightsAr", highlightsAr);
        return content;
    }

    Map<String, Object> buildPlatformSignals(JsonNode commonNode) {
        Map<String, Object> platformSignals = new LinkedHashMap<>();
        putIfNotBlank(platformSignals, "qcState", text(commonNode, "qc_state"));
        putIfNotBlank(platformSignals, "statusQc", text(commonNode, "status_qc_localized"));
        putIfNotBlank(platformSignals, "isActiveLocalized", text(commonNode, "is_active_localized"));
        putIfNotBlank(platformSignals, "qcApproved", text(commonNode, "qc_approved_localized"));
        putIfNotBlank(platformSignals, "completenessMandatory", text(commonNode, "status_completeness_mandatory"));
        putIfNotBlank(platformSignals, "completenessLocalized", text(commonNode, "status_completeness_details_localized"));
        putIfNotBlank(platformSignals, "qcSource", text(commonNode, "noon_qc_source_localized"));
        putIfNotNull(platformSignals, "statusImages", numberOrText(commonNode.path("status_images")));
        putIfNotNull(platformSignals, "imageCount", collectImages(commonNode).size());
        putIfNotNull(platformSignals, "hiddenImageCount", countHiddenImages(commonNode));
        putIfNotEmpty(
                platformSignals,
                "rejectionReasons",
                collectNodeTextList(commonNode.path("noon_qc_rejection_reasons_localized"))
        );
        putIfNotEmpty(
                platformSignals,
                "affectingAttributes",
                collectNodeTextList(commonNode.path("is_active_localized_affecting_attributes"))
        );
        return platformSignals;
    }

    ObjectNode buildGroupParentAttributeFetchBody(JsonNode groupDetailRoot, String skuGroup) {
        if (!StringUtils.hasText(skuGroup)) {
            return null;
        }
        JsonNode groupDetailNode = groupDetailRoot.path(skuGroup);
        LinkedHashSet<String> skuParents = new LinkedHashSet<>();
        JsonNode membersNode = groupDetailNode.path("zsku_parents");
        if (membersNode.isArray()) {
            for (JsonNode memberNode : membersNode) {
                String skuParent = memberNode.isTextual()
                        ? memberNode.asText()
                        : firstNonBlank(
                                text(memberNode, "sku_parent"),
                                text(memberNode, "zsku_parent"),
                                text(memberNode, "skuParent")
                        );
                if (StringUtils.hasText(skuParent)) {
                    skuParents.add(skuParent);
                }
            }
        }
        LinkedHashSet<String> axisCodes = new LinkedHashSet<>();
        JsonNode axesNode = groupDetailNode.path("axes");
        if (axesNode.isArray()) {
            for (JsonNode axisNode : axesNode) {
                String axisCode = text(axisNode, "axis_code");
                if (StringUtils.hasText(axisCode)) {
                    axisCodes.add(axisCode);
                }
            }
        }
        if (skuParents.isEmpty() || axisCodes.isEmpty()) {
            return null;
        }
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode skuParentsNode = body.putArray("skuParents");
        skuParents.forEach(skuParentsNode::add);
        ArrayNode attributeCodesNode = body.putArray("attributeCodes");
        axisCodes.forEach(attributeCodesNode::add);
        return body;
    }

    Map<String, Object> buildGroup(
            JsonNode groupCurrentNode,
            JsonNode groupDetailRoot,
            JsonNode groupListNode,
            String skuGroup,
            JsonNode groupParentAttributesRoot
    ) {
        Map<String, Object> group = new LinkedHashMap<>();
        putIfNotBlank(group, "skuGroup", skuGroup);
        putIfNotNull(group, "candidateGroupCount", groupListNode.isArray() ? groupListNode.size() : 0);

        JsonNode groupDetailNode = StringUtils.hasText(skuGroup)
                ? groupDetailRoot.path(skuGroup)
                : MissingNode.getInstance();
        JsonNode groupMeta = groupDetailNode.path("group");
        putIfNotBlank(group, "groupRef", text(groupMeta, "group_ref"));
        putIfNotBlank(group, "groupRefCanonical", text(groupMeta, "group_ref_canonical"));
        JsonNode conditionsNode = groupDetailNode.path("conditions");
        putIfNotBlank(group, "conditionsBrand", text(conditionsNode, "brand"));
        putIfNotBlank(group, "conditionsFulltype", text(conditionsNode, "fulltype"));

        JsonNode axesNode = groupDetailNode.path("axes");
        List<Map<String, Object>> axes = new ArrayList<>();
        if (axesNode.isArray()) {
            for (JsonNode axisNode : axesNode) {
                Map<String, Object> axis = new LinkedHashMap<>();
                putIfNotBlank(axis, "axisCode", text(axisNode, "axis_code"));
                putIfNotBlank(axis, "axisName", text(axisNode, "axis_name"));
                if (!axis.isEmpty()) {
                    axes.add(axis);
                }
            }
        }
        putIfNotNull(group, "memberCount", groupDetailNode.path("zsku_parents").size());
        putIfNotEmpty(group, "axes", axes);
        putIfNotEmpty(group, "members", buildGroupMembers(groupDetailNode.path("zsku_parents"), axes, groupParentAttributesRoot));
        putIfNotEmpty(group, "candidateGroups", buildCandidateGroups(groupListNode));

        if (!StringUtils.hasText(skuGroup) && groupCurrentNode.isObject()) {
            putIfNotBlank(group, "state", "当前商品未挂 group");
        }

        return group;
    }

    List<Map<String, Object>> buildVariants(JsonNode variantInfoRoot, JsonNode productNode) {
        Map<String, Map<String, Object>> variants = new LinkedHashMap<>();
        mergeVariantInfoRecords(variants, variantInfoRoot);
        mergeVariantSnapshotRecords(variants, productNode != null ? productNode.path("variants") : MissingNode.getInstance());
        return new ArrayList<>(variants.values());
    }

    private List<Map<String, Object>> buildGroupMembers(
            JsonNode membersNode,
            List<Map<String, Object>> axes,
            JsonNode groupParentAttributesRoot
    ) {
        List<Map<String, Object>> members = new ArrayList<>();
        if (!membersNode.isArray()) {
            return members;
        }

        List<String> axisCodes = groupAxisCodes(axes);
        for (JsonNode memberNode : membersNode) {
            Map<String, Object> member = new LinkedHashMap<>();
            if (memberNode.isTextual()) {
                putIfNotBlank(member, "skuParent", memberNode.asText());
            } else {
                putIfNotBlank(
                        member,
                        "skuParent",
                        firstNonBlank(
                                text(memberNode, "sku_parent"),
                                text(memberNode, "zsku_parent"),
                                text(memberNode, "skuParent")
                        )
                );
                putIfNotBlank(member, "title", text(memberNode, "title"));
                putIfNotBlank(member, "imageKey", text(memberNode, "image_key"));
                putIfNotBlank(member, "imageUrl", text(memberNode, "image_url"));
                putIfNotBlank(member, "groupRef", text(memberNode, "group_ref"));
                putIfNotBlank(member, "groupRefCanonical", text(memberNode, "group_ref_canonical"));
            }
            enrichGroupMemberAxisValues(member, axisCodes, groupParentAttributesRoot);
            if (!member.isEmpty()) {
                members.add(member);
            }
        }

        return members;
    }

    private List<String> groupAxisCodes(List<Map<String, Object>> axes) {
        List<String> axisCodes = new ArrayList<>();
        if (axes == null) {
            return axisCodes;
        }
        for (Map<String, Object> axis : axes) {
            String axisCode = firstNonBlank(textValue(axis.get("axisCode")), textValue(axis.get("axis_code")));
            if (StringUtils.hasText(axisCode)) {
                axisCodes.add(axisCode);
            }
        }
        return axisCodes;
    }

    private void enrichGroupMemberAxisValues(
            Map<String, Object> member,
            List<String> axisCodes,
            JsonNode groupParentAttributesRoot
    ) {
        if (member == null || axisCodes == null || axisCodes.isEmpty()) {
            return;
        }
        String skuParent = textValue(member.get("skuParent"));
        if (!StringUtils.hasText(skuParent)) {
            return;
        }
        JsonNode attributesNode = groupParentAttributesRoot != null
                ? groupParentAttributesRoot.path(skuParent).path("attributes")
                : MissingNode.getInstance();
        JsonNode commonNode = attributesNode.path("common");
        JsonNode enNode = attributesNode.path("en");
        JsonNode arNode = attributesNode.path("ar");
        Map<String, Object> axisValues = new LinkedHashMap<>();
        Map<String, Object> axisValuesAr = new LinkedHashMap<>();
        for (String axisCode : axisCodes) {
            String enValue = firstNonBlank(text(enNode, axisCode), text(commonNode, axisCode));
            String arValue = text(arNode, axisCode);
            if (StringUtils.hasText(enValue)) {
                axisValues.put(axisCode, enValue);
                member.put(axisCode, enValue);
                if (!member.containsKey("axisValue")) {
                    member.put("axisValue", enValue);
                }
            }
            if (StringUtils.hasText(arValue)) {
                axisValuesAr.put(axisCode, arValue);
            }
        }
        putMapIfNotEmpty(member, "axisValues", axisValues);
        putMapIfNotEmpty(member, "axisValuesAr", axisValuesAr);
    }

    private List<Map<String, Object>> buildCandidateGroups(JsonNode groupListNode) {
        List<Map<String, Object>> candidateGroups = new ArrayList<>();
        if (!groupListNode.isArray()) {
            return candidateGroups;
        }

        for (JsonNode candidateNode : groupListNode) {
            Map<String, Object> candidate = new LinkedHashMap<>();
            JsonNode groupNode = candidateNode.path("group");
            JsonNode conditionsNode = candidateNode.path("conditions");
            putIfNotBlank(
                    candidate,
                    "skuGroup",
                    firstNonBlank(
                            text(candidateNode, "zsku_group"),
                            text(candidateNode, "sku_group"),
                            text(groupNode, "zsku_group")
                    )
            );
            putIfNotBlank(
                    candidate,
                    "groupRef",
                    firstNonBlank(
                            text(candidateNode, "group_ref"),
                            text(groupNode, "group_ref")
                    )
            );
            putIfNotBlank(
                    candidate,
                    "groupRefCanonical",
                    firstNonBlank(
                            text(candidateNode, "group_ref_canonical"),
                            text(groupNode, "group_ref_canonical")
                    )
            );
            putIfNotBlank(candidate, "brand", firstNonBlank(text(candidateNode, "brand"), text(conditionsNode, "brand")));
            putIfNotBlank(
                    candidate,
                    "fulltype",
                    firstNonBlank(text(candidateNode, "fulltype"), text(conditionsNode, "fulltype"))
            );
            if (candidateNode.path("zsku_parents").isArray()) {
                putIfNotNull(candidate, "memberCount", candidateNode.path("zsku_parents").size());
            }
            if (!candidate.isEmpty()) {
                candidateGroups.add(candidate);
            }
        }

        return candidateGroups;
    }

    private void mergeVariantInfoRecords(Map<String, Map<String, Object>> variants, JsonNode variantInfoRoot) {
        if (variantInfoRoot == null || !variantInfoRoot.isObject()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> iterator = variantInfoRoot.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            mergeVariantRecord(variants, entry.getKey(), entry.getValue());
        }
    }

    private void mergeVariantSnapshotRecords(Map<String, Map<String, Object>> variants, JsonNode variantsNode) {
        if (variantsNode == null || variantsNode.isMissingNode() || variantsNode.isNull()) {
            return;
        }
        if (variantsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = variantsNode.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                mergeVariantRecord(variants, entry.getKey(), entry.getValue());
            }
            return;
        }
        if (variantsNode.isArray()) {
            for (JsonNode item : variantsNode) {
                mergeVariantRecord(variants, null, item);
            }
        }
    }

    private void mergeVariantRecord(Map<String, Map<String, Object>> variants, String fallbackChildSku, JsonNode variantNode) {
        String childSku = firstNonBlank(
                fallbackChildSku,
                text(variantNode, "childSku"),
                text(variantNode, "child_sku"),
                text(variantNode, "sku_child"),
                text(variantNode, "sku"),
                text(variantNode, "zsku"),
                text(variantNode, "zsku_child")
        );
        if (!StringUtils.hasText(childSku)) {
            return;
        }

        Map<String, Object> variant = variants.computeIfAbsent(childSku, (key) -> {
            Map<String, Object> next = new LinkedHashMap<>();
            next.put("childSku", key);
            return next;
        });
        putIfAbsentNotBlank(variant, "partnerSku", firstNonBlank(
                text(variantNode, "partnerSku"),
                text(variantNode, "partner_sku"),
                text(variantNode, "catalogSku"),
                text(variantNode, "catalog_sku"),
                text(variantNode, "sellerSku"),
                text(variantNode, "seller_sku")
        ));
        putIfAbsentNotBlank(variant, "pskuCode", firstNonBlank(
                text(variantNode, "pskuCode"),
                text(variantNode, "psku_code")
        ));
        putIfAbsentNotBlank(variant, "sizeEn", extractVariantSize(variantNode, "en"));
        putIfAbsentNotBlank(variant, "sizeAr", extractVariantSize(variantNode, "ar"));
        putIfAbsentNumber(variant, "variantIndex", firstNonMissingNode(
                variantNode.path("ix"),
                variantNode.path("variantIndex"),
                variantNode.path("variant_ix")
        ));
    }

    private String extractVariantSize(JsonNode variantNode, String lang) {
        String langSuffix = "en".equals(lang) ? "en" : "ar";
        String camelSuffix = "en".equals(lang) ? "En" : "Ar";
        return firstNonBlank(
                localizedNodeText(variantNode.path("size"), lang),
                localizedNodeText(variantNode.path("seller_size"), lang),
                localizedNodeText(variantNode.path("sellerSize"), lang),
                localizedNodeText(variantNode.path("attributes").path("size"), lang),
                localizedNodeText(variantNode.path("attributes").path(lang).path("size"), lang),
                localizedNodeText(variantNode.path("attributes").path("common").path("size"), lang),
                text(variantNode, "size_" + langSuffix),
                text(variantNode, "size" + camelSuffix),
                text(variantNode, langSuffix + "_size"),
                text(variantNode, "seller_size_" + langSuffix),
                text(variantNode, "sellerSize" + camelSuffix)
        );
    }

    private String localizedNodeText(JsonNode node, String lang) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            return textValue(node.asText());
        }
        String value = firstNonBlank(
                text(node, lang),
                text(node, lang.toUpperCase()),
                text(node, "value"),
                text(node, "name"),
                text(node, "label")
        );
        return StringUtils.hasText(value) ? value : null;
    }

    private JsonNode firstNonMissingNode(JsonNode... nodes) {
        if (nodes == null) {
            return MissingNode.getInstance();
        }
        for (JsonNode node : nodes) {
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }
        return MissingNode.getInstance();
    }

    private JsonNode firstDataItem(JsonNode root) {
        if (root.isObject() && root.path("data").isArray() && root.path("data").size() > 0) {
            return root.path("data").get(0);
        }
        if (root.isArray() && root.size() > 0) {
            return root.get(0);
        }
        return MissingNode.getInstance();
    }

    private List<String> collectOrderedText(JsonNode node, String fieldPrefix, int maxCount) {
        List<String> values = new ArrayList<>();
        for (int index = 1; index <= maxCount; index++) {
            String value = text(node, fieldPrefix + index);
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private List<String> collectImages(JsonNode commonNode) {
        Set<String> images = new LinkedHashSet<>();
        for (int index = 1; index <= 10; index++) {
            String imageUrl = text(commonNode, "image_url_" + index);
            if (StringUtils.hasText(imageUrl)) {
                images.add(imageUrl);
            }
        }
        for (int index = 1; index <= 10; index++) {
            String originalUrl = text(commonNode, "original_" + index);
            if (StringUtils.hasText(originalUrl)) {
                images.add(originalUrl);
            }
        }
        return new ArrayList<>(images);
    }

    private int countHiddenImages(JsonNode commonNode) {
        int hiddenCount = 0;
        for (int index = 1; index <= 10; index++) {
            JsonNode hiddenNode = commonNode.path("is_hidden_" + index);
            if (hiddenNode.isBoolean() && hiddenNode.asBoolean()) {
                hiddenCount++;
                continue;
            }
            if (hiddenNode.isTextual()) {
                String value = hiddenNode.asText();
                if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
                    hiddenCount++;
                }
            }
        }
        return hiddenCount;
    }

    private List<String> collectNodeTextList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return values;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.isValueNode() ? item.asText() : item.toString();
                if (StringUtils.hasText(value)) {
                    values.add(value);
                }
            }
            return values;
        }

        String value = node.isValueNode() ? node.asText() : node.toString();
        if (StringUtils.hasText(value)) {
            values.add(value);
        }
        return values;
    }

    private Object numberOrText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.asLong();
        }
        if (node.isFloat() || node.isDouble() || node.isBigDecimal()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return node.asText();
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

    private String noonText(JsonNode node, String field) {
        String value = text(node, field);
        return "null".equalsIgnoreCase(value) ? null : value;
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
                return value.trim();
            }
        }
        return null;
    }

    private void putIfAbsentNotBlank(Map<String, Object> target, String key, String value) {
        if (!target.containsKey(key) && StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private void putIfAbsentNumber(Map<String, Object> target, String key, JsonNode valueNode) {
        if (target.containsKey(key) || valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
            return;
        }
        if (valueNode.isInt() || valueNode.isLong()) {
            target.put(key, valueNode.asInt());
        }
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

    private void putMapIfNotEmpty(Map<String, Object> target, String key, Map<?, ?> values) {
        if (values != null && !values.isEmpty()) {
            target.put(key, values);
        }
    }
}
