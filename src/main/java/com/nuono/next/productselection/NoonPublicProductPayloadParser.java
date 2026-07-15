package com.nuono.next.productselection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class NoonPublicProductPayloadParser {

    private static final int MAX_IMAGES = 20;
    private static final int MAX_FEATURES = 12;
    private static final int MAX_SPECS = 64;
    private static final Pattern SPEC_LINE_PATTERN = Pattern.compile("^([^\\t:：]{2,80})[\\t:：]+(.{1,220})$");

    private final ProductSelectionSourceCollectionHtmlParser htmlParser;
    private final ObjectMapper objectMapper;

    public NoonPublicProductPayloadParser(
            ProductSelectionSourceCollectionHtmlParser htmlParser,
            ObjectMapper objectMapper
    ) {
        this.htmlParser = htmlParser;
        this.objectMapper = objectMapper;
    }

    NoonPublicProductSnapshot parse(Document document, String pageUrl) {
        NoonPublicProductSnapshot snapshot = new NoonPublicProductSnapshot();
        collectStructuredJson(snapshot, document, pageUrl);
        collectHtmlFallback(snapshot, document, pageUrl);
        parseLongDescriptionSpecs(snapshot);
        return snapshot;
    }

    NoonPublicProductSnapshot parseJson(String json) {
        return parseJson(json, "");
    }

    NoonPublicProductSnapshot parseJson(String json, String pageUrl) {
        NoonPublicProductSnapshot snapshot = new NoonPublicProductSnapshot();
        collectJson(snapshot, json, pageUrl);
        parseLongDescriptionSpecs(snapshot);
        return snapshot;
    }

    private void collectStructuredJson(NoonPublicProductSnapshot snapshot, Document document, String pageUrl) {
        for (Element script : document.select("script[type=application/ld+json], script#__NEXT_DATA__, script[type=application/json]")) {
            String json = htmlParser.compactText(script.data());
            if (!StringUtils.hasText(json)) {
                json = htmlParser.compactText(script.html());
            }
            collectJson(snapshot, json, pageUrl);
        }
    }

    private void collectJson(NoonPublicProductSnapshot snapshot, String json, String pageUrl) {
        if (!StringUtils.hasText(json)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            collectFromNode(snapshot, root, pageUrl);
        } catch (Exception ignored) {
            // Public page scripts are best-effort. HTML fallback still runs.
        }
    }

    private void collectFromNode(NoonPublicProductSnapshot snapshot, JsonNode node, String pageUrl) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectFromNode(snapshot, item, pageUrl);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        if (isProductLikeNode(node)) {
            mapProductNode(snapshot, node, pageUrl);
        } else if (isOfferLikeNode(node)) {
            mapOfferNode(snapshot, node);
        } else if (isRatingLikeNode(node)) {
            mapRatingNode(snapshot, node);
        }

        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            collectFromNode(snapshot, iterator.next().getValue(), pageUrl);
        }
    }

    private boolean isProductLikeNode(JsonNode node) {
        String type = textAny(node, "@type", "type");
        return containsIgnoreCase(type, "Product")
                || node.has("product_title")
                || node.has("productTitle")
                || node.has("feature_bullets")
                || node.has("featureBullets")
                || node.has("long_description")
                || node.has("image_keys")
                || node.has("specifications")
                || node.has("variants");
    }

    private boolean isOfferLikeNode(JsonNode node) {
        String type = textAny(node, "@type", "type");
        return containsIgnoreCase(type, "Offer")
                || node.has("sale_price")
                || node.has("salePrice")
                || node.has("salesPrice")
                || node.has("offer_code")
                || node.has("sku_config");
    }

    private boolean isRatingLikeNode(JsonNode node) {
        String type = textAny(node, "@type", "type");
        return containsIgnoreCase(type, "AggregateRating")
                || node.has("ratingValue")
                || node.has("reviewCount")
                || node.has("rating_count")
                || (node.has("value") && node.has("count"));
    }

    private void mapProductNode(NoonPublicProductSnapshot snapshot, JsonNode node, String pageUrl) {
        snapshot.title = firstText(
                snapshot.title,
                textAny(node, "product_title", "productTitle", "title", "name")
        );
        snapshot.sku = firstText(snapshot.sku, textAny(node, "sku", "skuParent", "pskuCode", "noonSku"));
        snapshot.brand = firstText(snapshot.brand, resolveBrand(node.path("brand")));
        snapshot.longDescription = firstText(
                snapshot.longDescription,
                normalizeDescription(textAny(node, "long_description", "longDescription", "description", "productDescription"))
        );

        addTextArray(snapshot::addFeatureBullet, node.path("feature_bullets"));
        addTextArray(snapshot::addFeatureBullet, node.path("featureBullets"));
        addTextArray(snapshot::addFeatureBullet, node.path("highlights"));

        addImageKeys(snapshot, node.path("image_keys"));
        addImages(snapshot, node.path("image"));
        addImages(snapshot, node.path("images"));
        addImages(snapshot, node.path("imageUrls"));
        addImages(snapshot, node.path("image_urls"));
        addSpecifications(snapshot, node.path("specifications"));
        addBreadcrumbs(snapshot, node.path("breadcrumbs"), pageUrl);
        mapRatingNode(snapshot, node.path("product_rating"));
        mapRatingNode(snapshot, node.path("aggregateRating"));
        mapOfferNode(snapshot, node.path("offers"));
        mapOfferNode(snapshot, node.path("variants"));
    }

    private void addBreadcrumbs(NoonPublicProductSnapshot snapshot, JsonNode node, String pageUrl) {
        if (!node.isArray()) {
            return;
        }
        List<String> pathParts = new ArrayList<>();
        String leafName = "";
        String leafCode = "";
        for (JsonNode item : node) {
            String name = htmlParser.compactText(textAny(item, "name", "title", "label"));
            String code = htmlParser.compactText(textAny(item, "code", "path", "slug"));
            if (!StringUtils.hasText(name) || (!StringUtils.hasText(code) && "home".equalsIgnoreCase(name))) {
                continue;
            }
            pathParts.add(name);
            leafName = name;
            leafCode = code;
        }
        if (StringUtils.hasText(leafName)) {
            snapshot.addCategoryLink(new ProductSelectionCompetitorCategoryLink(
                    leafName,
                    String.join(" > ", pathParts),
                    NoonPublicProductUrlSupport.categoryUrl(pageUrl, leafCode)
            ));
        }
    }

    private void mapOfferNode(NoonPublicProductSnapshot snapshot, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                mapOfferNode(snapshot, item);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        JsonNode offers = node.path("offers");
        if (offers.isArray()) {
            mapOfferNode(snapshot, offers);
        }
        String price = firstText(
                textAny(node, "sale_price", "salePrice", "salesPrice", "currentPrice", "finalPrice", "priceNow"),
                textAny(node, "price", "regularPrice", "wasPrice")
        );
        if (!StringUtils.hasText(price) && node.path("price").isObject()) {
            price = textAny(node.path("price"), "amount", "value", "price");
        }
        String currency = textAny(node, "currency", "priceCurrency", "currencyCode");
        if (StringUtils.hasText(price) && !StringUtils.hasText(snapshot.priceSummary)) {
            snapshot.priceSummary = withCurrency(price, currency);
        }
    }

    private void mapRatingNode(NoonPublicProductSnapshot snapshot, JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }
        String rating = textAny(node, "ratingValue", "value", "rating");
        String reviewCount = textAny(node, "reviewCount", "count", "rating_count", "ratingsCount");
        snapshot.rating = firstText(snapshot.rating, rating);
        snapshot.reviewCount = firstText(snapshot.reviewCount, reviewCount);
    }

    private void addSpecifications(NoonPublicProductSnapshot snapshot, JsonNode node) {
        if (!node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            addSpecPair(
                    snapshot,
                    textAny(item, "name", "label", "code"),
                    textAny(item, "value", "valueCode", "value_code")
            );
        }
    }

    private void addImageKeys(NoonPublicProductSnapshot snapshot, JsonNode node) {
        addTextArray(value -> snapshot.addImageUrl(normalizeNoonImageUrl(value, true)), node);
    }

    private void addImages(NoonPublicProductSnapshot snapshot, JsonNode node) {
        if (node.isTextual()) {
            snapshot.addImageUrl(normalizeNoonImageUrl(node.asText(""), false));
            return;
        }
        if (!node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            if (item.isTextual()) {
                snapshot.addImageUrl(normalizeNoonImageUrl(item.asText(""), false));
            } else if (item.isObject()) {
                snapshot.addImageUrl(normalizeNoonImageUrl(textAny(item, "url", "src", "image", "imageUrl"), false));
            }
        }
    }

    private void addTextArray(TextConsumer consumer, JsonNode node) {
        if (!node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            String value = item.isTextual() || item.isNumber() ? item.asText("") : textAny(item, "text", "title", "value", "name");
            if (StringUtils.hasText(value)) {
                consumer.accept(value);
            }
        }
    }

    private void addSpecPair(NoonPublicProductSnapshot snapshot, String label, String value) {
        String normalizedLabel = htmlParser.compactText(label).replaceAll("[:：]+$", "");
        String normalizedValue = htmlParser.compactText(value);
        if (!StringUtils.hasText(normalizedLabel) || !StringUtils.hasText(normalizedValue)) {
            return;
        }
        if (normalizedLabel.length() > 80 || normalizedValue.length() > 220 || snapshot.specHints.size() >= MAX_SPECS) {
            return;
        }
        snapshot.addSpecHint(normalizedLabel + ": " + normalizedValue);
    }

    private void parseLongDescriptionSpecs(NoonPublicProductSnapshot snapshot) {
        if (!StringUtils.hasText(snapshot.longDescription)) {
            return;
        }
        for (String line : snapshot.longDescription.replace('\r', '\n').split("\\n+")) {
            Matcher matcher = SPEC_LINE_PATTERN.matcher(htmlParser.compactText(line));
            if (matcher.find()) {
                addSpecPair(snapshot, matcher.group(1), matcher.group(2));
            }
        }
    }

    private void collectHtmlFallback(NoonPublicProductSnapshot snapshot, Document document, String pageUrl) {
        snapshot.title = firstText(
                snapshot.title,
                htmlParser.firstText(
                        meta(document, "meta[property=og:title]"),
                        meta(document, "meta[name=twitter:title]"),
                        attr(document.selectFirst("h1"), "text"),
                        document.title()
                ).replaceAll("\\s*\\|\\s*noon.*$", "")
        );
        snapshot.priceSummary = firstText(
                snapshot.priceSummary,
                htmlParser.firstText(
                        meta(document, "meta[property=product:price:amount]"),
                        meta(document, "meta[property=og:price:amount]"),
                        attr(document.selectFirst("[data-qa*=price], [class*=price], [class*=Price]"), "text")
                )
        );
        String metaCurrency = htmlParser.firstText(
                meta(document, "meta[property=product:price:currency]"),
                meta(document, "meta[property=og:price:currency]")
        );
        if (StringUtils.hasText(snapshot.priceSummary)) {
            snapshot.priceSummary = withCurrency(snapshot.priceSummary, metaCurrency);
        }
        snapshot.addImageUrl(normalizeNoonImageUrl(meta(document, "meta[property=og:image]"), false));
        snapshot.addImageUrl(normalizeNoonImageUrl(meta(document, "meta[name=twitter:image]"), false));
        for (Element image : document.select("img[src], img[data-src]")) {
            if (snapshot.imageUrls.size() >= MAX_IMAGES) {
                break;
            }
            snapshot.addImageUrl(normalizeNoonImageUrl(htmlParser.firstText(image.absUrl("src"), image.absUrl("data-src"), image.attr("src"), image.attr("data-src")), false));
        }
        for (Element item : document.select("ul li, [class*=feature] li, [class*=bullet] li")) {
            if (snapshot.featureBullets.size() >= MAX_FEATURES) {
                break;
            }
            String text = htmlParser.compactText(item.text());
            if (text.length() >= 16 && text.length() <= 360) {
                snapshot.addFeatureBullet(text);
            }
        }
        List<String> pathParts = new ArrayList<>();
        String leafName = "";
        String leafUrl = "";
        for (Element link : document.select("nav[aria-label*=breadcrumb] a, [class*=breadcrumb] a, [data-qa*=breadcrumb] a")) {
            String name = htmlParser.compactText(link.text());
            if (!StringUtils.hasText(name) || "home".equalsIgnoreCase(name)) {
                continue;
            }
            pathParts.add(name);
            leafName = name;
            leafUrl = htmlParser.firstText(link.absUrl("href"), link.attr("href"));
        }
        if (StringUtils.hasText(leafName)) {
            snapshot.addCategoryLink(new ProductSelectionCompetitorCategoryLink(
                    leafName,
                    String.join(" > ", pathParts),
                    leafUrl
            ));
        }
    }

    private String resolveBrand(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        return textAny(node, "name", "brand", "code", "brand_code");
    }

    private String normalizeNoonImageUrl(String rawValue, boolean forceImageKey) {
        String value = htmlParser.compactText(rawValue);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.startsWith("//")) {
            return NoonImageUrlNormalizer.normalize("https:" + value);
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return NoonImageUrlNormalizer.normalize(value);
        }
        if (forceImageKey || value.contains("/") || value.matches("(?i)^[a-z0-9_-]{8,}$")) {
            String key = value.replaceAll("^/+", "").replaceAll("\\.(jpg|jpeg|png|webp)$", "");
            String prefix = key.regionMatches(true, 0, "p/", 0, 2)
                    ? "https://f.nooncdn.com/"
                    : "https://f.nooncdn.com/p/";
            return NoonImageUrlNormalizer.normalize(prefix + key + ".jpg");
        }
        return value;
    }

    private String withCurrency(String price, String currency) {
        String compactPrice = htmlParser.compactText(price);
        String compactCurrency = htmlParser.compactText(currency);
        if (!StringUtils.hasText(compactCurrency)
                || compactPrice.toLowerCase(Locale.ROOT).contains(compactCurrency.toLowerCase(Locale.ROOT))) {
            return compactPrice;
        }
        return compactCurrency + " " + compactPrice;
    }

    private String normalizeDescription(String value) {
        String text = htmlParser.compactText(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.contains("<") && text.contains(">")) {
            return htmlParser.compactText(Jsoup.parse(text).text());
        }
        return text;
    }

    private String textAny(JsonNode node, String... fieldNames) {
        if (node == null || !node.isObject()) {
            return "";
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isTextual() || value.isNumber() || value.isBoolean()) {
                String text = htmlParser.compactText(value.asText(""));
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return "";
    }

    private String firstText(String... values) {
        return htmlParser.firstText(values);
    }

    private String meta(Document document, String selector) {
        Element element = document.selectFirst(selector);
        return element == null ? "" : htmlParser.compactText(element.attr("content"));
    }

    private String attr(Element element, String attribute) {
        if (element == null) {
            return "";
        }
        return "text".equals(attribute) ? htmlParser.compactText(element.text()) : htmlParser.compactText(element.attr(attribute));
    }

    private boolean containsIgnoreCase(String value, String target) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT));
    }

    private interface TextConsumer {
        void accept(String value);
    }
}
