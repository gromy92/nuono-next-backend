package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class HttpNoonFrontendSearchAdapterTest {

    @Test
    void buildsLocalizedNoonSearchUrlWithTop100Limit() {
        HttpNoonFrontendSearchAdapter adapter = adapter();

        String url = adapter.buildSearchUrl(NoonSearchRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .keyword("laundry basket")
                .limit(100)
                .build());

        assertEquals("https://www.noon.com/saudi-en/search?q=laundry+basket&limit=100", url);
    }

    @Test
    void buildsLocalizedNoonCatalogSearchUrl() {
        HttpNoonFrontendSearchAdapter adapter = adapter(
                "https://www.noon.com",
                "https://noon-catalog.noon.partners/_svc/catalog/api/u/",
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/"
        );

        String url = adapter.buildCatalogSearchUrl(NoonSearchRequest.builder()
                .siteCode("AE")
                .locale("en-AE")
                .keyword("star shaped sticky notes")
                .limit(20)
                .build());

        assertEquals("https://noon-catalog.noon.partners/_svc/catalog/api/u/uae-en/search?q=star+shaped+sticky+notes&limit=20", url);
    }

    @Test
    void buildsNoonCustomerCatalogV3SearchUrlWithoutMarketPath() {
        HttpNoonFrontendSearchAdapter adapter = new HttpNoonFrontendSearchAdapter(
                new NoonFrontendSearchPageParser(new ObjectMapper()),
                Duration.ofSeconds(3),
                Duration.ofSeconds(9),
                "https://www.noon.com"
        );

        String url = adapter.buildCustomerCatalogV3SearchUrl(NoonSearchRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .keyword("name tag")
                .limit(100)
                .build());

        assertEquals("https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=name+tag&limit=100", url);
    }

    @Test
    void capsSearchLimitAtTop100() {
        HttpNoonFrontendSearchAdapter adapter = adapter();

        String url = adapter.buildCustomerCatalogV3SearchUrl(NoonSearchRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .keyword("name tag")
                .limit(200)
                .build());

        assertEquals("https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=name+tag&limit=100", url);
    }

    @Test
    void mapsRateLimitStatusToProviderException() {
        HttpNoonFrontendSearchAdapter adapter = adapter();

        NoonSearchProviderException error = assertThrows(
                NoonSearchProviderException.class,
                () -> adapter.mapUnsuccessfulStatus(429, "https://www.noon.com/saudi-en/search?q=x")
        );

        assertEquals("RATE_LIMITED", error.getErrorCode());
        assertEquals(429, error.getProviderHttpStatus());
        assertTrue(error.getMessage().contains("429"));
    }

    @Test
    void buildsCustomerCatalogV3RequestWithNoonFrontendCookieAndBrowserHeaders() {
        HttpNoonFrontendSearchAdapter adapter = new HttpNoonFrontendSearchAdapter(
                new NoonFrontendSearchPageParser(new ObjectMapper()),
                Duration.ofSeconds(3),
                Duration.ofSeconds(9),
                "https://www.noon.com",
                "https://noon-catalog.noon.partners/_svc/catalog/api/u",
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u",
                true,
                () -> "visitor_id=visitor-test; nguestv2=session-test"
        );
        NoonSearchRequest searchRequest = NoonSearchRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .keyword("Qili")
                .limit(20)
                .build();

        HttpRequest request = adapter.buildCustomerCatalogV3SearchRequest(
                searchRequest,
                adapter.buildCustomerCatalogV3SearchUrl(searchRequest),
                "visitor_id=visitor-test; nguestv2=session-test"
        );

        assertEquals("visitor_id=visitor-test; nguestv2=session-test", request.headers().firstValue("Cookie").orElse(""));
        assertEquals("https://www.noon.com/saudi-en/search/?originalQuery=Qili&q=Qili", request.headers().firstValue("Referer").orElse(""));
        assertEquals("SA-RUH-S17", request.headers().firstValue("X-Ecom-Zonecode").orElse(""));
        assertEquals("W00083496A", request.headers().firstValue("X-Rocket-Zonecode").orElse(""));
        assertTrue(request.headers().firstValue("Sec-Fetch-Site").orElse("").contains("same-origin"));
    }

    @Test
    void buildsCustomerCatalogV3CurlConfigWithCookieOnStdinConfig() {
        HttpNoonFrontendSearchAdapter adapter = new HttpNoonFrontendSearchAdapter(
                new NoonFrontendSearchPageParser(new ObjectMapper()),
                Duration.ofSeconds(3),
                Duration.ofSeconds(9),
                "https://www.noon.com",
                "https://noon-catalog.noon.partners/_svc/catalog/api/u",
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u",
                true,
                () -> "visitor_id=visitor-test; nguestv2=session-test",
                true
        );
        NoonSearchRequest searchRequest = NoonSearchRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .keyword("Qili")
                .limit(20)
                .build();

        String config = adapter.buildCustomerCatalogV3CurlConfig(
                searchRequest,
                adapter.buildCustomerCatalogV3SearchUrl(searchRequest),
                "visitor_id=visitor-test; nguestv2=session-test"
        );

        assertTrue(config.contains("url = \"https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=Qili&limit=20\""));
        assertTrue(config.contains("header = \"cookie: visitor_id=visitor-test; nguestv2=session-test\""));
        assertTrue(config.contains("header = \"x-locale: en-sa\""));
        assertTrue(config.contains("header = \"referer: https://www.noon.com/saudi-en/search/?originalQuery=Qili&q=Qili\""));
    }

    @Test
    void configuredFrontendCookieHeaderTakesPrecedenceOverChromeCookieSupplier() {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        HttpNoonFrontendSearchAdapter adapter = new HttpNoonFrontendSearchAdapter(
                new NoonFrontendSearchPageParser(new ObjectMapper()),
                Duration.ofSeconds(3),
                Duration.ofSeconds(9),
                "https://www.noon.com",
                "https://noon-catalog.noon.partners/_svc/catalog/api/u",
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u",
                "visitor_id=configured-visitor; nguestv2=configured-session",
                true,
                () -> {
                    supplierCalled.set(true);
                    throw new IllegalStateException("local chrome should not be read");
                },
                true
        );

        String cookieHeader = adapter.loadFrontendCookieHeader("https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search");

        assertEquals("visitor_id=configured-visitor; nguestv2=configured-session", cookieHeader);
        assertEquals(false, supplierCalled.get());
    }

    @Test
    void throwsProviderExceptionWhenChromeCookieIsUnavailable() {
        HttpNoonFrontendSearchAdapter adapter = new HttpNoonFrontendSearchAdapter(
                new NoonFrontendSearchPageParser(new ObjectMapper()),
                Duration.ofSeconds(3),
                Duration.ofSeconds(9),
                "https://www.noon.com",
                "https://noon-catalog.noon.partners/_svc/catalog/api/u",
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u",
                true,
                () -> {
                    throw new IllegalStateException("keychain unavailable");
                },
                true
        );

        NoonSearchProviderException error = assertThrows(
                NoonSearchProviderException.class,
                () -> adapter.loadFrontendCookieHeader("https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=name+tag&limit=100")
        );

        assertEquals("PROVIDER_UNAVAILABLE", error.getErrorCode());
        assertTrue(error.getMessage().contains("缺少浏览器会话"));
    }

    private HttpNoonFrontendSearchAdapter adapter() {
        return adapter(
                "https://www.noon.com",
                "https://noon-catalog.noon.partners/_svc/catalog/api/u",
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u"
        );
    }

    private HttpNoonFrontendSearchAdapter adapter(String baseUrl, String catalogBaseUrl, String customerCatalogV3BaseUrl) {
        return new HttpNoonFrontendSearchAdapter(
                new NoonFrontendSearchPageParser(new ObjectMapper()),
                Duration.ofSeconds(3),
                Duration.ofSeconds(9),
                baseUrl,
                catalogBaseUrl,
                customerCatalogV3BaseUrl
        );
    }
}
