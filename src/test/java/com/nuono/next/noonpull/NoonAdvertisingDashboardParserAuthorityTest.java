package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.datapull.advertising.AdvertisingDashboard;
import org.junit.jupiter.api.Test;

class NoonAdvertisingDashboardParserAuthorityTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NoonAdvertisingDashboardParser parser =
            new NoonAdvertisingDashboardParser(objectMapper);

    @Test
    void currentDashboardShapeWithoutNativeEnumerationFieldsIsContractError() {
        ObjectNode response = response(campaigns(campaign("C-1")));

        NoonAdvertisingContractException failure = assertThrows(
                NoonAdvertisingContractException.class,
                () -> parser.parse(response)
        );

        assertEquals("ADS_CAMPAIGN_AUTHORITY_MISSING", failure.getSanitizedCode());
    }

    @Test
    void authoritativeEmptyEnumerationIsTheOnlyEmptySuccess() {
        ObjectNode response = withAuthority(response(campaigns()), 0L, true);

        AdvertisingDashboard dashboard = parser.parse(response);

        assertTrue(dashboard.getCampaignFacts().isEmpty());
        assertTrue(dashboard.getActiveCampaigns().isEmpty());
        assertEquals(0L, dashboard.getAuthority().getDeclaredCampaignCount());
    }

    @Test
    void incompleteNativeEnumerationCannotBecomeAnEmptySuccess() {
        ObjectNode response = withAuthority(response(campaigns()), 0L, false);

        NoonAdvertisingContractException failure = assertThrows(
                NoonAdvertisingContractException.class,
                () -> parser.parse(response)
        );

        assertEquals("ADS_CAMPAIGN_ENUMERATION_INCOMPLETE", failure.getSanitizedCode());
    }

    @Test
    void declaredCountMustEqualRawCampaignRowCount() {
        ObjectNode response = withAuthority(response(campaigns(campaign("C-1"))), 2L, true);

        NoonAdvertisingContractException failure = assertThrows(
                NoonAdvertisingContractException.class,
                () -> parser.parse(response)
        );

        assertEquals("ADS_CAMPAIGN_COUNT_MISMATCH", failure.getSanitizedCode());
    }

    @Test
    void duplicateCampaignIdentityPreservesRawOrderForDownstreamFirstWins() {
        ObjectNode response = withAuthority(
                response(campaigns(campaign("C-1"), campaign("C-1"))),
                2L,
                true
        );

        AdvertisingDashboard dashboard = parser.parse(response);

        assertEquals(2, dashboard.getCampaignFacts().size());
        assertEquals("C-1", dashboard.getCampaignFacts().get(0).getCampaignCode());
        assertEquals("C-1", dashboard.getCampaignFacts().get(1).getCampaignCode());
        assertEquals(0, dashboard.getBusinessSkippedCampaignCount());
    }

    @Test
    void invalidMetricSkipsOnlyThatRowWithExplicitRawAccounting() {
        ObjectNode invalid = campaign("C-BAD");
        invalid.put("effectiveStatus", "live");
        ObjectNode response = withAuthority(
                response(campaigns(invalid, campaign("C-GOOD"))),
                2L,
                true
        );
        ((ObjectNode) response.path("current").path("campaignMetrics"))
                .set("C-BAD", objectMapper.createObjectNode().put("views", "not-a-number"));

        AdvertisingDashboard dashboard = parser.parse(response);

        assertEquals(1, dashboard.getCampaignFacts().size());
        assertEquals("C-GOOD", dashboard.getCampaignFacts().get(0).getCampaignCode());
        assertEquals(1, dashboard.getBusinessSkippedCampaignCount());
        assertEquals(2, dashboard.getSourceCampaignCount());
        assertEquals("C-BAD", dashboard.getActiveCampaigns().get(0).getCampaignCode());
    }

    @Test
    void bigintCountsArePreservedWhileBusinessShapeDefectsSkipOnlyTheirRows() {
        ObjectNode bigintCount = campaign("C-COUNT");
        ObjectNode decimalOverflow = campaign("C-AMOUNT");
        ObjectNode fieldOverflow = campaign("X".repeat(121));
        ObjectNode response = withAuthority(response(campaigns(
                bigintCount,
                decimalOverflow,
                fieldOverflow,
                campaign("C-GOOD")
        )), 4L, true);
        ObjectNode campaignMetrics = (ObjectNode) response.path("current")
                .path("campaignMetrics");
        campaignMetrics.set("C-COUNT", objectMapper.createObjectNode()
                .put("views", "2147483648"));
        campaignMetrics.set("C-AMOUNT", objectMapper.createObjectNode()
                .put("spends", "1000000000000"));

        AdvertisingDashboard dashboard = parser.parse(response);

        assertEquals(2, dashboard.getCampaignFacts().size());
        assertEquals("C-COUNT", dashboard.getCampaignFacts().get(0).getCampaignCode());
        assertEquals(2_147_483_648L, dashboard.getCampaignFacts().get(0).getViews());
        assertEquals("C-GOOD", dashboard.getCampaignFacts().get(1).getCampaignCode());
        assertEquals(2, dashboard.getBusinessSkippedCampaignCount());
        assertEquals(4, dashboard.getSourceCampaignCount());
    }

    @Test
    void countBeyondSignedBigintFailsTheWholeDashboardContainer() {
        ObjectNode response = withAuthority(
                response(campaigns(campaign("C-OVERFLOW"), campaign("C-GOOD"))),
                2L,
                true
        );
        ((ObjectNode) response.path("current").path("campaignMetrics"))
                .set("C-OVERFLOW", objectMapper.createObjectNode()
                        .put("views", "9223372036854775808"));

        NoonAdvertisingContractException failure = assertThrows(
                NoonAdvertisingContractException.class,
                () -> parser.parse(response)
        );

        assertEquals("ADS_COUNT_OUT_OF_RANGE", failure.getSanitizedCode());
    }

    @Test
    void authorityCountBeyondSignedBigintFailsBeforeAnyDashboardFacts() {
        ObjectNode response = withAuthority(response(campaigns(campaign("C-1"))), 1L, true);
        ((ObjectNode) response.path("campaignCollectionAuthority")).put(
                "declaredCampaignCount", new java.math.BigInteger("9223372036854775808")
        );

        NoonAdvertisingContractException failure = assertThrows(
                NoonAdvertisingContractException.class,
                () -> parser.parse(response)
        );

        assertEquals("ADS_CAMPAIGN_AUTHORITY_INVALID", failure.getSanitizedCode());
    }

    @Test
    void duplicateIdentityCannotChangeItsFirstAuthoritativeActiveStatus() {
        ObjectNode later = campaign("C-1");
        later.put("effectiveStatus", "live");
        ObjectNode response = withAuthority(
                response(campaigns(campaign("C-1"), later)),
                2L,
                true
        );

        NoonAdvertisingContractException failure = assertThrows(
                NoonAdvertisingContractException.class,
                () -> parser.parse(response)
        );

        assertEquals("ADS_CAMPAIGN_STATUS_DRIFT", failure.getSanitizedCode());
    }

    private ObjectNode response(ArrayNode campaigns) {
        ObjectNode response = objectMapper.createObjectNode();
        response.set("campaigns", campaigns);
        ObjectNode current = objectMapper.createObjectNode();
        current.set("campaignMetrics", objectMapper.createObjectNode());
        response.set("current", current);
        return response;
    }

    private ObjectNode withAuthority(ObjectNode response, long count, boolean complete) {
        ObjectNode authority = objectMapper.createObjectNode();
        authority.put("generationToken", "generation-2026-08-01");
        authority.put("asOfUtc", "2026-08-02T00:00:00Z");
        authority.put("declaredCampaignCount", count);
        authority.put("complete", complete);
        response.set("campaignCollectionAuthority", authority);
        return response;
    }

    private ArrayNode campaigns(ObjectNode... campaigns) {
        ArrayNode result = objectMapper.createArrayNode();
        for (ObjectNode campaign : campaigns) {
            result.add(campaign);
        }
        return result;
    }

    private ObjectNode campaign(String code) {
        ObjectNode campaign = objectMapper.createObjectNode();
        campaign.put("campaignCode", code);
        campaign.put("name", code);
        campaign.put("effectiveStatus", "paused");
        return campaign;
    }
}
