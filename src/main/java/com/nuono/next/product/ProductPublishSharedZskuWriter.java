package com.nuono.next.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;
import org.springframework.util.StringUtils;

class ProductPublishSharedZskuWriter {

    private static final String ZSKU_UPSERT_URL =
            NoonProductGateway.ZSKU_UPSERT_URL;
    private static final String PRODUCT_UPDATE_URL =
            NoonProductGateway.PRODUCT_UPDATE_URL;
    private static final String CATPLAT_SKU_CACHE_URL =
            NoonProductGateway.CATPLAT_SKU_CACHE_URL;
    private static final String NOON_ASSET_UPLOAD_URL =
            "https://noon-catalog.noon.partners/_svc/mp-partner-catalog/catalog/asset/upload";
    private static final String LOCAL_PRODUCT_IMAGE_ASSET_PREFIX = "/api/product-master/image-assets/";
    private static final int MAX_IMAGE_UPLOAD_BYTES = 10 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final ProductNoonAdapter productNoonAdapter;
    private final ProductPublishChangedDomainComparator productPublishChangedDomainComparator;

    ProductPublishSharedZskuWriter(
            ObjectMapper objectMapper,
            ProductNoonAdapter productNoonAdapter,
            ProductPublishChangedDomainComparator productPublishChangedDomainComparator
    ) {
        this.objectMapper = objectMapper;
        this.productNoonAdapter = productNoonAdapter;
        this.productPublishChangedDomainComparator = productPublishChangedDomainComparator;
    }

    void publishSharedAttributes(
            NoonSession session,
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView liveBeforePublish,
            ProductPublishUnsupportedChanges unsupportedChanges,
            List<String> actionWarnings
    ) {
        ProductPublishUnsupportedChanges resolvedUnsupportedChanges = unsupportedChanges == null
                ? new ProductPublishUnsupportedChanges()
                : unsupportedChanges;
        ProductMasterSnapshotView publishDraft = resolveChangedLocalImageAssets(session, draft, baseline, actionWarnings);
        publishVariantSizes(session, publishDraft, baseline, liveBeforePublish, resolvedUnsupportedChanges);

        ObjectNode englishBody = buildZskuUpsertBody(publishDraft, baseline, "en", resolvedUnsupportedChanges);
        if (hasZskuUpsertPayloadChanges(englishBody)) {
            productNoonAdapter.postWriteJson(session, ZSKU_UPSERT_URL, englishBody, true);
        }

        ObjectNode arabicBody = buildZskuUpsertBody(publishDraft, baseline, "ar", resolvedUnsupportedChanges);
        if (hasZskuUpsertPayloadChanges(arabicBody)) {
            productNoonAdapter.postWriteJson(session, ZSKU_UPSERT_URL, arabicBody, true);
        }
    }

    private ProductMasterSnapshotView resolveChangedLocalImageAssets(
            NoonSession session,
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            List<String> actionWarnings
    ) {
        List<String> draftImages = stringList(draft.getContent().get("images"));
        List<String> baselineImages = stringList(baseline.getContent().get("images"));
        if (draftImages.equals(baselineImages) || draftImages.stream().noneMatch(this::isLocalProductImageAssetUrl)) {
            return draft;
        }
        ProductMasterSnapshotView publishDraft = objectMapper.convertValue(draft, ProductMasterSnapshotView.class);
        List<String> publishImages = new ArrayList<>();
        int uploadedCount = 0;
        for (String image : draftImages) {
            if (!isLocalProductImageAssetUrl(image)) {
                publishImages.add(image);
                continue;
            }
            publishImages.add(uploadLocalProductImageAsset(session, image));
            uploadedCount++;
        }
        publishDraft.getContent().put("images", publishImages);
        if (uploadedCount > 0 && actionWarnings != null) {
            actionWarnings.add("本地上传图片已先上传为 Noon 可访问图片，再写入商品详情。");
        }
        return publishDraft;
    }

    private boolean isLocalProductImageAssetUrl(String value) {
        return StringUtils.hasText(value) && value.trim().startsWith(LOCAL_PRODUCT_IMAGE_ASSET_PREFIX);
    }

    private String uploadLocalProductImageAsset(NoonSession session, String imageUrl) {
        ProductImageAssetContent image = readLocalProductImageAsset(imageUrl);
        JsonNode response = productNoonAdapter.postMultipartFile(
                session,
                NOON_ASSET_UPLOAD_URL,
                "file",
                uploadFileName(image),
                uploadContentType(image),
                image.content,
                true,
                null
        );
        String uploadPath = firstNonBlank(
                jsonText(response, "upload_path"),
                jsonText(response, "uploadPath"),
                jsonText(response, "path"),
                jsonText(response, "url")
        );
        if (!StringUtils.hasText(uploadPath)) {
            throw new IllegalStateException("Noon 图片上传响应缺少 upload_path。");
        }
        return uploadPath;
    }

    private ProductImageAssetContent readLocalProductImageAsset(String imageUrl) {
        String filename = imageUrl.trim().substring(LOCAL_PRODUCT_IMAGE_ASSET_PREFIX.length()).trim();
        if (!StringUtils.hasText(filename)
                || filename.contains("..")
                || filename.contains("/")
                || filename.contains("\\")) {
            throw new IllegalArgumentException("本地上传图片地址无效。");
        }
        Path uploadDir = ProductImageAssetFileSupport.productImageUploadDir().normalize();
        Path file = uploadDir.resolve(filename).normalize();
        if (!file.startsWith(uploadDir)) {
            throw new IllegalArgumentException("本地上传图片地址无效。");
        }
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new IllegalStateException("本地上传图片文件不存在，请重新上传或重新自动适配。");
        }
        try {
            byte[] content = Files.readAllBytes(file);
            if (content.length == 0) {
                throw new IllegalStateException("本地上传图片文件为空，请重新上传。");
            }
            if (content.length > MAX_IMAGE_UPLOAD_BYTES) {
                throw new IllegalStateException("本地上传图片超过 10MB，不能发布到 Noon。");
            }
            String contentType = Files.probeContentType(file);
            if (!StringUtils.hasText(contentType)) {
                contentType = contentTypeFromFileName(filename);
            }
            ProductImageAssetContent image = new ProductImageAssetContent(filename, contentType, content);
            supportedUploadFileType(image);
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException("读取本地上传图片失败：" + exception.getMessage(), exception);
        }
    }

    private String contentTypeFromFileName(String filename) {
        String lower = StringUtils.hasText(filename) ? filename.toLowerCase(Locale.ROOT) : "";
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        return "image/jpeg";
    }

    private String uploadFileName(ProductImageAssetContent image) {
        String fileType = supportedUploadFileType(image);
        String fileName = textValue(image.fileName);
        if (!StringUtils.hasText(fileName)) {
            return "image." + fileType;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if ("png".equals(fileType) && !lower.endsWith(".png")) {
            return stripKnownImageExtension(fileName) + ".png";
        }
        if ("jpg".equals(fileType) && !(lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
            return stripKnownImageExtension(fileName) + ".jpg";
        }
        if ("jpg".equals(fileType) && lower.endsWith(".jpeg")) {
            return fileName.substring(0, fileName.length() - 5) + ".jpg";
        }
        return fileName;
    }

    private String uploadContentType(ProductImageAssetContent image) {
        return "png".equals(supportedUploadFileType(image)) ? "image/png" : "image/jpeg";
    }

    private String jsonText(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.isTextual() ? value.asText() : value.toString();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String stripKnownImageExtension(String fileName) {
        String normalized = textValue(fileName);
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String extension : List.of(".jpg", ".jpeg", ".png", ".webp", ".avif", ".gif")) {
            if (lower.endsWith(extension)) {
                return normalized.substring(0, normalized.length() - extension.length());
            }
        }
        return StringUtils.hasText(normalized) ? normalized : "image";
    }

    private String supportedUploadFileType(ProductImageAssetContent image) {
        String fileName = textValue(image.fileName).toLowerCase(Locale.ROOT);
        String contentType = textValue(image.contentType).toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".png") || contentType.contains("png")) {
            return "png";
        }
        if (fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")
                || contentType.contains("jpeg")
                || contentType.contains("jpg")) {
            return "jpg";
        }
        throw new IllegalStateException("Noon 图片上传只支持 JPG/PNG，请先点击自动适配后再发布。");
    }

    void publishVariantSizes(
            NoonSession session,
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView liveBeforePublish,
            ProductPublishUnsupportedChanges unsupportedChanges
    ) {
        if (unsupportedChanges == null
                || unsupportedChanges.isVariantStructureChanged()
                || !productPublishChangedDomainComparator.variantSizeChanged(draft, baseline)) {
            return;
        }
        ObjectNode body = buildProductUpdateVariantSizeBody(draft, baseline, liveBeforePublish);
        if (body.path("productUpdate").size() == 0) {
            return;
        }
        productNoonAdapter.postWriteJson(session, PRODUCT_UPDATE_URL, body, true);

        ObjectNode cacheBody = objectMapper.createObjectNode();
        cacheBody.put("skuParent", textValue(draft.getIdentity().get("skuParent")));
        productNoonAdapter.postWriteJson(session, CATPLAT_SKU_CACHE_URL, cacheBody, true);
    }

    ObjectNode buildProductUpdateVariantSizeBody(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView liveBeforePublish
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode productUpdate = body.putArray("productUpdate");
        String skuParent = firstNonBlank(
                textValue(draft.getIdentity().get("skuParent")),
                textValue(draft.getIdentity().get("parentSku"))
        );
        if (!StringUtils.hasText(skuParent)) {
            return body;
        }

        Map<String, Map<String, Object>> baselineVariants = variantMap(baseline.getVariants());
        ArrayNode childrenUpdate = objectMapper.createArrayNode();
        ArrayNode axisOptions = objectMapper.createArrayNode();
        Set<String> seenOptions = new LinkedHashSet<>();
        List<Map<String, Object>> draftVariants = draft.getVariants() != null ? draft.getVariants() : List.of();
        int sortIndex = 1;
        for (Map<String, Object> variant : draftVariants) {
            String childSku = textValue(variant.get("childSku"));
            String sizeEn = textValue(variant.get("sizeEn"));
            String sizeAr = firstNonBlank(textValue(variant.get("sizeAr")), sizeEn);
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
            if (baselineVariant == null || Objects.equals(sizeEn, textValue(baselineVariant.get("sizeEn")))) {
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
        Map<String, Object> draftTaxonomy = draft.getTaxonomy() != null ? draft.getTaxonomy() : Map.of();
        Map<String, Object> liveTaxonomy = liveBeforePublish != null && liveBeforePublish.getTaxonomy() != null
                ? liveBeforePublish.getTaxonomy()
                : Map.of();
        putIfHasText(productFulltype, "family", resolveTaxonomyText(draftTaxonomy, liveTaxonomy, "familyNameEn", "family"));
        putIfHasText(
                productFulltype,
                "product_type",
                resolveTaxonomyText(draftTaxonomy, liveTaxonomy, "productTypeNameEn", "productType")
        );
        putIfHasText(
                productFulltype,
                "product_subtype",
                resolveTaxonomyText(draftTaxonomy, liveTaxonomy, "productSubtypeNameEn", "productSubtype")
        );

        ArrayNode axesUpdate = update.putArray("axesUpdate");
        ObjectNode sizeAxis = axesUpdate.addObject();
        sizeAxis.put("axisName", "Size");
        sizeAxis.put("axisCode", "size");
        sizeAxis.set("axisOptions", axisOptions);
        update.set("childrenUpdate", childrenUpdate);
        return body;
    }

    ObjectNode buildZskuUpsertBody(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String lang,
            ProductPublishUnsupportedChanges unsupportedChanges
    ) {
        ProductPublishUnsupportedChanges resolvedUnsupportedChanges = unsupportedChanges == null
                ? new ProductPublishUnsupportedChanges()
                : unsupportedChanges;
        ObjectNode body = objectMapper.createObjectNode();
        body.put("skuParent", textValue(draft.getIdentity().get("skuParent")));
        body.put("lang", lang);

        ObjectNode attributes = body.putObject("attributes");
        if ("en".equals(lang)) {
            putIfChangedText(attributes, "brand", draft.getIdentity().get("brand"), baseline.getIdentity().get("brand"));
            putIfChangedText(attributes, "family", draft.getTaxonomy().get("family"), baseline.getTaxonomy().get("family"));
            putIfChangedText(attributes, "product_type", draft.getTaxonomy().get("productType"), baseline.getTaxonomy().get("productType"));
            putIfChangedText(attributes, "product_subtype", draft.getTaxonomy().get("productSubtype"), baseline.getTaxonomy().get("productSubtype"));
            putIfChangedText(attributes, "product_fulltype", draft.getTaxonomy().get("productFulltype"), baseline.getTaxonomy().get("productFulltype"));
            putIfChangedText(attributes, "grade", draft.getTaxonomy().get("grade"), baseline.getTaxonomy().get("grade"));
            putIfChangedText(attributes, "item_condition", draft.getTaxonomy().get("itemCondition"), baseline.getTaxonomy().get("itemCondition"));
            if (attributes.has("product_fulltype")) {
                attributes.put("update_fulltype", true);
            }
        }
        putIfChangedText(
                attributes,
                "product_title",
                "ar".equals(lang) ? draft.getContent().get("titleAr") : draft.getContent().get("titleEn"),
                "ar".equals(lang) ? baseline.getContent().get("titleAr") : baseline.getContent().get("titleEn")
        );
        putIfChangedText(
                attributes,
                "long_description",
                "ar".equals(lang) ? draft.getContent().get("descriptionAr") : draft.getContent().get("descriptionEn"),
                "ar".equals(lang) ? baseline.getContent().get("descriptionAr") : baseline.getContent().get("descriptionEn")
        );

        List<String> highlights = "ar".equals(lang)
                ? stringList(draft.getContent().get("highlightsAr"))
                : stringList(draft.getContent().get("highlightsEn"));
        List<String> baselineHighlights = "ar".equals(lang)
                ? stringList(baseline.getContent().get("highlightsAr"))
                : stringList(baseline.getContent().get("highlightsEn"));
        if (!highlights.equals(baselineHighlights)) {
            for (int index = 0; index < highlights.size(); index++) {
                putIfHasText(attributes, "feature_bullet_" + (index + 1), highlights.get(index));
            }
        }

        if ("en".equals(lang)) {
            List<String> images = stringList(draft.getContent().get("images"));
            List<String> baselineImages = stringList(baseline.getContent().get("images"));
            if (!images.equals(baselineImages)) {
                for (int index = 0; index < images.size(); index++) {
                    putIfHasText(attributes, "image_url_" + (index + 1), images.get(index));
                }
            }
        }

        Map<String, Map<String, Object>> baselineAttributes = keyAttributeMap(baseline.getKeyAttributes());
        for (Map<String, Object> attribute : draft.getKeyAttributes()) {
            String code = textValue(attribute.get("code"));
            if (!StringUtils.hasText(code)
                    || resolvedUnsupportedChanges.getUnsupportedAttributeCodes().contains(code)
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
            if (StringUtils.hasText(textValue(attribute.get("unit")))) {
                setObjectNodeValue(attributes, code + "_unit", attribute.get("unit"));
            }
        }

        ArrayNode variantsNode = body.putArray("variants");
        if (!resolvedUnsupportedChanges.isVariantStructureChanged()) {
            Map<String, Map<String, Object>> baselineVariants = variantMap(baseline.getVariants());
            for (Map<String, Object> variant : draft.getVariants()) {
                String childSku = textValue(variant.get("childSku"));
                if (!StringUtils.hasText(childSku)) {
                    continue;
                }
                Map<String, Object> baselineVariant = baselineVariants.get(childSku);
                if (baselineVariant == null) {
                    continue;
                }

                String sizeValue = "ar".equals(lang)
                        ? textValue(variant.get("sizeAr"))
                        : textValue(variant.get("sizeEn"));
                String baselineSizeValue = "ar".equals(lang)
                        ? textValue(baselineVariant.get("sizeAr"))
                        : textValue(baselineVariant.get("sizeEn"));
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

    boolean hasZskuUpsertPayloadChanges(ObjectNode body) {
        return body != null
                && (body.path("attributes").size() > 0 || body.path("variants").size() > 0);
    }

    private String resolveTaxonomyText(
            Map<String, Object> draftTaxonomy,
            Map<String, Object> liveTaxonomy,
            String nameKey,
            String codeKey
    ) {
        return firstNonBlank(
                textValue(draftTaxonomy.get(nameKey)),
                textValue(liveTaxonomy.get(nameKey)),
                textValue(draftTaxonomy.get(codeKey)),
                textValue(liveTaxonomy.get(codeKey))
        );
    }

    private String resolveVariantPartnerSku(ProductMasterSnapshotView draft, Map<String, Object> variant) {
        String value = firstNonBlank(
                textValue(variant.get("partnerSku")),
                textValue(variant.get("catalogSku")),
                textValue(variant.get("catalog_sku")),
                textValue(variant.get("sellerSku"))
        );
        if (StringUtils.hasText(value)) {
            return value;
        }
        if (draft.getVariants() != null && draft.getVariants().size() == 1) {
            return textValue(draft.getIdentity().get("partnerSku"));
        }
        return null;
    }

    private String resolveVariantPskuCode(ProductMasterSnapshotView draft, Map<String, Object> variant) {
        String value = firstNonBlank(
                textValue(variant.get("pskuCode")),
                textValue(variant.get("psku_code"))
        );
        if (StringUtils.hasText(value)) {
            return value;
        }
        if (draft.getVariants() != null && draft.getVariants().size() == 1) {
            return textValue(draft.getIdentity().get("pskuCode"));
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
        if (isScalarAttributeValue(localizedValue) && StringUtils.hasText(textValue(localizedValue))) {
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

    private int parseInteger(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void putIfHasText(ObjectNode target, String key, Object value) {
        String text = textValue(value);
        if (StringUtils.hasText(text)) {
            target.put(key, text);
        }
    }

    private void putIfChangedText(ObjectNode target, String key, Object value, Object baselineValue) {
        String text = textValue(value);
        String baselineText = textValue(baselineValue);
        if (StringUtils.hasText(text) && !Objects.equals(text, baselineText)) {
            target.put(key, text);
        }
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

    private static final class ProductImageAssetContent {
        private final String fileName;
        private final String contentType;
        private final byte[] content;

        private ProductImageAssetContent(String fileName, String contentType, byte[] content) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.content = content == null ? new byte[0] : content;
        }
    }
}
