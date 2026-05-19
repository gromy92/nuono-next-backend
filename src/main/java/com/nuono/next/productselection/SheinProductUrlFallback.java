package com.nuono.next.productselection;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

class SheinProductUrlFallback {

    private static final Pattern PRODUCT_PATH_PATTERN = Pattern.compile("(?:^|/)([^/]+)-p-(\\d+)\\.html", Pattern.CASE_INSENSITIVE);

    private final ProductSelectionSourceCollectionHtmlParser htmlParser;

    SheinProductUrlFallback(ProductSelectionSourceCollectionHtmlParser htmlParser) {
        this.htmlParser = htmlParser;
    }

    ProductSelectionSourceCollectionResult fromUrl(String pageUrl, String failureMessage) {
        ParsedSheinUrl parsed = parse(pageUrl);
        if (!StringUtils.hasText(parsed.productId) && !StringUtils.hasText(parsed.title)) {
            return null;
        }
        ProductSelectionSourceCollectionResult result = new ProductSelectionSourceCollectionResult();
        result.setSourcePlatform("SHEIN");
        result.setSourceUrl(pageUrl);
        result.setPageUrl(pageUrl);
        result.setSourceTitle(htmlParser.shrink(htmlParser.firstText(
                parsed.title,
                StringUtils.hasText(parsed.productId) ? "SHEIN Product " + parsed.productId : ""
        ), 480));
        List<String> specs = new ArrayList<>();
        addSpec(specs, "SHEIN Product ID", parsed.productId);
        addSpec(specs, "SHEIN Mall Code", parsed.mallCode);
        addSpec(specs, "SHEIN Selected Attribute", parsed.mainAttr);
        addSpec(specs, "Source Access", "public page blocked, URL fallback only");
        addSpec(specs, "Source Failure", htmlParser.shrink(cleanFailure(failureMessage), 160));
        result.setSpecHints(specs);
        return result;
    }

    String productId(String pageUrl) {
        return parse(pageUrl).productId;
    }

    String title(String pageUrl) {
        return parse(pageUrl).title;
    }

    String country(String pageUrl) {
        String host = host(pageUrl);
        int separator = host.indexOf(".shein.");
        if (separator <= 0) {
            return "us";
        }
        return host.substring(0, separator).toLowerCase(Locale.ROOT);
    }

    String language(String pageUrl) {
        return "ar".equals(country(pageUrl)) ? "ar" : "en";
    }

    private ParsedSheinUrl parse(String pageUrl) {
        ParsedSheinUrl parsed = new ParsedSheinUrl();
        try {
            URI uri = URI.create(pageUrl);
            applyProductMatch(parsed, uri.getPath());
            parsed.mallCode = queryValue(uri.getRawQuery(), "mallCode");
            parsed.mainAttr = queryValue(uri.getRawQuery(), "main_attr");
            if (!StringUtils.hasText(parsed.productId)) {
                applyProductMatch(parsed, queryValue(uri.getRawQuery(), "redirection"));
            }
            if (!StringUtils.hasText(parsed.productId)) {
                applyProductMatch(parsed, pageUrl);
            }
        } catch (Exception ignored) {
            applyProductMatch(parsed, pageUrl);
        }
        return parsed;
    }

    private void applyProductMatch(ParsedSheinUrl parsed, String value) {
        Matcher matcher = PRODUCT_PATH_PATTERN.matcher(value == null ? "" : value);
        if (matcher.find()) {
            parsed.title = titleFromSlug(matcher.group(1));
            parsed.productId = matcher.group(2);
        }
    }

    private String host(String pageUrl) {
        try {
            String host = URI.create(pageUrl).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String titleFromSlug(String slug) {
        String decoded = URLDecoder.decode(slug == null ? "" : slug, StandardCharsets.UTF_8);
        return htmlParser.compactText(decoded
                .replace('-', ' ')
                .replaceAll("\\s+", " "));
    }

    private String queryValue(String rawQuery, String key) {
        if (!StringUtils.hasText(rawQuery)) {
            return "";
        }
        for (String part : rawQuery.split("&")) {
            int separator = part.indexOf('=');
            String name = separator >= 0 ? part.substring(0, separator) : part;
            if (!key.equals(name)) {
                continue;
            }
            String value = separator >= 0 ? part.substring(separator + 1) : "";
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        return "";
    }

    private void addSpec(List<String> specs, String label, String value) {
        String text = htmlParser.compactText(value);
        if (StringUtils.hasText(text)) {
            specs.add(label + ": " + text);
        }
    }

    private String cleanFailure(String failureMessage) {
        String text = htmlParser.compactText(failureMessage);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("status=403") || lower.contains("http 403") || lower.contains("403")) {
            return "HTTP 403";
        }
        if (lower.contains("outofservice")) {
            return "outOfService";
        }
        return text;
    }

    private static class ParsedSheinUrl {
        private String title = "";
        private String productId = "";
        private String mallCode = "";
        private String mainAttr = "";
    }
}
