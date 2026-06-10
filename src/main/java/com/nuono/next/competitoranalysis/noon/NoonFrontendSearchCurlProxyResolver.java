package com.nuono.next.competitoranalysis.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class NoonFrontendSearchCurlProxyResolver {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String proxyType;
    private final String proxyHost;
    private final int proxyPort;
    private final String proxyProviderUrl;
    private final ProviderResponseLoader providerResponseLoader;

    NoonFrontendSearchCurlProxyResolver(
            boolean enabled,
            String proxyType,
            String proxyHost,
            int proxyPort,
            String proxyProviderUrl
    ) {
        this(
                new ObjectMapper(),
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .version(HttpClient.Version.HTTP_1_1)
                        .build(),
                enabled,
                proxyType,
                proxyHost,
                proxyPort,
                proxyProviderUrl,
                null
        );
    }

    NoonFrontendSearchCurlProxyResolver(
            boolean enabled,
            String proxyType,
            String proxyHost,
            int proxyPort,
            String proxyProviderUrl,
            ProviderResponseLoader providerResponseLoader
    ) {
        this(
                new ObjectMapper(),
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .version(HttpClient.Version.HTTP_1_1)
                        .build(),
                enabled,
                proxyType,
                proxyHost,
                proxyPort,
                proxyProviderUrl,
                providerResponseLoader
        );
    }

    private NoonFrontendSearchCurlProxyResolver(
            ObjectMapper objectMapper,
            HttpClient httpClient,
            boolean enabled,
            String proxyType,
            String proxyHost,
            int proxyPort,
            String proxyProviderUrl,
            ProviderResponseLoader providerResponseLoader
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.enabled = enabled;
        this.proxyType = StringUtils.hasText(proxyType) ? proxyType.trim().toUpperCase(Locale.ROOT) : "HTTP";
        this.proxyHost = StringUtils.hasText(proxyHost) ? proxyHost.trim() : "";
        this.proxyPort = Math.max(0, proxyPort);
        this.proxyProviderUrl = StringUtils.hasText(proxyProviderUrl) ? proxyProviderUrl.trim() : "";
        this.providerResponseLoader = providerResponseLoader == null ? this::fetchProviderResponse : providerResponseLoader;
    }

    String resolveCurlProxy() {
        if (!enabled) {
            return null;
        }
        if (StringUtils.hasText(proxyProviderUrl)) {
            return resolveProviderProxy();
        }
        if (StringUtils.hasText(proxyHost) && proxyPort > 0) {
            return formatCurlProxy(proxyHost, String.valueOf(proxyPort));
        }
        return null;
    }

    private String resolveProviderProxy() {
        try {
            JsonNode root = objectMapper.readTree(providerResponseLoader.load());
            JsonNode firstProxy = root.path("data").isArray() && root.path("data").size() > 0
                    ? root.path("data").get(0)
                    : root;
            String host = firstText(firstProxy, "ip", "host");
            String port = firstText(firstProxy, "port");
            if (!StringUtils.hasText(host) || !StringUtils.hasText(port)) {
                throw new IllegalStateException("代理供应商响应缺少 ip/port。");
            }
            return formatCurlProxy(host, port);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("加载 Noon 搜索代理失败：" + exception.getClass().getSimpleName());
        }
    }

    private String fetchProviderResponse() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(proxyProviderUrl))
                    .GET()
                    .timeout(REQUEST_TIMEOUT)
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("代理供应商返回 HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("加载 Noon 搜索代理被中断。");
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("加载 Noon 搜索代理失败：" + exception.getClass().getSimpleName());
        }
    }

    private String formatCurlProxy(String host, String port) {
        String scheme = "SOCKS".equals(proxyType) ? "socks5h" : "http";
        return scheme + "://" + host.trim() + ":" + port.trim();
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    @FunctionalInterface
    interface ProviderResponseLoader {
        String load();
    }
}
