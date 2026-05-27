package com.nuono.next.productselection;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
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
    private final SourceCollectionProxyProvider proxyProvider;
    private final SourceCollectionPageFetchClient pageFetchClient;
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
            @Value("${nuono.product-selection.source-collection.shein-rapidapi.details-url:}") String rapidApiDetailsUrl,
            SourceCollectionProxyProvider proxyProvider,
            SourceCollectionPageFetchClient pageFetchClient
    ) {
        this.htmlParser = htmlParser;
        this.structuredDataParser = new SheinProductStructuredDataParser(htmlParser, objectMapper);
        this.urlFallback = new SheinProductUrlFallback(htmlParser);
        this.proxyProvider = proxyProvider;
        this.pageFetchClient = pageFetchClient;
        this.rapidApiSourceClient = new SheinRapidApiSourceClient(
                htmlParser,
                structuredDataParser,
                urlFallback,
                rapidApiEnabled,
                rapidApiKey,
                rapidApiCurrency,
                rapidApiTimeoutSeconds,
                htmlParser.firstText(rapidApiDescriptionUrl, SheinRapidApiSourceClient.DEFAULT_DESCRIPTION_URL),
                htmlParser.firstText(rapidApiDetailsUrl, SheinRapidApiSourceClient.DEFAULT_DETAILS_URL),
                proxyProvider
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

    SheinPublicPageSourceCollector(
            ProductSelectionSourceCollectionHtmlParser htmlParser,
            ObjectMapper objectMapper,
            int timeoutSeconds,
            boolean rapidApiEnabled,
            String rapidApiKey,
            String rapidApiCurrency,
            int rapidApiTimeoutSeconds,
            String rapidApiDescriptionUrl,
            String rapidApiDetailsUrl
    ) {
        this(
                htmlParser,
                objectMapper,
                timeoutSeconds,
                rapidApiEnabled,
                rapidApiKey,
                rapidApiCurrency,
                rapidApiTimeoutSeconds,
                rapidApiDescriptionUrl,
                rapidApiDetailsUrl,
                SourceCollectionProxyProvider.disabled(),
                SourceCollectionPageFetchClient.disabled()
        );
    }

    SheinPublicPageSourceCollector(
            ProductSelectionSourceCollectionHtmlParser htmlParser,
            ObjectMapper objectMapper,
            int timeoutSeconds,
            boolean rapidApiEnabled,
            String rapidApiKey,
            String rapidApiCurrency,
            int rapidApiTimeoutSeconds,
            String rapidApiDescriptionUrl,
            String rapidApiDetailsUrl,
            SourceCollectionProxyProvider proxyProvider
    ) {
        this(
                htmlParser,
                objectMapper,
                timeoutSeconds,
                rapidApiEnabled,
                rapidApiKey,
                rapidApiCurrency,
                rapidApiTimeoutSeconds,
                rapidApiDescriptionUrl,
                rapidApiDetailsUrl,
                proxyProvider,
                SourceCollectionPageFetchClient.disabled()
        );
    }

    @Override
    public boolean supports(String platform) {
        return "SHEIN".equalsIgnoreCase(platform);
    }

    @Override
    public ProductSelectionSourceCollectionResult collect(ProductSelectionSourceCollectionRow row) {
        String pageUrl = htmlParser.normalizeUrl(htmlParser.firstText(row.getPageUrl(), row.getSourceUrl()));
        SheinRapidApiSourceClient.CollectionAttempt rapidApiAttempt = rapidApiSourceClient.collectWithDiagnostics(pageUrl);
        ProductSelectionSourceCollectionResult rapidApiResult = rapidApiAttempt.result();
        if (rapidApiResult != null) {
            return rapidApiResult;
        }
        Document document;
        try {
            document = fetchDocument(pageUrl);
            rejectBlockedDocument(document);
        } catch (RuntimeException exception) {
            ProductSelectionSourceCollectionResult fallback = urlFallback.fromUrl(pageUrl, exception.getMessage());
            if (fallback != null) {
                throw new IllegalStateException(
                        "SHEIN 完整采集失败："
                                + failureDetail(rapidApiAttempt.failureSummary(), exception.getMessage())
                                + "；URL 兜底只能解析商品 ID/标题，不能作为采集成功。",
                        exception
                );
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

    private String failureDetail(String rapidApiFailure, String pageFailure) {
        String rapidApi = StringUtils.hasText(rapidApiFailure) ? rapidApiFailure : "RapidAPI: 未配置或不可用";
        String page = StringUtils.hasText(pageFailure) ? "页面: " + htmlParser.shrink(pageFailure, 180) : "页面: 公网页面不可访问";
        return rapidApi + "；" + page;
    }

    private void rejectBlockedDocument(Document document) {
        String html = document == null ? "" : document.outerHtml();
        if (!StringUtils.hasText(html)) {
            throw new IllegalStateException("SHEIN 公网页面采集失败：页面为空。");
        }
        String normalized = html.toLowerCase();
        boolean challenge = normalized.contains("/risk/challenge")
                || normalized.contains("captcha_type=")
                || normalized.contains("risk-id=")
                || normalized.contains("system updating");
        boolean genericLanding = document.title() != null
                && document.title().toLowerCase().contains("shop online fashion")
                && !hasStableProductPayload(normalized);
        if (challenge || genericLanding) {
            throw new IllegalStateException("SHEIN 公网页面仍被风控拦截，未采集到商品详情。");
        }
    }

    private boolean hasStableProductPayload(String normalizedHtml) {
        return normalizedHtml.contains("productintrodata")
                || normalizedHtml.contains("goods_name")
                || normalizedHtml.contains("retailprice")
                || normalizedHtml.contains("saleprice")
                || normalizedHtml.contains("\"@type\":\"product\"")
                || normalizedHtml.contains("\"@type\": \"product\"");
    }

    private Document fetchDocument(String pageUrl) {
        Document gatewayDocument = pageFetchClient.fetchDocument(pageUrl).orElse(null);
        if (gatewayDocument != null) {
            return gatewayDocument;
        }
        return fetchPublicDocument(pageUrl);
    }

    private Document fetchPublicDocument(String pageUrl) {
        try {
            return proxyProvider.applyTo(Jsoup.connect(pageUrl))
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
        result.setSourceTitle(htmlParser.shrink(snapshot.title(), 480));
        result.setPriceSummary(snapshot.price());
        List<String> images = snapshot.imageList();
        result.setImageUrls(images);
        result.setSourceImageUrl(images.isEmpty() ? "" : images.get(0));
        result.setSpecHints(snapshot.specList());
        result.setSourceDescriptionEn(snapshot.description());
        result.setSourceSellingPointsEn(snapshot.sellingPointList());
        result.setSelectedText(snapshot.description());
    }
}
