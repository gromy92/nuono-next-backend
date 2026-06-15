package com.nuono.next.competitoranalysis.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NoonProductDetailPageParser {
    private static final int MAX_JSON_SEARCH_DEPTH = 8;

    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public NoonProductDetailPageParser(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemUTC());
    }

    NoonProductDetailPageParser(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public NoonProductDetail parse(String body, String sourceUrl, int providerHttpStatus) {
        String content = body == null ? "" : body;
        String hash = sha256(content);
        NoonProductDetail detail = parseJsonBody(content, sourceUrl);
        if (detail == null) {
            detail = parseHtmlBody(content, sourceUrl);
        }
        if (detail == null) {
            throw new NoonSearchProviderException(
                    "PARSE_FAILED",
                    "Noon 商品详情页无法解析出商品信息。",
                    providerHttpStatus,
                    sourceUrl,
                    hash
            );
        }
        if (!StringUtils.hasText(detail.getNoonProductCode())) {
            detail.setNoonProductCode(NoonProductCodeSupport.extractFirst(sourceUrl).orElse(null));
        }
        if (!StringUtils.hasText(detail.getCodeType())) {
            detail.setCodeType(NoonProductCodeSupport.codeType(detail.getNoonProductCode()).orElse(null));
        }
        if (!StringUtils.hasText(detail.getNoonProductCode()) || !StringUtils.hasText(detail.getCodeType())) {
            throw new NoonSearchProviderException(
                    "PARSE_FAILED",
                    "Noon 商品详情页缺少有效商品码。",
                    providerHttpStatus,
                    sourceUrl,
                    hash
            );
        }
        detail.setNoonProductCode(NoonProductCodeSupport.normalize(detail.getNoonProductCode()));
        detail.setDetailUrl(firstNonBlank(detail.getDetailUrl(), sourceUrl));
        detail.setProviderHttpStatus(providerHttpStatus);
        detail.setSnapshotHash(hash);
        detail.setCapturedAt(LocalDateTime.now(clock));
        return detail;
    }

    private NoonProductDetail parseJsonBody(String content, String sourceUrl) {
        String trimmed = content == null ? "" : content.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            return fromJson(root, sourceUrl, root.toString(), 0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private NoonProductDetail parseHtmlBody(String content, String sourceUrl) {
        Document document = Jsoup.parse(content == null ? "" : content, sourceUrl);
        for (Element script : document.select("script[type=application/ld+json], script#__NEXT_DATA__, script[type=application/json]")) {
            String json = compact(firstNonBlank(script.data(), script.html()));
            if (!StringUtils.hasText(json)) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(json);
                NoonProductDetail detail = fromJson(root, sourceUrl, root.toString(), 0);
                if (detail != null) {
                    return detail;
                }
            } catch (Exception ignored) {
                // Continue with other embedded JSON blocks and meta fallback.
            }
        }
        String code = NoonProductCodeSupport.extractFirst(sourceUrl).orElse(null);
        String title = compact(firstNonBlank(
                document.selectFirst("meta[property=og:title]") == null ? null : document.selectFirst("meta[property=og:title]").attr("content"),
                document.title()
        ));
        if (!StringUtils.hasText(code) && !StringUtils.hasText(title)) {
            return null;
        }
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode(code);
        detail.setCodeType(NoonProductCodeSupport.codeType(code).orElse(null));
        detail.setDetailUrl(sourceUrl);
        detail.setTitleEn(title);
        Element image = document.selectFirst("meta[property=og:image]");
        if (image != null) {
            detail.setMainImageUrlRaw(compact(image.attr("content")));
            detail.setMainImageUrlNormalized(compact(image.attr("content")));
        }
        detail.setCurrencyCode(inferCurrencyCode(sourceUrl));
        detail.setRawDetailJson(null);
        return detail;
    }

    private NoonProductDetail fromJson(JsonNode node, String sourceUrl, String rawJson, int depth) {
        if (node == null || node.isNull() || node.isMissingNode() || depth > MAX_JSON_SEARCH_DEPTH) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                NoonProductDetail detail = fromJson(item, sourceUrl, rawJson, depth + 1);
                if (detail != null) {
                    return detail;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        if (hasProductCode(node) || looksLikeProductDetail(node)) {
            NoonProductDetail detail = toDetail(node, sourceUrl, rawJson);
            if (detail != null) {
                return detail;
            }
        }
        for (String field : List.of("product", "product_detail", "productDetail", "catalog", "sku", "data", "pageProps", "props")) {
            JsonNode child = node.path(field);
            NoonProductDetail detail = fromJson(child, sourceUrl, child == null ? rawJson : child.toString(), depth + 1);
            if (detail != null) {
                return detail;
            }
        }
        var fields = node.fields();
        while (fields.hasNext()) {
            JsonNode child = fields.next().getValue();
            NoonProductDetail detail = fromJson(child, sourceUrl, rawJson, depth + 1);
            if (detail != null) {
                return detail;
            }
        }
        return null;
    }

    private NoonProductDetail toDetail(JsonNode node, String sourceUrl, String rawJson) {
        String code = NoonProductCodeSupport.extractFirst(firstNonBlank(
                textAny(node, "sku", "sku_config", "catalog_sku", "noonProductCode", "productCode", "product_code", "pskuCode"),
                textAny(node, "url", "href", "canonicalUrl", "productUrl", "product_url"),
                sourceUrl
        )).orElse(null);
        String codeType = NoonProductCodeSupport.codeType(code).orElse(null);
        if (!StringUtils.hasText(codeType) && !looksLikeProductDetail(node)) {
            return null;
        }

        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode(code);
        detail.setCodeType(codeType);
        detail.setDetailUrl(resolveUrl(sourceUrl, textAny(node, "url", "href", "canonicalUrl", "productUrl", "product_url")));
        detail.setTitleEn(firstNonBlank(textAny(node, "name_en", "title_en", "name", "title", "product_title", "productTitle")));
        detail.setTitleAr(firstNonBlank(textAny(node, "name_ar", "title_ar")));
        detail.setBrand(resolveBrand(node.path("brand")));
        detail.setSellerName(resolveSeller(node));
        detail.setPriceAmount(resolvePrice(node));
        detail.setCurrencyCode(firstNonBlank(
                textAny(node, "currency", "currencyCode", "priceCurrency"),
                textAny(node.path("price"), "currency", "currencyCode"),
                inferCurrencyCode(sourceUrl)
        ));
        detail.setRating(resolveRating(node));
        detail.setReviewCount(resolveReviewCount(node));
        String image = resolveImage(node);
        detail.setMainImageUrlRaw(image);
        detail.setMainImageUrlNormalized(normalizeImageUrl(image));
        detail.setMainImageAssetKey(firstNonBlank(textAny(node, "image_key"), firstTextFromArray(node.path("image_keys"))));
        detail.setSupermallEnabled(booleanAny(node, "supermall_enabled", "supermallEnabled", "is_supermall", "isSupermall"));
        detail.setSoldRecentlyText(textAny(node, "sold_recently_text", "soldRecentlyText"));
        detail.setLogisticsTagsJson(jsonArrayOrObject(node.path("logistics_tags"), node.path("logisticsTags")));
        detail.setBadgesJson(jsonArrayOrObject(node.path("badges"), node.path("labels")));
        detail.setAvailabilityStatus(firstNonBlank(
                textAny(node, "availability_status", "availabilityStatus", "stock_status", "stockStatus"),
                booleanAny(node, "in_stock", "inStock", "available") == null
                        ? null
                        : Boolean.TRUE.equals(booleanAny(node, "in_stock", "inStock", "available")) ? "IN_STOCK" : "OUT_OF_STOCK"
        ));
        detail.setRawDetailJson(rawJson);
        return detail;
    }

    private boolean hasProductCode(JsonNode node) {
        return StringUtils.hasText(NoonProductCodeSupport.extractFirst(firstNonBlank(
                textAny(node, "sku", "sku_config", "catalog_sku", "noonProductCode", "productCode", "product_code", "pskuCode"),
                textAny(node, "url", "href", "canonicalUrl", "productUrl", "product_url")
        )).orElse(null));
    }

    private boolean looksLikeProductDetail(JsonNode node) {
        return StringUtils.hasText(firstNonBlank(
                textAny(node, "name", "title", "name_en", "title_en"),
                textAny(node.path("price"), "amount", "value", "price")
        ));
    }

    private BigDecimal resolvePrice(JsonNode node) {
        BigDecimal direct = decimalAny(node, "sale_price", "salePrice", "salesPrice", "currentPrice", "finalPrice", "priceNow", "price");
        if (direct != null) {
            return direct;
        }
        return decimalAny(node.path("price"), "amount", "value", "price");
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
        return decimalAny(node.path("aggregateRating"), "value", "ratingValue", "rating");
    }

    private Integer resolveReviewCount(JsonNode node) {
        Integer direct = intAny(node, "reviewCount", "reviewsCount", "rating_count", "ratingsCount");
        if (direct != null) {
            return direct;
        }
        Integer count = intAny(node.path("rating"), "count", "reviewCount", "rating_count", "ratingsCount");
        if (count != null) {
            return count;
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
        return firstNonBlank(textAny(brandNode, "name", "brand", "label"));
    }

    private String resolveSeller(JsonNode node) {
        String direct = textAny(node, "seller_name", "sellerName", "merchant_name", "merchantName");
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        JsonNode seller = node.path("seller");
        if (seller.isTextual()) {
            return compact(seller.asText());
        }
        return firstNonBlank(textAny(seller, "name", "seller_name", "sellerName"));
    }

    private String resolveImage(JsonNode node) {
        return firstNonBlank(
                textAny(node, "image", "imageUrl", "image_url", "main_image", "mainImage"),
                firstTextFromArray(node.path("images")),
                firstTextFromArray(node.path("image_urls")),
                firstTextFromArray(node.path("imageUrls")),
                noonImageUrl(textAny(node, "image_key")),
                noonImageUrl(firstTextFromArray(node.path("image_keys")))
        );
    }

    private String normalizeImageUrl(String image) {
        String value = compact(image);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return noonImageUrl(value);
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

    private String firstTextFromArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        for (JsonNode item : node) {
            if (item.isTextual()) {
                return item.asText();
            }
            String value = textAny(item, "url", "src", "image", "imageUrl", "key");
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String jsonArrayOrObject(JsonNode first, JsonNode second) {
        JsonNode value = first != null && !first.isMissingNode() && !first.isNull() ? first : second;
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isArray() && !value.isObject()) {
            return null;
        }
        return value.toString();
    }

    private String resolveUrl(String sourceUrl, String rawUrl) {
        String value = compact(rawUrl);
        if (!StringUtils.hasText(value)) {
            return sourceUrl;
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

    private Boolean booleanAny(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            if (value.isTextual()) {
                String text = value.asText("").toLowerCase(Locale.ROOT);
                if ("true".equals(text) || "yes".equals(text) || "1".equals(text)) {
                    return true;
                }
                if ("false".equals(text) || "no".equals(text) || "0".equals(text)) {
                    return false;
                }
            }
        }
        return null;
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
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
}
