package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonFrontendSearchAdapter;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchRequest;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CompetitorSearchRefreshRunnerRankChannelTest {

    @Test
    void writesOrganicAndSponsoredFactsWhenAConfirmedProductAppearsInBothChannels() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        NoonFrontendSearchAdapter adapter = mock(NoonFrontendSearchAdapter.class);
        CompetitorSearchRefreshRunner runner = new CompetitorSearchRefreshRunner(mapper, adapter);
        CompetitorProductRow confirmed = confirmedProduct();
        when(adapter.search(any(NoonSearchRequest.class))).thenReturn(page(
                result(1, 1, "NSELF0001", false),
                result(2, 1, "NCONFIRM01", true),
                result(5, 2, "NCONFIRM01", false)
        ));
        when(mapper.selectCompetitorProductByCode(180123L, "NCONFIRM01")).thenReturn(confirmed);
        when(mapper.listConfirmedCompetitorProductsByKeywordId(190001L)).thenReturn(List.of(confirmed));
        when(mapper.nextKeywordProductId()).thenReturn(210001L);
        when(mapper.nextSearchResultId()).thenReturn(240001L, 240002L, 240003L);
        when(mapper.nextRankFactId()).thenReturn(250001L, 250002L, 250003L);

        CompetitorKeywordRefreshOutcome outcome = runner.refresh(context());

        assertEquals(3, outcome.getResultCount());
        assertEquals(1, outcome.getCandidateUpsertedCount());
        assertEquals(3, outcome.getRankFactWrittenCount());
        verify(mapper, times(3)).insertSearchResult(any());
        ArgumentCaptor<CompetitorKeywordProductSearchCommand> relation =
                ArgumentCaptor.forClass(CompetitorKeywordProductSearchCommand.class);
        verify(mapper).upsertKeywordProductRelationFromSearch(relation.capture());
        assertEquals(2, relation.getValue().getRankNo());
        assertEquals(Boolean.TRUE, relation.getValue().getSponsored());
        ArgumentCaptor<CompetitorRankFactInsertCommand> facts =
                ArgumentCaptor.forClass(CompetitorRankFactInsertCommand.class);
        verify(mapper, times(3)).insertRankFact(facts.capture());
        assertRank(facts.getAllValues(), "COMPETITOR", "ORGANIC", 2);
        assertRank(facts.getAllValues(), "COMPETITOR", "SPONSORED", 1);
        facts.getAllValues().forEach(fact -> {
            assertEquals(LocalDateTime.parse("2026-07-27T02:00:00"), fact.getFactTime());
            assertEquals(LocalDate.parse("2026-07-27"), fact.getFactDate());
        });
        assertSourceResult(facts.getAllValues(), "ORGANIC", 240003L);
        assertSourceResult(facts.getAllValues(), "SPONSORED", 240002L);
    }

    private static void assertRank(
            List<CompetitorRankFactInsertCommand> facts,
            String productType,
            String channel,
            int rank
    ) {
        long matches = facts.stream()
                .filter(fact -> productType.equals(fact.getTrackedProductType()))
                .filter(fact -> channel.equals(fact.getRankChannel()))
                .filter(fact -> Integer.valueOf(rank).equals(fact.getRankNo()))
                .count();
        assertEquals(1L, matches);
    }

    private static void assertSourceResult(
            List<CompetitorRankFactInsertCommand> facts,
            String channel,
            long sourceResultId
    ) {
        CompetitorRankFactInsertCommand fact = facts.stream()
                .filter(candidate -> "COMPETITOR".equals(candidate.getTrackedProductType()))
                .filter(candidate -> channel.equals(candidate.getRankChannel()))
                .findFirst()
                .orElseThrow();
        assertEquals(sourceResultId, fact.getSourceResultId());
    }

    private static CompetitorKeywordRefreshContext context() {
        CompetitorWatchProductRow watchProduct = new CompetitorWatchProductRow();
        watchProduct.setId(180123L);
        watchProduct.setOwnerUserId(501L);
        watchProduct.setStoreCode("STR108065-NSA");
        watchProduct.setSiteCode("SA");
        watchProduct.setSelfNoonProductCode("NSELF0001");
        CompetitorKeywordRow keyword = new CompetitorKeywordRow();
        keyword.setId(190001L);
        keyword.setKeyword("laundry basket");
        keyword.setLocale("en-SA");
        return CompetitorKeywordRefreshContext.builder()
                .searchRunId(220123L)
                .keywordRunId(230123L)
                .keyword(keyword)
                .watchProduct(watchProduct)
                .actorUserId(601L)
                .build();
    }

    private static CompetitorProductRow confirmedProduct() {
        CompetitorProductRow product = new CompetitorProductRow();
        product.setId(200010L);
        product.setWatchProductId(180123L);
        product.setNoonProductCode("NCONFIRM01");
        product.setReviewStatus("CONFIRMED");
        return product;
    }

    private static NoonSearchPage page(NoonSearchResult... results) {
        NoonSearchPage page = new NoonSearchPage();
        page.setSourceUrl("https://www.noon.com/saudi-en/search?q=laundry+basket");
        page.setParserVersion("fixture-v1");
        page.setProviderHttpStatus(200);
        page.setResponseHash("abc123");
        page.setCapturedAt(LocalDateTime.parse("2026-07-27T02:00:00"));
        page.setResults(List.of(results));
        return page;
    }

    private static NoonSearchResult result(int position, int rankPosition, String code, boolean sponsored) {
        NoonSearchResult result = new NoonSearchResult();
        result.setPosition(position);
        result.setRankPosition(rankPosition);
        result.setNoonProductCode(code);
        result.setCodeType(code.startsWith("Z") ? "Z_CODE" : "N_CODE");
        result.setTitle(code);
        result.setSponsored(sponsored);
        return result;
    }
}
