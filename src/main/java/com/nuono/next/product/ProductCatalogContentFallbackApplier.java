package com.nuono.next.product;

import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

class ProductCatalogContentFallbackApplier {

    private final ProductNoonCatalogContentService productNoonCatalogContentService;

    ProductCatalogContentFallbackApplier(ProductNoonCatalogContentService productNoonCatalogContentService) {
        this.productNoonCatalogContentService = productNoonCatalogContentService;
    }

    void applyIfNeeded(
            NoonSession session,
            Map<String, Object> identity,
            Map<String, Object> content,
            String referenceSite,
            String reason
    ) {
        if (!needsCatalogContentFallback(content) || productNoonCatalogContentService == null) {
            return;
        }
        String catalogSku = textValue(identity.get("childSku"));
        productNoonCatalogContentService.fetchFollowSellCatalogContent(
                session,
                catalogSku,
                referenceSite,
                "detail." + normalizeReason(reason)
        ).ifPresent(catalogContent -> mergeCatalogContent(identity, content, catalogContent));
    }

    private boolean needsCatalogContentFallback(Map<String, Object> content) {
        if (content == null) {
            return true;
        }
        return !StringUtils.hasText(textValue(content.get("titleEn")))
                || stringList(content.get("images")).isEmpty();
    }

    private void mergeCatalogContent(
            Map<String, Object> identity,
            Map<String, Object> content,
            ProductNoonCatalogContentService.CatalogContent catalogContent
    ) {
        if (identity == null || content == null || catalogContent == null) {
            return;
        }
        if (!StringUtils.hasText(textValue(identity.get("brand")))) {
            putIfNotBlank(identity, "brand", catalogContent.getBrand());
        }
        if (!StringUtils.hasText(textValue(identity.get("childSku")))) {
            putIfNotBlank(identity, "childSku", catalogContent.getCatalogSku());
        }
        putIfNotBlank(identity, "productSourceType", ProductSourceTypeSupport.resolve(
                textValue(identity.get("productSourceType")),
                textValue(identity.get("childSku")),
                textValue(identity.get("skuParent"))
        ));
        if (!StringUtils.hasText(textValue(content.get("titleEn")))) {
            putIfNotBlank(content, "titleEn", catalogContent.getTitleEn());
        }
        if (!StringUtils.hasText(textValue(content.get("titleAr")))) {
            putIfNotBlank(content, "titleAr", catalogContent.getTitleAr());
        }
        if (!StringUtils.hasText(textValue(content.get("descriptionEn")))) {
            putIfNotBlank(content, "descriptionEn", catalogContent.getDescriptionEn());
        }
        if (!StringUtils.hasText(textValue(content.get("descriptionAr")))) {
            putIfNotBlank(content, "descriptionAr", catalogContent.getDescriptionAr());
        }
        if (stringList(content.get("images")).isEmpty()) {
            putIfNotEmpty(content, "images", catalogContent.getImages());
            putIfNotNull(content, "imageCount", catalogContent.getImages().size());
        }
        if (stringList(content.get("highlightsEn")).isEmpty()) {
            putIfNotEmpty(content, "highlightsEn", catalogContent.getHighlightsEn());
        }
        if (stringList(content.get("highlightsAr")).isEmpty()) {
            putIfNotEmpty(content, "highlightsAr", catalogContent.getHighlightsAr());
        }
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?>) {
            List<String> items = new ArrayList<>();
            for (Object item : (List<?>) value) {
                String text = textValue(item);
                if (StringUtils.hasText(text)) {
                    items.add(text);
                }
            }
            return items;
        }
        String text = textValue(value);
        return StringUtils.hasText(text) ? List.of(text) : List.of();
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
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

    private String normalizeReason(String reason) {
        String normalized = StringUtils.hasText(reason) ? reason.trim() : "default";
        return normalized.replaceAll("[^a-zA-Z0-9_.-]", "-");
    }
}
