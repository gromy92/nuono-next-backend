package com.nuono.next.productselection;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
class SourceCollectionPageFetchClient {

    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String extraQueryParams;
    private final int timeoutSeconds;
    private final HttpClient client;

    @Autowired
    SourceCollectionPageFetchClient(
            @Value("${nuono.product-selection.source-collection.page-fetch.enabled:false}") boolean enabled,
            @Value("${nuono.product-selection.source-collection.page-fetch.base-url:}") String baseUrl,
            @Value("${nuono.product-selection.source-collection.page-fetch.api-key:}") String apiKey,
            @Value("${nuono.product-selection.source-collection.page-fetch.extra-query-params:}") String extraQueryParams,
            @Value("${nuono.product-selection.source-collection.page-fetch.timeout-seconds:30}") int timeoutSeconds
    ) {
        this.enabled = enabled;
        this.baseUrl = normalize(baseUrl);
        this.apiKey = normalize(apiKey);
        this.extraQueryParams = normalizeQueryParams(extraQueryParams);
        this.timeoutSeconds = Math.max(8, timeoutSeconds);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(this.timeoutSeconds))
                .build();
    }

    SourceCollectionPageFetchClient(boolean enabled, String baseUrl, String apiKey, int timeoutSeconds) {
        this(enabled, baseUrl, apiKey, "", timeoutSeconds);
    }

    static SourceCollectionPageFetchClient disabled() {
        return new SourceCollectionPageFetchClient(false, "", "", "", 30);
    }

    Optional<Document> fetchDocument(String pageUrl) {
        if (!enabled || !StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(pageUrl)) {
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(buildUri(pageUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.8,*/*;q=0.5")
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !StringUtils.hasText(response.body())) {
                return Optional.empty();
            }
            return Optional.of(Jsoup.parse(response.body(), pageUrl));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private URI buildUri(String pageUrl) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        String query = "api_key=" + encode(apiKey)
                + "&url=" + encode(pageUrl)
                + "&keep_headers=true";
        if (StringUtils.hasText(extraQueryParams)) {
            query += "&" + extraQueryParams;
        }
        return URI.create(baseUrl + separator + query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeQueryParams(String value) {
        String text = normalize(value);
        while (text.startsWith("?") || text.startsWith("&")) {
            text = text.substring(1).trim();
        }
        return text;
    }
}
