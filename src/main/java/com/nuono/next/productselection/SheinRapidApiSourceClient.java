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
import org.springframework.util.StringUtils;

class SheinRapidApiSourceClient {

    static final String DEFAULT_DESCRIPTION_URL = "https://shein-data-api.p.rapidapi.com/product/description/v2?goods_sn={goodsId}&country={country}";
    static final String DEFAULT_DETAILS_URL = "https://shein-scraper-api.p.rapidapi.com/shein/product/details?goods_id={goodsId}&currency={currency}&country={country}&language={language}";

    private final ProductSelectionSourceCollectionHtmlParser htmlParser;
    private final SheinProductStructuredDataParser structuredDataParser;
    private final SheinProductUrlFallback urlFallback;
    private final HttpClient client;
    private final boolean enabled;
    private final String rapidApiKey;
    private final String currency;
    private final int timeoutSeconds;
    private final String descriptionUrlTemplate;
    private final String detailsUrlTemplate;

    SheinRapidApiSourceClient(
            ProductSelectionSourceCollectionHtmlParser htmlParser,
            SheinProductStructuredDataParser structuredDataParser,
            SheinProductUrlFallback urlFallback,
            boolean enabled,
            String rapidApiKey,
            String currency,
            int timeoutSeconds,
            String descriptionUrl,
            String detailsUrl
    ) {
        this.htmlParser = htmlParser;
        this.structuredDataParser = structuredDataParser;
        this.urlFallback = urlFallback;
        this.enabled = enabled;
        this.rapidApiKey = htmlParser.compactText(rapidApiKey);
        this.currency = htmlParser.firstText(currency, "USD");
        this.timeoutSeconds = Math.max(8, timeoutSeconds);
        this.descriptionUrlTemplate = htmlParser.compactText(descriptionUrl);
        this.detailsUrlTemplate = htmlParser.compactText(detailsUrl);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(this.timeoutSeconds))
                .build();
    }

    ProductSelectionSourceCollectionResult collect(String pageUrl) {
        if (!enabled || !StringUtils.hasText(rapidApiKey)) {
            return null;
        }
        String goodsId = urlFallback.productId(pageUrl);
        if (!StringUtils.hasText(goodsId)) {
            return null;
        }
        String country = urlFallback.country(pageUrl);
        SheinProductSnapshot englishSnapshot = new SheinProductSnapshot();
        SheinProductSnapshot arabicSnapshot = new SheinProductSnapshot();
        boolean collectedDetails = collectInto(detailsUrlTemplate, goodsId, country, "en", englishSnapshot);
        if (detailsUrlTemplate.contains("{language}")) {
            collectedDetails = collectInto(detailsUrlTemplate, goodsId, country, "ar", arabicSnapshot) || collectedDetails;
        }
        if (collectedDetails && hasUsefulSnapshot(englishSnapshot, arabicSnapshot)) {
            return mapSnapshots(pageUrl, goodsId, englishSnapshot, arabicSnapshot);
        }
        SheinProductSnapshot fallbackSnapshot = new SheinProductSnapshot();
        boolean collectedDescription = collectInto(descriptionUrlTemplate, goodsId, country, urlFallback.language(pageUrl), fallbackSnapshot);
        return collectedDescription && hasUsefulSnapshot(fallbackSnapshot, new SheinProductSnapshot())
                ? mapSnapshots(pageUrl, goodsId, fallbackSnapshot, new SheinProductSnapshot())
                : null;
    }

    private boolean collectInto(
            String template,
            String goodsId,
            String country,
            String language,
            SheinProductSnapshot snapshot
    ) {
        if (!StringUtils.hasText(template)) {
            return false;
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
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("SHEIN RapidAPI HTTP " + response.statusCode());
            }
            structuredDataParser.parseJsonPayload(response.body(), snapshot);
            return hasUsefulSnapshot(snapshot, new SheinProductSnapshot());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SHEIN RapidAPI interrupted", exception);
        } catch (Exception exception) {
            // Keep the public-page and URL fallback path available when the third-party API is unavailable.
            return false;
        }
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
}
