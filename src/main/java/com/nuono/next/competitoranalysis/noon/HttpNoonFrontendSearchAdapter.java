package com.nuono.next.competitoranalysis.noon;

import com.nuono.next.noon.ChromeNoonCookieSupport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class HttpNoonFrontendSearchAdapter implements NoonFrontendSearchAdapter {
    private static final int DEFAULT_SEARCH_LIMIT = 30;
    private static final int MAX_SEARCH_LIMIT = 100;

    private final NoonFrontendSearchPageParser parser;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final String baseUrl;
    private final String catalogBaseUrl;
    private final String customerCatalogV3BaseUrl;
    private final String configuredFrontendCookieHeader;
    private final boolean chromeFrontendCookieEnabled;
    private final Supplier<String> frontendCookieHeaderSupplier;
    private final boolean curlEnabled;

    @Autowired
    public HttpNoonFrontendSearchAdapter(
            NoonFrontendSearchPageParser parser,
            @Value("${nuono.competitor-analysis.noon-search.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${nuono.competitor-analysis.noon-search.request-timeout-seconds:20}") int requestTimeoutSeconds,
            @Value("${nuono.competitor-analysis.noon-search.base-url:https://www.noon.com}") String baseUrl,
            @Value("${nuono.competitor-analysis.noon-search.catalog-base-url:https://noon-catalog.noon.partners/_svc/catalog/api/u}") String catalogBaseUrl,
            @Value("${nuono.competitor-analysis.noon-search.customer-catalog-v3-base-url:https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u}") String customerCatalogV3BaseUrl,
            @Value("${nuono.competitor-analysis.noon-search.frontend-cookie-header:}") String configuredFrontendCookieHeader,
            @Value("${nuono.competitor-analysis.noon-search.chrome-frontend-cookie-enabled:true}") boolean chromeFrontendCookieEnabled,
            @Value("${nuono.competitor-analysis.noon-search.curl-enabled:true}") boolean curlEnabled
    ) {
        this(
                parser,
                Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)),
                Duration.ofSeconds(Math.max(3, requestTimeoutSeconds)),
                baseUrl,
                catalogBaseUrl,
                customerCatalogV3BaseUrl,
                configuredFrontendCookieHeader,
                chromeFrontendCookieEnabled,
                ChromeNoonCookieSupport::loadNoonFrontendCookieHeader,
                curlEnabled
        );
    }

    HttpNoonFrontendSearchAdapter(
            NoonFrontendSearchPageParser parser,
            Duration connectTimeout,
            Duration requestTimeout,
            String baseUrl
    ) {
        this(
                parser,
                connectTimeout,
                requestTimeout,
                baseUrl,
                "https://noon-catalog.noon.partners/_svc/catalog/api/u",
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u"
        );
    }

    HttpNoonFrontendSearchAdapter(
            NoonFrontendSearchPageParser parser,
            Duration connectTimeout,
            Duration requestTimeout,
            String baseUrl,
            String catalogBaseUrl,
            String customerCatalogV3BaseUrl
    ) {
        this(
                parser,
                connectTimeout,
                requestTimeout,
                baseUrl,
                catalogBaseUrl,
                customerCatalogV3BaseUrl,
                null,
                false,
                () -> null,
                false
        );
    }

    HttpNoonFrontendSearchAdapter(
            NoonFrontendSearchPageParser parser,
            Duration connectTimeout,
            Duration requestTimeout,
            String baseUrl,
            String catalogBaseUrl,
            String customerCatalogV3BaseUrl,
            boolean chromeFrontendCookieEnabled,
            Supplier<String> frontendCookieHeaderSupplier,
            boolean curlEnabled
    ) {
        this(
                parser,
                connectTimeout,
                requestTimeout,
                baseUrl,
                catalogBaseUrl,
                customerCatalogV3BaseUrl,
                null,
                chromeFrontendCookieEnabled,
                frontendCookieHeaderSupplier,
                curlEnabled
        );
    }

    HttpNoonFrontendSearchAdapter(
            NoonFrontendSearchPageParser parser,
            Duration connectTimeout,
            Duration requestTimeout,
            String baseUrl,
            String catalogBaseUrl,
            String customerCatalogV3BaseUrl,
            boolean chromeFrontendCookieEnabled,
            Supplier<String> frontendCookieHeaderSupplier
    ) {
        this(
                parser,
                connectTimeout,
                requestTimeout,
                baseUrl,
                catalogBaseUrl,
                customerCatalogV3BaseUrl,
                null,
                chromeFrontendCookieEnabled,
                frontendCookieHeaderSupplier,
                false
        );
    }

    HttpNoonFrontendSearchAdapter(
            NoonFrontendSearchPageParser parser,
            Duration connectTimeout,
            Duration requestTimeout,
            String baseUrl,
            String catalogBaseUrl,
            String customerCatalogV3BaseUrl,
            String configuredFrontendCookieHeader,
            boolean chromeFrontendCookieEnabled,
            Supplier<String> frontendCookieHeaderSupplier,
            boolean curlEnabled
    ) {
        this.parser = parser;
        this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(20) : requestTimeout;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.catalogBaseUrl = normalizeCatalogBaseUrl(catalogBaseUrl);
        this.customerCatalogV3BaseUrl = normalizeCustomerCatalogV3BaseUrl(customerCatalogV3BaseUrl);
        this.configuredFrontendCookieHeader = configuredFrontendCookieHeader == null ? "" : configuredFrontendCookieHeader.trim();
        this.chromeFrontendCookieEnabled = chromeFrontendCookieEnabled;
        this.frontendCookieHeaderSupplier = frontendCookieHeaderSupplier == null ? () -> null : frontendCookieHeaderSupplier;
        this.curlEnabled = curlEnabled;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public NoonSearchPage search(NoonSearchRequest request) {
        return searchCustomerCatalogV3(request);
    }

    private NoonSearchPage searchCustomerCatalogV3(NoonSearchRequest request) {
        String url = buildCustomerCatalogV3SearchUrl(request);
        String frontendCookieHeader = loadFrontendCookieHeader(url);
        if (curlEnabled) {
            return searchCustomerCatalogV3WithCurl(request, url, frontendCookieHeader);
        }
        HttpRequest httpRequest = buildCustomerCatalogV3SearchRequest(request, url, frontendCookieHeader);
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw mapUnsuccessfulStatus(statusCode, url);
            }
            return parser.parseCatalogJson(response.body(), url, statusCode);
        } catch (NoonSearchProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new NoonSearchProviderException(
                    "PROVIDER_UNAVAILABLE",
                    "Noon 前台 catalog v3 搜索暂不可用：" + shrink(exception.getMessage(), 180),
                    null,
                    url,
                    null
            );
        }
    }

    private NoonSearchPage searchCustomerCatalogV3WithCurl(NoonSearchRequest request, String url, String frontendCookieHeader) {
        try {
            CurlResponse response = executeCurl(buildCustomerCatalogV3CurlConfig(request, url, frontendCookieHeader));
            if (response.exitCode != 0) {
                throw new NoonSearchProviderException(
                        "PROVIDER_UNAVAILABLE",
                        "Noon 前台 catalog v3 curl 请求失败：" + shrink(response.stderr, 180),
                        null,
                        url,
                        null
                );
            }
            int statusCode = response.statusCode;
            if (statusCode < 200 || statusCode >= 300) {
                throw mapUnsuccessfulStatus(statusCode, url);
            }
            return parser.parseCatalogJson(response.body, url, statusCode);
        } catch (NoonSearchProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new NoonSearchProviderException(
                    "PROVIDER_UNAVAILABLE",
                    "Noon 前台 catalog v3 curl 请求暂不可用：" + shrink(exception.getMessage(), 180),
                    null,
                    url,
                    null
            );
        }
    }

    String buildCustomerCatalogV3CurlConfig(NoonSearchRequest request, String url, String frontendCookieHeader) {
        StringBuilder config = new StringBuilder();
        appendCurlOption(config, "url", url);
        appendCurlHeader(config, "accept", "application/json, text/plain, */*");
        appendCurlHeader(config, "accept-language", acceptLanguage(request));
        appendCurlHeader(config, "cache-control", "no-cache, max-age=0, must-revalidate, no-store");
        appendCurlHeader(config, "referer", buildPublicSearchReferer(request));
        appendCurlHeader(config, "sec-ch-ua", "\"Chromium\";v=\"148\", \"Google Chrome\";v=\"148\", \"Not/A)Brand\";v=\"99\"");
        appendCurlHeader(config, "sec-ch-ua-mobile", "?0");
        appendCurlHeader(config, "sec-ch-ua-platform", "\"macOS\"");
        appendCurlHeader(config, "sec-fetch-dest", "empty");
        appendCurlHeader(config, "sec-fetch-mode", "cors");
        appendCurlHeader(config, "sec-fetch-site", "same-origin");
        appendCurlHeader(config, "user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36");
        appendCurlHeader(config, "x-border-enabled", "true");
        appendCurlHeader(config, "x-cms", "v2");
        appendCurlHeader(config, "x-content", "desktop");
        appendCurlHeader(config, "x-ecom-zonecode", xEcomZoneCode(request));
        appendCurlHeader(config, "x-lang", xLang(request));
        appendCurlHeader(config, "x-lat", xLatitude(request));
        appendCurlHeader(config, "x-lng", xLongitude(request));
        appendCurlHeader(config, "x-locale", xLocale(request));
        appendCurlHeader(config, "x-mp-country", xCountry(request));
        appendCurlHeader(config, "x-platform", "web");
        appendCurlHeader(config, "x-rocket-enabled", "true");
        appendCurlHeader(config, "x-rocket-zonecode", xRocketZoneCode(request));
        if (StringUtils.hasText(frontendCookieHeader)) {
            appendCurlHeader(config, "cookie", frontendCookieHeader.trim());
        }
        return config.toString();
    }

    private CurlResponse executeCurl(String config) throws IOException {
        long maxTimeSeconds = Math.max(3L, requestTimeout.getSeconds());
        Process process = new ProcessBuilder(
                "curl",
                "-sS",
                "--http1.1",
                "--compressed",
                "--max-time",
                String.valueOf(maxTimeSeconds),
                "-K",
                "-",
                "-w",
                "\n__NUONO_HTTP_STATUS__:%{http_code}\n"
        ).start();
        byte[] stdout;
        byte[] stderr;
        try (OutputStream stdin = process.getOutputStream();
             InputStream inputStream = process.getInputStream();
             InputStream errorStream = process.getErrorStream()) {
            stdin.write(config.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            stdin.close();
            stdout = readAllBytes(inputStream);
            stderr = readAllBytes(errorStream);
        }
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("curl 请求被中断", exception);
        }
        return parseCurlResponse(exitCode, stdout, stderr);
    }

    private CurlResponse parseCurlResponse(int exitCode, byte[] stdout, byte[] stderr) {
        String output = new String(stdout, StandardCharsets.UTF_8);
        String errorOutput = new String(stderr, StandardCharsets.UTF_8);
        String marker = "\n__NUONO_HTTP_STATUS__:";
        int markerIndex = output.lastIndexOf(marker);
        if (markerIndex < 0) {
            return new CurlResponse(exitCode, 0, output, errorOutput);
        }
        String body = output.substring(0, markerIndex);
        String statusText = output.substring(markerIndex + marker.length()).trim();
        int statusCode = 0;
        try {
            statusCode = Integer.parseInt(statusText);
        } catch (NumberFormatException ignored) {
            statusCode = 0;
        }
        return new CurlResponse(exitCode, statusCode, body, errorOutput);
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private void appendCurlHeader(StringBuilder config, String name, String value) {
        if (!StringUtils.hasText(name) || !StringUtils.hasText(value)) {
            return;
        }
        appendCurlOption(config, "header", name + ": " + value);
    }

    private void appendCurlOption(StringBuilder config, String option, String value) {
        config.append(option)
                .append(" = \"")
                .append(escapeCurlConfigValue(value))
                .append("\"")
                .append('\n');
    }

    private String escapeCurlConfigValue(String value) {
        return (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "");
    }

    HttpRequest buildCustomerCatalogV3SearchRequest(NoonSearchRequest request, String url, String frontendCookieHeader) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36")
                .header("Accept-Language", acceptLanguage(request))
                .header("Accept", "application/json, text/plain, */*")
                .header("Cache-Control", "no-cache, max-age=0, must-revalidate, no-store")
                .header("Referer", buildPublicSearchReferer(request))
                .header("Sec-CH-UA", "\"Chromium\";v=\"148\", \"Google Chrome\";v=\"148\", \"Not/A)Brand\";v=\"99\"")
                .header("Sec-CH-UA-Mobile", "?0")
                .header("Sec-CH-UA-Platform", "\"macOS\"")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .header("X-Border-Enabled", "true")
                .header("X-Cms", "v2")
                .header("X-Content", "desktop")
                .header("X-Ecom-Zonecode", xEcomZoneCode(request))
                .header("X-Lang", xLang(request))
                .header("X-Lat", xLatitude(request))
                .header("X-Lng", xLongitude(request))
                .header("X-Locale", xLocale(request))
                .header("X-Mp-Country", xCountry(request))
                .header("X-Platform", "web")
                .header("X-Rocket-Enabled", "true")
                .header("X-Rocket-Zonecode", xRocketZoneCode(request));
        if (StringUtils.hasText(frontendCookieHeader)) {
            builder.header("Cookie", frontendCookieHeader.trim());
        }
        return builder.GET().build();
    }

    String loadFrontendCookieHeader(String url) {
        if (StringUtils.hasText(configuredFrontendCookieHeader)) {
            return configuredFrontendCookieHeader;
        }
        if (!chromeFrontendCookieEnabled) {
            return null;
        }
        try {
            return frontendCookieHeaderSupplier.get();
        } catch (RuntimeException exception) {
            throw new NoonSearchProviderException(
                    "PROVIDER_UNAVAILABLE",
                    "Noon 前台 catalog v3 搜索缺少浏览器会话：" + shrink(exception.getMessage(), 180),
                    null,
                    url,
                    null
            );
        }
    }

    private NoonSearchPage searchCatalog(NoonSearchRequest request) {
        String url = buildCatalogSearchUrl(request);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .header("Accept-Language", acceptLanguage(request))
                .header("Accept", "application/json,text/plain,*/*")
                .header("X-Lang", xLang(request))
                .header("X-Locale", xLocale(request))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw mapUnsuccessfulStatus(statusCode, url);
            }
            return parser.parseCatalogJson(response.body(), url, statusCode);
        } catch (NoonSearchProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new NoonSearchProviderException(
                    "PROVIDER_UNAVAILABLE",
                    "Noon catalog 搜索暂不可用：" + shrink(exception.getMessage(), 180),
                    null,
                    url,
                    null
            );
        }
    }

    private NoonSearchPage searchHtml(NoonSearchRequest request) {
        String url = buildSearchUrl(request);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .header("Accept-Language", acceptLanguage(request))
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw mapUnsuccessfulStatus(statusCode, url);
            }
            return parser.parse(response.body(), url, statusCode);
        } catch (NoonSearchProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new NoonSearchProviderException(
                    "PROVIDER_UNAVAILABLE",
                    "Noon 前台搜索暂不可用：" + shrink(exception.getMessage(), 180),
                    null,
                    url,
                    null
            );
        }
    }

    String buildSearchUrl(NoonSearchRequest request) {
        String path = marketPath(request == null ? null : request.getSiteCode(), request == null ? null : request.getLocale());
        String keyword = request == null ? "" : trim(request.getKeyword());
        int limit = request == null || request.getLimit() == null
                ? DEFAULT_SEARCH_LIMIT
                : Math.max(1, Math.min(request.getLimit(), MAX_SEARCH_LIMIT));
        return baseUrl
                + path
                + "/search?q="
                + URLEncoder.encode(keyword == null ? "" : keyword, StandardCharsets.UTF_8)
                + "&limit="
                + limit;
    }

    String buildCatalogSearchUrl(NoonSearchRequest request) {
        String path = marketPath(request == null ? null : request.getSiteCode(), request == null ? null : request.getLocale());
        String keyword = request == null ? "" : trim(request.getKeyword());
        int limit = request == null || request.getLimit() == null
                ? DEFAULT_SEARCH_LIMIT
                : Math.max(1, Math.min(request.getLimit(), MAX_SEARCH_LIMIT));
        return catalogBaseUrl
                + path
                + "/search?q="
                + URLEncoder.encode(keyword == null ? "" : keyword, StandardCharsets.UTF_8)
                + "&limit="
                + limit;
    }

    String buildCustomerCatalogV3SearchUrl(NoonSearchRequest request) {
        String keyword = request == null ? "" : trim(request.getKeyword());
        int limit = request == null || request.getLimit() == null
                ? DEFAULT_SEARCH_LIMIT
                : Math.max(1, Math.min(request.getLimit(), MAX_SEARCH_LIMIT));
        return customerCatalogV3BaseUrl
                + "/search?q="
                + URLEncoder.encode(keyword == null ? "" : keyword, StandardCharsets.UTF_8)
                + "&limit="
                + limit;
    }

    NoonSearchProviderException mapUnsuccessfulStatus(int statusCode, String url) {
        if (statusCode == 403) {
            throw new NoonSearchProviderException("BLOCKED_BY_RISK_CONTROL", "Noon 前台搜索返回 HTTP 403。", statusCode, url, null);
        }
        if (statusCode == 429) {
            throw new NoonSearchProviderException("RATE_LIMITED", "Noon 前台搜索返回 HTTP 429。", statusCode, url, null);
        }
        if (statusCode >= 500) {
            throw new NoonSearchProviderException("PROVIDER_UNAVAILABLE", "Noon 前台搜索返回 HTTP " + statusCode + "。", statusCode, url, null);
        }
        throw new NoonSearchProviderException("PARSE_FAILED", "Noon 前台搜索返回 HTTP " + statusCode + "。", statusCode, url, null);
    }

    private String marketPath(String siteCode, String locale) {
        String site = normalize(siteCode);
        String language = normalize(locale);
        boolean arabic = language != null && language.toLowerCase(Locale.ROOT).contains("ar");
        if ("AE".equals(site) || "UAE".equals(site)) {
            return arabic ? "/uae-ar" : "/uae-en";
        }
        if ("EG".equals(site) || "EGY".equals(site) || "EGYPT".equals(site)) {
            return arabic ? "/egypt-ar" : "/egypt-en";
        }
        return arabic ? "/saudi-ar" : "/saudi-en";
    }

    private String acceptLanguage(NoonSearchRequest request) {
        String locale = request == null ? null : normalize(request.getLocale());
        return locale != null && locale.toLowerCase(Locale.ROOT).contains("ar")
                ? "ar-SA,ar;q=0.9,en;q=0.7"
                : "en-SA,en;q=0.9,ar;q=0.5";
    }

    private String xLang(NoonSearchRequest request) {
        String locale = request == null ? null : normalize(request.getLocale());
        return locale != null && locale.toLowerCase(Locale.ROOT).contains("ar") ? "ar" : "en";
    }

    private String xLocale(NoonSearchRequest request) {
        String site = normalize(request == null ? null : request.getSiteCode());
        String country = "sa";
        if ("AE".equals(site) || "UAE".equals(site)) {
            country = "ae";
        } else if ("EG".equals(site) || "EGY".equals(site) || "EGYPT".equals(site)) {
            country = "eg";
        }
        return xLang(request) + "-" + country;
    }

    private String xCountry(NoonSearchRequest request) {
        String site = normalize(request == null ? null : request.getSiteCode());
        if ("AE".equals(site) || "UAE".equals(site)) {
            return "ae";
        }
        if ("EG".equals(site) || "EGY".equals(site) || "EGYPT".equals(site)) {
            return "eg";
        }
        return "sa";
    }

    private String xEcomZoneCode(NoonSearchRequest request) {
        String country = xCountry(request);
        if ("ae".equals(country)) {
            return "AE-DXB-DXB";
        }
        if ("eg".equals(country)) {
            return "EG-CAI-CAI";
        }
        return "SA-RUH-S17";
    }

    private String xLatitude(NoonSearchRequest request) {
        String country = xCountry(request);
        if ("ae".equals(country)) {
            return "25197872";
        }
        if ("eg".equals(country)) {
            return "30044596";
        }
        return "247311382";
    }

    private String xLongitude(NoonSearchRequest request) {
        String country = xCountry(request);
        if ("ae".equals(country)) {
            return "55174350";
        }
        if ("eg".equals(country)) {
            return "31235844";
        }
        return "466700814";
    }

    private String xRocketZoneCode(NoonSearchRequest request) {
        String country = xCountry(request);
        if ("ae".equals(country)) {
            return "W00000168A";
        }
        if ("eg".equals(country)) {
            return "W00000100A";
        }
        return "W00083496A";
    }

    private String buildPublicSearchReferer(NoonSearchRequest request) {
        String path = marketPath(request == null ? null : request.getSiteCode(), request == null ? null : request.getLocale());
        String keyword = request == null ? "" : trim(request.getKeyword());
        String encoded = URLEncoder.encode(keyword == null ? "" : keyword, StandardCharsets.UTF_8);
        return baseUrl + path + "/search/?originalQuery=" + encoded + "&q=" + encoded;
    }

    private String normalizeBaseUrl(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "https://www.noon.com";
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String normalizeCatalogBaseUrl(String value) {
        String normalized = StringUtils.hasText(value)
                ? value.trim()
                : "https://noon-catalog.noon.partners/_svc/catalog/api/u";
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String normalizeCustomerCatalogV3BaseUrl(String value) {
        String normalized = StringUtils.hasText(value)
                ? value.trim()
                : "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u";
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String shrink(String value, int maxLength) {
        String text = StringUtils.hasText(value) ? value.replaceAll("\\s+", " ").trim() : "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static final class CurlResponse {
        private final int exitCode;
        private final int statusCode;
        private final String body;
        private final String stderr;

        private CurlResponse(int exitCode, int statusCode, String body, String stderr) {
            this.exitCode = exitCode;
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
            this.stderr = stderr == null ? "" : stderr;
        }
    }
}
