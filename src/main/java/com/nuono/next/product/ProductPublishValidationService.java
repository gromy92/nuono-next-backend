package com.nuono.next.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.StringUtils;

public class ProductPublishValidationService {

    private final ObjectMapper objectMapper;

    private final ProductPublishPlanner productPublishPlanner;

    public ProductPublishValidationService(
            ObjectMapper objectMapper,
            ProductPublishPlanner productPublishPlanner
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.productPublishPlanner = Objects.requireNonNull(productPublishPlanner, "productPublishPlanner");
    }

    public ProductUnsupportedChanges detectUnsupportedChanges(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        ProductUnsupportedChanges unsupportedChanges = new ProductUnsupportedChanges();

        if (!jsonEquals(
                groupDefinitionComparable(draft == null ? null : draft.getGroup()),
                groupDefinitionComparable(baseline == null ? null : baseline.getGroup())
        )) {
            unsupportedChanges.setGroupChanged(true);
        }
        Map<String, Map<String, Object>> draftVariants = ProductPublishSnapshotSupport.variantMap(
                draft == null ? null : draft.getVariants()
        );
        Map<String, Map<String, Object>> baselineVariants = ProductPublishSnapshotSupport.variantMap(
                baseline == null ? null : baseline.getVariants()
        );
        if (!draftVariants.keySet().equals(baselineVariants.keySet())) {
            unsupportedChanges.setVariantStructureChanged(true);
        }

        Map<String, Map<String, Object>> draftAttributes = ProductPublishSnapshotSupport.keyAttributeMap(
                draft == null ? null : draft.getKeyAttributes()
        );
        Map<String, Map<String, Object>> baselineAttributes = ProductPublishSnapshotSupport.keyAttributeMap(
                baseline == null ? null : baseline.getKeyAttributes()
        );
        Set<String> allAttributeCodes = new LinkedHashSet<>();
        allAttributeCodes.addAll(draftAttributes.keySet());
        allAttributeCodes.addAll(baselineAttributes.keySet());
        for (String code : allAttributeCodes) {
            Map<String, Object> draftAttribute = draftAttributes.get(code);
            Map<String, Object> baselineAttribute = baselineAttributes.get(code);
            if (jsonEquals(draftAttribute, baselineAttribute)) {
                continue;
            }
            if (draftAttribute == null || baselineAttribute == null || isCoreAttribute(code) || isBarcodeAttribute(code)) {
                unsupportedChanges.addUnsupportedAttributeCode(code);
                continue;
            }
            if (!isScalarAttributeValue(attributeValue(draftAttribute, "commonValue"))
                    || !isScalarAttributeValue(attributeValue(draftAttribute, "enValue"))
                    || !isScalarAttributeValue(attributeValue(draftAttribute, "arValue"))
                    || !isScalarAttributeValue(attributeValue(baselineAttribute, "commonValue"))
                    || !isScalarAttributeValue(attributeValue(baselineAttribute, "enValue"))
                    || !isScalarAttributeValue(attributeValue(baselineAttribute, "arValue"))) {
                unsupportedChanges.addUnsupportedAttributeCode(code);
            }
        }

        Map<String, Map<String, Object>> draftOffers = ProductPublishSnapshotSupport.siteOfferMap(
                draft == null ? null : draft.getSiteOffers()
        );
        Map<String, Map<String, Object>> baselineOffers = ProductPublishSnapshotSupport.siteOfferMap(
                baseline == null ? null : baseline.getSiteOffers()
        );
        Set<String> relevantSiteCodes = new LinkedHashSet<>();
        if (StringUtils.hasText(currentSiteCode)) {
            relevantSiteCodes.add(currentSiteCode.trim());
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
                if (!jsonEquals(draftOffer.get(field), baselineOffer.get(field))) {
                    unsupportedChanges.markUnsupportedSiteField(siteCode, field);
                }
            }
        }

        return unsupportedChanges;
    }

    public ProductMasterSnapshotView publishableSnapshotForSupportedChanges(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            ProductUnsupportedChanges unsupportedChanges
    ) {
        ProductMasterSnapshotView publishable = ProductPublishSnapshotSupport.copySnapshot(objectMapper, draft);
        ProductPublishPlan plan = productPublishPlanner.plan(publishable, baseline, null);
        if (!plan.isPublishable() && unsupportedChanges != null) {
            unsupportedChanges.addPublishBlockers(plan.getBlockers());
        }
        return plan.getPublishableSnapshot();
    }

    public ProductPublishValidationResult validate(
            ProductMasterSnapshotView publishableSnapshot,
            ProductMasterSnapshotView baseline,
            String currentSiteCode,
            ProductUnsupportedChanges unsupportedChanges
    ) {
        List<String> errors = new ArrayList<>();
        errors.addAll(validateSnapshotOnly(publishableSnapshot, baseline, currentSiteCode));
        errors.addAll(validateOperationalKeysOnly(publishableSnapshot, baseline, currentSiteCode));
        errors.addAll(validateWriteCoverageOnly(unsupportedChanges));
        return new ProductPublishValidationResult(errors);
    }

    public List<String> validateSnapshotOnly(
            ProductMasterSnapshotView snapshot,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> content = safeMap(snapshot == null ? null : snapshot.getContent());
        Map<String, Object> identity = safeMap(snapshot == null ? null : snapshot.getIdentity());
        Map<String, Object> taxonomy = safeMap(snapshot == null ? null : snapshot.getTaxonomy());
        String titleEn = ProductPublishSnapshotSupport.textValue(content.get("titleEn"));
        String brand = ProductPublishSnapshotSupport.textValue(identity.get("brand"));
        String productFulltype = ProductPublishSnapshotSupport.textValue(taxonomy.get("productFulltype"));
        List<String> images = stringList(content.get("images"));

        if (!StringUtils.hasText(titleEn)) {
            errors.add("共享主档缺少标题 EN。");
        }
        if (!StringUtils.hasText(brand)) {
            errors.add("共享主档缺少品牌。");
        }
        if (!StringUtils.hasText(productFulltype)) {
            errors.add("共享主档缺少 Fulltype。");
        }
        if (images.isEmpty()) {
            errors.add("共享主档至少需要保留 1 张图片。");
        }
        Map<String, Object> baselineContent = safeMap(baseline == null ? null : baseline.getContent());
        List<String> baselineImages = stringList(baselineContent.get("images"));
        if (!jsonEquals(images, baselineImages) && images.stream().anyMatch(this::isLocalProductImageAssetUrl)) {
            errors.add("本地上传图片还没有 Noon 可访问 URL，暂时不能发布；请先删除本地上传图片或等待图片外链适配。");
        }

        Map<String, Map<String, Object>> baselineOffers = ProductPublishSnapshotSupport.siteOfferMap(
                baseline == null ? null : baseline.getSiteOffers()
        );
        for (Map<String, Object> siteOffer : ProductPublishSnapshotSupport.siteOfferComparableList(
                snapshot,
                currentSiteCode,
                false
        )) {
            String siteCode = ProductPublishSnapshotSupport.textValue(siteOffer.get("storeCode"));
            Map<String, Object> baselineOffer = baselineOffers.get(siteCode);
            if (baselineOffer != null
                    && jsonEquals(
                    ProductPublishSnapshotSupport.siteOfferComparable(siteOffer, false),
                    ProductPublishSnapshotSupport.siteOfferComparable(baselineOffer, false)
            )) {
                continue;
            }
            String label = ProductPublishSnapshotSupport.textValue(siteOffer.get("site"))
                    + " / "
                    + ProductPublishSnapshotSupport.textValue(siteOffer.get("storeCode"));
            BigDecimal price = ProductPublishSnapshotSupport.asBigDecimal(siteOffer.get("price"));
            BigDecimal salePrice = ProductPublishSnapshotSupport.asBigDecimal(siteOffer.get("salePrice"));
            BigDecimal priceMin = ProductPublishSnapshotSupport.asBigDecimal(siteOffer.get("priceMin"));
            BigDecimal priceMax = ProductPublishSnapshotSupport.asBigDecimal(siteOffer.get("priceMax"));
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(label + " 缺少有效售价。");
            }
            if (salePrice != null && price != null && salePrice.compareTo(price) > 0) {
                errors.add(label + " 的促销价不能高于原价。");
            }
            if (price != null && priceMin != null && price.compareTo(priceMin) < 0) {
                errors.add(label + " 的售价低于允许范围。");
            }
            if (price != null && priceMax != null && price.compareTo(priceMax) > 0) {
                errors.add(label + " 的售价高于允许范围。");
            }
        }
        return errors;
    }

    public List<String> validateOperationalKeysOnly(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        List<String> errors = new ArrayList<>();
        Map<String, Map<String, Object>> draftOffers = ProductPublishSnapshotSupport.siteOfferMap(
                draft == null ? null : draft.getSiteOffers()
        );
        Map<String, Map<String, Object>> baselineOffers = ProductPublishSnapshotSupport.siteOfferMap(
                baseline == null ? null : baseline.getSiteOffers()
        );
        Set<String> relevantSiteCodes = new LinkedHashSet<>();
        if (StringUtils.hasText(currentSiteCode)) {
            relevantSiteCodes.add(currentSiteCode.trim());
        } else {
            relevantSiteCodes.addAll(draftOffers.keySet());
        }
        for (String siteCode : relevantSiteCodes) {
            Map<String, Object> siteOffer = draftOffers.get(siteCode);
            Map<String, Object> baselineOffer = baselineOffers.get(siteCode);
            if (siteOffer == null || baselineOffer == null) {
                continue;
            }
            if (jsonEquals(
                    ProductPublishSnapshotSupport.siteOfferComparable(siteOffer, false),
                    ProductPublishSnapshotSupport.siteOfferComparable(baselineOffer, false)
            )) {
                continue;
            }
            if (!StringUtils.hasText(ProductPublishSnapshotSupport.firstNonBlank(
                    ProductPublishSnapshotSupport.textValue(siteOffer.get("pskuCode")),
                    ProductPublishSnapshotSupport.textValue(baselineOffer.get("pskuCode"))
            ))) {
                errors.add(
                        ProductPublishSnapshotSupport.textValue(siteOffer.get("site"))
                                + " / "
                                + siteCode
                                + " 缺少 pskuCode，暂时不能发布当前站点经营字段。"
                );
            }
        }
        return errors;
    }

    public List<String> validateWriteCoverageOnly(ProductUnsupportedChanges unsupportedChanges) {
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
        comparable.put("skuGroup", ProductPublishSnapshotSupport.textValue(group.get("skuGroup")));
        comparable.put("groupRef", ProductPublishSnapshotSupport.textValue(group.get("groupRef")));
        comparable.put("groupRefCanonical", ProductPublishSnapshotSupport.textValue(group.get("groupRefCanonical")));
        comparable.put("conditionsBrand", ProductPublishSnapshotSupport.textValue(group.get("conditionsBrand")));
        comparable.put("conditionsFulltype", ProductPublishSnapshotSupport.textValue(group.get("conditionsFulltype")));

        List<Map<String, Object>> axes = new ArrayList<>();
        for (Map<String, Object> axis : recordListValue(group.get("axes"))) {
            String axisCode = ProductPublishSnapshotSupport.textValue(firstNonNull(axis.get("axisCode"), axis.get("axis_code")));
            if (!StringUtils.hasText(axisCode)) {
                continue;
            }
            Map<String, Object> axisComparable = new LinkedHashMap<>();
            axisComparable.put("axisCode", axisCode);
            axisComparable.put("axisName", ProductPublishSnapshotSupport.textValue(firstNonNull(
                    axis.get("axisName"),
                    axis.get("axis_name")
            )));
            axes.add(axisComparable);
        }
        axes.sort((left, right) -> ProductPublishSnapshotSupport.textValue(left.get("axisCode"))
                .compareTo(ProductPublishSnapshotSupport.textValue(right.get("axisCode"))));
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

    private List<String> stringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                String text = ProductPublishSnapshotSupport.textValue(item);
                if (StringUtils.hasText(text)) {
                    values.add(text);
                }
            }
        } else if (StringUtils.hasText(ProductPublishSnapshotSupport.textValue(value))) {
            values.add(ProductPublishSnapshotSupport.textValue(value));
        }
        return values;
    }

    private boolean isLocalProductImageAssetUrl(String url) {
        return StringUtils.hasText(url) && url.trim().startsWith("/api/product-master/image-assets/");
    }

    private Map<String, Object> safeMap(Map<String, Object> source) {
        return source == null ? Map.of() : source;
    }

    private boolean jsonEquals(Object left, Object right) {
        return objectMapper.valueToTree(left).equals(objectMapper.valueToTree(right));
    }
}
