package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class Ali1688CandidateScoringServiceTest {

    private final Ali1688CandidateScoringService service = new Ali1688CandidateScoringService(new ObjectMapper());

    @Test
    void scoreKeepsAiFieldsPendingAndReturnsRuleSubtotal() {
        Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();
        candidate.priceMin = new BigDecimal("12.80");
        candidate.priceMax = new BigDecimal("18.60");
        candidate.moqValue = 2;
        candidate.supplierName = "义乌诚信通源头工厂";
        candidate.badgesJson = "[\"认证\", \"48小时发货\", \"现货\"]";
        candidate.supplierSnapshotJson = "{\"responseRate\":\"高\"}";
        candidate.logisticsSnapshotJson = "{\"shipFrom\":\"浙江义乌\", \"stock\":\"现货\"}";

        service.score(candidate);

        assertEquals(15, candidate.priceScore);
        assertEquals(10, candidate.moqScore);
        assertEquals(12, candidate.supplierScore);
        assertEquals(8, candidate.deliveryScore);
        assertEquals(45, candidate.ruleScore);
        assertNull(candidate.totalScore);
        assertNull(candidate.matchScore);
        assertNull(candidate.specScore);
        assertEquals("partial", candidate.scoreStatus);
        assertEquals(Ali1688CandidateScoringService.SCORE_VERSION, candidate.scoreVersion);
        assertTrue(candidate.scoreDetailJson.contains("\"aiPending\":true"));
    }

    @Test
    void scoreDoesNotInventRulePointsWhenStructuredInputsAreMissing() {
        Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();

        service.score(candidate);

        assertEquals(0, candidate.priceScore);
        assertEquals(0, candidate.moqScore);
        assertEquals(0, candidate.supplierScore);
        assertEquals(0, candidate.deliveryScore);
        assertEquals(0, candidate.ruleScore);
        assertNull(candidate.totalScore);
        assertEquals("partial", candidate.scoreStatus);
    }

    @Test
    void scoreMarksListPagePriceAsHintEvenWhenParsedNumbersLookAvailable() {
        Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();
        candidate.priceText = "¥ 6 .93 运费4元起 4400+件 50件起批";
        candidate.priceMin = new BigDecimal("4");
        candidate.priceMax = new BigDecimal("4400");
        candidate.moqValue = 50;

        service.score(candidate);

        assertEquals("partial", candidate.scoreStatus);
        assertNull(candidate.totalScore);
        assertTrue(candidate.scoreDetailJson.contains("\"priceBasis\":\"list_price_hint\""));
        assertTrue(candidate.scoreDetailJson.contains("\"confirmedRealPrice\":false"));
    }
}
