package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceCollectionPageFetchClientTest {

    @Test
    void appendsConfiguredGatewayQueryParametersWithoutLeakingThemIntoTargetUrl() throws Exception {
        List<String> queries = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/", exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            byte[] bytes = "<html><head><title>ok</title></head><body>ok</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            SourceCollectionPageFetchClient client = new SourceCollectionPageFetchClient(
                    true,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/",
                    "test-page-fetch-key",
                    "bypass=generic_level_2&render_js=true&residential=true&country=us",
                    10
            );

            client.fetchDocument("https://ar.shein.com/Product-p-33710994.html?mallCode=1");

            assertEquals(1, queries.size());
            String query = URLDecoder.decode(queries.get(0), StandardCharsets.UTF_8);
            assertTrue(query.contains("api_key=test-page-fetch-key"));
            assertTrue(query.contains("url=https://ar.shein.com/Product-p-33710994.html?mallCode=1"));
            assertTrue(query.contains("keep_headers=true"));
            assertTrue(query.contains("bypass=generic_level_2"));
            assertTrue(query.contains("render_js=true"));
            assertTrue(query.contains("residential=true"));
            assertTrue(query.contains("country=us"));
        } finally {
            server.stop(0);
        }
    }
}
