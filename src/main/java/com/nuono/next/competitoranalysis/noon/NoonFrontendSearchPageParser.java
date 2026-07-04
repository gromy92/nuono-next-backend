package com.nuono.next.competitoranalysis.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NoonFrontendSearchPageParser {
    public static final String PARSER_VERSION = "noon-search-html-v1";
    public static final String CATALOG_PARSER_VERSION = "noon-search-catalog-v1";
    public static final String CUSTOMER_CATALOG_V3_PARSER_VERSION = "noon-search-customer-catalog-v3";

    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public NoonFrontendSearchPageParser(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemUTC());
    }

    NoonFrontendSearchPageParser(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public NoonSearchPage parse(String html, String sourceUrl, int providerHttpStatus) {
        String body = html == null ? "" : html;
        String hash = sha256(body);
        if (looksLikeCaptcha(body)) {
            throw new NoonSearchProviderException(
                    "CAPTCHA_REQUIRED",
                    "Noon 前台要求验证码或机器人校验。",
                    providerHttpStatus,
                    sourceUrl,
                    hash
            );
        }
        Document document = Jsoup.parse(body, sourceUrl);
        Map<String, NoonSearchResult> byCode = new LinkedHashMap<>();
        PositionCounter positionCounter = new PositionCounter();
        collectJsonResults(document, sourceUrl, byCode, positionCounter);
        collectHtmlResults(document, sourceUrl, byCode, positionCounter);
        if (byCode.isEmpty()) {
            throw new NoonSearchProviderException(
                    "PARSE_FAILED",
                    "Noon 搜索页结构无法解析出商品结果。",
                    providerHttpStatus,
                    sourceUrl,
                    hash
            );
        }

        NoonSearchPage page = new NoonSearchPage();
        page.setSourceUrl(sourceUrl);
        page.setParserVersion(PARSER_VERSION);
        page.setProviderHttpStatus(providerHttpStatus);
        page.setResponseHash(hash);
        page.setCapturedAt(LocalDateTime.now(clock));
        page.setResults(new ArrayList<>(byCode.values()));
        return page;
    }

    public NoonSearchPage parseCatalogJson(String json, String sourceUrl, int providerHttpStatus) {
        String body = json == null ? "" : json;
        String hash = sha256(body);
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new NoonSearchProviderException(
                    "PARSE_FAILED",
                    "Noon catalog 搜索响应不是有效 JSON。",
                    providerHttpStatus,
                    sourceUrl,
                    hash
            );
        }

        Map<String, NoonSearchResult> byCode = new LinkedHashMap<>();
        PositionCounter positionCounter = new PositionCounter();
        collectCatalogSponsoredResults(root, sourceUrl, byCode, positionCounter, 0, false);
        JsonNode hits = firstArray(root, "hits", "products", "items", "results");
        if (hits != null) {
            for (JsonNode hit : hits) {
                NoonSearchResult result = toCatalogResult(hit, sourceUrl);
                if (result == null || byCode.containsKey(result.getNoonProductCode())) {
                    continue;
                }
                result.setPosition(positionCounter.next());
                byCode.put(result.getNoonProductCode(), result);
            }
        }
        if (byCode.isEmpty()) {
            throw new NoonSearchProviderException(
                    "PARSE_FAILED",
                    "Noon catalog 搜索响应无法解析出商品结果。",
                    providerHttpStatus,
                    sourceUrl,
                    hash
            );
        }

        NoonSearchPage page = new NoonSearchPage();
        page.setSourceUrl(sourceUrl);
        page.setParserVersion(catalogParserVersion(sourceUrl));
        page.setProviderHttpStatus(providerHttpStatus);
        page.setResponseHash(hash);
        page.setCapturedAt(LocalDateTime.now(clock));
        page.setResults(new ArrayList<>(byCode.values()));
        return page;
    }

    private void collectCatalogSponsoredResults(
            JsonNode node,
            String sourceUrl,
            Map<String, NoonSearchResult> byCode,
            PositionCounter positionCounter,
            int depth,
            boolean sponsoredContext
    ) {
        if (node == null || depth > 5) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectCatalogSponsoredResults(item, sourceUrl, byCode, positionCounter, depth + 1, sponsoredContext);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        boolean currentSponsoredContext = sponsoredContext || resolveSponsored(node);
        if (currentSponsoredContext) {
            NoonSearchResult result = toCatalogResult(node, sourceUrl);
            if (result != null && !byCode.containsKey(result.getNoonProductCode())) {
                result.setSponsored(true);
                result.setPosition(positionCounter.next());
                byCode.put(result.getNoonProductCode(), result);
            }
        }

        var fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            boolean childSponsoredContext = currentSponsoredContext || isSponsoredCatalogContainer(entry.getKey());
            collectCatalogSponsoredResults(
                    entry.getValue(),
                    sourceUrl,
                    byCode,
                    positionCounter,
                    depth + 1,
                    childSponsoredContext
            );
        }
    }

    private boolean isSponsoredCatalogContainer(String fieldName) {
        String value = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        return value.contains("sponsor")
                || value.contains("advert")
                || value.equals("ads")
                || value.endsWith("_ads")
                || value.contains("promoted")
                || value.equals("pla")
                || value.contains("product_listing_ad");
    }

    private void collectJsonResults(
            Document document,
            String sourceUrl,
            Map<String, NoonSearchResult> byCode,
            PositionCounter positionCounter
    ) {
        for (Element script : document.select("script[type=application/ld+json], script#__NEXT_DATA__, script[type=application/json]")) {
            String json = compact(firstNonBlank(script.data(), script.html()));
            if (!StringUtils.hasText(json)) {
                continue;
            }
            try {
                collectFromJsonNode(objectMapper.readTree(json), sourceUrl, byCode, positionCounter);
            } catch (Exception ignored) {
                // Search pages often carry non-product JSON fragments. HTML fallback remains available.
            }
        }
    }

    private void collectFromJsonNode(
            JsonNode node,
            String sourceUrl,
            Map<String, NoonSearchResult> byCode,
            PositionCounter positionCounter
    ) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectFromJsonNode(item, sourceUrl, byCode, positionCounter);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        NoonSearchResult result = toJsonResult(node, sourceUrl);
        if (result != null) {
            result.setPosition(positionCounter.next());
            byCode.putIfAbsent(result.getNoonProductCode(), result);
        }
        node.fields().forEachRemaining(entry -> collectFromJsonNode(entry.getValue(), sourceUrl, byCode, positionCounter));
    }

    private NoonSearchResult toJsonResult(JsonNode node, String sourceUrl) {
        String code = NoonProductCodeSupport.extractFirst(firstNonBlank(
                textAny(node, "sku", "noonProductCode", "productCode", "product_code", "offer_code", "pskuCode"),
                textAny(node, "url", "href", "canonicalUrl", "productUrl")
        )).orElse(null);
        if (!StringUtils.hasText(code)) {
            return null;
        }
        String codeType = NoonProductCodeSupport.codeType(code).orElse(null);
        if (!StringUtils.hasText(codeType)) {
            return null;
        }

        NoonSearchResult result = new NoonSearchResult();
        String titleEn = resolveTitleEn(node);
        String titleAr = resolveTitleAr(node);
        String title = firstNonBlank(titleEn, titleAr, textAny(node, "name", "title", "product_title", "productTitle"));
        result.setNoonProductCode(code);
        result.setCodeType(codeType);
        result.setCanonicalUrl(resolveUrl(sourceUrl, textAny(node, "url", "href", "canonicalUrl", "productUrl")));
        result.setTitle(title);
        result.setTitleEn(titleEn);
        result.setTitleAr(titleAr);
        result.setBrand(resolveBrand(node.path("brand")));
        result.setImageUrl(resolveImage(node));
        result.setPriceAmount(resolvePrice(node));
        result.setCurrencyCode(firstNonBlank(
                textAny(node, "currency", "currencyCode", "priceCurrency"),
                textAny(node.path("price"), "currency", "currencyCode"),
                inferCurrencyCode(sourceUrl)
        ));
        result.setRating(resolveRating(node));
        result.setReviewCount(resolveReviewCount(node));
        result.setTagsJson(resolveTagsJson(node));
        result.setSponsored(resolveSponsored(node));
        result.setRawResultJson(node.toString());
        return result;
    }

    private NoonSearchResult toCatalogResult(JsonNode node, String sourceUrl) {
        JsonNode payload = catalogProductPayload(node);
        if (payload == null || !payload.isObject()) {
            return null;
        }
        String code = NoonProductCodeSupport.extractFirst(firstNonBlank(
                textAny(payload, "sku", "sku_config", "catalog_sku", "noonProductCode", "productCode", "product_code", "offer_code", "pskuCode"),
                textAny(payload, "url", "href", "canonicalUrl", "productUrl")
        )).orElse(null);
        if (!StringUtils.hasText(code)) {
            return null;
        }
        String codeType = NoonProductCodeSupport.codeType(code).orElse(null);
        if (!StringUtils.hasText(codeType)) {
            return null;
        }

        NoonSearchResult result = new NoonSearchResult();
        String titleEn = resolveTitleEn(payload);
        String titleAr = resolveTitleAr(payload);
        String title = firstNonBlank(titleEn, titleAr, textAny(payload, "name", "title", "product_title", "productTitle"));
        result.setNoonProductCode(code);
        result.setCodeType(codeType);
        result.setCanonicalUrl(catalogCanonicalUrl(sourceUrl, payload, code));
        result.setTitle(title);
        result.setTitleEn(titleEn);
        result.setTitleAr(titleAr);
        result.setBrand(resolveBrand(payload.path("brand")));
        result.setImageUrl(resolveImage(payload));
        result.setPriceAmount(resolvePrice(payload));
        result.setCurrencyCode(firstNonBlank(
                textAny(payload, "currency", "currencyCode", "priceCurrency"),
                textAny(payload.path("price"), "currency", "currencyCode"),
                inferCurrencyCode(sourceUrl)
        ));
        result.setRating(resolveRating(payload));
        result.setReviewCount(resolveReviewCount(payload));
        result.setTagsJson(resolveTagsJson(payload));
        result.setSponsored(resolveSponsored(node) || resolveSponsored(payload));
        result.setRawResultJson(node == payload ? payload.toString() : node.toString());
        return result;
    }

    private JsonNode catalogProductPayload(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        if (hasCatalogProductCode(node)) {
            return node;
        }
        for (String field : List.of(
                "_source",
                "source",
                "product",
                "item",
                "data",
                "catalog",
                "catalog_sku",
                "sku",
                "hit",
                "record"
        )) {
            JsonNode child = node.path(field);
            if (child.isObject() && hasCatalogProductCode(child)) {
                return child;
            }
        }
        return node;
    }

    private boolean hasCatalogProductCode(JsonNode node) {
        return StringUtils.hasText(NoonProductCodeSupport.extractFirst(firstNonBlank(
                textAny(node, "sku", "sku_config", "catalog_sku", "noonProductCode", "productCode", "product_code", "offer_code", "pskuCode"),
                textAny(node, "url", "href", "canonicalUrl", "productUrl")
        )).orElse(null));
    }

    private void collectHtmlResults(
            Document document,
            String sourceUrl,
            Map<String, NoonSearchResult> byCode,
            PositionCounter positionCounter
    ) {
        for (Element element : document.select("[data-product-code], [data-qa*=product], a[href*=/p/]")) {
            String code = NoonProductCodeSupport.extractFirst(firstNonBlank(
                    element.attr("data-product-code"),
                    element.attr("data-sku"),
                    element.attr("href"),
                    element.html()
            )).orElse(null);
            if (!StringUtils.hasText(code) || byCode.containsKey(code)) {
                continue;
            }
            NoonSearchResult result = new NoonSearchResult();
            result.setPosition(positionCounter.next());
            result.setNoonProductCode(code);
            result.setCodeType(NoonProductCodeSupport.codeType(code).orElse(null));
            result.setCanonicalUrl(resolveUrl(sourceUrl, element.attr("href")));
            result.setTitle(compact(firstNonBlank(element.attr("title"), element.text())));
            result.setTitleEn(result.getTitle());
            result.setSponsored(containsIgnoreCase(element.className(), "sponsored")
                    || containsIgnoreCase(element.attr("data-sponsored"), "true")
                    || containsIgnoreCase(element.text(), "sponsored"));
            result.setRawResultJson(element.outerHtml());
            byCode.putIfAbsent(code, result);
        }
    }

    private boolean looksLikeCaptcha(String html) {
        String normalized = html == null ? "" : html.toLowerCase(Locale.ROOT);
        return normalized.contains("captcha")
                || normalized.contains("verify you are human")
                || normalized.contains("robot check")
                || normalized.contains("are you a robot");
    }

    private BigDecimal resolvePrice(JsonNode node) {
        BigDecimal direct = decimalAny(node, "sale_price", "salePrice", "salesPrice", "currentPrice", "finalPrice", "priceNow", "price");
        if (direct != null) {
            return direct;
        }
        JsonNode price = node.path("price");
        return decimalAny(price, "amount", "value", "price");
    }

    private BigDecimal resolveRating(JsonNode node) {
        BigDecimal direct = decimalAny(node, "rating", "ratingValue");
        if (direct != null) {
            return direct;
        }
        BigDecimal rating = decimalAny(node.path("rating"), "value", "ratingValue", "rating");
        if (rating != null) {
            return rating;
        }
        rating = decimalAny(node.path("product_rating"), "value", "ratingValue", "rating");
        if (rating != null) {
            return rating;
        }
        return decimalAny(node.path("aggregateRating"), "value", "ratingValue", "rating");
    }

    private Integer resolveReviewCount(JsonNode node) {
        Integer direct = intAny(node, "reviewCount", "reviewsCount", "rating_count", "ratingsCount");
        if (direct != null) {
            return direct;
        }
        Integer reviewCount = intAny(node.path("rating"), "count", "reviewCount", "rating_count", "ratingsCount");
        if (reviewCount != null) {
            return reviewCount;
        }
        reviewCount = intAny(node.path("product_rating"), "count", "reviewCount", "rating_count", "ratingsCount");
        if (reviewCount != null) {
            return reviewCount;
        }
        return intAny(node.path("aggregateRating"), "count", "reviewCount", "rating_count", "ratingsCount");
    }

    private String resolveBrand(JsonNode brandNode) {
        if (brandNode == null || brandNode.isMissingNode() || brandNode.isNull()) {
            return null;
        }
        if (brandNode.isTextual()) {
            return compact(brandNode.asText());
        }
        return firstNonBlank(textAny(brandNode, "name", "brand", "label"), null);
    }

    private String resolveTitleEn(JsonNode node) {
        String explicit = firstNonBlank(
                textAny(node, "name_en", "title_en", "nameEnglish", "titleEnglish", "english_title"),
                textAny(node.path("name"), "en", "english"),
                textAny(node.path("title"), "en", "english")
        );
        return firstNonBlank(explicit, textAny(node, "name", "title", "product_title", "productTitle"));
    }

    private String resolveTitleAr(JsonNode node) {
        return firstNonBlank(
                textAny(node, "name_ar", "title_ar", "nameArabic", "titleArabic", "arabic_title"),
                textAny(node.path("name"), "ar", "arabic"),
                textAny(node.path("title"), "ar", "arabic")
        );
    }

    private String resolveTagsJson(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Map<String, JsonNode> tags = new LinkedHashMap<>();
        for (String field : List.of(
                "badges",
                "labels",
                "flags",
                "tags",
                "tag",
                "promo_tags",
                "promotion_tags",
                "logistics_tags"
        )) {
            JsonNode value = node.path(field);
            if (hasTagContent(value)) {
                tags.put(field, value);
            }
        }
        if (tags.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean hasTagContent(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isArray()) {
            return node.size() > 0;
        }
        if (node.isObject()) {
            return node.size() > 0;
        }
        if (node.isTextual()) {
            return StringUtils.hasText(compact(node.asText()));
        }
        return node.isNumber() || node.isBoolean();
    }

    private String resolveImage(JsonNode node) {
        String image = firstNonBlank(
                textAny(node, "image", "imageUrl", "image_url", "imageUrlSnapshot"),
                firstTextFromArray(node.path("images")),
                firstTextFromArray(node.path("image_urls")),
                firstTextFromArray(node.path("imageUrls")),
                noonImageUrl(textAny(node, "image_key")),
                noonImageUrl(firstTextFromArray(node.path("image_keys")))
        );
        return compact(image);
    }

    private String firstTextFromArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        for (JsonNode item : node) {
            if (item.isTextual()) {
                return item.asText();
            }
            String url = textAny(item, "url", "src", "image", "imageUrl");
            if (StringUtils.hasText(url)) {
                return url;
            }
        }
        return null;
    }

    private String resolveUrl(String sourceUrl, String rawUrl) {
        String value = compact(rawUrl);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            if (uri.isAbsolute()) {
                return uri.toString();
            }
            return URI.create(sourceUrl).resolve(uri).toString();
        } catch (Exception ignored) {
            return value;
        }
    }

    private String textAny(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual() || value.isNumber() || value.isBoolean()) {
                String text = compact(value.asText());
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private BigDecimal decimalAny(JsonNode node, String... fields) {
        String value = textAny(node, fields);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer intAny(JsonNode node, String... fields) {
        String value = textAny(node, fields);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.replace(",", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean booleanAny(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return false;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            if (value.isTextual()) {
                String text = value.asText("").toLowerCase(Locale.ROOT);
                if ("true".equals(text) || "sponsored".equals(text) || "ad".equals(text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean resolveSponsored(JsonNode node) {
        if (booleanAny(
                node,
                "isSponsored",
                "is_sponsored",
                "sponsored",
                "isAd",
                "is_ad",
                "ad",
                "is_plp_sponsored",
                "isPlpSponsored",
                "is_product_listing_ad",
                "isProductListingAd",
                "is_sponsored_product",
                "isSponsoredProduct",
                "product_listing_ad",
                "pla",
                "promoted"
        )) {
            return true;
        }
        JsonNode flags = node == null ? null : node.path("flags");
        if (containsSponsoredText(flags)) {
            return true;
        }
        if (containsSponsoredText(node == null ? null : node.path("badges"))) {
            return true;
        }
        if (containsSponsoredText(node == null ? null : node.path("labels"))) {
            return true;
        }
        return false;
    }

    private boolean containsSponsoredText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isTextual()) {
            return containsIgnoreCase(node.asText(), "sponsor")
                    || "ad".equalsIgnoreCase(compact(node.asText()))
                    || containsIgnoreCase(node.asText(), "advert");
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (containsSponsoredText(item)) {
                    return true;
                }
            }
            return false;
        }
        if (!node.isObject()) {
            return false;
        }
        if (booleanAny(node, "sponsored", "isSponsored", "is_sponsored", "isAd", "is_ad", "ad")) {
            return true;
        }
        String text = firstNonBlank(
                textAny(node, "text", "label", "name", "type", "code", "value", "badge")
        );
        return containsIgnoreCase(text, "sponsor")
                || "ad".equalsIgnoreCase(compact(text))
                || containsIgnoreCase(text, "advert");
    }

    private JsonNode firstArray(JsonNode root, String... fieldNames) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        if (!root.isObject()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode direct = root.path(fieldName);
            if (direct.isArray()) {
                return direct;
            }
        }
        for (String fieldName : fieldNames) {
            JsonNode found = findArray(root, fieldName, 0);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private JsonNode findArray(JsonNode node, String fieldName, int depth) {
        if (node == null || depth > 4) {
            return null;
        }
        if (node.isObject()) {
            JsonNode direct = node.path(fieldName);
            if (direct.isArray()) {
                return direct;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                JsonNode found = findArray(fields.next().getValue(), fieldName, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode found = findArray(item, fieldName, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String catalogCanonicalUrl(String sourceUrl, JsonNode node, String noonProductCode) {
        String rawUrl = compact(textAny(node, "url", "href", "canonicalUrl", "productUrl", "product_url"));
        if (StringUtils.hasText(rawUrl)) {
            try {
                URI uri = URI.create(rawUrl);
                if (uri.isAbsolute()) {
                    return uri.toString();
                }
            } catch (Exception ignored) {
                // Build the public Noon URL below.
            }
        }
        String slug = rawUrl;
        if (StringUtils.hasText(slug)) {
            slug = slug.replaceFirst("^/+", "").replaceFirst("/+$", "");
        }
        StringBuilder url = new StringBuilder("https://www.noon.com")
                .append(publicMarketPath(sourceUrl))
                .append('/');
        if (StringUtils.hasText(slug)) {
            url.append(slug).append('/');
        }
        url.append(noonProductCode).append("/p/");
        String offerCode = compact(textAny(node, "offer_code", "offerCode"));
        if (StringUtils.hasText(offerCode)) {
            url.append("?o=").append(offerCode);
        }
        return url.toString();
    }

    private String publicMarketPath(String sourceUrl) {
        String value = sourceUrl == null ? "" : sourceUrl.toLowerCase(Locale.ROOT);
        if (value.contains("/uae-ar")) {
            return "/uae-ar";
        }
        if (value.contains("/uae-en")) {
            return "/uae-en";
        }
        if (value.contains("/egypt-ar") || value.contains("/egy-ar")) {
            return "/egypt-ar";
        }
        if (value.contains("/egypt-en") || value.contains("/egy-en")) {
            return "/egypt-en";
        }
        if (value.contains("/saudi-ar") || value.contains("/ksa-ar")) {
            return "/saudi-ar";
        }
        return "/saudi-en";
    }

    private String inferCurrencyCode(String sourceUrl) {
        String value = sourceUrl == null ? "" : sourceUrl.toLowerCase(Locale.ROOT);
        if (value.contains("/uae-")) {
            return "AED";
        }
        if (value.contains("/egypt-") || value.contains("/egy-")) {
            return "EGP";
        }
        return "SAR";
    }

    private String catalogParserVersion(String sourceUrl) {
        String value = sourceUrl == null ? "" : sourceUrl.toLowerCase(Locale.ROOT);
        return value.contains("/mp-customer-catalog-api/api/v3/")
                ? CUSTOMER_CATALOG_V3_PARSER_VERSION
                : CATALOG_PARSER_VERSION;
    }

    private String noonImageUrl(String imageKey) {
        String value = compact(imageKey);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return "https://f.nooncdn.com/p/" + value.replaceFirst("^/+", "") + ".jpg";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && needle != null && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private String compact(String value) {
        return StringUtils.hasText(value) ? value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim() : null;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            return null;
        }
    }

    private static final class PositionCounter {
        private int value = 0;

        private int next() {
            value++;
            return value;
        }
    }
}
