package com.nuono.next.product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.StringUtils;

final class ProductGroupSnapshotSupport {

    private ProductGroupSnapshotSupport() {
    }

    static Map<String, Map<String, Object>> groupAxisValueChanges(
            Map<String, Object> draftGroup,
            Map<String, Object> baselineGroup,
            String lang
    ) {
        Map<String, Map<String, Object>> changesBySkuParent = new LinkedHashMap<>();
        if (draftGroup == null || baselineGroup == null) {
            return changesBySkuParent;
        }
        List<String> axisCodes = groupAxisCodes(recordListValue(draftGroup.get("axes")));
        if (axisCodes.isEmpty()) {
            return changesBySkuParent;
        }
        Map<String, Map<String, Object>> baselineMembers = groupMemberMap(baselineGroup);
        for (Map<String, Object> draftMember : recordListValue(draftGroup.get("members"))) {
            String skuParent = groupMemberSkuParent(draftMember);
            if (!StringUtils.hasText(skuParent)) {
                continue;
            }
            Map<String, Object> baselineMember = baselineMembers.get(skuParent);
            Map<String, Object> memberChanges = new LinkedHashMap<>();
            for (String axisCode : axisCodes) {
                String draftValue = groupMemberAxisValue(draftMember, axisCode, lang);
                String baselineValue = baselineMember == null ? null : groupMemberAxisValue(baselineMember, axisCode, lang);
                if (Objects.equals(draftValue, baselineValue)) {
                    continue;
                }
                if (baselineMember == null && !StringUtils.hasText(draftValue)) {
                    continue;
                }
                memberChanges.put(axisCode, StringUtils.hasText(draftValue) ? draftValue : null);
            }
            if (!memberChanges.isEmpty()) {
                changesBySkuParent.put(skuParent, memberChanges);
            }
        }
        return changesBySkuParent;
    }

    static List<String> removedGroupMembers(Map<String, Object> draftGroup, Map<String, Object> baselineGroup) {
        Set<String> draftMembers = groupMemberSkuParents(draftGroup);
        List<String> removed = new ArrayList<>();
        for (String skuParent : groupMemberSkuParents(baselineGroup)) {
            if (!draftMembers.contains(skuParent)) {
                removed.add(skuParent);
            }
        }
        return removed;
    }

    static List<String> addedGroupMembers(Map<String, Object> draftGroup, Map<String, Object> baselineGroup) {
        Set<String> baselineMembers = groupMemberSkuParents(baselineGroup);
        List<String> added = new ArrayList<>();
        for (String skuParent : groupMemberSkuParents(draftGroup)) {
            if (!baselineMembers.contains(skuParent)) {
                added.add(skuParent);
            }
        }
        return added;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> recordListValue(Object value) {
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

    static String groupMemberSkuParent(Map<String, Object> member) {
        if (member == null) {
            return null;
        }
        return firstNonBlank(
                textValue(member.get("skuParent")),
                textValue(member.get("parentSku")),
                textValue(member.get("sku")),
                textValue(member.get("zskuParent")),
                textValue(member.get("memberSku")),
                textValue(member.get("childSku")),
                textValue(member.get("partnerSku"))
        );
    }

    static String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    static String firstNonBlank(String... values) {
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

    static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Map<String, Map<String, Object>> groupMemberMap(Map<String, Object> group) {
        Map<String, Map<String, Object>> members = new LinkedHashMap<>();
        if (group == null) {
            return members;
        }
        for (Map<String, Object> member : recordListValue(group.get("members"))) {
            String skuParent = groupMemberSkuParent(member);
            if (StringUtils.hasText(skuParent)) {
                members.put(skuParent, member);
            }
        }
        return members;
    }

    private static Set<String> groupMemberSkuParents(Map<String, Object> group) {
        LinkedHashSet<String> skuParents = new LinkedHashSet<>();
        if (group == null) {
            return skuParents;
        }
        for (Map<String, Object> member : recordListValue(group.get("members"))) {
            String skuParent = groupMemberSkuParent(member);
            if (StringUtils.hasText(skuParent)) {
                skuParents.add(skuParent);
            }
        }
        return skuParents;
    }

    private static List<String> groupAxisCodes(List<Map<String, Object>> axes) {
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

    @SuppressWarnings("unchecked")
    private static String groupMemberAxisValue(Map<String, Object> member, String axisCode, String lang) {
        if (member == null || !StringUtils.hasText(axisCode)) {
            return null;
        }
        String normalizedLang = normalize(lang);
        if ("ar".equalsIgnoreCase(normalizedLang)) {
            Map<String, Object> axisValuesAr = member.get("axisValuesAr") instanceof Map<?, ?>
                    ? (Map<String, Object>) member.get("axisValuesAr")
                    : Map.of();
            String axisSpecificArValue = firstNonBlank(
                    textValue(axisValuesAr.get(axisCode)),
                    textValue(member.get(axisCode + "Ar"))
            );
            if (StringUtils.hasText(axisSpecificArValue)) {
                return axisSpecificArValue;
            }
            return firstNonBlank(textValue(member.get("axisValueAr")));
        }

        Map<String, Object> axisValues = member.get("axisValues") instanceof Map<?, ?>
                ? (Map<String, Object>) member.get("axisValues")
                : Map.of();
        String axisSpecificValue = firstNonBlank(
                textValue(member.get(axisCode)),
                textValue(axisValues.get(axisCode))
        );
        if (StringUtils.hasText(axisSpecificValue)) {
            return axisSpecificValue;
        }
        if (!axisValues.isEmpty()) {
            return null;
        }
        return firstNonBlank(textValue(member.get("axisValue")));
    }
}
