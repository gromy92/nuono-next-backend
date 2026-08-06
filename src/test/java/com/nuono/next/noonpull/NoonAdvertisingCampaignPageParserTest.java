package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.datapull.advertising.AdvertisingCampaignPage;
import org.junit.jupiter.api.Test;

class NoonAdvertisingCampaignPageParserTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NoonAdvertisingCampaignPageParser parser =
            new NoonAdvertisingCampaignPageParser(objectMapper);

    @Test
    void missingPaginationRejectsTheWholeContainer() {
        ObjectNode response = objectMapper.createObjectNode();
        response.set("campaigns", campaigns(campaign("C-1")));

        NoonAdvertisingContractException failure = assertThrows(
                NoonAdvertisingContractException.class,
                () -> parser.parse(response, 1)
        );

        assertEquals("ADS_CAMPAIGN_PAGE_CONTAINER_INVALID", failure.getSanitizedCode());
    }

    @Test
    void declaredEmptyEnumerationNormalizesToOneLogicalPage() {
        AdvertisingCampaignPage page = parser.parse(response(0L, 0L, campaigns()), 1);

        assertEquals(1, page.getPageNo());
        assertEquals(1, page.getTotalPages());
        assertEquals(0L, page.getDeclaredCampaignCount());
        assertTrue(page.getFacts().isEmpty());
        assertEquals(0, page.getSourceItemCount());
    }

    @Test
    void pageExtentMustContainRowsUntilTheDeclaredLastPage() {
        NoonAdvertisingContractException failure = assertThrows(
                NoonAdvertisingContractException.class,
                () -> parser.parse(response(2L, 2L, campaigns()), 1)
        );

        assertEquals("ADS_CAMPAIGN_PAGE_EXTENT_INVALID", failure.getSanitizedCode());
    }

    @Test
    void duplicateIdentityPreservesRawOrderForDownstreamFirstValidWins() {
        AdvertisingCampaignPage page = parser.parse(response(
                2L, 1L, campaigns(campaign("C-1"), campaign("C-1"))
        ), 1);

        assertEquals(2, page.getFacts().size());
        assertEquals("C-1", page.getFacts().get(0).getCampaignFact().getCampaignCode());
        assertEquals("C-1", page.getFacts().get(1).getCampaignFact().getCampaignCode());
        assertEquals(2, page.getObservations().size());
        assertEquals(0, page.getBusinessSkippedItemCount());
    }

    @Test
    void invalidMetricSkipsOnlyThatBusinessRowWithExplicitRawAccounting() {
        ObjectNode invalid = campaign("C-BAD");
        ((ObjectNode) invalid.path("metrics")).put("views", "not-a-number");

        AdvertisingCampaignPage page = parser.parse(response(
                2L, 1L, campaigns(invalid, campaign("C-GOOD"))
        ), 1);

        assertEquals(1, page.getFacts().size());
        assertEquals("C-GOOD", page.getFacts().get(0).getCampaignFact().getCampaignCode());
        assertEquals(1, page.getBusinessSkippedItemCount());
        assertEquals(2, page.getSourceItemCount());
        assertEquals(1, page.getObservations().size());
    }

    @Test
    void bigintCountIsPreservedAndOverflowingRowIsSkipped() {
        ObjectNode bigint = campaign("C-COUNT");
        ((ObjectNode) bigint.path("metrics")).put("views", "2147483648");
        ObjectNode overflow = campaign("C-OVERFLOW");
        ((ObjectNode) overflow.path("metrics")).put(
                "views", "9223372036854775808"
        );

        AdvertisingCampaignPage page = parser.parse(response(
                2L, 1L, campaigns(bigint, overflow)
        ), 1);

        assertEquals(1, page.getFacts().size());
        assertEquals(2_147_483_648L,
                page.getFacts().get(0).getCampaignFact().getViews());
        assertEquals(1, page.getBusinessSkippedItemCount());
    }

    @Test
    void declaredCountBeyondSignedBigintRejectsTheContainer() {
        ObjectNode response = response(1L, 1L, campaigns(campaign("C-1")));
        ((ObjectNode) response.path("paginationMetadata")).put(
                "nbHits", new java.math.BigInteger("9223372036854775808")
        );

        NoonAdvertisingContractException failure = assertThrows(
                NoonAdvertisingContractException.class,
                () -> parser.parse(response, 1)
        );

        assertEquals("ADS_CAMPAIGN_PAGE_EXTENT_INVALID", failure.getSanitizedCode());
    }

    @Test
    void duplicateStatusConflictRemainsOrderedForCheckpointResolution() {
        ObjectNode later = campaign("C-1");
        later.put("effectiveStatus", "live");

        AdvertisingCampaignPage page = parser.parse(response(
                2L, 1L, campaigns(campaign("C-1"), later)
        ), 1);

        assertEquals(false, page.getObservations().get(0).isActive());
        assertEquals(true, page.getObservations().get(1).isActive());
    }

    private ObjectNode response(long count, long totalPages, ArrayNode campaigns) {
        ObjectNode response = objectMapper.createObjectNode();
        response.set("campaigns", campaigns);
        ObjectNode pagination = objectMapper.createObjectNode();
        pagination.put("nbHits", count);
        pagination.put("nbPages", totalPages);
        response.set("paginationMetadata", pagination);
        return response;
    }

    private ArrayNode campaigns(ObjectNode... campaigns) {
        ArrayNode result = objectMapper.createArrayNode();
        for (ObjectNode campaign : campaigns) result.add(campaign);
        return result;
    }

    private ObjectNode campaign(String code) {
        ObjectNode campaign = objectMapper.createObjectNode();
        campaign.put("campaignCode", code);
        campaign.put("name", code);
        campaign.put("effectiveStatus", "paused");
        campaign.set("metrics", objectMapper.createObjectNode()
                .put("views", "1")
                .put("clicks", "0")
                .put("orders", "0")
                .put("assistedOrders", "0")
                .put("atc", "0")
                .put("spends", "0")
                .put("revenue", "0")
                .put("ctr", "0")
                .put("roas", "0")
                .put("cpc", "0")
                .put("cps", "0")
                .put("cvr", "0"));
        return campaign;
    }
}
