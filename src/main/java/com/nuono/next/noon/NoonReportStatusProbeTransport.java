package com.nuono.next.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import org.springframework.util.StringUtils;

/** Minimal read-only transport used only by the isolated release report probe. */
public class NoonReportStatusProbeTransport {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";
    private static final String DEFAULT_ACCEPT_LANGUAGE = "en-SA,en;q=0.9";

    private final ObjectMapper json;
    private final NoonProxyRouteFactory routes;
    private final String proxyMode;
    private final String userAgent;
    private final String acceptLanguage;

    public NoonReportStatusProbeTransport(
            ObjectMapper json,
            boolean proxyEnabled,
            String proxyType,
            String proxyHost,
            int proxyPort,
            String proxyProviderUrl,
            String proxyMode,
            String userAgent,
            String acceptLanguage
    ) {
        this.json = json;
        this.routes = new NoonProxyRouteFactory(
                json, proxyEnabled, proxyType, proxyHost, proxyPort, proxyProviderUrl
        );
        this.proxyMode = proxyMode;
        this.userAgent = defaultIfBlank(userAgent, DEFAULT_USER_AGENT);
        this.acceptLanguage = defaultIfBlank(acceptLanguage, DEFAULT_ACCEPT_LANGUAGE);
    }

    public JsonNode poll(
            String statusUrl,
            String projectCode,
            String storeCode,
            String siteCode,
            String exportId,
            String persistedCookie
    ) {
        requireText(projectCode, "projectCode");
        requireText(storeCode, "storeCode");
        requireText(siteCode, "siteCode");
        requireText(exportId, "exportId");
        requireText(persistedCookie, "persistedCookie");
        URI endpoint = URI.create(statusUrl);
        String targetHost = endpoint.getHost();
        requireText(targetHost, "status endpoint host");
        NoonProxyRouteFactory.Route route = routes.select(proxyMode);
        HttpClient.Builder client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1);
        if (route.proxySelector() != null) client.proxy(route.proxySelector());
        ObjectNode body = json.createObjectNode();
        body.put("exportCode", exportId);
        body.put("log", false);
        String url = statusUrl + (statusUrl.contains("?") ? "&" : "?")
                + "project=" + URLEncoder.encode(projectCode, StandardCharsets.UTF_8);
        String site = siteCode.toLowerCase(Locale.ROOT);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Accept-Language", acceptLanguage)
                    .header("X-Locale", "en-" + site)
                    .header("X-Lang", "en")
                    .header("X-Platform", "web")
                    .header("X-Project", projectCode)
                    .header("Origin", endpoint.getScheme() + "://" + targetHost)
                    .header("Referer", endpoint.getScheme() + "://" + targetHost + "/")
                    .header("User-Agent", userAgent)
                    .header("Cookie", persistedCookie + "; projectCode=" + projectCode
                            + "; noonStore=" + storeCode)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to serialize release report status request", failure);
        }
        try {
            HttpResponse<String> response = NoonHardDeadlineHttpClient.send(
                    client.build(), request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new NoonHttpException(
                        response.statusCode(), response.body(), endpoint.getPath()
                );
            }
            return json.readTree(response.body());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Release report status request interrupted", failure);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Release report status request failed: "
                            + failure.getClass().getSimpleName(), failure
            );
        }
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Release report probe is missing " + name);
        }
    }
}
