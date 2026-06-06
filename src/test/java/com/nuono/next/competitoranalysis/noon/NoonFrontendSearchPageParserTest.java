package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NoonFrontendSearchPageParserTest {

    @Test
    void parsesFixtureAndKeepsEarliestSponsoredDuplicateRank() throws Exception {
        NoonFrontendSearchPageParser parser = new NoonFrontendSearchPageParser(new ObjectMapper());
        String html = Files.readString(Path.of(
                "src",
                "test",
                "resources",
                "competitoranalysis",
                "noon-search-fixture.html"
        ));

        NoonSearchPage page = parser.parse(html, "https://www.noon.com/saudi-en/search?q=laundry%20basket", 200);

        assertEquals("noon-search-html-v1", page.getParserVersion());
        assertEquals(200, page.getProviderHttpStatus());
        assertEquals(2, page.getResults().size());

        NoonSearchResult sponsored = page.getResults().get(0);
        assertEquals("Z11111111AD", sponsored.getNoonProductCode());
        assertEquals(1, sponsored.getPosition());
        assertTrue(sponsored.isSponsored());
        assertEquals("HomePro", sponsored.getBrand());
        assertEquals("SAR", sponsored.getCurrencyCode());

        NoonSearchResult natural = page.getResults().get(1);
        assertEquals("N22222222NAT", natural.getNoonProductCode());
        assertEquals(2, natural.getPosition());
        assertEquals("Natural foldable hamper", natural.getTitle());
        assertEquals(88, natural.getReviewCount());
    }
}
