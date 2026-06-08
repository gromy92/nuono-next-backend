package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonFrontendSearchAdapter;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchRequest;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorSearchRefreshRunnerTest {

    @Mock
    private CompetitorAnalysisMapper mapper;

    @Mock
    private NoonFrontendSearchAdapter adapter;

    private CompetitorSearchRefreshRunner runner;

    @BeforeEach
    void setUp() {
        runner = new CompetitorSearchRefreshRunner(mapper, adapter);
    }

    @Test
    void writesCandidatesSearchResultsAndRankFactsFromSearchPage() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorKeywordRow keyword = keyword();
        CompetitorProductRow confirmedPresent = confirmedProduct(200010L, "NCONFIRM01");
        CompetitorProductRow confirmedMissing = confirmedProduct(200011L, "ZMISS00001");
        NoonSearchPage page = page(
                result(1, "NSELF0001", true, "Self basket"),
                result(2, "Z11111111AD", true, "Pending competitor"),
                result(3, "NCONFIRM01", false, "Confirmed competitor")
        );
        when(adapter.search(any(NoonSearchRequest.class))).thenReturn(page);
        when(mapper.selectCompetitorProductByCode(180123L, "Z11111111AD")).thenReturn(null);
        when(mapper.nextCompetitorProductId()).thenReturn(200012L);
        when(mapper.selectCompetitorProductByCode(180123L, "NCONFIRM01")).thenReturn(confirmedPresent);
        when(mapper.listConfirmedCompetitorProductsByKeywordId(190001L))
                .thenReturn(List.of(confirmedPresent, confirmedMissing));
        when(mapper.nextKeywordProductId()).thenReturn(210001L, 210002L);
        when(mapper.nextSearchResultId()).thenReturn(240001L, 240002L, 240003L);
        when(mapper.nextRankFactId()).thenReturn(250001L, 250002L, 250003L);

        CompetitorKeywordRefreshOutcome outcome = runner.refresh(CompetitorKeywordRefreshContext.builder()
                .searchRunId(220123L)
                .keywordRunId(230123L)
                .keyword(keyword)
                .watchProduct(watchProduct)
                .actorUserId(601L)
                .build());

        assertEquals("SUCCESS", outcome.getProviderStatus());
        assertEquals(3, outcome.getResultCount());
        assertEquals(2, outcome.getCandidateUpsertedCount());
        assertEquals(3, outcome.getRankFactWrittenCount());
        verify(adapter).search(argThat((request) ->
                "SA".equals(request.getSiteCode())
                        && "laundry basket".equals(request.getKeyword())
                        && Integer.valueOf(20).equals(request.getLimit())
        ));
        verify(mapper).insertCompetitorProduct(argThat((command) ->
                Long.valueOf(200012L).equals(command.getId())
                        && "Z11111111AD".equals(command.getNoonProductCode())
                        && "PENDING".equals(command.getReviewStatus())
        ));
        ArgumentCaptor<CompetitorKeywordProductSearchCommand> relationCaptor =
                ArgumentCaptor.forClass(CompetitorKeywordProductSearchCommand.class);
        verify(mapper, times(2)).upsertKeywordProductRelationFromSearch(relationCaptor.capture());
        assertEquals("DISCOVERED", relationCaptor.getAllValues().get(0).getRelationStatus());
        assertEquals("CONFIRMED", relationCaptor.getAllValues().get(1).getRelationStatus());
        verify(mapper).softDeleteDiscoveredKeywordProductRelationsOutsideSet(
                org.mockito.ArgumentMatchers.eq(190001L),
                org.mockito.ArgumentMatchers.eq(List.of(200012L, 200010L)),
                org.mockito.ArgumentMatchers.eq(601L)
        );
        verify(mapper, times(3)).insertSearchResult(any());

        ArgumentCaptor<CompetitorRankFactInsertCommand> rankCaptor =
                ArgumentCaptor.forClass(CompetitorRankFactInsertCommand.class);
        verify(mapper, times(3)).insertRankFact(rankCaptor.capture());
        List<CompetitorRankFactInsertCommand> facts = rankCaptor.getAllValues();
        assertEquals("SELF", facts.get(0).getTrackedProductType());
        assertEquals("RANKED", facts.get(0).getRankStatus());
        assertEquals(1, facts.get(0).getRankNo());
        assertEquals(Boolean.TRUE, facts.get(0).getSponsored());
        assertEquals("COMPETITOR", facts.get(1).getTrackedProductType());
        assertEquals("RANKED", facts.get(1).getRankStatus());
        assertEquals(3, facts.get(1).getRankNo());
        assertEquals("COMPETITOR", facts.get(2).getTrackedProductType());
        assertEquals("NOT_IN_TOP_20", facts.get(2).getRankStatus());
        assertNull(facts.get(2).getRankNo());
    }

    @Test
    void recordsSponsoredResultWhenAdAppearsBeforeNaturalDuplicate() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorKeywordRow keyword = keyword();
        CompetitorProductRow confirmedPresent = confirmedProduct(200010L, "NCONFIRM01");
        NoonSearchPage page = page(
                result(1, "NSELF0001", false, "Self basket"),
                result(2, "NCONFIRM01", true, "Confirmed competitor ad"),
                result(5, "NCONFIRM01", false, "Confirmed competitor natural")
        );
        when(adapter.search(any(NoonSearchRequest.class))).thenReturn(page);
        when(mapper.selectCompetitorProductByCode(180123L, "NCONFIRM01")).thenReturn(confirmedPresent);
        when(mapper.listConfirmedCompetitorProductsByKeywordId(190001L)).thenReturn(List.of(confirmedPresent));
        when(mapper.nextKeywordProductId()).thenReturn(210001L);
        when(mapper.nextSearchResultId()).thenReturn(240001L, 240002L);
        when(mapper.nextRankFactId()).thenReturn(250001L, 250002L);

        runner.refresh(CompetitorKeywordRefreshContext.builder()
                .searchRunId(220123L)
                .keywordRunId(230123L)
                .keyword(keyword)
                .watchProduct(watchProduct)
                .actorUserId(601L)
                .build());

        ArgumentCaptor<CompetitorSearchResultInsertCommand> searchCaptor =
                ArgumentCaptor.forClass(CompetitorSearchResultInsertCommand.class);
        verify(mapper, times(2)).insertSearchResult(searchCaptor.capture());
        assertEquals("NCONFIRM01", searchCaptor.getAllValues().get(1).getNoonProductCode());
        assertEquals(2, searchCaptor.getAllValues().get(1).getResultPosition());
        assertEquals(Boolean.TRUE, searchCaptor.getAllValues().get(1).getSponsored());

        ArgumentCaptor<CompetitorKeywordProductSearchCommand> relationCaptor =
                ArgumentCaptor.forClass(CompetitorKeywordProductSearchCommand.class);
        verify(mapper).upsertKeywordProductRelationFromSearch(relationCaptor.capture());
        assertEquals(2, relationCaptor.getValue().getRankNo());
        assertEquals(Boolean.TRUE, relationCaptor.getValue().getSponsored());

        ArgumentCaptor<CompetitorRankFactInsertCommand> rankCaptor =
                ArgumentCaptor.forClass(CompetitorRankFactInsertCommand.class);
        verify(mapper, times(2)).insertRankFact(rankCaptor.capture());
        CompetitorRankFactInsertCommand competitorFact = rankCaptor.getAllValues().get(1);
        assertEquals("COMPETITOR", competitorFact.getTrackedProductType());
        assertEquals(2, competitorFact.getRankNo());
        assertEquals(Boolean.TRUE, competitorFact.getSponsored());
    }

    private static NoonSearchPage page(NoonSearchResult... results) {
        NoonSearchPage page = new NoonSearchPage();
        page.setSourceUrl("https://www.noon.com/saudi-en/search?q=laundry+basket");
        page.setParserVersion("fixture-v1");
        page.setProviderHttpStatus(200);
        page.setResponseHash("abc123");
        page.setCapturedAt(LocalDateTime.parse("2026-06-06T08:00:00"));
        page.setResults(List.of(results));
        return page;
    }

    private static NoonSearchResult result(int position, String code, boolean sponsored, String title) {
        NoonSearchResult result = new NoonSearchResult();
        result.setPosition(position);
        result.setNoonProductCode(code);
        result.setCodeType(code.startsWith("Z") ? "Z_CODE" : "N_CODE");
        result.setCanonicalUrl("https://www.noon.com/saudi-en/item/" + code + "/p/");
        result.setTitle(title);
        result.setBrand("Brand " + code);
        result.setImageUrl("https://f.nooncdn.com/p/" + code + ".jpg");
        result.setPriceAmount(new BigDecimal("25.50"));
        result.setCurrencyCode("SAR");
        result.setRating(new BigDecimal("4.5"));
        result.setReviewCount(88);
        result.setSponsored(sponsored);
        result.setRawResultJson("{\"sku\":\"" + code + "\"}");
        return result;
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setSelfNoonProductCode("NSELF0001");
        row.setSelfCodeType("N_CODE");
        row.setPartnerSku("BASKET-SA-001");
        return row;
    }

    private static CompetitorKeywordRow keyword() {
        CompetitorKeywordRow row = new CompetitorKeywordRow();
        row.setId(190001L);
        row.setWatchProductId(180123L);
        row.setKeyword("laundry basket");
        row.setLocale("en-SA");
        return row;
    }

    private static CompetitorProductRow confirmedProduct(Long id, String code) {
        CompetitorProductRow row = new CompetitorProductRow();
        row.setId(id);
        row.setWatchProductId(180123L);
        row.setNoonProductCode(code);
        row.setCodeType(code.startsWith("Z") ? "Z_CODE" : "N_CODE");
        row.setReviewStatus("CONFIRMED");
        return row;
    }
}
