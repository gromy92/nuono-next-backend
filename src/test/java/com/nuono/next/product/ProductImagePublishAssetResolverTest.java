package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductImagePublishAssetResolverTest {
    private static final String PRODUCTION_MAIN =
            "https://www.nuoon.com/ai/product-image-results/papersay-main-20260725/"
                    + "PAPERSAYSB334-MAIN-v1-3e9798d4.png";

    @TempDir
    Path managedAssetRoot;

    @Test
    void shouldResolveOriginalSevenImageFixtureInOrder() throws Exception {
        byte[] expected = new byte[] {7, 8, 9};
        String relative =
                "papersay-main-20260725/PAPERSAYSB334-MAIN-v1-3e9798d4.png";
        Path storeDir = managedAssetRoot.resolve("STR108065-NSA");
        Files.createDirectories(storeDir);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ai/product-image-results/" + relative, exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, expected.length);
            exchange.getResponseBody().write(expected);
            exchange.close();
        });
        server.start();
        try {
            URI trustedRoot = localTrustedRoot(server);
            ProductImagePublishAssetResolver resolver = resolver(trustedRoot);
            List<String> imageUrls = new ArrayList<>();
            imageUrls.add(trustedRoot.resolve(relative).toString());
            for (int index = 1; index <= 6; index++) {
                String fileName = "detail-" + index + ".png";
                Files.write(storeDir.resolve(fileName), new byte[] {(byte) index});
                imageUrls.add("/api/product-images/assets/STR108065-NSA/" + fileName);
            }
            List<ProductImagePublishAsset> results = new ArrayList<>();
            for (String imageUrl : imageUrls) {
                results.add(resolver.resolve(imageUrl));
            }

            assertEquals(7, results.size());
            assertEquals(imageUrls.get(0), results.get(0).sourceUrl);
            assertEquals("PAPERSAYSB334-MAIN-v1-3e9798d4.png", results.get(0).fileName);
            assertEquals("image/png", results.get(0).contentType);
            assertArrayEquals(expected, results.get(0).content);
            assertEquals(sha256(expected), results.get(0).sha256);
            assertEquals(imageUrls.get(6), results.get(6).sourceUrl);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldAcceptOnlyManagedAndExactProductionStaticAddresses() {
        assertDoesNotThrow(() ->
                ProductImagePublishAssetResolver.validateDefaultAddress(PRODUCTION_MAIN));
        assertDoesNotThrow(() -> ProductImagePublishAssetResolver.validateDefaultAddress(
                "/api/product-images/assets/STR108065-NSA/main.png"
        ));

        List<String> rejected = List.of(
                "https://evil.example/ai/product-image-results/batch/main.png",
                "https://www.nuoon.com/ai/product-image-results/../secret.png",
                PRODUCTION_MAIN + "?download=1",
                "/api/product-images/assets/store/nested/main.png",
                "/api/product-images/assets/store/main.gif"
        );
        for (String imageUrl : rejected) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ProductImagePublishAssetResolver.validateDefaultAddress(imageUrl)
            );
        }
    }

    @Test
    void shouldNotFollowRedirectOrAcceptNonImageResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ai/product-image-results/batch/redirect.png", exchange -> {
            exchange.getResponseHeaders().set("Location", "/ai/product-image-results/batch/main.png");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/ai/product-image-results/batch/not-image.png", exchange -> {
            byte[] body = "<html>not an image</html>".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            URI trustedRoot = localTrustedRoot(server);
            ProductImagePublishAssetResolver resolver = resolver(trustedRoot);

            IllegalStateException redirect = assertThrows(
                    IllegalStateException.class,
                    () -> resolver.resolve(trustedRoot.resolve("batch/redirect.png").toString())
            );
            IllegalStateException wrongType = assertThrows(
                    IllegalStateException.class,
                    () -> resolver.resolve(trustedRoot.resolve("batch/not-image.png").toString())
            );

            assertTrue(redirect.getMessage().contains("HTTP 302"));
            assertTrue(wrongType.getMessage().contains("内容类型"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectUnsupportedAddressWhenCheckpointStarts() {
        ProductImageSuiteAssetRecord asset = new ProductImageSuiteAssetRecord();
        asset.setId(1058L);
        asset.setImageUrl("https://untrusted.example/main.png");
        asset.setSha256("a".repeat(64));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> ProductImagePublishCheckpoint.start(List.of(asset))
        );

        assertTrue(failure.getMessage().contains("仅支持受管商品图资产"));
    }

    @Test
    void shouldRejectMissingManagedAsset() {
        ProductImagePublishAssetResolver resolver = resolver(
                URI.create("https://www.nuoon.com/ai/product-image-results/")
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> resolver.resolve(
                        "/api/product-images/assets/STR108065-NSA/missing.png"
                )
        );

        assertTrue(failure.getMessage().contains("不存在"));
    }

    private ProductImagePublishAssetResolver resolver(URI trustedRoot) {
        return new ProductImagePublishAssetResolver(
                managedAssetRoot,
                trustedRoot,
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build()
        );
    }

    private URI localTrustedRoot(HttpServer server) {
        return URI.create(
                "http://127.0.0.1:" + server.getAddress().getPort()
                        + "/ai/product-image-results/"
        );
    }

    private String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder builder = new StringBuilder();
        for (byte item : digest) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }
}
