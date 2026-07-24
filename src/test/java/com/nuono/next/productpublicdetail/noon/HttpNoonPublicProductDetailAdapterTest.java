package com.nuono.next.productpublicdetail.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.nuono.next.competitoranalysis.noon.NoonFrontendSearchPageParser;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.competitoranalysis.noon.NoonSearchRequest;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpNoonPublicProductDetailAdapterTest {

    @Test
    void exactMatchReturnsPartialPublicDetail() {
        StubAdapter adapter = new StubAdapter(page(result("ZABCDEF12", "Canman Bag")), false, "");

        NoonPublicProductDetailResult detail = adapter.fetch(NoonPublicProductDetailRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .noonProductCode("zabcdef12")
                .build());

        assertEquals(ProductPublicDetailSyncStatus.PARTIAL, detail.getStatus());
        assertEquals("PARTIAL_DETAIL", detail.getFailureCode());
        assertEquals("ZABCDEF12", detail.getNoonProductCode());
        assertEquals("Canman Bag", detail.getTitleEn());
        assertEquals(new BigDecimal("19.90"), detail.getPriceAmount());
        assertEquals("https://www.noon.com/saudi-en/canman/p/", detail.getDetailUrl());
        assertEquals("noon-search-customer-catalog-v3", detail.getProviderParserVersion());
    }

    @Test
    void fetchUsesFrontendCatalogDetailEndpointBeforeSearch() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        AtomicReference<String> requestedPath = new AtomicReference<>();
        AtomicReference<String> cookieHeader = new AtomicReference<>();
        server.createContext("/", exchange -> {
            requestedPath.set(exchange.getRequestURI().toString());
            cookieHeader.set(exchange.getRequestHeaders().getFirst("Cookie"));
            if (exchange.getRequestURI().getPath().endsWith("/ZABCDEF12/p")) {
                sendJson(exchange, 200, "{"
                        + "\"catalog\":{"
                        + "\"type\":\"detail\","
                        + "\"product\":{"
                        + "\"sku\":\"ZABCDEF12\","
                        + "\"product_title\":\"Frontend Detail Bag\","
                        + "\"brand\":\"Canman\","
                        + "\"image_urls\":[\"https://f.nooncdn.com/products/abc.jpg\"],"
                        + "\"canonical_url\":\"/saudi-en/front-detail-bag/ZABCDEF12/p/\","
                        + "\"price\":{\"amount\":24.50,\"currency\":\"SAR\"},"
                        + "\"rating\":4.4,"
                        + "\"reviews_count\":12"
                        + "}"
                        + "}"
                        + "}");
                return;
            }
            sendJson(exchange, 200, "{\"hits\":[{\"sku\":\"ZOTHER123\",\"title\":\"Wrong product\"}]}");
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort()
                    + "/_vs/nc/mp-customer-catalog-api/api/v3/u";
            HttpNoonPublicProductDetailAdapter adapter = new HttpNoonPublicProductDetailAdapter(
                    new NoonFrontendSearchPageParser(new ObjectMapper()),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(3),
                    "https://www.noon.com",
                    baseUrl,
                    "visitor_id=test",
                    false
            );

            NoonPublicProductDetailResult detail = adapter.fetch(NoonPublicProductDetailRequest.builder()
                    .siteCode("SA")
                    .locale("en-SA")
                    .noonProductCode("ZABCDEF12")
                    .build());

            assertEquals(ProductPublicDetailSyncStatus.PARTIAL, detail.getStatus());
            assertEquals("PARTIAL_DETAIL", detail.getFailureCode());
            assertEquals("Frontend Detail Bag", detail.getTitleEn());
            assertEquals("Canman", detail.getBrand());
            assertEquals(0, new BigDecimal("24.50").compareTo(detail.getPriceAmount()));
            assertEquals("SAR", detail.getCurrencyCode());
            assertEquals(new BigDecimal("4.4"), detail.getRating());
            assertEquals(12, detail.getReviewCount());
            assertEquals("https://f.nooncdn.com/products/abc.jpg", detail.getMainImageUrl());
            assertEquals("https://www.noon.com/saudi-en/front-detail-bag/ZABCDEF12/p/", detail.getDetailUrl());
            assertTrue(detail.getProviderSourceUrl().endsWith("/ZABCDEF12/p"));
            assertEquals("noon-frontend-catalog-detail-v1", detail.getProviderParserVersion());
            assertEquals("visitor_id=test", cookieHeader.get());
            assertTrue(requestedPath.get().endsWith("/ZABCDEF12/p"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void frontendCatalogDetailUsesCurlWhenCurlIsEnabled() {
        AtomicInteger httpCount = new AtomicInteger();
        AtomicInteger curlCount = new AtomicInteger();
        DetailTransportSpyAdapter adapter = new DetailTransportSpyAdapter(true, httpCount, curlCount);

        NoonPublicProductDetailResult detail = adapter.fetch(NoonPublicProductDetailRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .noonProductCode("ZABCDEF12")
                .build());

        assertEquals(ProductPublicDetailSyncStatus.PARTIAL, detail.getStatus());
        assertEquals("curl", detail.getProviderParserVersion());
        assertEquals(0, httpCount.get());
        assertEquals(1, curlCount.get());
    }

    @Test
    void frontendCatalogDetailIsSkippedWithoutFrontendCookie() {
        AtomicInteger httpCount = new AtomicInteger();
        AtomicInteger curlCount = new AtomicInteger();
        DetailTransportSpyAdapter adapter = new DetailTransportSpyAdapter(true, httpCount, curlCount, "");

        NoonPublicProductDetailResult detail = adapter.fetch(NoonPublicProductDetailRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .noonProductCode("ZABCDEF12")
                .build());

        assertEquals(ProductPublicDetailSyncStatus.PARTIAL, detail.getStatus());
        assertEquals("Canman Bag", detail.getTitleEn());
        assertEquals(0, httpCount.get());
        assertEquals(0, curlCount.get());
    }

    @Test
    void missingExactMatchReturnsNotFound() {
        StubAdapter adapter = new StubAdapter(page(result("ZOTHER123", "Other")), false, "");

        NoonPublicProductDetailResult detail = adapter.fetch(NoonPublicProductDetailRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .noonProductCode("ZABCDEF12")
                .build());

        assertEquals(ProductPublicDetailSyncStatus.NOT_FOUND, detail.getStatus());
        assertEquals("PUBLIC_DETAIL_NOT_FOUND", detail.getFailureCode());
        assertEquals("ZABCDEF12", detail.getNoonProductCode());
    }

    @Test
    void providerExceptionReturnsFailedStatus() {
        StubAdapter adapter = new StubAdapter(null, false, "");
        adapter.failure = new NoonSearchProviderException(
                "RATE_LIMITED",
                "Noon 前台限流",
                429,
                "https://www.noon.com/_vs/nc/search",
                "hash-1"
        );

        NoonPublicProductDetailResult detail = adapter.fetch(NoonPublicProductDetailRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .noonProductCode("ZABCDEF12")
                .build());

        assertEquals(ProductPublicDetailSyncStatus.FAILED, detail.getStatus());
        assertEquals("RATE_LIMITED", detail.getFailureCode());
        assertEquals(429, detail.getProviderHttpStatus());
        assertEquals("hash-1", detail.getProviderResponseHash());
    }

    @Test
    void http403DoesNotLookLikeRealPublicDetailNotFound() {
        StubAdapter adapter = new StubAdapter(null, false, "");
        adapter.failure = new NoonSearchProviderException(
                "PARSE_FAILED",
                "Noon 前台公开搜索返回 HTTP 403。",
                403,
                "https://www.noon.com/_vs/nc/search",
                null
        );
        adapter.catalogFailure = new NoonSearchProviderException(
                "PARSE_FAILED",
                "Noon catalog partner 公开搜索返回 HTTP 403。",
                403,
                "https://noon-catalog.noon.partners/_svc/catalog/api/u/saudi-en/search",
                null
        );

        NoonPublicProductDetailResult detail = adapter.fetch(NoonPublicProductDetailRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .noonProductCode("ZABCDEF12")
                .build());

        assertEquals(ProductPublicDetailSyncStatus.FAILED, detail.getStatus());
        assertEquals("BLOCKED_BY_RISK_CONTROL", detail.getFailureCode());
        assertEquals(403, detail.getProviderHttpStatus());
    }

    @Test
    void neverFallsBackToCatalogPartnerWhenNoonFrontendIsUnavailable() {
        StubAdapter adapter = new StubAdapter(null, false, "");
        adapter.failure = new NoonSearchProviderException(
                "PROVIDER_UNAVAILABLE",
                "Noon customer catalog timed out",
                null,
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=ZABCDEF12&limit=20",
                null
        );
        adapter.catalogFallbackPage = page(result("ZABCDEF12", "Canman Bag"));
        adapter.catalogFallbackPage.setSourceUrl("https://noon-catalog.noon.partners/_svc/catalog/api/u/uae-en/search?q=ZABCDEF12&limit=20");

        NoonPublicProductDetailResult detail = adapter.fetch(NoonPublicProductDetailRequest.builder()
                .siteCode("AE")
                .locale("en-AE")
                .noonProductCode("ZABCDEF12")
                .build());

        assertEquals(ProductPublicDetailSyncStatus.FAILED, detail.getStatus());
        assertEquals("PROVIDER_UNAVAILABLE", detail.getFailureCode());
        assertTrue(detail.getProviderSourceUrl().contains("www.noon.com/_vs/nc/"));
        assertEquals(0, adapter.catalogRequestCount.get());
    }

    @Test
    void catalogPartnerFallbackCannotBeEnabledForProductDetail() {
        StubAdapter adapter = new StubAdapter(null, false, "", true);
        adapter.failure = new NoonSearchProviderException(
                "PROVIDER_UNAVAILABLE",
                "Noon customer catalog timed out",
                null,
                "https://gateway.example.test/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=ZABCDEF12&limit=20",
                null
        );
        adapter.catalogFallbackPage = page(result("ZABCDEF12", "Catalog Partner Bag"));
        adapter.catalogFallbackPage.setSourceUrl("https://noon-catalog.noon.partners/_svc/catalog/api/u/saudi-en/search?q=ZABCDEF12&limit=20");

        NoonPublicProductDetailResult detail = adapter.fetch(NoonPublicProductDetailRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .noonProductCode("ZABCDEF12")
                .build());

        assertEquals(ProductPublicDetailSyncStatus.FAILED, detail.getStatus());
        assertEquals("PROVIDER_UNAVAILABLE", detail.getFailureCode());
        assertTrue(detail.getProviderSourceUrl().contains("gateway.example.test"));
        assertFalse(detail.getProviderSourceUrl().contains("noon-catalog.noon.partners"));
        assertEquals(0, adapter.catalogRequestCount.get());
    }

    @Test
    void fallsBackToFrontendHtmlSearchBeforeCatalogPartner() {
        StubAdapter adapter = new StubAdapter(null, false, "");
        adapter.failure = new NoonSearchProviderException(
                "PROVIDER_UNAVAILABLE",
                "Noon customer catalog returned 500",
                500,
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=ZABCDEF12&limit=20",
                null
        );
        adapter.htmlFallbackPage = page(result("ZABCDEF12", "Canman Bag"));
        adapter.htmlFallbackPage.setSourceUrl("https://www.noon.com/saudi-en/search/?originalQuery=ZABCDEF12&q=ZABCDEF12");
        adapter.catalogFallbackPage = page(result("ZABCDEF12", "Catalog Partner Bag"));
        adapter.catalogFallbackPage.setSourceUrl("https://noon-catalog.noon.partners/_svc/catalog/api/u/saudi-en/search?q=ZABCDEF12&limit=20");

        NoonPublicProductDetailResult detail = adapter.fetch(NoonPublicProductDetailRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .noonProductCode("ZABCDEF12")
                .build());

        assertEquals(ProductPublicDetailSyncStatus.PARTIAL, detail.getStatus());
        assertTrue(detail.getProviderSourceUrl().contains("www.noon.com/saudi-en/search/"));
        assertEquals("Canman Bag", detail.getTitleEn());
    }

    @Test
    void genericFrontendFailuresDoNotUseCatalogPartnerFallback() {
        StubAdapter adapter = new StubAdapter(null, false, "");
        adapter.runtimeFailure = new IllegalStateException("customer transport exploded");
        adapter.catalogRuntimeFailure = new IllegalStateException("catalog transport exploded");

        NoonPublicProductDetailResult detail = adapter.fetch(NoonPublicProductDetailRequest.builder()
                .siteCode("AE")
                .locale("en-AE")
                .noonProductCode("ZABCDEF12")
                .build());

        assertEquals(ProductPublicDetailSyncStatus.FAILED, detail.getStatus());
        assertEquals("PROVIDER_UNAVAILABLE", detail.getFailureCode());
        assertTrue(detail.getProviderSourceUrl().contains("www.noon.com/_vs/nc/"));
        assertTrue(detail.getFailureMessage().contains("customer transport exploded"));
        assertEquals(0, adapter.catalogRequestCount.get());
    }

    @Test
    void requestDoesNotSendCookieUnlessExplicitlyConfigured() {
        StubAdapter adapter = new StubAdapter(page(result("ZABCDEF12", "Canman Bag")), false, "");
        NoonSearchRequest request = NoonSearchRequest.builder().siteCode("SA").locale("en-SA").keyword("ZABCDEF12").limit(20).build();

        HttpRequest noCookie = adapter.buildCustomerCatalogV3SearchRequest(request, adapter.buildCustomerCatalogV3SearchUrl(request), "");
        HttpRequest withCookie = adapter.buildCustomerCatalogV3SearchRequest(request, adapter.buildCustomerCatalogV3SearchUrl(request), "visitor_id=abc");

        assertFalse(noCookie.headers().firstValue("Cookie").isPresent());
        assertTrue(withCookie.headers().firstValue("Cookie").isPresent());
        assertEquals("visitor_id=abc", withCookie.headers().firstValue("Cookie").orElseThrow());
    }

    @Test
    void fetchLoadsBrowserFrontendCookieWhenNoCookieIsConfigured() {
        AtomicInteger loadCount = new AtomicInteger();
        StubAdapter adapter = new StubAdapter(
                page(result("ZABCDEF12", "Canman Bag")),
                false,
                "",
                true,
                () -> {
                    loadCount.incrementAndGet();
                    return "visitor_id=chrome";
                }
        );

        NoonPublicProductDetailResult detail = adapter.fetch(NoonPublicProductDetailRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .noonProductCode("ZABCDEF12")
                .build());

        assertEquals(ProductPublicDetailSyncStatus.PARTIAL, detail.getStatus());
        assertEquals("visitor_id=chrome", adapter.lastFrontendCookieHeader);
        assertEquals(1, loadCount.get());
    }

    @Test
    void curlCommandUsesCompressedResponsesWithoutForcingHttp11() {
        StubAdapter adapter = new StubAdapter(page(result("ZABCDEF12", "Canman Bag")), true, "");

        assertTrue(adapter.buildCurlCommand().contains("--compressed"));
        assertFalse(adapter.buildCurlCommand().contains("--http1.1"));
    }

    private static NoonSearchPage page(NoonSearchResult result) {
        NoonSearchPage page = new NoonSearchPage();
        page.setSourceUrl("https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=ZABCDEF12&limit=20");
        page.setParserVersion("noon-search-customer-catalog-v3");
        page.setProviderHttpStatus(200);
        page.setResponseHash("hash-123");
        page.setResults(List.of(result));
        return page;
    }

    private static NoonSearchResult result(String code, String title) {
        NoonSearchResult result = new NoonSearchResult();
        result.setNoonProductCode(code);
        result.setCodeType("Z_CODE");
        result.setCanonicalUrl("/saudi-en/canman/p/");
        result.setTitle(title);
        result.setBrand("Canman");
        result.setPriceAmount(new BigDecimal("19.90"));
        result.setCurrencyCode("SAR");
        result.setRawResultJson("{\"sku\":\"" + code + "\"}");
        return result;
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static final class StubAdapter extends HttpNoonPublicProductDetailAdapter {
        private final NoonSearchPage page;
        private NoonSearchProviderException failure;
        private NoonSearchProviderException catalogFailure;
        private RuntimeException runtimeFailure;
        private NoonSearchPage htmlFallbackPage;
        private NoonSearchPage catalogFallbackPage;
        private RuntimeException htmlRuntimeFailure;
        private RuntimeException catalogRuntimeFailure;
        private String lastFrontendCookieHeader;
        private final AtomicInteger catalogRequestCount = new AtomicInteger();

        private StubAdapter(NoonSearchPage page, boolean curlEnabled, String cookie) {
            this(page, curlEnabled, cookie, true);
        }

        private StubAdapter(NoonSearchPage page, boolean curlEnabled, String cookie, boolean catalogPartnerFallbackEnabled) {
            this(page, curlEnabled, cookie, false, () -> null, catalogPartnerFallbackEnabled);
        }

        private StubAdapter(NoonSearchPage page, boolean curlEnabled, String cookie, boolean chromeCookieEnabled,
                            java.util.function.Supplier<String> frontendCookieHeaderSupplier) {
            this(page, curlEnabled, cookie, chromeCookieEnabled, frontendCookieHeaderSupplier, true);
        }

        private StubAdapter(NoonSearchPage page, boolean curlEnabled, String cookie, boolean chromeCookieEnabled,
                            java.util.function.Supplier<String> frontendCookieHeaderSupplier, boolean catalogPartnerFallbackEnabled) {
            super(
                    new NoonFrontendSearchPageParser(new ObjectMapper()),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(3),
                    "https://www.noon.com",
                    "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u",
                    cookie,
                    chromeCookieEnabled,
                    frontendCookieHeaderSupplier,
                    curlEnabled
            );
            this.page = page;
        }

        @Override
        NoonPublicProductDetailResult fetchFrontendCatalogDetail(NoonSearchRequest searchRequest, String code) {
            return null;
        }

        @Override
        NoonSearchPage fetchSearchPageWithHttp(NoonSearchRequest request, String url, String frontendCookieHeader) {
            lastFrontendCookieHeader = frontendCookieHeader;
            if (url.contains("/search/?")) {
                if (htmlRuntimeFailure != null) {
                    throw htmlRuntimeFailure;
                }
                if (htmlFallbackPage == null) {
                    throw new NoonSearchProviderException("PROVIDER_UNAVAILABLE", "html unavailable", null, url, null);
                }
                return htmlFallbackPage;
            }
            if (url.contains("noon-catalog.noon.partners")) {
                catalogRequestCount.incrementAndGet();
                if (catalogFailure != null) {
                    throw catalogFailure;
                }
                if (catalogRuntimeFailure != null) {
                    throw catalogRuntimeFailure;
                }
                return catalogFallbackPage;
            }
            if (runtimeFailure != null) {
                throw runtimeFailure;
            }
            if (failure != null) {
                throw failure;
            }
            return page;
        }
    }

    private static final class DetailTransportSpyAdapter extends HttpNoonPublicProductDetailAdapter {
        private final AtomicInteger httpCount;
        private final AtomicInteger curlCount;

        private DetailTransportSpyAdapter(boolean curlEnabled, AtomicInteger httpCount, AtomicInteger curlCount) {
            this(curlEnabled, httpCount, curlCount, "visitor_id=test");
        }

        private DetailTransportSpyAdapter(boolean curlEnabled, AtomicInteger httpCount, AtomicInteger curlCount, String cookie) {
            super(
                    new NoonFrontendSearchPageParser(new ObjectMapper()),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(3),
                    "https://www.noon.com",
                    "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u",
                    cookie,
                    false,
                    () -> null,
                    curlEnabled
            );
            this.httpCount = httpCount;
            this.curlCount = curlCount;
        }

        @Override
        NoonPublicProductDetailResult fetchFrontendCatalogDetailWithHttp(
                NoonSearchRequest searchRequest,
                String code,
                String url,
                String frontendCookieHeader
        ) {
            httpCount.incrementAndGet();
            return partialFromTransport(code, "http");
        }

        @Override
        NoonPublicProductDetailResult fetchFrontendCatalogDetailWithCurl(
                NoonSearchRequest searchRequest,
                String code,
                String url,
                String frontendCookieHeader
        ) {
            curlCount.incrementAndGet();
            return partialFromTransport(code, "curl");
        }

        @Override
        NoonSearchPage fetchSearchPageWithHttp(NoonSearchRequest request, String url, String frontendCookieHeader) {
            return page(result("ZABCDEF12", "Canman Bag"));
        }

        @Override
        NoonSearchPage fetchSearchPageWithCurl(NoonSearchRequest request, String url, String frontendCookieHeader) {
            return page(result("ZABCDEF12", "Canman Bag"));
        }

        private NoonPublicProductDetailResult partialFromTransport(String code, String transport) {
            NoonPublicProductDetailResult result = new NoonPublicProductDetailResult();
            result.setStatus(ProductPublicDetailSyncStatus.PARTIAL);
            result.setNoonProductCode(code);
            result.setFailureCode("PARTIAL_DETAIL");
            result.setProviderParserVersion(transport);
            return result;
        }
    }
}
