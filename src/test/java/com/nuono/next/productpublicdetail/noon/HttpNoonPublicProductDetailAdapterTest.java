package com.nuono.next.productpublicdetail.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.competitoranalysis.noon.NoonFrontendSearchPageParser;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.competitoranalysis.noon.NoonSearchRequest;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
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
    void fallsBackToCatalogPartnerWhenCustomerCatalogIsUnavailable() {
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

        assertEquals(ProductPublicDetailSyncStatus.PARTIAL, detail.getStatus());
        assertEquals("ZABCDEF12", detail.getNoonProductCode());
        assertTrue(detail.getProviderSourceUrl().contains("noon-catalog.noon.partners"));
    }

    @Test
    void genericCustomerAndFallbackFailuresReportFallbackSource() {
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
        assertTrue(detail.getProviderSourceUrl().contains("noon-catalog.noon.partners"));
        assertTrue(detail.getFailureMessage().contains("catalog transport exploded"));
        assertFalse(detail.getFailureMessage().contains("customer transport exploded"));
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
    void curlCommandForcesHttp11ForNoonPublicProvider() {
        StubAdapter adapter = new StubAdapter(page(result("ZABCDEF12", "Canman Bag")), true, "");

        assertTrue(adapter.buildCurlCommand().contains("--http1.1"));
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

    private static final class StubAdapter extends HttpNoonPublicProductDetailAdapter {
        private final NoonSearchPage page;
        private NoonSearchProviderException failure;
        private RuntimeException runtimeFailure;
        private NoonSearchPage catalogFallbackPage;
        private RuntimeException catalogRuntimeFailure;

        private StubAdapter(NoonSearchPage page, boolean curlEnabled, String cookie) {
            super(
                    new NoonFrontendSearchPageParser(new ObjectMapper()),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(3),
                    "https://www.noon.com",
                    "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u",
                    cookie,
                    curlEnabled
            );
            this.page = page;
        }

        @Override
        NoonSearchPage fetchSearchPageWithHttp(NoonSearchRequest request, String url, String frontendCookieHeader) {
            if (url.contains("noon-catalog.noon.partners")) {
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
}
