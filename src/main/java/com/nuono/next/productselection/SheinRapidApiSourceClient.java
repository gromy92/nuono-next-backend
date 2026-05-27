package com.nuono.next.productselection;

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
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

class SheinRapidApiSourceClient {

    static final String DEFAULT_DESCRIPTION_URL = "https://shein-data-api.p.rapidapi.com/product/description/v2?goods_sn={goodsId}&country={country}";
    static final String DEFAULT_DETAILS_URL = "https://shein-scraper-api.p.rapidapi.com/shein/product/details?goods_id={goodsId}&currency={currency}&country={country}&language={language}";

    private final ProductSelectionSourceCollectionHtmlParser htmlParser;
    private final SheinProductStructuredDataParser structuredDataParser;
    private final SheinProductUrlFallback urlFallback;
    private final SourceCollectionProxyProvider proxyProvider;
    private final HttpClient directClient;
    private final boolean enabled;
    private final String rapidApiKey;
    private final String currency;
    private final int timeoutSeconds;
    private final String descriptionUrlTemplate;
    private final String detailsUrlTemplate;
    private static final Pattern JSON_MESSAGE_PATTERN = Pattern.compile(
            "\"(?:message|msg|error)\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    SheinRapidApiSourceClient(
            ProductSelectionSourceCollectionHtmlParser htmlParser,
            SheinProductStructuredDataParser structuredDataParser,
            SheinProductUrlFallback urlFallback,
            boolean enabled,
            String rapidApiKey,
            String currency,
            int timeoutSeconds,
            String descriptionUrl,
            String detailsUrl,
            SourceCollectionProxyProvider proxyProvider
    ) {
        this.htmlParser = htmlParser;
        this.structuredDataParser = structuredDataParser;
        this.urlFallback = urlFallback;
        this.proxyProvider = proxyProvider;
        this.enabled = enabled;
        this.rapidApiKey = htmlParser.compactText(rapidApiKey);
        this.currency = htmlParser.firstText(currency, "USD");
        this.timeoutSeconds = Math.max(8, timeoutSeconds);
        this.descriptionUrlTemplate = htmlParser.compactText(descriptionUrl);
        this.detailsUrlTemplate = htmlParser.compactText(detailsUrl);
        this.directClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(this.timeoutSeconds))
                .build();
    }

    ProductSelectionSourceCollectionResult collect(String pageUrl) {
        return collectWithDiagnostics(pageUrl).result();
    }

    CollectionAttempt collectWithDiagnostics(String pageUrl) {
        if (!enabled || !StringUtils.hasText(rapidApiKey)) {
            return CollectionAttempt.failure(!enabled ? "RapidAPI: 未启用" : "RapidAPI: 未配置 key");
        }
        String goodsId = urlFallback.productId(pageUrl);
        if (!StringUtils.hasText(goodsId)) {
            return CollectionAttempt.failure("RapidAPI: 未识别 SHEIN 商品 ID");
        }
        String country = urlFallback.country(pageUrl);
        List<String> failures = new ArrayList<>();
        SheinProductSnapshot englishSnapshot = new SheinProductSnapshot();
        SheinProductSnapshot arabicSnapshot = new SheinProductSnapshot();
        EndpointAttempt collectedDetails = collectInto(detailsUrlTemplate, goodsId, country, "en", englishSnapshot);
        failures.addAll(collectedDetails.failureReasons());
        if (detailsUrlTemplate.contains("{language}")) {
            EndpointAttempt arabicDetails = collectInto(detailsUrlTemplate, goodsId, country, "ar", arabicSnapshot);
            failures.addAll(arabicDetails.failureReasons());
            collectedDetails = EndpointAttempt.merge(collectedDetails, arabicDetails);
        }
        if (collectedDetails.collected() && hasUsefulSnapshot(englishSnapshot, arabicSnapshot)) {
            return CollectionAttempt.success(mapSnapshots(pageUrl, goodsId, englishSnapshot, arabicSnapshot));
        }
        SheinProductSnapshot fallbackSnapshot = new SheinProductSnapshot();
        EndpointAttempt collectedDescription = collectInto(descriptionUrlTemplate, goodsId, country, urlFallback.language(pageUrl), fallbackSnapshot);
        failures.addAll(collectedDescription.failureReasons());
        if (collectedDescription.collected() && hasUsefulSnapshot(fallbackSnapshot, new SheinProductSnapshot())) {
            return CollectionAttempt.success(mapSnapshots(pageUrl, goodsId, fallbackSnapshot, new SheinProductSnapshot()));
        }
        if (failures.isEmpty()) {
            failures.add("RapidAPI: 未返回可用商品字段");
        }
        return CollectionAttempt.failure(failures);
    }

    private EndpointAttempt collectInto(
            String template,
            String goodsId,
            String country,
            String language,
            SheinProductSnapshot snapshot
    ) {
        if (!StringUtils.hasText(template)) {
            return EndpointAttempt.failure("RapidAPI: 未配置接口模板");
        }
        String url = renderUrl(template, goodsId, country, language);
        URI uri = URI.create(url);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Accept", "application/json")
                .header("x-rapidapi-host", uri.getHost())
                .header("x-rapidapi-key", rapidApiKey)
                .build();
        try {
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return EndpointAttempt.failure(
                        "RapidAPI: HTTP " + response.statusCode() + safeResponseMessage(response.body())
                );
            }
            structuredDataParser.parseJsonPayload(response.body(), snapshot);
            return hasUsefulSnapshot(snapshot, new SheinProductSnapshot())
                    ? EndpointAttempt.success()
                    : EndpointAttempt.failure("RapidAPI: " + safeEndpointName(uri) + " 未返回可用商品字段");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SHEIN RapidAPI interrupted", exception);
        } catch (Exception exception) {
            // Keep the public-page and URL fallback path available when the third-party API is unavailable.
            return EndpointAttempt.failure("RapidAPI: " + safeEndpointName(uri) + " 请求失败 " + shrink(exception.getMessage(), 100));
        }
    }

    private String safeResponseMessage(String body) {
        String message = extractJsonMessage(body);
        return StringUtils.hasText(message) ? " " + shrink(message, 140) : "";
    }

    private String extractJsonMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        Matcher matcher = JSON_MESSAGE_PATTERN.matcher(body);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String safeEndpointName(URI uri) {
        String host = uri.getHost();
        String path = uri.getPath();
        return firstText(host, "") + firstText(path, "");
    }

    private String shrink(String value, int maxLength) {
        String text = htmlParser.compactText(value);
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)) + "...";
    }

    private HttpClient httpClient() {
        return proxyProvider == null
                ? directClient
                : proxyProvider.httpClient(Duration.ofSeconds(timeoutSeconds));
    }

    private ProductSelectionSourceCollectionResult mapSnapshots(
            String pageUrl,
            String goodsId,
            SheinProductSnapshot englishSnapshot,
            SheinProductSnapshot arabicSnapshot
    ) {
        List<String> images = mergeLists(englishSnapshot.imageList(), arabicSnapshot.imageList());
        List<String> specs = mergeLists(englishSnapshot.specList(), arabicSnapshot.specList());
        if (specs.stream().noneMatch(item -> item.toLowerCase().startsWith("shein product id:"))) {
            specs.add(0, "SHEIN Product ID: " + goodsId);
        }
        String englishTitle = firstText(
                nonArabicText(englishSnapshot.title()),
                nonArabicText(arabicSnapshot.title()),
                urlFallback.title(pageUrl)
        );
        String arabicTitle = firstText(arabicText(arabicSnapshot.title()), arabicText(englishSnapshot.title()));
        String englishDescription = firstText(
                nonArabicText(englishSnapshot.description()),
                nonArabicText(arabicSnapshot.description())
        );
        String arabicDescription = firstText(
                arabicText(arabicSnapshot.description()),
                arabicText(englishSnapshot.description())
        );
        ProductSelectionSourceCollectionResult result = new ProductSelectionSourceCollectionResult();
        result.setSourcePlatform("SHEIN");
        result.setSourceUrl(pageUrl);
        result.setPageUrl(pageUrl);
        result.setSourceTitle(htmlParser.shrink(englishTitle, 480));
        result.setSourceTitleAr(htmlParser.shrink(arabicTitle, 480));
        result.setPriceSummary(firstText(englishSnapshot.price(), arabicSnapshot.price()));
        result.setImageUrls(images);
        result.setSourceImageUrl(images.isEmpty() ? "" : images.get(0));
        result.setSpecHints(specs);
        result.setSourceDescriptionEn(englishDescription);
        result.setSourceDescriptionAr(arabicDescription);
        result.setSourceSellingPointsEn(nonArabicList(englishSnapshot.sellingPointList(), arabicSnapshot.sellingPointList()));
        result.setSourceSellingPointsAr(arabicList(arabicSnapshot.sellingPointList(), englishSnapshot.sellingPointList()));
        result.setSelectedText(englishDescription);
        result.setSelectedTextAr(arabicDescription);
        return result;
    }

    private String renderUrl(String template, String goodsId, String country, String language) {
        return template
                .replace("{goodsId}", encode(goodsId))
                .replace("{country}", encode(country))
                .replace("{language}", encode(language))
                .replace("{currency}", encode(currency));
    }

    private String encode(String value) {
        return URLEncoder.encode(htmlParser.firstText(value, ""), StandardCharsets.UTF_8);
    }

    private boolean hasUsefulSnapshot(SheinProductSnapshot first, SheinProductSnapshot second) {
        return StringUtils.hasText(first.title())
                || StringUtils.hasText(second.title())
                || StringUtils.hasText(first.price())
                || StringUtils.hasText(second.price())
                || first.imageCount() > 0
                || second.imageCount() > 0
                || !first.specList().isEmpty()
                || !second.specList().isEmpty()
                || !first.sellingPointList().isEmpty()
                || !second.sellingPointList().isEmpty()
                || StringUtils.hasText(first.description())
                || StringUtils.hasText(second.description());
    }

    private List<String> mergeLists(List<String> first, List<String> second) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (first != null) {
            first.stream().filter(StringUtils::hasText).forEach(merged::add);
        }
        if (second != null) {
            second.stream().filter(StringUtils::hasText).forEach(merged::add);
        }
        return new ArrayList<>(merged);
    }

    private List<String> nonArabicList(List<String> first, List<String> second) {
        return mergeLists(first, second).stream()
                .filter(item -> !containsArabic(item))
                .limit(12)
                .collect(Collectors.toList());
    }

    private List<String> arabicList(List<String> first, List<String> second) {
        return mergeLists(first, second).stream()
                .filter(this::containsArabic)
                .limit(12)
                .collect(Collectors.toList());
    }

    private String firstText(String... values) {
        for (String value : values) {
            String text = htmlParser.compactText(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private String nonArabicText(String value) {
        return containsArabic(value) ? "" : value;
    }

    private String arabicText(String value) {
        return containsArabic(value) ? value : "";
    }

    private boolean containsArabic(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current >= '\u0600' && current <= '\u06FF') {
                return true;
            }
        }
        return false;
    }

    static final class CollectionAttempt {

        private final ProductSelectionSourceCollectionResult result;
        private final List<String> failureReasons;

        private CollectionAttempt(ProductSelectionSourceCollectionResult result, List<String> failureReasons) {
            this.result = result;
            this.failureReasons = failureReasons == null ? List.of() : failureReasons;
        }

        static CollectionAttempt success(ProductSelectionSourceCollectionResult result) {
            return new CollectionAttempt(result, List.of());
        }

        static CollectionAttempt failure(String failureReason) {
            return new CollectionAttempt(null, StringUtils.hasText(failureReason) ? List.of(failureReason) : List.of());
        }

        static CollectionAttempt failure(List<String> failureReasons) {
            return new CollectionAttempt(null, deduplicate(failureReasons));
        }

        ProductSelectionSourceCollectionResult result() {
            return result;
        }

        String failureSummary() {
            return String.join("；", failureReasons);
        }

        private static List<String> deduplicate(List<String> values) {
            LinkedHashSet<String> deduped = new LinkedHashSet<>();
            if (values != null) {
                values.stream().filter(StringUtils::hasText).forEach(deduped::add);
            }
            return new ArrayList<>(deduped);
        }
    }

    private static final class EndpointAttempt {

        private final boolean collected;
        private final List<String> failureReasons;

        private EndpointAttempt(boolean collected, List<String> failureReasons) {
            this.collected = collected;
            this.failureReasons = failureReasons == null ? List.of() : failureReasons;
        }

        static EndpointAttempt success() {
            return new EndpointAttempt(true, List.of());
        }

        static EndpointAttempt failure(String failureReason) {
            return new EndpointAttempt(false, StringUtils.hasText(failureReason) ? List.of(failureReason) : List.of());
        }

        static EndpointAttempt merge(EndpointAttempt first, EndpointAttempt second) {
            List<String> failures = new ArrayList<>();
            if (first != null) {
                failures.addAll(first.failureReasons);
            }
            if (second != null) {
                failures.addAll(second.failureReasons);
            }
            return new EndpointAttempt(
                    (first != null && first.collected) || (second != null && second.collected),
                    CollectionAttempt.deduplicate(failures)
            );
        }

        boolean collected() {
            return collected;
        }

        List<String> failureReasons() {
            return failureReasons;
        }
    }
}
