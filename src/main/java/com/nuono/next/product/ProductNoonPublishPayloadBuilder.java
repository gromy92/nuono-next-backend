package com.nuono.next.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.StringUtils;

final class ProductNoonPublishPayloadBuilder {

    private static final String PRICING_METHOD_MANUAL = "manual";
    private static final DateTimeFormatter NOON_OFFER_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId PRODUCT_MANAGEMENT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_SALE_WINDOW_YEARS = 10;

    private final ObjectMapper objectMapper;

    ProductNoonPublishPayloadBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    boolean shouldPublishVariantSizes(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            ProductUnsupportedChanges unsupportedChanges
    ) {
        return unsupportedChanges != null
                && !unsupportedChanges.isVariantStructureChanged()
                && variantSizeChanged(draft, baseline);
    }

    ObjectNode buildProductUpdateVariantSizeBody(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView liveBeforePublish
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode productUpdate = body.putArray("productUpdate");
        String skuParent = ProductPublishSnapshotSupport.firstNonBlank(
                ProductPublishSnapshotSupport.textValue(safeIdentity(draft).get("skuParent")),
                ProductPublishSnapshotSupport.textValue(safeIdentity(draft).get("parentSku"))
        );
        if (!StringUtils.hasText(skuParent)) {
            return body;
        }

        Map<String, Map<String, Object>> baselineVariants =
                ProductPublishSnapshotSupport.variantMap(safeVariants(baseline));
        ArrayNode childrenUpdate = objectMapper.createArrayNode();
        ArrayNode axisOptions = objectMapper.createArrayNode();
        Set<String> seenOptions = new LinkedHashSet<>();
        int sortIndex = 1;
        for (Map<String, Object> variant : safeVariants(draft)) {
            String childSku = ProductPublishSnapshotSupport.textValue(variant.get("childSku"));
            String sizeEn = ProductPublishSnapshotSupport.textValue(variant.get("sizeEn"));
            String sizeAr = ProductPublishSnapshotSupport.firstNonBlank(
                    ProductPublishSnapshotSupport.textValue(variant.get("sizeAr")),
                    sizeEn
            );
            if (StringUtils.hasText(sizeEn) && seenOptions.add(sizeEn.toLowerCase())) {
                ObjectNode axisOption = axisOptions.addObject();
                axisOption.put("optionName", sizeEn);
                ObjectNode optionLocale = objectMapper.createObjectNode();
                optionLocale.put("en", sizeEn);
                optionLocale.put("ar", StringUtils.hasText(sizeAr) ? sizeAr : sizeEn);
                axisOption.put("optionLocale", optionLocale.toString());
                axisOption.put("sortOrder", parseInteger(variant.get("variantIndex"), sortIndex));
            }
            sortIndex++;

            if (!StringUtils.hasText(childSku)) {
                continue;
            }
            Map<String, Object> baselineVariant = baselineVariants.get(childSku);
            if (baselineVariant == null
                    || Objects.equals(sizeEn, ProductPublishSnapshotSupport.textValue(baselineVariant.get("sizeEn")))) {
                continue;
            }
            if (!StringUtils.hasText(sizeEn)) {
                continue;
            }

            ObjectNode childUpdate = childrenUpdate.addObject();
            childUpdate.put("sku", childSku);
            putIfHasText(childUpdate, "partnerSku", resolveVariantPartnerSku(draft, variant));
            putIfHasText(childUpdate, "pskuCode", resolveVariantPskuCode(draft, variant));
            childUpdate.put("size", sizeEn);
        }

        if (childrenUpdate.size() == 0 || axisOptions.size() == 0) {
            return body;
        }

        ObjectNode update = productUpdate.addObject();
        ObjectNode parent = update.putObject("parent");
        parent.put("parentGroupKey", skuParent);
        parent.put("skuParent", skuParent);
        ObjectNode productFulltype = parent.putObject("product_fulltype");
        Map<String, Object> draftTaxonomy = safeTaxonomy(draft);
        Map<String, Object> liveTaxonomy = safeTaxonomy(liveBeforePublish);
        putIfHasText(productFulltype, "family",
                resolveTaxonomyText(draftTaxonomy, liveTaxonomy, "familyNameEn", "family"));
        putIfHasText(productFulltype, "product_type",
                resolveTaxonomyText(draftTaxonomy, liveTaxonomy, "productTypeNameEn", "productType"));
        putIfHasText(productFulltype, "product_subtype",
                resolveTaxonomyText(draftTaxonomy, liveTaxonomy, "productSubtypeNameEn", "productSubtype"));

        ArrayNode axesUpdate = update.putArray("axesUpdate");
        ObjectNode sizeAxis = axesUpdate.addObject();
        sizeAxis.put("axisName", "Size");
        sizeAxis.put("axisCode", "size");
        sizeAxis.set("axisOptions", axisOptions);
        update.set("childrenUpdate", childrenUpdate);
        return body;
    }

    boolean hasZskuUpsertPayloadChanges(ObjectNode body) {
        return body != null
                && (body.path("attributes").size() > 0 || body.path("variants").size() > 0);
    }

    ObjectNode buildZskuUpsertBody(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String lang,
            ProductUnsupportedChanges unsupportedChanges
    ) {
        ProductUnsupportedChanges safeUnsupportedChanges =
                unsupportedChanges != null ? unsupportedChanges : new ProductUnsupportedChanges();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("skuParent", ProductPublishSnapshotSupport.textValue(safeIdentity(draft).get("skuParent")));
        body.put("lang", lang);

        ObjectNode attributes = body.putObject("attributes");
        if ("en".equals(lang)) {
            putIfChangedText(attributes, "brand", safeIdentity(draft).get("brand"), safeIdentity(baseline).get("brand"));
            putIfChangedText(attributes, "family", safeTaxonomy(draft).get("family"), safeTaxonomy(baseline).get("family"));
            putIfChangedText(attributes, "product_type", safeTaxonomy(draft).get("productType"), safeTaxonomy(baseline).get("productType"));
            putIfChangedText(attributes, "product_subtype", safeTaxonomy(draft).get("productSubtype"), safeTaxonomy(baseline).get("productSubtype"));
            putIfChangedText(attributes, "product_fulltype", safeTaxonomy(draft).get("productFulltype"), safeTaxonomy(baseline).get("productFulltype"));
            putIfChangedText(attributes, "grade", safeTaxonomy(draft).get("grade"), safeTaxonomy(baseline).get("grade"));
            putIfChangedText(attributes, "item_condition", safeTaxonomy(draft).get("itemCondition"), safeTaxonomy(baseline).get("itemCondition"));
            if (attributes.has("product_fulltype")) {
                attributes.put("update_fulltype", true);
            }
        }
        putIfChangedText(
                attributes,
                "product_title",
                "ar".equals(lang) ? safeContent(draft).get("titleAr") : safeContent(draft).get("titleEn"),
                "ar".equals(lang) ? safeContent(baseline).get("titleAr") : safeContent(baseline).get("titleEn")
        );
        putIfChangedText(
                attributes,
                "long_description",
                "ar".equals(lang) ? safeContent(draft).get("descriptionAr") : safeContent(draft).get("descriptionEn"),
                "ar".equals(lang) ? safeContent(baseline).get("descriptionAr") : safeContent(baseline).get("descriptionEn")
        );

        List<String> highlights = "ar".equals(lang)
                ? stringList(safeContent(draft).get("highlightsAr"))
                : stringList(safeContent(draft).get("highlightsEn"));
        List<String> baselineHighlights = "ar".equals(lang)
                ? stringList(safeContent(baseline).get("highlightsAr"))
                : stringList(safeContent(baseline).get("highlightsEn"));
        if (!highlights.equals(baselineHighlights)) {
            for (int index = 0; index < highlights.size(); index++) {
                putIfHasText(attributes, "feature_bullet_" + (index + 1), highlights.get(index));
            }
        }

        if ("en".equals(lang)) {
            List<String> images = stringList(safeContent(draft).get("images"));
            List<String> baselineImages = stringList(safeContent(baseline).get("images"));
            if (!images.equals(baselineImages)) {
                for (int index = 0; index < images.size(); index++) {
                    putIfHasText(attributes, "image_url_" + (index + 1), images.get(index));
                }
            }
        }

        Map<String, Map<String, Object>> baselineAttributes =
                ProductPublishSnapshotSupport.keyAttributeMap(safeKeyAttributes(baseline));
        for (Map<String, Object> attribute : safeKeyAttributes(draft)) {
            String code = ProductPublishSnapshotSupport.textValue(attribute.get("code"));
            if (!StringUtils.hasText(code)
                    || safeUnsupportedChanges.getUnsupportedAttributeCodes().contains(code)
                    || isCoreAttribute(code)
                    || isBarcodeAttribute(code)) {
                continue;
            }

            Map<String, Object> baselineAttribute = baselineAttributes.get(code);
            if (objectMapper.valueToTree(attribute).equals(objectMapper.valueToTree(baselineAttribute))) {
                continue;
            }
            if (!hasAttributeChangeForLanguage(attribute, baselineAttribute, lang)) {
                continue;
            }

            Object value = "ar".equals(lang)
                    ? firstLocalizedValue(attribute.get("arValue"), attribute.get("commonValue"))
                    : firstLocalizedValue(attribute.get("enValue"), attribute.get("commonValue"));
            if (isScalarAttributeValue(value)) {
                setObjectNodeValue(attributes, code, value);
            }
            if (StringUtils.hasText(ProductPublishSnapshotSupport.textValue(attribute.get("unit")))) {
                setObjectNodeValue(attributes, code + "_unit", attribute.get("unit"));
            }
        }

        ArrayNode variantsNode = body.putArray("variants");
        if (!safeUnsupportedChanges.isVariantStructureChanged()) {
            Map<String, Map<String, Object>> baselineVariants =
                    ProductPublishSnapshotSupport.variantMap(safeVariants(baseline));
            for (Map<String, Object> variant : safeVariants(draft)) {
                String childSku = ProductPublishSnapshotSupport.textValue(variant.get("childSku"));
                if (!StringUtils.hasText(childSku)) {
                    continue;
                }
                Map<String, Object> baselineVariant = baselineVariants.get(childSku);
                if (baselineVariant == null) {
                    continue;
                }

                String sizeValue = "ar".equals(lang)
                        ? ProductPublishSnapshotSupport.textValue(variant.get("sizeAr"))
                        : ProductPublishSnapshotSupport.textValue(variant.get("sizeEn"));
                String baselineSizeValue = "ar".equals(lang)
                        ? ProductPublishSnapshotSupport.textValue(baselineVariant.get("sizeAr"))
                        : ProductPublishSnapshotSupport.textValue(baselineVariant.get("sizeEn"));
                if (!StringUtils.hasText(sizeValue) || sizeValue.equals(baselineSizeValue)) {
                    continue;
                }

                ObjectNode variantNode = variantsNode.addObject();
                variantNode.put("sku", childSku);
                ObjectNode variantAttributes = variantNode.putObject("attributes");
                variantAttributes.put("size", sizeValue);
            }
        }
        return body;
    }

    ObjectNode buildOfferUpsertBody(String pskuCode, Map<String, Object> siteOffer) {
        Map<String, Object> safeSiteOffer = siteOffer != null ? siteOffer : Map.of();
        requireText(
                pskuCode,
                ProductPublishSnapshotSupport.textValue(safeSiteOffer.get("site"))
                        + " / "
                        + ProductPublishSnapshotSupport.siteOfferCode(safeSiteOffer)
                        + " 缺少 pskuCode，暂时不能发布站点经营字段。"
        );
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode pskus = body.putArray("pskus");
        ObjectNode offerNode = pskus.addObject();
        offerNode.put("pskuCode", pskuCode);
        offerNode.put("country", resolveOfferPublishSite(safeSiteOffer).toLowerCase());
        if (hasOfferFieldValue(safeSiteOffer, "isActive")) {
            offerNode.put("isActive", truthy(safeSiteOffer.get("isActive")) ? 1 : 0);
        }
        offerNode.put("pricingMethod", PRICING_METHOD_MANUAL);
        setDecimalNode(offerNode, "price", safeSiteOffer.get("price"));
        setDecimalNode(offerNode, "salePrice", safeSiteOffer.get("salePrice"));
        setDecimalNode(offerNode, "priceMin", safeSiteOffer.get("priceMin"));
        setDecimalNode(offerNode, "priceMax", safeSiteOffer.get("priceMax"));
        Map<String, String> saleWindow = saleWindowForPublish(safeSiteOffer);
        putIfHasText(offerNode, "saleStart", saleWindow.get("saleStart"));
        putIfHasText(offerNode, "saleEnd", saleWindow.get("saleEnd"));
        if (hasOfferFieldValue(safeSiteOffer, "idWarranty")) {
            offerNode.put("idWarranty", parseInteger(safeSiteOffer.get("idWarranty"), 0));
        }
        putIfHasText(offerNode, "offerNote", safeSiteOffer.get("offerNote"));
        offerNode.putNull("pricingRule");
        offerNode.putNull("priceEngineMin");
        offerNode.putNull("priceEngineMax");
        return body;
    }

    String resolveOfferPublishSite(Map<String, Object> siteOffer) {
        Map<String, Object> safeSiteOffer = siteOffer != null ? siteOffer : Map.of();
        return ProductPublishSnapshotSupport.firstNonBlank(
                ProductPublishSnapshotSupport.textValue(safeSiteOffer.get("site")),
                deriveSiteFromStoreCode(ProductPublishSnapshotSupport.siteOfferCode(safeSiteOffer)),
                "AE"
        );
    }

    ObjectNode buildGroupMemberCreateBody(
            Map<String, Object> draftGroup,
            Map<String, Object> baselineGroup,
            List<String> addedSkuParents
    ) {
        return buildGroupMemberUpdateBody(draftGroup, baselineGroup, addedSkuParents, List.of(), "新增成员");
    }

    ObjectNode buildGroupMemberDeleteBody(
            Map<String, Object> draftGroup,
            Map<String, Object> baselineGroup,
            List<String> removedSkuParents
    ) {
        return buildGroupMemberUpdateBody(draftGroup, baselineGroup, List.of(), removedSkuParents, "Unlink");
    }

    List<ObjectNode> buildGroupAxisValueBodies(
            Map<String, Object> draftGroup,
            Map<String, Object> baselineGroup,
            String lang
    ) {
        List<ObjectNode> bodies = new ArrayList<>();
        Map<String, Map<String, Object>> changesBySkuParent =
                ProductGroupSnapshotSupport.groupAxisValueChanges(draftGroup, baselineGroup, lang);
        for (Map.Entry<String, Map<String, Object>> entry : changesBySkuParent.entrySet()) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("skuParent", entry.getKey());
            body.put("lang", lang);
            ObjectNode attributes = body.putObject("attributes");
            for (Map.Entry<String, Object> attribute : entry.getValue().entrySet()) {
                Object value = attribute.getValue();
                if (value == null) {
                    attributes.put(attribute.getKey(), "#del#");
                } else {
                    setObjectNodeValue(attributes, attribute.getKey(), value);
                }
            }
            body.putArray("variants");
            if (attributes.size() > 0) {
                bodies.add(body);
            }
        }
        return bodies;
    }

    private ObjectNode buildGroupMemberUpdateBody(
            Map<String, Object> draftGroup,
            Map<String, Object> baselineGroup,
            List<String> parentsCreate,
            List<String> parentsDelete,
            String action
    ) {
        Map<String, Object> safeDraftGroup = draftGroup != null ? draftGroup : Map.of();
        Map<String, Object> safeBaselineGroup = baselineGroup != null ? baselineGroup : Map.of();
        String skuGroup = ProductGroupSnapshotSupport.firstNonBlank(
                ProductGroupSnapshotSupport.textValue(safeBaselineGroup.get("skuGroup")),
                ProductGroupSnapshotSupport.textValue(safeDraftGroup.get("skuGroup"))
        );
        String partnerRef = ProductGroupSnapshotSupport.firstNonBlank(
                ProductGroupSnapshotSupport.textValue(safeBaselineGroup.get("groupRef")),
                ProductGroupSnapshotSupport.textValue(safeDraftGroup.get("groupRef"))
        );
        requireText(skuGroup, "当前 Group 缺少 skuGroup，暂时不能" + action + "。");
        requireText(partnerRef, "当前 Group 缺少 groupRef，暂时不能" + action + "。");

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode groupUpdate = body.putObject("groupUpdate");
        ObjectNode groupNode = groupUpdate.putObject("group");
        groupNode.put("skuGroup", skuGroup);
        groupNode.put("partnerRef", partnerRef);
        ArrayNode parentsCreateNode = groupUpdate.putArray("parentsCreate");
        for (String skuParent : safeStringList(parentsCreate)) {
            parentsCreateNode.add(skuParent);
        }
        ArrayNode parentsDeleteNode = groupUpdate.putArray("parentsDelete");
        for (String skuParent : safeStringList(parentsDelete)) {
            parentsDeleteNode.add(skuParent);
        }
        return body;
    }

    private boolean variantSizeChanged(ProductMasterSnapshotView draft, ProductMasterSnapshotView baseline) {
        Map<String, Map<String, Object>> baselineVariants =
                ProductPublishSnapshotSupport.variantMap(safeVariants(baseline));
        for (Map<String, Object> variant : safeVariants(draft)) {
            String childSku = ProductPublishSnapshotSupport.textValue(variant.get("childSku"));
            if (!StringUtils.hasText(childSku)) {
                continue;
            }
            Map<String, Object> baselineVariant = baselineVariants.get(childSku);
            if (baselineVariant == null) {
                continue;
            }
            if (!Objects.equals(
                    ProductPublishSnapshotSupport.textValue(variant.get("sizeEn")),
                    ProductPublishSnapshotSupport.textValue(baselineVariant.get("sizeEn"))
            ) || !Objects.equals(
                    ProductPublishSnapshotSupport.textValue(variant.get("sizeAr")),
                    ProductPublishSnapshotSupport.textValue(baselineVariant.get("sizeAr"))
            )) {
                return true;
            }
        }
        return false;
    }

    private String resolveTaxonomyText(
            Map<String, Object> draftTaxonomy,
            Map<String, Object> liveTaxonomy,
            String nameKey,
            String codeKey
    ) {
        return ProductPublishSnapshotSupport.firstNonBlank(
                ProductPublishSnapshotSupport.textValue(draftTaxonomy.get(nameKey)),
                ProductPublishSnapshotSupport.textValue(liveTaxonomy.get(nameKey)),
                ProductPublishSnapshotSupport.textValue(draftTaxonomy.get(codeKey)),
                ProductPublishSnapshotSupport.textValue(liveTaxonomy.get(codeKey))
        );
    }

    private String resolveVariantPartnerSku(ProductMasterSnapshotView draft, Map<String, Object> variant) {
        String value = ProductPublishSnapshotSupport.firstNonBlank(
                ProductPublishSnapshotSupport.textValue(variant.get("partnerSku")),
                ProductPublishSnapshotSupport.textValue(variant.get("catalogSku")),
                ProductPublishSnapshotSupport.textValue(variant.get("catalog_sku")),
                ProductPublishSnapshotSupport.textValue(variant.get("sellerSku"))
        );
        if (StringUtils.hasText(value)) {
            return value;
        }
        if (safeVariants(draft).size() == 1) {
            return ProductPublishSnapshotSupport.textValue(safeIdentity(draft).get("partnerSku"));
        }
        return null;
    }

    private String resolveVariantPskuCode(ProductMasterSnapshotView draft, Map<String, Object> variant) {
        String value = ProductPublishSnapshotSupport.firstNonBlank(
                ProductPublishSnapshotSupport.textValue(variant.get("pskuCode")),
                ProductPublishSnapshotSupport.textValue(variant.get("psku_code"))
        );
        if (StringUtils.hasText(value)) {
            return value;
        }
        if (safeVariants(draft).size() == 1) {
            return ProductPublishSnapshotSupport.textValue(safeIdentity(draft).get("pskuCode"));
        }
        return null;
    }

    private boolean hasAttributeChangeForLanguage(
            Map<String, Object> attribute,
            Map<String, Object> baselineAttribute,
            String lang
    ) {
        String localizedField = "ar".equals(lang) ? "arValue" : "enValue";
        return !objectMapper.valueToTree(attributeValue(attribute, localizedField))
                .equals(objectMapper.valueToTree(attributeValue(baselineAttribute, localizedField)))
                || !objectMapper.valueToTree(attributeValue(attribute, "commonValue"))
                .equals(objectMapper.valueToTree(attributeValue(baselineAttribute, "commonValue")))
                || !objectMapper.valueToTree(attributeValue(attribute, "unit"))
                .equals(objectMapper.valueToTree(attributeValue(baselineAttribute, "unit")));
    }

    private Object firstLocalizedValue(Object localizedValue, Object commonValue) {
        if (isScalarAttributeValue(localizedValue)
                && StringUtils.hasText(ProductPublishSnapshotSupport.textValue(localizedValue))) {
            return localizedValue;
        }
        return commonValue;
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

    private boolean hasOfferFieldValue(Map<String, Object> siteOffer, String field) {
        if (siteOffer == null || !siteOffer.containsKey(field)) {
            return false;
        }
        Object value = siteOffer.get(field);
        if (value == null) {
            return false;
        }
        if (value instanceof String) {
            return StringUtils.hasText((String) value);
        }
        return true;
    }

    private Map<String, String> saleWindowForPublish(Map<String, Object> siteOffer) {
        Map<String, String> saleWindow = new LinkedHashMap<>();
        if (siteOffer == null) {
            return saleWindow;
        }

        String saleStart = ProductPublishSnapshotSupport.normalizeOfferDateForNoon(siteOffer.get("saleStart"));
        String saleEnd = ProductPublishSnapshotSupport.normalizeOfferDateForNoon(siteOffer.get("saleEnd"));
        if (ProductPublishSnapshotSupport.asBigDecimal(siteOffer.get("salePrice")) != null) {
            LocalDate today = LocalDate.now(PRODUCT_MANAGEMENT_ZONE);
            if (!StringUtils.hasText(saleStart)) {
                saleStart = today.format(NOON_OFFER_DATE_FORMATTER);
            }
            if (!StringUtils.hasText(saleEnd)) {
                saleEnd = today.plusYears(DEFAULT_SALE_WINDOW_YEARS).format(NOON_OFFER_DATE_FORMATTER);
            }
        }

        if (StringUtils.hasText(saleStart)) {
            saleWindow.put("saleStart", saleStart);
        }
        if (StringUtils.hasText(saleEnd)) {
            saleWindow.put("saleEnd", saleEnd);
        }
        return saleWindow;
    }

    private void setObjectNodeValue(ObjectNode target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Boolean) {
            target.put(key, (Boolean) value);
            return;
        }
        if (value instanceof Integer) {
            target.put(key, (Integer) value);
            return;
        }
        if (value instanceof Long) {
            target.put(key, (Long) value);
            return;
        }
        if (value instanceof Float) {
            target.put(key, (Float) value);
            return;
        }
        if (value instanceof Double) {
            target.put(key, (Double) value);
            return;
        }
        if (value instanceof BigDecimal) {
            target.put(key, (BigDecimal) value);
            return;
        }
        target.put(key, String.valueOf(value));
    }

    private void setDecimalNode(ObjectNode target, String key, Object value) {
        BigDecimal decimal = ProductPublishSnapshotSupport.asBigDecimal(value);
        if (decimal == null) {
            target.putNull(key);
            return;
        }
        target.put(key, decimal);
    }

    private int parseInteger(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = ProductPublishSnapshotSupport.textValue(value);
        if (!StringUtils.hasText(text)) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = ProductPublishSnapshotSupport.textValue(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "active".equalsIgnoreCase(text);
    }

    private void putIfHasText(ObjectNode target, String key, Object value) {
        String text = ProductPublishSnapshotSupport.textValue(value);
        if (StringUtils.hasText(text)) {
            target.put(key, text);
        }
    }

    private void putIfChangedText(ObjectNode target, String key, Object value, Object baselineValue) {
        String text = ProductPublishSnapshotSupport.textValue(value);
        String baselineText = ProductPublishSnapshotSupport.textValue(baselineValue);
        if (StringUtils.hasText(text) && !Objects.equals(text, baselineText)) {
            target.put(key, text);
        }
    }

    private String deriveSiteFromStoreCode(String storeCode) {
        if (!StringUtils.hasText(storeCode) || storeCode.length() < 2) {
            return null;
        }
        String suffix = storeCode.substring(storeCode.length() - 2).toUpperCase();
        if ("SA".equals(suffix) || "AE".equals(suffix)) {
            return suffix;
        }
        return null;
    }

    private List<String> stringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List) {
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

    private List<String> safeStringList(List<String> values) {
        return values != null ? values : List.of();
    }

    private Map<String, Object> safeIdentity(ProductMasterSnapshotView snapshot) {
        return snapshot != null && snapshot.getIdentity() != null ? snapshot.getIdentity() : Map.of();
    }

    private Map<String, Object> safeTaxonomy(ProductMasterSnapshotView snapshot) {
        return snapshot != null && snapshot.getTaxonomy() != null ? snapshot.getTaxonomy() : Map.of();
    }

    private Map<String, Object> safeContent(ProductMasterSnapshotView snapshot) {
        return snapshot != null && snapshot.getContent() != null ? snapshot.getContent() : Map.of();
    }

    private List<Map<String, Object>> safeKeyAttributes(ProductMasterSnapshotView snapshot) {
        return snapshot != null && snapshot.getKeyAttributes() != null ? snapshot.getKeyAttributes() : List.of();
    }

    private List<Map<String, Object>> safeVariants(ProductMasterSnapshotView snapshot) {
        return snapshot != null && snapshot.getVariants() != null ? snapshot.getVariants() : List.of();
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
