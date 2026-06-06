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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NoonFrontendSearchPageParser {
    public static final String PARSER_VERSION = "noon-search-html-v1";

    private final ObjectMapper objectMapper;
    private final Clock clock;

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
        result.setNoonProductCode(code);
        result.setCodeType(codeType);
        result.setCanonicalUrl(resolveUrl(sourceUrl, textAny(node, "url", "href", "canonicalUrl", "productUrl")));
        result.setTitle(firstNonBlank(textAny(node, "name", "title", "product_title", "productTitle"), null));
        result.setBrand(resolveBrand(node.path("brand")));
        result.setImageUrl(resolveImage(node));
        result.setPriceAmount(resolvePrice(node));
        result.setCurrencyCode(firstNonBlank(textAny(node, "currency", "currencyCode", "priceCurrency"), textAny(node.path("price"), "currency", "currencyCode")));
        result.setRating(resolveRating(node));
        result.setReviewCount(resolveReviewCount(node));
        result.setSponsored(booleanAny(node, "isSponsored", "sponsored", "isAd", "is_ad", "ad", "is_plp_sponsored"));
        result.setRawResultJson(node.toString());
        return result;
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
        return decimalAny(node.path("rating"), "value", "ratingValue", "rating");
    }

    private Integer resolveReviewCount(JsonNode node) {
        Integer direct = intAny(node, "reviewCount", "reviewsCount", "rating_count", "ratingsCount");
        if (direct != null) {
            return direct;
        }
        return intAny(node.path("rating"), "count", "reviewCount", "rating_count", "ratingsCount");
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

    private String resolveImage(JsonNode node) {
        String image = firstNonBlank(
                textAny(node, "image", "imageUrl", "image_url", "imageUrlSnapshot"),
                firstTextFromArray(node.path("images")),
                firstTextFromArray(node.path("image_urls")),
                firstTextFromArray(node.path("imageUrls"))
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
