package com.nuono.next.productselection;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class NoonPublicProductUrlSupport {

    private static final Pattern NOON_LOCALE_PATTERN = Pattern.compile("/(uae|saudi|egypt|egy|ksa)-(en|ar)(?=/)", Pattern.CASE_INSENSITIVE);

    private NoonPublicProductUrlSupport() {
    }

    static String language(String pageUrl) {
        Matcher matcher = NOON_LOCALE_PATTERN.matcher(pageUrl == null ? "" : pageUrl);
        return matcher.find() ? matcher.group(2).toLowerCase(Locale.ROOT) : "en";
    }

    static String toLanguageUrl(String pageUrl, String language) {
        if (pageUrl == null || pageUrl.isBlank()) {
            return "";
        }
        Matcher matcher = NOON_LOCALE_PATTERN.matcher(pageUrl);
        if (!matcher.find()) {
            return pageUrl;
        }
        String targetLanguage = "ar".equalsIgnoreCase(language) ? "ar" : "en";
        return matcher.replaceFirst("/" + matcher.group(1).toLowerCase(Locale.ROOT) + "-" + targetLanguage);
    }

    static String acceptLanguage(String language) {
        return "ar".equalsIgnoreCase(language) ? "ar-SA,ar;q=0.9,en;q=0.7" : "en-SA,en;q=0.9,ar;q=0.5";
    }

    static String xLocale(String language, String pageUrl) {
        String lang = "ar".equalsIgnoreCase(language) ? "ar" : "en";
        String country = "sa";
        Matcher matcher = NOON_LOCALE_PATTERN.matcher(pageUrl == null ? "" : pageUrl);
        if (matcher.find()) {
            String market = matcher.group(1).toLowerCase(Locale.ROOT);
            if ("uae".equals(market)) {
                country = "ae";
            } else if ("egypt".equals(market) || "egy".equals(market)) {
                country = "eg";
            }
        }
        return lang + "-" + country;
    }

    static String catalogApiUrl(String pageUrl, String catalogBaseUrl) {
        if (pageUrl == null || pageUrl.isBlank()) {
            return "";
        }
        try {
            URI uri = new URI(pageUrl);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!host.endsWith("noon.com")) {
                return "";
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            Matcher matcher = NOON_LOCALE_PATTERN.matcher(path);
            if (!matcher.find()) {
                return "";
            }
            String productPath = path.substring(matcher.end()).replaceFirst("^/+", "");
            if (productPath.isBlank()) {
                return "";
            }
            StringBuilder url = new StringBuilder(normalizeCatalogBaseUrl(catalogBaseUrl)).append(productPath);
            if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
                url.append('?').append(uri.getRawQuery());
            }
            return url.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String normalizeCatalogBaseUrl(String catalogBaseUrl) {
        String baseUrl = catalogBaseUrl == null || catalogBaseUrl.isBlank()
                ? "https://noon-catalog.noon.partners/_svc/catalog/api/u/"
                : catalogBaseUrl.trim();
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }
}
