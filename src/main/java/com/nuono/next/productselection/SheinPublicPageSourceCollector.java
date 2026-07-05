package com.nuono.next.productselection;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
@Order(30)
public class SheinPublicPageSourceCollector implements ProductSelectionMarketplaceSourceCollector {

    private final ProductSelectionSourceCollectionHtmlParser htmlParser;
    private final SheinProductStructuredDataParser structuredDataParser;
    private final SheinProductUrlFallback urlFallback;
    private final SheinRapidApiSourceClient rapidApiSourceClient;
    private final int timeoutSeconds;

    @Autowired
    public SheinPublicPageSourceCollector(
            ProductSelectionSourceCollectionHtmlParser htmlParser,
            ObjectMapper objectMapper,
            @Value("${nuono.product-selection.source-collection.shein-public.timeout-seconds:20}") int timeoutSeconds,
            @Value("${nuono.product-selection.source-collection.shein-rapidapi.enabled:true}") boolean rapidApiEnabled,
            @Value("${nuono.product-selection.source-collection.shein-rapidapi.key:}") String rapidApiKey,
            @Value("${nuono.product-selection.source-collection.shein-rapidapi.currency:USD}") String rapidApiCurrency,
            @Value("${nuono.product-selection.source-collection.shein-rapidapi.timeout-seconds:30}") int rapidApiTimeoutSeconds,
            @Value("${nuono.product-selection.source-collection.shein-rapidapi.description-url:}") String rapidApiDescriptionUrl,
            @Value("${nuono.product-selection.source-collection.shein-rapidapi.details-url:}") String rapidApiDetailsUrl
    ) {
        this.htmlParser = htmlParser;
        this.structuredDataParser = new SheinProductStructuredDataParser(htmlParser, objectMapper);
        this.urlFallback = new SheinProductUrlFallback(htmlParser);
        this.rapidApiSourceClient = new SheinRapidApiSourceClient(
                htmlParser,
                structuredDataParser,
                urlFallback,
                rapidApiEnabled,
                rapidApiKey,
                rapidApiCurrency,
                rapidApiTimeoutSeconds,
                htmlParser.firstText(rapidApiDescriptionUrl, SheinRapidApiSourceClient.DEFAULT_DESCRIPTION_URL),
                htmlParser.firstText(rapidApiDetailsUrl, SheinRapidApiSourceClient.DEFAULT_DETAILS_URL)
        );
        this.timeoutSeconds = Math.max(8, timeoutSeconds);
    }

    SheinPublicPageSourceCollector(
            ProductSelectionSourceCollectionHtmlParser htmlParser,
            ObjectMapper objectMapper,
            int timeoutSeconds
    ) {
        this(htmlParser, objectMapper, timeoutSeconds, false, "", "USD", 30, "", "");
    }

    @Override
    public boolean supports(String platform) {
        return "SHEIN".equalsIgnoreCase(platform);
    }

    @Override
    public ProductSelectionSourceCollectionResult collect(ProductSelectionSourceCollectionRow row) {
        String pageUrl = htmlParser.normalizeUrl(htmlParser.firstText(row.getPageUrl(), row.getSourceUrl()));
        ProductSelectionSourceCollectionResult rapidApiResult = rapidApiSourceClient.collect(pageUrl);
        if (rapidApiResult != null) {
            return rapidApiResult;
        }
        Document document;
        try {
            document = fetchDocument(pageUrl);
        } catch (RuntimeException exception) {
            ProductSelectionSourceCollectionResult fallback = urlFallback.fromUrl(pageUrl, exception.getMessage());
            if (fallback != null) {
                return fallback;
            }
            throw exception;
        }
        ProductSelectionSourceCollectionResult result = baseResult(document, pageUrl);
        SheinProductSnapshot snapshot = new SheinProductSnapshot();
        snapshot.ingest(result);
        structuredDataParser.parse(document, snapshot);
        applySnapshot(result, snapshot, pageUrl);
        if (!StringUtils.hasText(result.getSourceTitle()) && result.getImageUrls().isEmpty()) {
            throw new IllegalStateException("SHEIN 页面已打开，但没有识别到稳定的商品标题或图片。");
        }
        if (!StringUtils.hasText(result.getSourceTitle())) {
            result.setSourceTitle("SHEIN 源头商品");
        }
        return result;
    }

    private Document fetchDocument(String pageUrl) {
        try {
            return Jsoup.connect(pageUrl)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    .header("Accept-Language", "en-SA,en;q=0.9,ar;q=0.8")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .timeout((int) Duration.ofSeconds(timeoutSeconds).toMillis())
                    .maxBodySize(5 * 1024 * 1024)
                    .followRedirects(true)
                    .get();
        } catch (Exception exception) {
            throw new IllegalStateException("SHEIN 公网页面采集失败：" + htmlParser.shrink(exception.getMessage(), 180), exception);
        }
    }

    private ProductSelectionSourceCollectionResult baseResult(Document document, String pageUrl) {
        try {
            return htmlParser.collectHtml(document.outerHtml(), pageUrl, "SHEIN");
        } catch (RuntimeException ignored) {
            ProductSelectionSourceCollectionResult result = new ProductSelectionSourceCollectionResult();
            result.setSourcePlatform("SHEIN");
            result.setSourceUrl(pageUrl);
            result.setPageUrl(pageUrl);
            return result;
        }
    }

    private void applySnapshot(
            ProductSelectionSourceCollectionResult result,
            SheinProductSnapshot snapshot,
            String pageUrl
    ) {
        result.setSourcePlatform("SHEIN");
        result.setSourceUrl(pageUrl);
        result.setPageUrl(pageUrl);
        String rawTitle = htmlParser.shrink(snapshot.title(), 480);
        String urlTitle = htmlParser.shrink(urlFallback.title(pageUrl), 480);
        result.setSourceTitle(firstNonArabicText(rawTitle, urlTitle));
        result.setSourceTitleAr(arabicText(rawTitle));
        result.setPriceSummary(snapshot.price());
        List<String> images = snapshot.imageList();
        result.setImageUrls(images);
        result.setSourceImageUrl(images.isEmpty() ? "" : images.get(0));
        result.setSpecHints(snapshot.specList());
        String rawDescription = snapshot.description();
        String englishDescription = nonArabicText(rawDescription);
        String arabicDescription = arabicText(rawDescription);
        result.setSourceDescriptionEn(englishDescription);
        result.setSourceDescriptionAr(arabicDescription);
        result.setSourceSellingPointsEn(nonArabicList(snapshot.sellingPointList()));
        result.setSourceSellingPointsAr(arabicList(snapshot.sellingPointList()));
        result.setSelectedText(englishDescription);
        result.setSelectedTextAr(arabicDescription);
    }

    private String firstNonArabicText(String... values) {
        for (String value : values) {
            String text = nonArabicText(value);
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

    private List<String> nonArabicList(List<String> values) {
        return values.stream()
                .filter(item -> !containsArabic(item))
                .collect(Collectors.toList());
    }

    private List<String> arabicList(List<String> values) {
        return values.stream()
                .filter(this::containsArabic)
                .collect(Collectors.toList());
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
