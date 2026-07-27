package com.nuono.next.noon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class RejectedOtpResponse {
    private final int statusCode;
    private final String body;

    RejectedOtpResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    int statusCode() {
        return statusCode;
    }

    String body() {
        return body;
    }
}

final class RecoveryServer implements AutoCloseable {
    private final HttpServer server;
    private final List<RejectedOtpResponse> validateResponses;
    private final String whoamiBody;
    private final String projectsBody;
    private final AtomicInteger generateCount = new AtomicInteger();
    private final AtomicInteger validateCount = new AtomicInteger();
    private final AtomicInteger sessionCreateCount = new AtomicInteger();
    private final AtomicInteger whoamiCount = new AtomicInteger();
    private final AtomicInteger catalogBootstrapCount = new AtomicInteger();
    private final AtomicInteger catalogCount = new AtomicInteger();
    private volatile int generateStatus = 200;
    private volatile String generateBody = "{\"emailotp\":\"ok\"}";
    private volatile int whoamiStatus = 200;
    private volatile int catalogStatus = 200;
    private volatile boolean catalogSessionCookieRequired;
    private volatile boolean catalogWebSessionBootstrapRequired;
    private volatile String lastCatalogCookieHeader = "";
    private volatile String lastSessionProjectCode;

    RecoveryServer(int validateStatus, String validateBody, String whoamiBody) throws IOException {
        this(
                validateStatus,
                validateBody,
                whoamiBody,
                "{\"projects\":[{\"projectCode\":\"PRJ7001\"}]}"
        );
    }

    RecoveryServer(
            int validateStatus,
            String validateBody,
            String whoamiBody,
            String projectsBody
    ) throws IOException {
        this(
                List.of(new RejectedOtpResponse(validateStatus, validateBody)),
                whoamiBody,
                projectsBody
        );
    }

    RecoveryServer(
            List<RejectedOtpResponse> validateResponses,
            String whoamiBody
    ) throws IOException {
        this(
                validateResponses,
                whoamiBody,
                "{\"projects\":[{\"projectCode\":\"PRJ7001\"}]}"
        );
    }

    RecoveryServer(
            List<RejectedOtpResponse> validateResponses,
            String whoamiBody,
            String projectsBody
    ) throws IOException {
        if (validateResponses == null || validateResponses.isEmpty()) {
            throw new IllegalArgumentException("validateResponses must not be empty");
        }
        this.validateResponses = List.copyOf(validateResponses);
        this.whoamiBody = whoamiBody;
        this.projectsBody = projectsBody;
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    static RecoveryServer forProjects(List<String> projectCodes) throws IOException {
        return new RecoveryServer(
                200,
                "{\"success\":true,\"access_token\":\"token-1\"}",
                null,
                projectsBody(projectCodes)
        );
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/lookup".equals(path)) {
            sendJson(exchange, 200,
                    "[{\"userCode\":\"merchant@example.com\","
                            + "\"channels\":[{\"channelCode\":\"emailotp\"}]}]",
                    null);
        } else if ("/pkce".equals(path)) {
            sendJson(exchange, 200, "{\"success\":true,\"pkce_key\":\"pkce-1\"}", null);
        } else if ("/generate".equals(path)) {
            generateCount.incrementAndGet();
            sendJson(exchange, generateStatus, generateBody, null);
        } else if ("/validate".equals(path)) {
            int invocation = validateCount.incrementAndGet();
            RejectedOtpResponse response = validateResponses.get(Math.min(
                    invocation - 1,
                    validateResponses.size() - 1
            ));
            sendJson(exchange, response.statusCode(), response.body(), null);
        } else if ("/projects".equals(path)) {
            sendJson(exchange, 200, projectsBody, null);
        } else if ("/session-create".equals(path)) {
            sessionCreateCount.incrementAndGet();
            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            );
            lastSessionProjectCode =
                    new ObjectMapper().readTree(body).path("projectCode").asText(null);
            sendJson(exchange, 200, "{\"success\":true}", "sid=recovered; Path=/");
        } else if ("/whoami".equals(path)) {
            whoamiCount.incrementAndGet();
            String body = whoamiBody != null
                    ? whoamiBody
                    : "{\"projectCode\":\"" + lastSessionProjectCode + "\"}";
            sendJson(exchange, whoamiStatus, body, null);
        } else if ("/catalog-bootstrap".equals(path)) {
            handleCatalogBootstrap(exchange);
        } else if ("/catalog".equals(path)) {
            handleCatalog(exchange);
        } else {
            sendJson(exchange, 404, "{\"error\":\"not found\"}", null);
        }
    }

    private void handleCatalogBootstrap(HttpExchange exchange) throws IOException {
        catalogBootstrapCount.incrementAndGet();
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null || !cookieHeader.contains("sid=recovered")) {
            exchange.getResponseHeaders().set(
                    "Location",
                    "https://login.noon.partners/en/?domain=noon-catalog.noon.partners"
            );
            sendJson(exchange, 307, "temporary redirect", null);
        } else {
            sendJson(exchange, 200, "<html>catalog</html>", "catalog_sid=ready; Path=/");
        }
    }

    private void handleCatalog(HttpExchange exchange) throws IOException {
        catalogCount.incrementAndGet();
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        lastCatalogCookieHeader = cookieHeader == null ? "" : cookieHeader;
        boolean missingSession = catalogSessionCookieRequired
                && (cookieHeader == null || !cookieHeader.contains("sid=recovered"));
        boolean missingBootstrap = catalogWebSessionBootstrapRequired
                && (cookieHeader == null || !cookieHeader.contains("catalog_sid=ready"));
        if (catalogStatus == 307 || missingSession || missingBootstrap) {
            exchange.getResponseHeaders().set(
                    "Location",
                    "https://login.noon.partners/en/?domain=noon-catalog.noon.partners"
            );
            sendJson(exchange, 307, "temporary redirect", null);
        } else {
            sendJson(exchange, catalogStatus, "{\"data\":{\"hits\":[],\"total\":0}}", null);
        }
    }

    String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    String catalogUrl(String path) {
        return "http://localhost:" + server.getAddress().getPort() + path;
    }

    int validateCount() {
        return validateCount.get();
    }

    int generateCount() {
        return generateCount.get();
    }

    void failGenerate(int status, String body) {
        generateStatus = status;
        generateBody = body;
    }

    void failWhoami(int status) {
        whoamiStatus = status;
    }

    void failCatalog(int status) {
        catalogStatus = status;
    }

    int sessionCreateCount() {
        return sessionCreateCount.get();
    }

    int whoamiCount() {
        return whoamiCount.get();
    }

    int catalogCount() {
        return catalogCount.get();
    }

    int catalogBootstrapCount() {
        return catalogBootstrapCount.get();
    }

    String lastCatalogCookieHeader() {
        return lastCatalogCookieHeader;
    }

    void redirectCatalogToLogin() {
        catalogStatus = 307;
    }

    void requireCatalogSessionCookie() {
        catalogSessionCookieRequired = true;
    }

    void requireCatalogWebSessionBootstrap() {
        catalogWebSessionBootstrapRequired = true;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void sendJson(
            HttpExchange exchange,
            int status,
            String body,
            String setCookie
    ) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (setCookie != null) {
            exchange.getResponseHeaders().add("Set-Cookie", setCookie);
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String projectsBody(List<String> projectCodes) {
        StringBuilder body = new StringBuilder("{\"projects\":[");
        for (int index = 0; index < projectCodes.size(); index++) {
            if (index > 0) {
                body.append(',');
            }
            body.append("{\"projectCode\":\"")
                    .append(projectCodes.get(index))
                    .append("\"}");
        }
        return body.append("]}").toString();
    }
}
