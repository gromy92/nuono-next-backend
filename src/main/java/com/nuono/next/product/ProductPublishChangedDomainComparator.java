package com.nuono.next.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.StringUtils;

class ProductPublishChangedDomainComparator {

    private static final DateTimeFormatter FETCH_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter NOON_OFFER_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ObjectMapper objectMapper;

    ProductPublishChangedDomainComparator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<String> resolve(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode,
            boolean groupChanged,
            boolean variantStructureChanged
    ) {
        List<String> domains = new ArrayList<>();
        if (!objectMapper.valueToTree(publishComparableContent(draft))
                .equals(objectMapper.valueToTree(publishComparableContent(baseline)))) {
            domains.add("content");
        }
        if (!objectMapper.valueToTree(publishComparableIdentity(draft))
                .equals(objectMapper.valueToTree(publishComparableIdentity(baseline)))
                || !objectMapper.valueToTree(publishComparableTaxonomy(draft))
                .equals(objectMapper.valueToTree(publishComparableTaxonomy(baseline)))) {
            domains.add("main");
        }
        if (!objectMapper.valueToTree(publishComparableKeyAttributes(draft))
                .equals(objectMapper.valueToTree(publishComparableKeyAttributes(baseline)))) {
            domains.add("attributes");
        }
        if (!objectMapper.valueToTree(siteOfferComparableList(draft, currentSiteCode, false))
                .equals(objectMapper.valueToTree(siteOfferComparableList(baseline, currentSiteCode, false)))) {
            domains.add("site_offer");
        }
        if (variantSizeChanged(draft, baseline)) {
            domains.add("sizes");
        }
        if (!objectMapper.valueToTree(publishComparableGroup(draft))
                .equals(objectMapper.valueToTree(publishComparableGroup(baseline)))
                || groupChanged) {
            domains.add("grouping");
        }
        if (variantStructureChanged && !domains.contains("sizes")) {
            domains.add("sizes");
        }
        return domains.isEmpty() ? List.of("unknown") : domains;
    }

    boolean variantSizeChanged(ProductMasterSnapshotView draft, ProductMasterSnapshotView baseline) {
        Map<String, Map<String, Object>> draftVariants = variantMap(draft != null ? draft.getVariants() : null);
        Map<String, Map<String, Object>> baselineVariants = variantMap(baseline != null ? baseline.getVariants() : null);
        Set<String> childSkus = new LinkedHashSet<>();
        childSkus.addAll(draftVariants.keySet());
        childSkus.addAll(baselineVariants.keySet());
        for (String childSku : childSkus) {
            Map<String, Object> draftVariant = draftVariants.get(childSku);
            Map<String, Object> baselineVariant = baselineVariants.get(childSku);
            if (draftVariant == null || baselineVariant == null) {
                return true;
            }
            if (!Objects.equals(textValue(draftVariant.get("sizeEn")), textValue(baselineVariant.get("sizeEn")))
                    || !Objects.equals(textValue(draftVariant.get("sizeAr")), textValue(baselineVariant.get("sizeAr")))) {
                return true;
            }
        }
        return false;
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
        comparable.sort((left, right) -> textValue(left.get("skuParent")).compareTo(textValue(right.get("skuParent"))));
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

    private String groupMemberSkuParent(Map<String, Object> member) {
        if (member == null) {
            return null;
        }
        return firstNonBlank(
                textValue(member.get("skuParent")),
                textValue(member.get("parentSku")),
                textValue(member.get("sku")),
                textValue(member.get("childSku")),
                textValue(member.get("partnerSku"))
        );
    }

    @SuppressWarnings("unchecked")
    private String groupMemberAxisValue(Map<String, Object> member, String axisCode, String lang) {
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
            if (!axisValuesAr.isEmpty()) {
                return null;
            }
            return firstNonBlank(
                    textValue(member.get("axisValueAr"))
            );
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
        return firstNonBlank(
                textValue(member.get("axisValue"))
        );
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

    private Map<String, Object> publishComparableIdentity(ProductMasterSnapshotView snapshot) {
        Map<String, Object> identity = snapshot != null && snapshot.getIdentity() != null
                ? snapshot.getIdentity()
                : Map.of();
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("brand", textValue(identity.get("brand")));
        return comparable;
    }

    private Map<String, Object> publishComparableTaxonomy(ProductMasterSnapshotView snapshot) {
        Map<String, Object> taxonomy = snapshot != null && snapshot.getTaxonomy() != null
                ? snapshot.getTaxonomy()
                : Map.of();
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("family", textValue(taxonomy.get("family")));
        comparable.put("productType", textValue(taxonomy.get("productType")));
        comparable.put("productSubtype", textValue(taxonomy.get("productSubtype")));
        comparable.put("productFulltype", textValue(taxonomy.get("productFulltype")));
        comparable.put("grade", textValue(taxonomy.get("grade")));
        comparable.put("itemCondition", textValue(taxonomy.get("itemCondition")));
        return comparable;
    }

    private Map<String, Object> publishComparableContent(ProductMasterSnapshotView snapshot) {
        Map<String, Object> content = snapshot != null && snapshot.getContent() != null
                ? snapshot.getContent()
                : Map.of();
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("titleEn", textValue(content.get("titleEn")));
        comparable.put("titleAr", textValue(content.get("titleAr")));
        comparable.put("descriptionEn", textValue(content.get("descriptionEn")));
        comparable.put("descriptionAr", textValue(content.get("descriptionAr")));
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
            String code = normalizeAttributeCode(textValue(attribute.get("code")));
            if (!StringUtils.hasText(code)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", code);
            row.put("commonValue", attribute.get("commonValue"));
            row.put("enValue", attribute.get("enValue"));
            row.put("arValue", attribute.get("arValue"));
            row.put("unit", textValue(attribute.get("unit")));
            comparable.add(row);
        }
        comparable.sort((left, right) -> textValue(left.get("code")).compareTo(textValue(right.get("code"))));
        return comparable;
    }

    private List<Map<String, Object>> siteOfferComparableList(
            ProductMasterSnapshotView snapshot,
            String siteCode,
            boolean includeUnsupportedFields
    ) {
        List<Map<String, Object>> comparable = new ArrayList<>();
        if (snapshot == null || snapshot.getSiteOffers() == null) {
            return comparable;
        }

        for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
            String storeCode = siteOfferCode(siteOffer);
            if (StringUtils.hasText(siteCode) && !storeCode.equalsIgnoreCase(siteCode)) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            putIfNotNull(row, "storeCode", storeCode);
            putIfNotNull(row, "site", siteOffer.get("site"));
            putComparableDecimalIfPresent(row, "price", siteOffer.get("price"));
            putComparableDecimalIfPresent(row, "salePrice", siteOffer.get("salePrice"));
            putIfNotNull(row, "saleStart", normalizeOfferDateForNoon(siteOffer.get("saleStart")));
            putIfNotNull(row, "saleEnd", normalizeOfferDateForNoon(siteOffer.get("saleEnd")));
            putComparableDecimalIfPresent(row, "priceMin", siteOffer.get("priceMin"));
            putComparableDecimalIfPresent(row, "priceMax", siteOffer.get("priceMax"));
            putComparableBooleanIfPresent(row, "isActive", siteOffer.get("isActive"));
            putComparableDecimalIfPresent(row, "idWarranty", siteOffer.get("idWarranty"));
            putIfNotNull(row, "offerNote", siteOffer.get("offerNote"));
            if (includeUnsupportedFields) {
                putIfNotNull(row, "barcode", siteOffer.get("barcode"));
            }
            comparable.add(row);
        }
        return comparable;
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

    private String siteOfferCode(Map<String, Object> siteOffer) {
        return textValue(siteOffer.get("storeCode"));
    }

    private List<String> stringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                String text = textValue(item);
                if (StringUtils.hasText(text)) {
                    values.add(text);
                }
            }
        } else if (StringUtils.hasText(textValue(value))) {
            values.add(textValue(value));
        }
        return values;
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value));
        }
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = textValue(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "active".equalsIgnoreCase(text);
    }

    private String normalizeOfferDateForNoon(Object value) {
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return ZonedDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text, FETCH_TIME_FORMATTER).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(text).format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return text;
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private void putComparableDecimalIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        BigDecimal decimal = asBigDecimal(value);
        if (decimal == null) {
            target.put(key, value);
            return;
        }
        target.put(key, decimal.stripTrailingZeros().toPlainString());
    }

    private void putComparableBooleanIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        target.put(key, truthy(value));
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
