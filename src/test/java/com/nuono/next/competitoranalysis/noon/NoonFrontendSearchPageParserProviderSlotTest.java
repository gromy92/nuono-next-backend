package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class NoonFrontendSearchPageParserProviderSlotTest {

    @Test
    void countsRawProviderSlotsAndPreservesUnparseableAndDuplicateGaps() {
        NoonFrontendSearchPageParser parser =
                new NoonFrontendSearchPageParser(new ObjectMapper());
        String json = String.join(
                "\n",
                "{",
                "  \"page\": 1,",
                "  \"limit\": 100,",
                "  \"nbHits\": 300,",
                "  \"nbPages\": 3,",
                "  \"hits\": [",
                "    {\"sku\": \"N11111111A\", \"name\": \"First\"},",
                "    {\"sku\": \"N11111111A\", \"name\": \"Duplicate\"},",
                "    {\"name\": \"Missing product code\"},",
                "    {\"sku\": \"N22222222A\", \"name\": \"Third\"}",
                "  ]",
                "}"
        );

        NoonSearchPage page = parser.parseCatalogJson(
                json,
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=test&limit=100",
                200
        );

        assertEquals(4, page.getProviderResultSlotCount());
        assertEquals(4, page.getProviderOrganicSlotCount());
        assertEquals(0, page.getProviderSponsoredSlotCount());
        assertEquals(2, page.getResults().size());
        assertEquals(
                1,
                page.getResults().get(0).getRankPosition()
        );
        assertEquals(
                4,
                page.getResults().get(1).getRankPosition()
        );
    }
}
