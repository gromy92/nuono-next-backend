package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class HttpNoonFrontendSearchAdapterTest {

    @Test
    void buildsLocalizedNoonSearchUrl() {
        HttpNoonFrontendSearchAdapter adapter = new HttpNoonFrontendSearchAdapter(
                new NoonFrontendSearchPageParser(new ObjectMapper()),
                Duration.ofSeconds(3),
                Duration.ofSeconds(9),
                "https://www.noon.com"
        );

        String url = adapter.buildSearchUrl(NoonSearchRequest.builder()
                .siteCode("SA")
                .locale("en-SA")
                .keyword("laundry basket")
                .limit(30)
                .build());

        assertEquals("https://www.noon.com/saudi-en/search?q=laundry+basket&limit=30", url);
    }

    @Test
    void mapsRateLimitStatusToProviderException() {
        HttpNoonFrontendSearchAdapter adapter = new HttpNoonFrontendSearchAdapter(
                new NoonFrontendSearchPageParser(new ObjectMapper()),
                Duration.ofSeconds(3),
                Duration.ofSeconds(9),
                "https://www.noon.com"
        );

        NoonSearchProviderException error = assertThrows(
                NoonSearchProviderException.class,
                () -> adapter.mapUnsuccessfulStatus(429, "https://www.noon.com/saudi-en/search?q=x")
        );

        assertEquals("RATE_LIMITED", error.getErrorCode());
        assertEquals(429, error.getProviderHttpStatus());
        assertTrue(error.getMessage().contains("429"));
    }
}
