package com.nuono.next.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class NoonProxyRouteFactory {
    private static final Duration PROVIDER_CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration PROVIDER_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final boolean proxyEnabled;
    private final String proxyType;
    private final String proxyHost;
    private final int proxyPort;
    private final String proxyProviderUrl;
    private final NoonProxyConnectPreflight connectPreflight = new NoonProxyConnectPreflight();

    NoonProxyRouteFactory(
            ObjectMapper objectMapper,
            boolean proxyEnabled,
            String proxyType,
            String proxyHost,
            int proxyPort,
            String proxyProviderUrl
    ) {
        this.objectMapper = objectMapper;
        this.proxyEnabled = proxyEnabled;
        this.proxyType = StringUtils.hasText(proxyType)
                ? proxyType.trim().toUpperCase(Locale.ROOT)
                : "HTTP";
        this.proxyHost = StringUtils.hasText(proxyHost) ? proxyHost.trim() : null;
        this.proxyPort = Math.max(0, proxyPort);
        this.proxyProviderUrl = normalize(proxyProviderUrl);
    }

    NoonProxyMode resolveMode(String configuredMode) {
        return NoonProxyMode.resolve(
                configuredMode,
                proxyEnabled,
                StringUtils.hasText(proxyHost) && proxyPort > 0,
                StringUtils.hasText(proxyProviderUrl)
        );
    }

    Route select(String configuredMode) {
        NoonProxyMode mode = resolveMode(configuredMode);
        Proxy proxy = null;
        if (mode == NoonProxyMode.FIXED) {
            proxy = new Proxy(configuredProxyType(), new InetSocketAddress(proxyHost, proxyPort));
        } else if (mode == NoonProxyMode.PROVIDER) {
            proxy = loadProviderProxy();
        }
        return new Route(mode, proxy, fingerprint(proxy));
    }

    Route selectAndPreflight(
            String configuredMode,
            String targetHost,
            int targetPort,
            int connectTimeoutMillis,
            int readTimeoutMillis
    ) {
        Route route = select(configuredMode);
        if (route.proxy != null) {
            connectPreflight.verify(
                    route.proxy,
                    route.fingerprint,
                    targetHost,
                    targetPort,
                    connectTimeoutMillis,
                    readTimeoutMillis
            );
        }
        return route;
    }

    private Proxy loadProviderProxy() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(PROVIDER_CONNECT_TIMEOUT)
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(proxyProviderUrl))
                    .GET()
                    .timeout(PROVIDER_REQUEST_TIMEOUT)
                    .build();
            HttpResponse<String> response = NoonHardDeadlineHttpClient.send(
                    client,
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Noon proxy provider returned HTTP " + response.statusCode()
                );
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode firstProxy = root.path("data").isArray() && root.path("data").size() > 0
                    ? root.path("data").get(0)
                    : root;
            String host = firstText(firstProxy, "ip", "host");
            String portText = firstText(firstProxy, "port");
            if (!StringUtils.hasText(host) || !StringUtils.hasText(portText)) {
                throw new IllegalStateException("Noon proxy provider response is missing ip/port");
            }
            return new Proxy(
                    configuredProxyType(),
                    new InetSocketAddress(host, Integer.parseInt(portText))
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Noon proxy provider request interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Noon proxy provider request failed: "
                            + exception.getClass().getSimpleName(),
                    exception
            );
        }
    }

    private Proxy.Type configuredProxyType() {
        return "SOCKS".equalsIgnoreCase(proxyType) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
    }

    private static String fingerprint(Proxy proxy) {
        if (proxy == null) {
            return "direct";
        }
        String source = proxy.type() + "|" + String.valueOf(proxy.address());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int index = 0; index < 8; index++) {
                hex.append(String.format("%02x", digest[index]));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint Noon proxy route", exception);
        }
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    static final class Route {
        private final NoonProxyMode mode;
        private final Proxy proxy;
        private final String fingerprint;

        private Route(NoonProxyMode mode, Proxy proxy, String fingerprint) {
            this.mode = mode;
            this.proxy = proxy;
            this.fingerprint = fingerprint;
        }

        Proxy proxy() {
            return proxy;
        }

        String fingerprint() {
            return fingerprint;
        }

        NoonProxyMode mode() {
            return mode;
        }

        ProxySelector proxySelector() {
            return proxy == null ? null : new FixedProxySelector(proxy);
        }
    }

    private static final class FixedProxySelector extends ProxySelector {
        private final Proxy proxy;

        private FixedProxySelector(Proxy proxy) {
            this.proxy = proxy;
        }

        @Override
        public java.util.List<Proxy> select(URI uri) {
            return Collections.singletonList(proxy);
        }

        @Override
        public void connectFailed(URI uri, java.net.SocketAddress address, IOException failure) {
            // The request layer owns bounded failure handling.
        }
    }
}
