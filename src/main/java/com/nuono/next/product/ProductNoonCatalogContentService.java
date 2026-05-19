package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductNoonCatalogContentService {

    private static final Logger log = LoggerFactory.getLogger(ProductNoonCatalogContentService.class);
    private static final String CATALOG_DETAIL_URL_PREFIX = "https://catalog.noon.partners/_svc/catalog/api/u/";
    private static final int MAX_IMAGES = 20;
    private static final int MAX_FEATURES = 12;

    public Optional<CatalogContent> fetchFollowSellCatalogContent(
            NoonSession session,
            String catalogSku,
            String site,
            String context
    ) {
        String normalizedSku = normalize(catalogSku);
        if (session == null || !isLikelyCatalogSku(normalizedSku)) {
            return Optional.empty();
        }

        String country = countryCode(site);
        String url = CATALOG_DETAIL_URL_PREFIX + normalizedSku + "/p";
        try {
            JsonNode enRoot = session.getJson(url, false, localeHeaders("en", country));
            CatalogContent content = parseCatalogContent(enRoot, "en");
            if (!content.hasUsableContent()) {
                return Optional.empty();
            }

            try {
                JsonNode arRoot = session.getJson(url, false, localeHeaders("ar", country));
                content.mergeMissing(parseCatalogContent(arRoot, "ar"));
            } catch (RuntimeException exception) {
                log.debug(
                        "product-management follow-sell Arabic catalog fallback failed context={} sku={} message={}",
                        context,
                        normalizedSku,
                        exception.getMessage()
                );
            }
            return Optional.of(content);
        } catch (RuntimeException exception) {
            log.debug(
                    "product-management follow-sell catalog fallback failed context={} sku={} message={}",
                    context,
                    normalizedSku,
                    exception.getMessage()
            );
            return Optional.empty();
        }
    }

    public CatalogContent parseCatalogContent(JsonNode root, String language) {
        JsonNode product = root == null ? null : root.path("product");
        if (product == null || product.isMissingNode() || product.isNull()) {
            return new CatalogContent();
        }

        CatalogContent content = new CatalogContent();
        String normalizedLanguage = "ar".equalsIgnoreCase(language) ? "ar" : "en";
        if ("ar".equals(normalizedLanguage)) {
            content.titleAr = firstText(product, "product_title", "title", "name");
            content.descriptionAr = firstText(product, "long_description", "description");
            addTextArray(content.highlightsAr, product.path("feature_bullets"), MAX_FEATURES);
        } else {
            content.titleEn = firstText(product, "product_title", "title", "name");
            content.descriptionEn = firstText(product, "long_description", "description");
            addTextArray(content.highlightsEn, product.path("feature_bullets"), MAX_FEATURES);
        }
        content.catalogSku = firstText(product, "sku");
        content.brand = firstText(product, "brand", "brand_code");
        content.images.addAll(extractImages(product));
        return content;
    }

    private static Map<String, String> localeHeaders(String language, String country) {
        String lang = "ar".equalsIgnoreCase(language) ? "ar" : "en";
        String normalizedCountry = StringUtils.hasText(country) ? country.toLowerCase(Locale.ROOT) : "ae";
        return Map.of(
                "X-Lang", lang,
                "X-Locale", lang + "-" + normalizedCountry,
                "Accept-Language", "ar".equals(lang) ? "ar-AE,ar;q=0.9,en;q=0.7" : "en-AE,en;q=0.9,ar;q=0.5"
        );
    }

    private static String countryCode(String site) {
        String normalized = normalize(site);
        if (!StringUtils.hasText(normalized)) {
            return "ae";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("sa")) {
            return "sa";
        }
        if (lower.contains("eg")) {
            return "eg";
        }
        return "ae";
    }

    private static boolean isLikelyCatalogSku(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.toUpperCase(Locale.ROOT).startsWith("N");
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null || !node.isObject() || fields == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual() || value.isNumber() || value.isBoolean()) {
                String text = normalize(value.asText());
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private static void addTextArray(List<String> target, JsonNode node, int maxCount) {
        if (target == null || node == null || !node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            if (target.size() >= maxCount) {
                break;
            }
            String value = item.isTextual() || item.isNumber()
                    ? normalize(item.asText())
                    : firstText(item, "text", "title", "value", "name");
            if (StringUtils.hasText(value) && !target.contains(value)) {
                target.add(value);
            }
        }
    }

    private static List<String> extractImages(JsonNode product) {
        Set<String> images = new LinkedHashSet<>();
        addImageKeys(images, product.path("image_keys"));
        addImageValues(images, product.path("images"));
        addImageValues(images, product.path("imageUrls"));
        addImageValues(images, product.path("image_urls"));
        String singleImage = normalizeNoonImageUrl(firstText(product, "image", "image_url"), false);
        if (StringUtils.hasText(singleImage)) {
            images.add(singleImage);
        }
        return new ArrayList<>(images);
    }

    private static void addImageKeys(Set<String> images, JsonNode node) {
        if (images == null || node == null || !node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            if (images.size() >= MAX_IMAGES) {
                break;
            }
            String url = normalizeNoonImageUrl(item.asText(""), true);
            if (StringUtils.hasText(url)) {
                images.add(url);
            }
        }
    }

    private static void addImageValues(Set<String> images, JsonNode node) {
        if (images == null || node == null) {
            return;
        }
        if (node.isTextual()) {
            String url = normalizeNoonImageUrl(node.asText(""), false);
            if (StringUtils.hasText(url)) {
                images.add(url);
            }
            return;
        }
        if (!node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            if (images.size() >= MAX_IMAGES) {
                break;
            }
            String value = item.isTextual()
                    ? item.asText("")
                    : firstText(item, "url", "src", "image", "imageUrl", "image_url");
            String url = normalizeNoonImageUrl(value, false);
            if (StringUtils.hasText(url)) {
                images.add(url);
            }
        }
    }

    private static String normalizeNoonImageUrl(String rawValue, boolean forceImageKey) {
        String value = normalize(rawValue);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return value;
        }
        if (forceImageKey || value.contains("/") || value.matches("(?i)^[a-z0-9_-]{8,}$")) {
            String key = value.replaceAll("^/+", "").replaceAll("\\.(jpg|jpeg|png|webp)$", "");
            return "https://f.nooncdn.com/p/" + key + ".jpg";
        }
        return value;
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static class CatalogContent {
        private String catalogSku;
        private String brand;
        private String titleEn;
        private String titleAr;
        private String descriptionEn;
        private String descriptionAr;
        private final List<String> images = new ArrayList<>();
        private final List<String> highlightsEn = new ArrayList<>();
        private final List<String> highlightsAr = new ArrayList<>();

        public boolean hasUsableContent() {
            return StringUtils.hasText(titleEn)
                    || StringUtils.hasText(titleAr)
                    || !images.isEmpty();
        }

        public void mergeMissing(CatalogContent other) {
            if (other == null) {
                return;
            }
            catalogSku = firstNonBlank(catalogSku, other.catalogSku);
            brand = firstNonBlank(brand, other.brand);
            titleEn = firstNonBlank(titleEn, other.titleEn);
            titleAr = firstNonBlank(titleAr, other.titleAr);
            descriptionEn = firstNonBlank(descriptionEn, other.descriptionEn);
            descriptionAr = firstNonBlank(descriptionAr, other.descriptionAr);
            mergeList(images, other.images);
            mergeList(highlightsEn, other.highlightsEn);
            mergeList(highlightsAr, other.highlightsAr);
        }

        private static void mergeList(List<String> target, List<String> source) {
            if (target == null || source == null) {
                return;
            }
            for (String value : source) {
                if (StringUtils.hasText(value) && !target.contains(value)) {
                    target.add(value);
                }
            }
        }

        private static String firstNonBlank(String first, String second) {
            return StringUtils.hasText(first) ? first : second;
        }

        public String getCatalogSku() {
            return catalogSku;
        }

        public String getBrand() {
            return brand;
        }

        public String getTitleEn() {
            return titleEn;
        }

        public String getTitleAr() {
            return titleAr;
        }

        public String getDescriptionEn() {
            return descriptionEn;
        }

        public String getDescriptionAr() {
            return descriptionAr;
        }

        public List<String> getImages() {
            return images;
        }

        public List<String> getHighlightsEn() {
            return highlightsEn;
        }

        public List<String> getHighlightsAr() {
            return highlightsAr;
        }
    }
}
