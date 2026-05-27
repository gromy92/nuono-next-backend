package com.nuono.next.productselection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jsoup.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
class SourceCollectionProxyProvider {

    private static final Duration CACHE_TTL = Duration.ofSeconds(45);

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Proxy.Type proxyType;
    private final String fixedHost;
    private final int fixedPort;
    private final String providerUrl;
    private final int timeoutSeconds;
    private final HttpClient directClient;

    private volatile Proxy cachedProxy;
    private volatile long cachedAtMillis;

    @Autowired
    SourceCollectionProxyProvider(
            ObjectMapper objectMapper,
            @Value("${nuono.product-selection.source-collection.proxy.enabled:${nuono.noon.proxy.enabled:false}}") boolean enabled,
            @Value("${nuono.product-selection.source-collection.proxy.type:${nuono.noon.proxy.type:HTTP}}") String proxyType,
            @Value("${nuono.product-selection.source-collection.proxy.host:${nuono.noon.proxy.host:}}") String fixedHost,
            @Value("${nuono.product-selection.source-collection.proxy.port:${nuono.noon.proxy.port:0}}") int fixedPort,
            @Value("${nuono.product-selection.source-collection.proxy.provider-url:${nuono.noon.proxy.provider-url:}}") String providerUrl,
            @Value("${nuono.product-selection.source-collection.proxy.timeout-seconds:10}") int timeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.proxyType = "SOCKS".equalsIgnoreCase(proxyType) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        this.fixedHost = normalize(fixedHost);
        this.fixedPort = Math.max(0, fixedPort);
        this.providerUrl = normalize(providerUrl);
        this.timeoutSeconds = Math.max(3, timeoutSeconds);
        this.directClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(this.timeoutSeconds))
                .build();
    }

    static SourceCollectionProxyProvider disabled() {
        return new SourceCollectionProxyProvider(new ObjectMapper(), false, "HTTP", "", 0, "", 10);
    }

    Optional<Proxy> resolveProxy() {
        if (!enabled) {
            return Optional.empty();
        }
        if (StringUtils.hasText(fixedHost) && fixedPort > 0) {
            return Optional.of(new Proxy(proxyType, new InetSocketAddress(fixedHost, fixedPort)));
        }
        if (!StringUtils.hasText(providerUrl)) {
            return Optional.empty();
        }
        Proxy proxy = cachedProxy;
        if (proxy != null && System.currentTimeMillis() - cachedAtMillis < CACHE_TTL.toMillis()) {
            return Optional.of(proxy);
        }
        Optional<Proxy> loaded = loadProviderProxy();
        loaded.ifPresent(value -> {
            cachedProxy = value;
            cachedAtMillis = System.currentTimeMillis();
        });
        return loaded;
    }

    Connection applyTo(Connection connection) {
        resolveProxy().ifPresent(connection::proxy);
        return connection;
    }

    HttpClient httpClient(Duration timeout) {
        Optional<Proxy> proxy = resolveProxy();
        if (proxy.isEmpty()) {
            return directClient;
        }
        return HttpClient.newBuilder()
                .connectTimeout(timeout)
                .proxy(new FixedProxySelector(proxy.get()))
                .build();
    }

    static ProxySelector proxySelector(Proxy proxy) {
        return new FixedProxySelector(proxy);
    }

    private Optional<Proxy> loadProviderProxy() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(providerUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Accept", "application/json,text/plain,*/*")
                    .build();
            HttpResponse<String> response = directClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            return parseProxy(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private Optional<Proxy> parseProxy(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (root.has("code") && root.get("code").asInt() != 200) {
            return Optional.empty();
        }
        JsonNode candidate = firstProxyNode(root);
        if (candidate == null) {
            return Optional.empty();
        }
        String host = firstText(candidate, "ip", "host");
        int port = firstPort(candidate);
        if (!StringUtils.hasText(host) || port <= 0) {
            return Optional.empty();
        }
        return Optional.of(new Proxy(proxyType, new InetSocketAddress(host, port)));
    }

    private JsonNode firstProxyNode(JsonNode root) {
        JsonNode data = root.get("data");
        if (data != null && data.isArray() && data.size() > 0) {
            return data.get(0);
        }
        if (data != null && data.isObject()) {
            return data;
        }
        if (root.has("ip") || root.has("host")) {
            return root;
        }
        return null;
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return "";
    }

    private int firstPort(JsonNode node) {
        JsonNode port = node.get("port");
        if (port == null) {
            return 0;
        }
        if (port.isInt()) {
            return port.asInt();
        }
        try {
            return Integer.parseInt(port.asText().trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class FixedProxySelector extends ProxySelector {

        private final Proxy proxy;

        private FixedProxySelector(Proxy proxy) {
            this.proxy = proxy;
        }

        @Override
        public List<Proxy> select(URI uri) {
            return Collections.singletonList(proxy);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        }
    }
}
