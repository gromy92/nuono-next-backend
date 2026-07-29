package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NoonFrontendSearchPageParserRankChannelTest {

    @Test
    void acceptsAnExplicitZeroHitListResponse() {
        NoonFrontendSearchPageParser parser =
                new NoonFrontendSearchPageParser(new ObjectMapper());

        NoonSearchPage page = parser.parseCatalogJson(
                "{\"nbHits\":0,\"nbPages\":0,"
                        + "\"search\":{\"page\":1,\"limit\":100},"
                        + "\"hits\":[]}",
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=missing&limit=100",
                200
        );

        assertTrue(page.getResults().isEmpty());
        assertEquals(0, page.getTotalHits());
        assertEquals(0, page.getTotalPages());
    }

    @Test
    void preservesSponsoredAndOrganicAppearancesOfTheSameProduct() {
        NoonFrontendSearchPageParser parser = parserAt("2026-07-26T18:00:00Z");
        NoonSearchPage page = parser.parseCatalogJson(
                duplicateChannelPayload(),
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=pencil&limit=100",
                200
        );

        List<NoonSearchResult> duplicateResults = page.getResults().stream()
                .filter(result -> "Z11111111AD".equals(result.getNoonProductCode()))
                .collect(Collectors.toList());

        assertEquals(2, duplicateResults.size());
        assertTrue(duplicateResults.stream().anyMatch(NoonSearchResult::isSponsored));
        assertTrue(duplicateResults.stream().anyMatch(result -> !result.isSponsored()));
        assertEquals(1, rankPosition(duplicateResults.get(0)));
        assertEquals(1, rankPosition(duplicateResults.get(1)));
    }

    @Test
    void recordsCapturedAtInShanghaiBusinessTime() {
        NoonFrontendSearchPageParser parser = parserAt("2026-07-26T18:00:00Z");
        NoonSearchPage page = parser.parseCatalogJson(
                "{\"hits\":[{\"sku\":\"N22222222NAT\",\"name\":\"Natural pencil set\"}]}",
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=pencil&limit=100",
                200
        );

        assertEquals(LocalDateTime.parse("2026-07-27T02:00:00"), page.getCapturedAt());
    }

    @Test
    void preservesProviderHitOrderWhenALaterHitIsSponsored() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode hits = payload.putArray("hits");
        for (int position = 1; position <= 21; position++) {
            ObjectNode hit = hits.addObject();
            hit.put("sku", String.format("N%08d", position));
            hit.put("name", "Product " + position);
            hit.put("is_sponsored", position == 21);
        }

        NoonSearchPage page = new NoonFrontendSearchPageParser(objectMapper).parseCatalogJson(
                payload.toString(),
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=pencil&limit=100",
                200
        );

        assertEquals("N00000020", page.getResults().get(19).getNoonProductCode());
        assertEquals(20, page.getResults().get(19).getPosition());
        assertEquals("N00000021", page.getResults().get(20).getNoonProductCode());
        assertEquals(21, page.getResults().get(20).getPosition());
        assertTrue(page.getResults().get(20).isSponsored());
    }

    @Test
    void parsesTop200CoverageMetadataFromCustomerCatalogResponse() {
        NoonSearchPage page = new NoonFrontendSearchPageParser(
                new ObjectMapper()
        ).parseCatalogJson(
                "{\"nbHits\":415,\"nbPages\":5,\"search\":{\"page\":2,\"limit\":100},"
                        + "\"hits\":[{\"sku\":\"N22222222NAT\",\"name\":\"Pencil\"}]}",
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=pencil&limit=100&page=2",
                200
        );

        assertEquals(415, page.getTotalHits());
        assertEquals(5, page.getTotalPages());
        assertEquals(2, page.getProviderPage());
        assertEquals(100, page.getProviderLimit());
    }

    private static NoonFrontendSearchPageParser parserAt(String instant) {
        return new NoonFrontendSearchPageParser(
                new ObjectMapper(),
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)
        );
    }

    private static int rankPosition(NoonSearchResult result) {
        try {
            return (Integer) result.getClass().getMethod("getRankPosition").invoke(result);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Noon search result must expose channel-relative rank position", exception);
        }
    }

    private static String duplicateChannelPayload() {
        return String.join("\n",
                "{",
                "  \"sponsoredProducts\": [",
                "    {\"sku\":\"Z11111111AD\",\"name\":\"Sponsored pencil set\"}",
                "  ],",
                "  \"hits\": [",
                "    {\"sku\":\"Z11111111AD\",\"name\":\"Natural appearance\"},",
                "    {\"sku\":\"N22222222NAT\",\"name\":\"Natural pencil set\"}",
                "  ]",
                "}"
        );
    }
}
