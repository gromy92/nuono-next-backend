package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class HttpNoonFrontendRetryAfterTransportTest {

    @Test
    void javaHttpPathCarriesRetryAfterIntoProviderException() throws Exception {
        assertRetryAfter(false);
    }

    @Test
    void defaultCurlPathCarriesRetryAfterIntoProviderException() throws Exception {
        assertRetryAfter(true);
    }

    private void assertRetryAfter(boolean curlEnabled) throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                0
        );
        server.createContext("/search", exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "67");
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            try (OutputStream response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpNoonFrontendSearchAdapter adapter = new HttpNoonFrontendSearchAdapter(
                    new NoonFrontendSearchPageParser(new ObjectMapper()),
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(3),
                    baseUrl,
                    baseUrl,
                    baseUrl,
                    false,
                    () -> null,
                    curlEnabled
            );
            NoonSearchRequest request = NoonSearchRequest.builder()
                    .siteCode("SA")
                    .locale("en-SA")
                    .keyword("sticky notes")
                    .limit(20)
                    .build();

            NoonSearchProviderException failure = assertThrows(
                    NoonSearchProviderException.class,
                    () -> adapter.searchPage(request)
            );

            assertEquals("RATE_LIMITED", failure.getErrorCode());
            assertEquals(Duration.ofSeconds(67), failure.getRetryAfter());
        } finally {
            server.stop(0);
        }
    }
}
