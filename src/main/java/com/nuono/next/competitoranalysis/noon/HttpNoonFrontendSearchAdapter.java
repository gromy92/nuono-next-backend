package com.nuono.next.competitoranalysis.noon;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class HttpNoonFrontendSearchAdapter implements NoonFrontendSearchAdapter {
    private final NoonFrontendSearchPageParser parser;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final String baseUrl;

    public HttpNoonFrontendSearchAdapter(
            NoonFrontendSearchPageParser parser,
            @Value("${nuono.competitor-analysis.noon-search.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${nuono.competitor-analysis.noon-search.request-timeout-seconds:20}") int requestTimeoutSeconds,
            @Value("${nuono.competitor-analysis.noon-search.base-url:https://www.noon.com}") String baseUrl
    ) {
        this(
                parser,
                Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)),
                Duration.ofSeconds(Math.max(3, requestTimeoutSeconds)),
                baseUrl
        );
    }

    HttpNoonFrontendSearchAdapter(
            NoonFrontendSearchPageParser parser,
            Duration connectTimeout,
            Duration requestTimeout,
            String baseUrl
    ) {
        this.parser = parser;
        this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(20) : requestTimeout;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public NoonSearchPage search(NoonSearchRequest request) {
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
        int limit = request == null || request.getLimit() == null ? 30 : Math.max(1, Math.min(request.getLimit(), 30));
        return baseUrl
                + path
                + "/search?q="
                + URLEncoder.encode(keyword == null ? "" : keyword, StandardCharsets.UTF_8)
                + "&limit="
                + limit;
    }

    NoonSearchProviderException mapUnsuccessfulStatus(int statusCode, String url) {
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

    private String normalizeBaseUrl(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "https://www.noon.com";
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
}
