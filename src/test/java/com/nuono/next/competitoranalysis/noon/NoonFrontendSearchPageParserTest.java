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

    @Test
    void parsesNoonCatalogSearchJsonHits() {
        NoonFrontendSearchPageParser parser = new NoonFrontendSearchPageParser(new ObjectMapper());
        String json = String.join("\n",
                "{",
                "  \"hits\": [",
                "    {",
                "      \"sku\": \"Z668B504C093C5F296F18Z\",",
                "      \"offer_code\": \"z668b504c093c5f296f18z-1\",",
                "      \"brand\": \"Sadaf\",",
                "      \"name\": \"250-Sheet Star Shaped Sticky Notes Multicolour\",",
                "      \"sale_price\": 5.95,",
                "      \"url\": \"250-sheet-star-shaped-sticky-notes-multicolour\",",
                "      \"image_url\": \"https://f.nooncdn.com/p/pzsku/Z668B504C093C5F296F18Z/45/main.jpg\",",
                "      \"product_rating\": {\"value\": 4.8, \"count\": 12},",
                "      \"is_sponsored\": true",
                "    },",
                "    {",
                "      \"sku\": \"N53335547A\",",
                "      \"brand\": \"Soundcore\",",
                "      \"name\": \"Life Q30 Headphones\",",
                "      \"price\": 79,",
                "      \"image_urls\": [\"https://f.nooncdn.com/p/pnsku/N53335547A/45/main.jpg\"],",
                "      \"product_rating\": {\"value\": 4.5, \"count\": 6257}",
                "    }",
                "  ]",
                "}"
        );

        NoonSearchPage page = parser.parseCatalogJson(
                json,
                "https://noon-catalog.noon.partners/_svc/catalog/api/u/uae-en/search?q=sticky&limit=20",
                200
        );

        assertEquals("noon-search-catalog-v1", page.getParserVersion());
        assertEquals(2, page.getResults().size());
        NoonSearchResult first = page.getResults().get(0);
        assertEquals(1, first.getPosition());
        assertEquals("Z668B504C093C5F296F18Z", first.getNoonProductCode());
        assertEquals("Z_CODE", first.getCodeType());
        assertEquals("Sadaf", first.getBrand());
        assertEquals("AED", first.getCurrencyCode());
        assertTrue(first.isSponsored());
        assertEquals("https://www.noon.com/uae-en/250-sheet-star-shaped-sticky-notes-multicolour/Z668B504C093C5F296F18Z/p/?o=z668b504c093c5f296f18z-1", first.getCanonicalUrl());

        NoonSearchResult second = page.getResults().get(1);
        assertEquals(2, second.getPosition());
        assertEquals("N53335547A", second.getNoonProductCode());
        assertEquals("N_CODE", second.getCodeType());
        assertEquals(6257, second.getReviewCount());
    }

    @Test
    void parsesNoonCatalogSponsoredBlocksBeforeNaturalHits() {
        NoonFrontendSearchPageParser parser = new NoonFrontendSearchPageParser(new ObjectMapper());
        String json = String.join("\n",
                "{",
                "  \"sponsoredProducts\": [",
                "    {",
                "      \"sku\": \"Z11111111AD\",",
                "      \"brand\": \"Ad Brand\",",
                "      \"name\": \"Sponsored pencil set\",",
                "      \"price\": 12.5,",
                "      \"url\": \"sponsored-pencil-set\",",
                "      \"image_url\": \"https://f.nooncdn.com/p/pzsku/Z11111111AD/45/main.jpg\"",
                "    }",
                "  ],",
                "  \"hits\": [",
                "    {",
                "      \"sku\": \"Z11111111AD\",",
                "      \"brand\": \"Ad Brand\",",
                "      \"name\": \"Sponsored pencil set\",",
                "      \"price\": 12.5,",
                "      \"url\": \"sponsored-pencil-set\"",
                "    },",
                "    {",
                "      \"sku\": \"N22222222NAT\",",
                "      \"brand\": \"Natural Brand\",",
                "      \"name\": \"Natural pencil set\",",
                "      \"price\": 9.9,",
                "      \"url\": \"natural-pencil-set\"",
                "    }",
                "  ]",
                "}"
        );

        NoonSearchPage page = parser.parseCatalogJson(
                json,
                "https://noon-catalog.noon.partners/_svc/catalog/api/u/saudi-en/search?q=pencil&limit=20",
                200
        );

        assertEquals(2, page.getResults().size());
        NoonSearchResult sponsored = page.getResults().get(0);
        assertEquals("Z11111111AD", sponsored.getNoonProductCode());
        assertEquals(1, sponsored.getPosition());
        assertTrue(sponsored.isSponsored());

        NoonSearchResult natural = page.getResults().get(1);
        assertEquals("N22222222NAT", natural.getNoonProductCode());
        assertEquals(2, natural.getPosition());
    }

    @Test
    void parsesNoonCustomerCatalogV3WrappedHitsAndSponsoredMarkers() {
        NoonFrontendSearchPageParser parser = new NoonFrontendSearchPageParser(new ObjectMapper());
        String json = String.join("\n",
                "{",
                "  \"data\": {",
                "    \"hits\": [",
                "      {",
                "        \"_source\": {",
                "          \"sku\": \"ZF47007A9D75977AB9A83Z\",",
                "          \"offer_code\": \"zf47007a9d75977ab9a83z-1\",",
                "          \"brand\": \"QiLi\",",
                "          \"name\": \"QiLi 30 Pcs Wooden Black HB Pencils\",",
                "          \"sale_price\": 32.95,",
                "          \"url\": \"qili-30-pcs-wooden-black-hb-pencils\",",
                "          \"image_key\": \"pzsku/ZF47007A9D75977AB9A83Z/45/main\",",
                "          \"product_rating\": {\"value\": 4.4, \"count\": 47},",
                "          \"is_ad\": true",
                "        }",
                "      },",
                "      {",
                "        \"product\": {",
                "          \"sku\": \"Z9C3822DFD5742951E961Z\",",
                "          \"brand\": \"QiLi\",",
                "          \"name\": \"Natural QiLi pencil set\",",
                "          \"price\": 31.80,",
                "          \"badges\": [{\"label\": \"Ad\"}]",
                "        }",
                "      },",
                "      {",
                "        \"product\": {",
                "          \"sku\": \"N51360862A\",",
                "          \"brand\": \"Generic\",",
                "          \"name\": \"Natural tablet cover\",",
                "          \"price\": 38",
                "        }",
                "      }",
                "    ]",
                "  }",
                "}"
        );

        NoonSearchPage page = parser.parseCatalogJson(
                json,
                "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search?q=Qili&limit=20",
                200
        );

        assertEquals("noon-search-customer-catalog-v3", page.getParserVersion());
        assertEquals(3, page.getResults().size());
        NoonSearchResult first = page.getResults().get(0);
        assertEquals("ZF47007A9D75977AB9A83Z", first.getNoonProductCode());
        assertEquals(1, first.getPosition());
        assertTrue(first.isSponsored());
        assertEquals("QiLi", first.getBrand());
        assertEquals("SAR", first.getCurrencyCode());
        assertEquals(47, first.getReviewCount());

        NoonSearchResult second = page.getResults().get(1);
        assertEquals("Z9C3822DFD5742951E961Z", second.getNoonProductCode());
        assertTrue(second.isSponsored());

        NoonSearchResult third = page.getResults().get(2);
        assertEquals("N51360862A", third.getNoonProductCode());
        assertEquals(3, third.getPosition());
    }
}
