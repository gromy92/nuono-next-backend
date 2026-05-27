package com.nuono.next.productselection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
@Order(10)
public class AmazonExternalCrawlerSourceCollector implements ProductSelectionMarketplaceSourceCollector {

    private static final String AMAZON_CHANNEL = "1";
    private static final String DEFAULT_CRAWLER_URL_TEMPLATE = "http://47.96.124.237/flask/crawl?channel={channel}&url={url}";
    private static final Pattern ASIN_PATH_PATTERN = Pattern.compile("/(?:dp|gp/aw/d|gp/product)/([A-Z0-9]{10})(?:[/?#]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REVIEW_COUNT_PATTERN = Pattern.compile("([0-9][0-9,]*)\\s*(?:ratings?|reviews?)", Pattern.CASE_INSENSITIVE);
    private static final int MAX_STRUCTURED_SPECS = 40;
    private static final int CRAWLER_MAX_ATTEMPTS = 3;

    private final ProductSelectionSourceCollectionHtmlParser htmlParser;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String crawlerUrlTemplate;
    private final int timeoutSeconds;
    private final boolean fallbackToPublicPage;
    private final boolean publicPageSpecsEnabled;

    public AmazonExternalCrawlerSourceCollector(
            ProductSelectionSourceCollectionHtmlParser htmlParser,
            ObjectMapper objectMapper,
            @Value("${nuono.product-selection.source-collection.amazon-crawler.enabled:true}") boolean enabled,
            @Value("${nuono.product-selection.source-collection.amazon-crawler.url:}") String crawlerUrlTemplate,
            @Value("${nuono.product-selection.source-collection.amazon-crawler.timeout-seconds:90}") int timeoutSeconds,
            @Value("${nuono.product-selection.source-collection.amazon-crawler.fallback-to-public-page:false}") boolean fallbackToPublicPage,
            @Value("${nuono.product-selection.source-collection.amazon-crawler.public-page-specs.enabled:true}") boolean publicPageSpecsEnabled
    ) {
        this.htmlParser = htmlParser;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.crawlerUrlTemplate = StringUtils.hasText(crawlerUrlTemplate) ? crawlerUrlTemplate : DEFAULT_CRAWLER_URL_TEMPLATE;
        this.timeoutSeconds = Math.max(10, timeoutSeconds);
        this.fallbackToPublicPage = fallbackToPublicPage;
        this.publicPageSpecsEnabled = publicPageSpecsEnabled;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.min(this.timeoutSeconds, 30))).build();
    }

    @Override
    public boolean supports(String platform) {
        return "Amazon".equalsIgnoreCase(platform);
    }

    @Override
    public ProductSelectionSourceCollectionResult collect(ProductSelectionSourceCollectionRow row) {
        String pageUrl = htmlParser.normalizeUrl(htmlParser.firstText(row.getPageUrl(), row.getSourceUrl()));
        if (!enabled) {
            return htmlParser.collectUrl(pageUrl, "Amazon");
        }
        try {
            return collectFromExternalCrawler(pageUrl);
        } catch (Exception exception) {
            if (fallbackToPublicPage) {
                return htmlParser.collectUrl(pageUrl, "Amazon");
            }
            throw new IllegalStateException("Amazon 外部爬虫采集失败：" + htmlParser.shrink(exception.getMessage(), 180), exception);
        }
    }

    private ProductSelectionSourceCollectionResult collectFromExternalCrawler(String pageUrl) throws Exception {
        JsonNode data = collectCrawlerData(normalizeAmazonEnglishLanguageUrl(pageUrl));
        ProductSelectionSourceCollectionResult result = mapCrawlerData(pageUrl, data);
        if (shouldCollectArabicLanguagePage(pageUrl)) {
            try {
                JsonNode arabicData = collectCrawlerData(normalizeAmazonArabicLanguageUrl(pageUrl));
                result.setSourceTitleAr(htmlParser.shrink(text(arabicData, "title"), 480));
                String arabicDescription = resolveDescription(arabicData);
                result.setSourceDescriptionAr(arabicDescription);
                result.setSourceSellingPointsAr(resolveSellingPoints(arabicData, arabicDescription));
                result.setSelectedTextAr(arabicDescription);
            } catch (Exception ignored) {
                // Arabic page collection is best-effort; AI translation fills the gap later.
            }
        }
        if (!StringUtils.hasText(result.getSourceTitle()) && result.getImageUrls().isEmpty()) {
            throw new IllegalStateException("外部爬虫未返回稳定的 Amazon 标题或图片。");
        }
        if (!StringUtils.hasText(result.getSourceTitle())) {
            result.setSourceTitle("Amazon 源头商品");
        }
        return result;
    }

    private JsonNode collectCrawlerData(String crawlTargetUrl) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= CRAWLER_MAX_ATTEMPTS; attempt++) {
            try {
                return collectCrawlerDataOnce(crawlTargetUrl);
            } catch (Exception exception) {
                lastException = exception;
                if (attempt >= CRAWLER_MAX_ATTEMPTS || !isRetryableCrawlerFailure(exception)) {
                    throw exception;
                }
            }
        }
        throw lastException;
    }

    private JsonNode collectCrawlerDataOnce(String crawlTargetUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(buildCrawlerUrl(crawlTargetUrl)))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("外部爬虫 HTTP " + response.statusCode());
        }
        return resolveCrawlerData(response.body());
    }

    private boolean isRetryableCrawlerFailure(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }
        return message.contains("返回空数据")
                || message.contains("返回空数组")
                || message.contains("HTTP 429")
                || message.contains("HTTP 500")
                || message.contains("HTTP 502")
                || message.contains("HTTP 503")
                || message.contains("HTTP 504");
    }

    private ProductSelectionSourceCollectionResult mapCrawlerData(String pageUrl, JsonNode data) {
        ProductSelectionSourceCollectionResult result = new ProductSelectionSourceCollectionResult();
        result.setSourcePlatform("Amazon");
        result.setSourceUrl(pageUrl);
        result.setPageUrl(pageUrl);
        result.setSourceTitle(htmlParser.shrink(text(data, "title"), 480));
        result.setPriceSummary(resolvePrice(data));
        List<String> images = normalizeImageUrls(data);
        result.setImageUrls(images);
        result.setSourceImageUrl(images.isEmpty() ? "" : images.get(0));
        result.setSpecHints(resolveSpecHints(data, pageUrl));
        String description = resolveDescription(data);
        result.setSourceDescriptionEn(description);
        result.setSourceSellingPointsEn(resolveSellingPoints(data, description));
        result.setSelectedText(description);
        return result;
    }

    private JsonNode resolveCrawlerData(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (root.has("code") && root.path("code").asInt(200) != 200) {
            throw new IllegalStateException(htmlParser.firstText(root.path("msg").asText(), "外部爬虫返回失败。"));
        }
        JsonNode data = root.has("data") ? root.path("data") : root;
        if (data.isTextual()) {
            String text = data.asText();
            if (!StringUtils.hasText(text)) {
                throw new IllegalStateException("外部爬虫返回空数据。");
            }
            return normalizeCrawlerDataNode(objectMapper.readTree(text));
        }
        if (data.isMissingNode() || data.isNull()) {
            throw new IllegalStateException("外部爬虫返回空数据。");
        }
        return normalizeCrawlerDataNode(data);
    }

    private JsonNode normalizeCrawlerDataNode(JsonNode data) {
        if (!data.isArray()) {
            return data;
        }
        if (data.size() == 0) {
            throw new IllegalStateException("外部爬虫返回空数组。");
        }
        for (JsonNode item : data) {
            if (item != null && item.isObject() && (StringUtils.hasText(text(item, "title")) || item.path("images").size() > 0)) {
                return item;
            }
        }
        return data.get(0);
    }

    private String buildCrawlerUrl(String pageUrl) {
        String encodedUrl = URLEncoder.encode(pageUrl, StandardCharsets.UTF_8);
        return crawlerUrlTemplate
                .replace("{channel}", AMAZON_CHANNEL)
                .replace("{url}", encodedUrl)
                .replace("{0}", AMAZON_CHANNEL)
                .replace("{1}", encodedUrl);
    }

    private String normalizeAmazonEnglishLanguageUrl(String pageUrl) {
        String withoutQuery = removeQuery(pageUrl);
        if (shouldCollectArabicLanguagePage(pageUrl)) {
            return withoutQuery + "?language=en_AE";
        }
        return pageUrl;
    }

    private String normalizeAmazonArabicLanguageUrl(String pageUrl) {
        return shouldCollectArabicLanguagePage(pageUrl)
                ? removeQuery(pageUrl) + "?language=ar_AE"
                : pageUrl;
    }

    private boolean shouldCollectArabicLanguagePage(String pageUrl) {
        String lower = pageUrl.toLowerCase(Locale.ROOT);
        return lower.contains("amazon.sa") || lower.contains("amazon.ae");
    }

    private String removeQuery(String pageUrl) {
        try {
            URI uri = URI.create(pageUrl);
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null).toString();
        } catch (Exception ignored) {
            int queryStart = pageUrl.indexOf('?');
            return queryStart >= 0 ? pageUrl.substring(0, queryStart) : pageUrl;
        }
    }

    private String resolvePrice(JsonNode data) {
        String price = text(data, "price");
        String wasPrice = text(data, "wasPrice");
        if (StringUtils.hasText(price) && StringUtils.hasText(wasPrice) && !price.equals(wasPrice)) {
            return price + " / 原价 " + wasPrice;
        }
        return price;
    }

    private List<String> normalizeImageUrls(JsonNode data) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        addTextArray(urls, data.path("images"));
        addTextArray(urls, data.path("imageUrls"));
        addTextArray(urls, data.path("image_urls"));
        return new ArrayList<>(urls);
    }

    private List<String> resolveSpecHints(JsonNode data, String pageUrl) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        hints.addAll(collectPublicPageSpecHints(pageUrl));
        return hints.stream().limit(64).collect(Collectors.toList());
    }

    private List<String> collectPublicPageSpecHints(String pageUrl) {
        if (!publicPageSpecsEnabled) {
            return List.of();
        }
        try {
            String detailUrl = normalizeAmazonMobileDetailUrl(pageUrl);
            Document document = Jsoup.connect(detailUrl)
                    .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1")
                    .header("Accept-Language", acceptLanguage(pageUrl))
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .timeout(Math.min(timeoutSeconds, 20) * 1000)
                    .maxBodySize(4 * 1024 * 1024)
                    .followRedirects(true)
                    .get();
            LinkedHashSet<String> specs = new LinkedHashSet<>();
            addAmazonOverviewSpecs(specs, document);
            addAmazonMarketSpecs(specs, document);
            addAmazonTableSpecs(specs, document);
            return specs.stream().limit(MAX_STRUCTURED_SPECS).collect(Collectors.toList());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void addAmazonOverviewSpecs(LinkedHashSet<String> specs, Document document) {
        Elements rows = document.select("#productOverview_feature_div .a-row[class*=po-], #productOverview_hoc_view_div .a-row[class*=po-]");
        for (Element row : rows) {
            addSpecPair(
                    specs,
                    firstElementText(row.select(".a-span5 .a-text-bold, .a-span5 span").first()),
                    firstElementText(row.select(".a-span7").first())
            );
        }
    }

    private void addAmazonMarketSpecs(LinkedHashSet<String> specs, Document document) {
        String rating = findAmazonRating(document);
        if (StringUtils.hasText(rating)) {
            specs.add("Rating: " + rating);
        }
        String reviewCount = findAmazonReviewCount(document);
        if (StringUtils.hasText(reviewCount)) {
            specs.add("Review Count: " + reviewCount);
        }
    }

    private String findAmazonRating(Document document) {
        for (Element element : document.select("#acrPopover .a-icon-alt, #averageCustomerReviews .a-icon-alt, .mvt-review-stars-mini .a-icon-alt, i[class*=a-icon-star] .a-icon-alt")) {
            String text = htmlParser.compactText(element.text());
            if (text.toLowerCase(Locale.ROOT).contains("out of 5")) {
                return text;
            }
        }
        Matcher matcher = Pattern.compile("([0-9](?:\\.[0-9])?\\s+out of 5 stars)", Pattern.CASE_INSENSITIVE)
                .matcher(document.text());
        return matcher.find() ? matcher.group(1) : "";
    }

    private String findAmazonReviewCount(Document document) {
        String text = firstElementText(document.select("#acrCustomerReviewText").first());
        String normalized = normalizeReviewCount(text);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        for (Element element : document.select("[aria-label]")) {
            normalized = normalizeReviewCount(element.attr("aria-label"));
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        Matcher matcher = REVIEW_COUNT_PATTERN.matcher(document.text());
        return matcher.find() ? matcher.group(1).replace(",", "") : "";
    }

    private String normalizeReviewCount(String value) {
        Matcher matcher = REVIEW_COUNT_PATTERN.matcher(htmlParser.compactText(value));
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).replace(",", "");
    }

    private void addAmazonTableSpecs(LinkedHashSet<String> specs, Document document) {
        Elements rows = document.select("#productSpecificationsTable_feature_div tr, #productDetails_techSpec_section_1 tr, #productDetails_detailBullets_sections1 tr");
        for (Element row : rows) {
            addSpecPair(
                    specs,
                    firstElementText(row.select("th").first()),
                    firstElementText(row.select("td").first())
            );
        }
    }

    private void addSpecPair(LinkedHashSet<String> specs, String label, String value) {
        String normalizedLabel = cleanSpecLabel(label);
        String normalizedValue = cleanSpecValue(value, normalizedLabel);
        if (!StringUtils.hasText(normalizedLabel) || !StringUtils.hasText(normalizedValue)) {
            return;
        }
        if (normalizedLabel.length() > 80 || normalizedValue.length() > 220) {
            return;
        }
        specs.add(normalizedLabel + ": " + normalizedValue);
    }

    private String normalizeAmazonMobileDetailUrl(String pageUrl) {
        String asin = extractAsin(pageUrl);
        if (!StringUtils.hasText(asin)) {
            return pageUrl;
        }
        try {
            URI uri = URI.create(pageUrl);
            String scheme = StringUtils.hasText(uri.getScheme()) ? uri.getScheme() : "https";
            return new URI(scheme, uri.getAuthority(), "/gp/aw/d/" + asin, "th=1&psc=1", null).toString();
        } catch (Exception ignored) {
            return pageUrl;
        }
    }

    private String extractAsin(String pageUrl) {
        Matcher matcher = ASIN_PATH_PATTERN.matcher(pageUrl == null ? "" : pageUrl);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private String acceptLanguage(String pageUrl) {
        String lower = pageUrl == null ? "" : pageUrl.toLowerCase(Locale.ROOT);
        if (lower.contains("amazon.sa") || lower.contains("amazon.ae")) {
            return "en-AE,en;q=0.9,ar;q=0.7";
        }
        return "en-US,en;q=0.9";
    }

    private String firstElementText(Element element) {
        return element == null ? "" : htmlParser.compactText(element.text());
    }

    private String cleanSpecLabel(String value) {
        return htmlParser.compactText(value)
                .replaceAll("\\s+", " ")
                .replaceAll("[:：]+$", "")
                .trim();
    }

    private String cleanSpecValue(String value, String label) {
        String text = htmlParser.compactText(value).replaceAll("\\s+", " ").trim();
        if (StringUtils.hasText(label) && text.startsWith(label)) {
            text = text.substring(label.length()).trim();
        }
        return text;
    }

    private String resolveDescription(JsonNode data) {
        List<String> segments = new ArrayList<>();
        for (String field : List.of("description", "product_pack_info", "feature_attributes")) {
            String value = text(data, field);
            if (StringUtils.hasText(value)) {
                segments.add(value);
            }
        }
        addTextArray(segments, data.path("descriptions"));
        return htmlParser.shrink(String.join("\n", segments), 1800);
    }

    private List<String> resolveSellingPoints(JsonNode data, String description) {
        LinkedHashSet<String> points = new LinkedHashSet<>();
        addSellingPointArray(points, data.path("feature_bullets"));
        addSellingPointArray(points, data.path("features"));
        return points.stream()
                .filter(point -> !sameContent(point, description))
                .limit(8)
                .collect(Collectors.toList());
    }

    private boolean sameContent(String point, String description) {
        String pointText = normalizeContent(point);
        String descriptionText = normalizeContent(description);
        if (pointText.length() < 80 || descriptionText.length() < 80) {
            return false;
        }
        String descriptionPrefix = descriptionText.substring(0, Math.min(descriptionText.length(), 160));
        String pointPrefix = pointText.substring(0, Math.min(pointText.length(), 160));
        return pointText.contains(descriptionPrefix) || descriptionText.contains(pointPrefix);
    }

    private String normalizeContent(String value) {
        return htmlParser.compactText(value)
                .replaceAll("^\\[[^\\]]{2,80}\\]\\s*-?", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u0600-\\u06ff]+", " ")
                .trim();
    }

    private void addSellingPointArray(LinkedHashSet<String> target, JsonNode node) {
        if (!node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            String text = htmlParser.compactText(item.asText(""));
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (text.length() < 4 && !text.trim().startsWith("[")) {
                continue;
            }
            target.add(text);
        }
    }

    private void addTextArray(List<String> target, JsonNode node) {
        if (!node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            String value = item.asText("");
            if (StringUtils.hasText(value)) {
                target.add(htmlParser.compactText(value));
            }
        }
    }

    private void addTextArray(LinkedHashSet<String> target, JsonNode node) {
        if (!node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            addText(target, item.asText(""));
        }
    }

    private void addText(LinkedHashSet<String> target, String value) {
        String text = htmlParser.compactText(value);
        if (StringUtils.hasText(text)) {
            target.add(text);
        }
    }

    private String text(JsonNode data, String fieldName) {
        JsonNode value = data.path(fieldName);
        return value.isMissingNode() || value.isNull() ? "" : htmlParser.compactText(value.asText(""));
    }
}
