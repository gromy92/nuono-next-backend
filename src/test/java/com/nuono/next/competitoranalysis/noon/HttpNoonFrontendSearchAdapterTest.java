package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpRequest;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class HttpNoonFrontendSearchAdapterTest {

    @Test
    void buildsLocalizedNoonSearchUrl() {
        HttpNoonFrontendSearchAdapter adapter = new HttpNoonFrontendSearchAdapter(
                new NoonFrontendSearchPageParser(new ObjectMapper()),
                Duration.ofSeconds(3),
                Duration.ofSeconds(9),
                "https://www.noon.com",
                "https://noon-catalog.noon.partners/_svc/catalog/api/u",
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u"
        );

        String url = adapter.buildSearchUrl(NoonSearchRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .keyword("laundry basket")
                .limit(20)
                .build());

        assertEquals("https://www.noon.com/saudi-en/search?q=laundry+basket&limit=20", url);
    }

    @Test
    void buildsLocalizedNoonCatalogSearchUrl() {
        HttpNoonFrontendSearchAdapter adapter = new HttpNoonFrontendSearchAdapter(
                new NoonFrontendSearchPageParser(new ObjectMapper()),
                Duration.ofSeconds(3),
                Duration.ofSeconds(9),
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
                "https://www.noon.com",
                "https://noon-catalog.noon.partners/_svc/catalog/api/u/",
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/"
        );

        String url = adapter.buildCustomerCatalogV3SearchUrl(NoonSearchRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .keyword("Qili")
                .limit(30)
                .build());

        assertEquals("https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=Qili&limit=30", url);
    }

    @Test
    void mapsRateLimitStatusToProviderException() {
        HttpNoonFrontendSearchAdapter adapter = new HttpNoonFrontendSearchAdapter(
                new NoonFrontendSearchPageParser(new ObjectMapper()),
                Duration.ofSeconds(3),
                Duration.ofSeconds(9),
                "https://www.noon.com",
                "https://noon-catalog.noon.partners/_svc/catalog/api/u",
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u"
        );

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
}
