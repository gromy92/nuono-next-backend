package com.nuono.next.productselection;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
@Order(20)
public class NoonPublicPageSourceCollector implements ProductSelectionMarketplaceSourceCollector {

    private final ProductSelectionSourceCollectionHtmlParser htmlParser;
    private final NoonPublicProductPayloadParser payloadParser;
    private final int timeoutSeconds;
    private final String catalogBaseUrl;

    public NoonPublicPageSourceCollector(
            ProductSelectionSourceCollectionHtmlParser htmlParser,
            NoonPublicProductPayloadParser payloadParser,
            @Value("${nuono.product-selection.source-collection.noon-public.timeout-seconds:20}") int timeoutSeconds,
            @Value("${nuono.product-selection.source-collection.noon-public.catalog-base-url:https://noon-catalog.noon.partners/_svc/catalog/api/u/}") String catalogBaseUrl
    ) {
        this.htmlParser = htmlParser;
        this.payloadParser = payloadParser;
        this.timeoutSeconds = Math.max(8, timeoutSeconds);
        this.catalogBaseUrl = catalogBaseUrl;
    }

    @Override
    public boolean supports(String platform) {
        return "Noon".equalsIgnoreCase(platform);
    }

    @Override
    public ProductSelectionSourceCollectionResult collect(ProductSelectionSourceCollectionRow row) {
        String pageUrl = htmlParser.normalizeUrl(htmlParser.firstText(row.getPageUrl(), row.getSourceUrl()));
        // Noon 源头选品只读取公网商品页，不能复用店铺 Noon session/cookie。
        try {
            ProductSelectionSourceCollectionResult result = collectStructured(pageUrl);
            if (StringUtils.hasText(result.getSourceTitle()) || !result.getImageUrls().isEmpty()) {
                return result;
            }
        } catch (Exception ignored) {
            // Fall through to the generic public HTML parser.
        }
        return htmlParser.collectUrl(pageUrl, "Noon");
    }

    private ProductSelectionSourceCollectionResult collectStructured(String pageUrl) {
        ProductSelectionSourceCollectionResult catalogResult = collectCatalogStructured(pageUrl);
        if (catalogResult != null) {
            return catalogResult;
        }
        return collectPublicPageStructured(pageUrl);
    }

    private ProductSelectionSourceCollectionResult collectCatalogStructured(String pageUrl) {
        NoonPublicProductSnapshot english = fetchCatalogSnapshotQuietly(pageUrl, "en");
        NoonPublicProductSnapshot arabic = fetchCatalogSnapshotQuietly(pageUrl, "ar");
        if ((english == null || !english.hasStableProductData()) && (arabic == null || !arabic.hasStableProductData())) {
            return null;
        }
        NoonPublicProductSnapshot current = english != null && english.hasStableProductData() ? english : arabic;
        if (english == null || !english.hasStableProductData()) {
            english = current;
        }
        if (arabic == null) {
            arabic = new NoonPublicProductSnapshot();
        }
        return buildResult(pageUrl, english, arabic, current);
    }

    private ProductSelectionSourceCollectionResult collectPublicPageStructured(String pageUrl) {
        String sourceLanguage = NoonPublicProductUrlSupport.language(pageUrl);
        String englishUrl = NoonPublicProductUrlSupport.toLanguageUrl(pageUrl, "en");
        String arabicUrl = NoonPublicProductUrlSupport.toLanguageUrl(pageUrl, "ar");
        NoonPublicProductSnapshot current = fetchSnapshot(pageUrl, sourceLanguage);
        NoonPublicProductSnapshot english = "ar".equals(sourceLanguage)
                ? fetchSnapshotQuietly(englishUrl, "en")
                : current;
        NoonPublicProductSnapshot arabic = "ar".equals(sourceLanguage)
                ? current
                : fetchSnapshotQuietly(arabicUrl, "ar");
        if (english == null || !english.hasStableProductData()) {
            english = current;
        }
        if (arabic == null) {
            arabic = new NoonPublicProductSnapshot();
        }
        return buildResult(pageUrl, english, arabic, current);
    }

    private ProductSelectionSourceCollectionResult buildResult(
            String pageUrl,
            NoonPublicProductSnapshot english,
            NoonPublicProductSnapshot arabic,
            NoonPublicProductSnapshot current
    ) {
        ProductSelectionSourceCollectionResult result = new ProductSelectionSourceCollectionResult();
        result.setSourcePlatform("Noon");
        result.setSourceUrl(pageUrl);
        result.setPageUrl(pageUrl);
        result.setSourceTitle(htmlParser.shrink(firstText(english == null ? "" : english.title, current == null ? "" : current.title), 480));
        result.setSourceTitleAr(htmlParser.shrink(arabic == null ? "" : arabic.title, 480));
        List<String> images = english != null && !english.imageUrls.isEmpty()
                ? english.imageUrls
                : current == null ? List.of() : current.imageUrls;
        result.setImageUrls(images.stream().limit(20).collect(Collectors.toList()));
        result.setSourceImageUrl(result.getImageUrls().isEmpty() ? "" : result.getImageUrls().get(0));
        result.setPriceSummary(firstText(
                english == null ? "" : english.priceSummary,
                current == null ? "" : current.priceSummary,
                arabic == null ? "" : arabic.priceSummary
        ));
        result.setSpecHints(buildSpecHints(english, current));
        result.setSourceDescriptionEn(firstText(english == null ? "" : english.longDescription, current == null ? "" : current.longDescription));
        result.setSourceDescriptionAr(firstText(arabic == null ? "" : arabic.longDescription, ""));
        result.setSourceSellingPointsEn(english == null ? List.of() : english.featureBullets.stream().limit(12).collect(Collectors.toList()));
        result.setSourceSellingPointsAr(arabic == null ? List.of() : arabic.featureBullets.stream().limit(12).collect(Collectors.toList()));
        result.setSelectedText(result.getSourceDescriptionEn());
        result.setSelectedTextAr(result.getSourceDescriptionAr());
        return result;
    }

    private NoonPublicProductSnapshot fetchCatalogSnapshotQuietly(String pageUrl, String language) {
        try {
            return fetchCatalogSnapshot(pageUrl, language);
        } catch (Exception ignored) {
            return new NoonPublicProductSnapshot();
        }
    }

    private NoonPublicProductSnapshot fetchCatalogSnapshot(String pageUrl, String language) {
        String catalogUrl = NoonPublicProductUrlSupport.catalogApiUrl(pageUrl, catalogBaseUrl);
        if (!StringUtils.hasText(catalogUrl)) {
            return new NoonPublicProductSnapshot();
        }
        try {
            String json = Jsoup.connect(catalogUrl)
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    .header("X-Lang", "ar".equalsIgnoreCase(language) ? "ar" : "en")
                    .header("X-Locale", NoonPublicProductUrlSupport.xLocale(language, pageUrl))
                    .header("Accept-Language", NoonPublicProductUrlSupport.acceptLanguage(language))
                    .header("Accept", "application/json,text/plain,*/*")
                    .timeout((int) Duration.ofSeconds(timeoutSeconds).toMillis())
                    .maxBodySize(5 * 1024 * 1024)
                    .followRedirects(true)
                    .execute()
                    .body();
            return payloadParser.parseJson(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Noon catalog 接口采集失败：" + htmlParser.shrink(exception.getMessage(), 180), exception);
        }
    }

    private NoonPublicProductSnapshot fetchSnapshotQuietly(String pageUrl, String language) {
        try {
            return fetchSnapshot(pageUrl, language);
        } catch (Exception ignored) {
            return new NoonPublicProductSnapshot();
        }
    }

    private NoonPublicProductSnapshot fetchSnapshot(String pageUrl, String language) {
        Document document;
        try {
            document = Jsoup.connect(pageUrl)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    .header("Accept-Language", NoonPublicProductUrlSupport.acceptLanguage(language))
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .timeout((int) Duration.ofSeconds(timeoutSeconds).toMillis())
                    .maxBodySize(5 * 1024 * 1024)
                    .followRedirects(true)
                    .get();
        } catch (Exception exception) {
            throw new IllegalStateException("Noon 公网页面采集失败：" + htmlParser.shrink(exception.getMessage(), 180), exception);
        }
        return payloadParser.parse(document, pageUrl);
    }

    private List<String> buildSpecHints(NoonPublicProductSnapshot english, NoonPublicProductSnapshot current) {
        NoonPublicProductSnapshot source = english == null ? current : english;
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        addSpec(hints, "Noon SKU", firstText(source.sku, current == null ? "" : current.sku));
        addSpec(hints, "Brand", firstText(source.brand, current == null ? "" : current.brand));
        if (source != null) {
            hints.addAll(source.specHints);
        }
        if (current != null) {
            hints.addAll(current.specHints);
        }
        addSpec(hints, "Rating", normalizeRating(firstText(source == null ? "" : source.rating, current == null ? "" : current.rating)));
        addSpec(hints, "Review Count", firstText(source == null ? "" : source.reviewCount, current == null ? "" : current.reviewCount));
        return hints.stream().filter(StringUtils::hasText).limit(64).collect(Collectors.toList());
    }

    private void addSpec(Set<String> hints, String label, String value) {
        String text = htmlParser.compactText(value);
        if (StringUtils.hasText(text)) {
            hints.add(label + ": " + text);
        }
    }

    private String normalizeRating(String rating) {
        String text = htmlParser.compactText(rating);
        if (!StringUtils.hasText(text) || text.toLowerCase(Locale.ROOT).contains("out of 5")) {
            return text;
        }
        return text + " out of 5";
    }

    private String firstText(String... values) {
        return htmlParser.firstText(values);
    }
}
