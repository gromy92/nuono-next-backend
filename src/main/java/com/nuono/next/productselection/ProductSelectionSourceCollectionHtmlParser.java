package com.nuono.next.productselection;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class ProductSelectionSourceCollectionHtmlParser {

    private static final int MAX_IMAGES = 20;
    private static final int MAX_SPEC_HINTS = 10;
    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "(?:(?:SAR|AED|EGP|USD|CNY|RMB|\\$|£|¥|￥|元|ر\\.س|د\\.إ)\\s*\\d[\\d,.]*(?:\\s*[-~–]\\s*\\d[\\d,.]*)?)|(?:\\d[\\d,.]*\\s*(?:SAR|AED|EGP|USD|CNY|RMB|元|ر\\.س|د\\.إ))",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MOQ_PATTERN = Pattern.compile(
            "(?:minimum\\s+order|moq|起订|起批|最小起订)\\s*[:：]?\\s*\\d+\\s*(?:pieces?|pcs|件|个|套)?|\\d+\\s*(?:pieces?|pcs|件|个|套)\\s*(?:min|起)",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<String> SPEC_KEYWORDS = List.of(
            "material", "材质", "color", "colour", "颜色", "size", "尺寸", "dimension", "capacity",
            "battery", "rechargeable", "usb", "power", "ceramic", "metal", "plastic", "glass",
            "gift", "package", "packaging", "包装", "warranty", "brand", "model"
    );
    private static final List<String> SHIPPING_KEYWORDS = List.of(
            "ships from", "shipping", "fulfilled", "sold by", "delivery", "发货", "配送", "库存", "仓"
    );

    public ProductSelectionSourceCollectionResult collectUrl(String pageUrl, String platform) {
        String normalizedUrl = normalizeUrl(pageUrl);
        String normalizedPlatform = inferPlatform(firstText(platform, normalizedUrl));
        try {
            Document document = Jsoup.connect(normalizedUrl)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    .header("Accept-Language", acceptLanguage(normalizedPlatform))
                    .timeout(12000)
                    .followRedirects(true)
                    .get();
            return collectDocument(document, normalizedUrl, normalizedPlatform);
        } catch (Exception exception) {
            throw new IllegalStateException("源头页面采集失败：" + shrink(exception.getMessage(), 180), exception);
        }
    }

    public ProductSelectionSourceCollectionResult collectHtml(String html, String pageUrl, String platform) {
        return collectDocument(Jsoup.parse(html, pageUrl), normalizeUrl(pageUrl), inferPlatform(firstText(platform, pageUrl)));
    }

    public String normalizeUrl(String rawUrl) {
        String value = compactText(rawUrl);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.startsWith("//")) {
            value = "https:" + value;
        }
        try {
            URI uri = new URI(value);
            if (!StringUtils.hasText(uri.getScheme())) {
                throw new IllegalArgumentException("商品页链接必须包含 http 或 https。");
            }
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null).toString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("商品页链接格式不正确。");
        }
    }

    public String inferPlatform(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (normalized.contains("noon.")) {
            return "Noon";
        }
        if (normalized.contains("amazon.")) {
            return "Amazon";
        }
        if (normalized.contains("shein.")) {
            return "SHEIN";
        }
        if (normalized.contains("1688.")) {
            return "1688";
        }
        return compactText(value);
    }

    public String compactText(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    public String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return compactText(value);
            }
        }
        return "";
    }

    public String shrink(String value, int maxLength) {
        String text = compactText(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private ProductSelectionSourceCollectionResult collectDocument(Document document, String pageUrl, String platform) {
        ProductSelectionSourceCollectionResult result = new ProductSelectionSourceCollectionResult();
        result.setSourcePlatform(platform);
        result.setSourceUrl(pageUrl);
        result.setPageUrl(pageUrl);
        result.setSourceTitle(resolveTitle(document));

        List<String> imageUrls = resolveImages(document);
        result.setImageUrls(imageUrls);
        result.setSourceImageUrl(imageUrls.isEmpty() ? "" : imageUrls.get(0));
        result.setPriceSummary(resolvePrice(document));
        result.setMoqHint(resolveMoq(document));
        result.setShippingFrom(resolveShipping(document));
        result.setSpecHints(resolveSpecHints(document));
        String sourceDescription = resolveVisibleSummary(document);
        result.setSourceDescriptionEn(sourceDescription);
        result.setSourceSellingPointsEn(resolveSellingPoints(document));
        result.setSelectedText(sourceDescription);

        if (!StringUtils.hasText(result.getSourceTitle()) && imageUrls.isEmpty()) {
            throw new IllegalStateException("页面已打开，但没有识别到稳定的商品标题或图片。");
        }
        if (!StringUtils.hasText(result.getSourceTitle())) {
            result.setSourceTitle(platform + " 源头商品");
        }
        return result;
    }

    private String resolveTitle(Document document) {
        String title = firstText(
                meta(document, "meta[property=og:title]"),
                meta(document, "meta[name=twitter:title]"),
                meta(document, "meta[name=title]"),
                attr(document.selectFirst("h1"), "text"),
                document.title()
        );
        return cleanTitle(title);
    }

    private List<String> resolveImages(Document document) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        addImage(urls, meta(document, "meta[property=og:image]"));
        addImage(urls, meta(document, "meta[property=og:image:secure_url]"));
        addImage(urls, meta(document, "meta[name=twitter:image]"));
        Element imageLink = document.selectFirst("link[rel=image_src]");
        if (imageLink != null) {
            addImage(urls, imageLink.absUrl("href"));
        }
        for (Element image : document.select("img[src], img[data-src], img[data-lazy-src], img[data-original], source[srcset]")) {
            if (urls.size() >= MAX_IMAGES) {
                break;
            }
            addImage(urls, resolveImageAttribute(image));
        }
        return new ArrayList<>(urls);
    }

    private String resolveImageAttribute(Element element) {
        String srcset = element.attr("srcset");
        if (StringUtils.hasText(srcset)) {
            String first = srcset.split(",")[0].trim().split("\\s+")[0];
            if (StringUtils.hasText(first)) {
                return normalizeAssetUrl(element.absUrl("srcset"), first, element.baseUri());
            }
        }
        for (String attribute : List.of("src", "data-src", "data-lazy-src", "data-original")) {
            String value = element.attr(attribute);
            if (StringUtils.hasText(value)) {
                return normalizeAssetUrl(element.absUrl(attribute), value, element.baseUri());
            }
        }
        return "";
    }

    private void addImage(Set<String> urls, String rawUrl) {
        String url = normalizeAssetUrl(rawUrl, rawUrl, "");
        if (!StringUtils.hasText(url)) {
            return;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:") || lower.endsWith(".svg") || lower.contains("sprite") || lower.contains("logo")) {
            return;
        }
        urls.add(url);
    }

    private String resolvePrice(Document document) {
        String metaPrice = firstText(
                meta(document, "meta[property=product:price:amount]"),
                meta(document, "meta[property=og:price:amount]"),
                meta(document, "meta[itemprop=price]"),
                attr(document.selectFirst("[itemprop=price]"), "content"),
                attr(document.selectFirst(".a-price .a-offscreen"), "text"),
                attr(document.selectFirst("[data-qa*=price], [class*=price], [class*=Price]"), "text")
        );
        String currency = firstText(
                meta(document, "meta[property=product:price:currency]"),
                meta(document, "meta[property=og:price:currency]"),
                attr(document.selectFirst("[itemprop=priceCurrency]"), "content")
        );
        String price = firstPrice(firstText(metaPrice, document.text()));
        if (!StringUtils.hasText(price) && StringUtils.hasText(metaPrice) && metaPrice.matches("\\d[\\d,.]*(?:\\.\\d+)?")) {
            price = metaPrice;
        }
        if (StringUtils.hasText(price) && StringUtils.hasText(currency) && !price.toLowerCase(Locale.ROOT).contains(currency.toLowerCase(Locale.ROOT))) {
            return currency + " " + price;
        }
        return price;
    }

    private String resolveMoq(Document document) {
        String text = document.text();
        Matcher matcher = MOQ_PATTERN.matcher(text == null ? "" : text);
        return matcher.find() ? compactText(matcher.group()) : "";
    }

    private String resolveShipping(Document document) {
        List<String> hits = new ArrayList<>();
        for (String segment : collectTextSegments(document, 140)) {
            String normalized = segment.toLowerCase(Locale.ROOT);
            if (SHIPPING_KEYWORDS.stream().anyMatch(normalized::contains)) {
                hits.add(segment);
            }
            if (hits.size() >= 2) {
                break;
            }
        }
        return String.join(" / ", hits);
    }

    private List<String> resolveSpecHints(Document document) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        for (String segment : collectTextSegments(document, 160)) {
            String normalized = segment.toLowerCase(Locale.ROOT);
            if (SPEC_KEYWORDS.stream().anyMatch(normalized::contains)) {
                hints.add(segment);
            }
            if (hints.size() >= MAX_SPEC_HINTS) {
                break;
            }
        }
        return new ArrayList<>(hints);
    }

    private String resolveVisibleSummary(Document document) {
        List<String> segments = collectTextSegments(document, 180);
        LinkedHashSet<String> useful = new LinkedHashSet<>();
        for (String segment : segments) {
            String lower = segment.toLowerCase(Locale.ROOT);
            if (segment.length() >= 20 && !lower.contains("cookie") && !lower.contains("javascript")) {
                useful.add(segment);
            }
            if (useful.size() >= 8) {
                break;
            }
        }
        return shrink(String.join("\n", useful), 1800);
    }

    private List<String> resolveSellingPoints(Document document) {
        LinkedHashSet<String> points = new LinkedHashSet<>();
        for (Element element : document.select("#feature-bullets li, [class*=bullet] li, [class*=feature] li, ul li")) {
            String text = compactText(element.text());
            if (text.length() >= 12 && text.length() <= 360) {
                points.add(text);
            }
            if (points.size() >= 8) {
                break;
            }
        }
        return new ArrayList<>(points);
    }

    private List<String> collectTextSegments(Document document, int maxLength) {
        LinkedHashSet<String> segments = new LinkedHashSet<>();
        Elements elements = document.select("h1, h2, h3, li, td, th, p, span, div[class*=bullet], div[class*=feature], div[class*=description]");
        for (Element element : elements) {
            String text = compactText(element.ownText());
            if (!StringUtils.hasText(text)) {
                text = compactText(element.text());
            }
            if (text.length() < 4 || text.length() > maxLength) {
                continue;
            }
            segments.add(text);
            if (segments.size() >= 80) {
                break;
            }
        }
        return new ArrayList<>(segments);
    }

    private String firstPrice(String rawText) {
        Matcher matcher = PRICE_PATTERN.matcher(rawText == null ? "" : rawText);
        return matcher.find() ? compactText(matcher.group()) : "";
    }

    private String meta(Document document, String selector) {
        Element element = document.selectFirst(selector);
        return element == null ? "" : compactText(firstText(element.attr("content"), element.attr("value")));
    }

    private String attr(Element element, String attribute) {
        if (element == null) {
            return "";
        }
        if ("text".equals(attribute)) {
            return compactText(element.text());
        }
        return compactText(element.attr(attribute));
    }

    private String cleanTitle(String title) {
        String text = compactText(title);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return shrink(text
                .replaceAll("\\s*\\|\\s*noon.*$", "")
                .replaceAll("\\s*:\\s*Amazon\\..*$", "")
                .replaceAll("\\s*-\\s*SHEIN.*$", "")
                .replaceAll("\\s*[-_]?\\s*阿里巴巴1688.*$", "")
                .replaceAll("\\s*[-_]?\\s*1688.*$", ""), 480);
    }

    private String acceptLanguage(String platform) {
        if ("1688".equalsIgnoreCase(platform)) {
            return "zh-CN,zh;q=0.9,en;q=0.8";
        }
        if ("SHEIN".equalsIgnoreCase(platform)) {
            return "ar-SA,en;q=0.9";
        }
        return "en-SA,en;q=0.9";
    }

    private String normalizeAssetUrl(String absoluteUrl, String rawValue, String baseUri) {
        String value = compactText(firstText(absoluteUrl, rawValue));
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:")) {
            return value;
        }
        if (StringUtils.hasText(baseUri)) {
            try {
                return URI.create(baseUri).resolve(value).toString();
            } catch (IllegalArgumentException ignored) {
                return Jsoup.parse("", baseUri).baseUri().replaceAll("/$", "") + "/" + value.replaceAll("^/", "");
            }
        }
        return value;
    }
}
