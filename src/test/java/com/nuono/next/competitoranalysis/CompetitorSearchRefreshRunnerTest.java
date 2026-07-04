package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
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
                        && Integer.valueOf(100).equals(request.getLimit())
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
        assertEquals("NOT_IN_SCAN_DEPTH", facts.get(2).getRankStatus());
        assertNull(facts.get(2).getRankNo());
        assertEquals(100, facts.get(2).getScanDepth());
    }

    @Test
    void scansTop100ForRankFactsButKeepsCandidateDiscoveryToTop20() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorKeywordRow keyword = keyword();
        CompetitorProductRow confirmedAt21 = confirmedProduct(200021L, "NCONFIRM21");
        CompetitorProductRow confirmedMissing = confirmedProduct(200022L, "ZMISS00001");
        List<NoonSearchResult> results = new ArrayList<>();
        results.add(result(1, "NSELF0001", false, "Self basket"));
        for (int position = 2; position <= 20; position++) {
            results.add(result(position, "ZPENDING" + String.format("%02d", position), false, "Pending " + position));
        }
        results.add(result(21, "NCONFIRM21", false, "Confirmed competitor after top 20"));
        NoonSearchPage page = page(results.toArray(NoonSearchResult[]::new));
        AtomicLong competitorProductId = new AtomicLong(200100L);
        AtomicLong keywordProductId = new AtomicLong(210100L);
        AtomicLong searchResultId = new AtomicLong(240100L);
        when(adapter.search(any(NoonSearchRequest.class))).thenReturn(page);
        when(mapper.selectCompetitorProductByCode(anyLong(), anyString())).thenAnswer(invocation ->
                "NCONFIRM21".equals(invocation.getArgument(1)) ? confirmedAt21 : null
        );
        when(mapper.listConfirmedCompetitorProductsByKeywordId(190001L))
                .thenReturn(List.of(confirmedAt21, confirmedMissing));
        when(mapper.nextCompetitorProductId()).thenAnswer(invocation -> competitorProductId.getAndIncrement());
        when(mapper.nextKeywordProductId()).thenAnswer(invocation -> keywordProductId.getAndIncrement());
        when(mapper.nextSearchResultId()).thenAnswer(invocation -> searchResultId.getAndIncrement());
        when(mapper.nextRankFactId()).thenReturn(250001L, 250002L, 250003L);

        CompetitorKeywordRefreshOutcome outcome = runner.refresh(CompetitorKeywordRefreshContext.builder()
                .searchRunId(220123L)
                .keywordRunId(230123L)
                .keyword(keyword)
                .watchProduct(watchProduct)
                .actorUserId(601L)
                .build());

        assertEquals(21, outcome.getResultCount());
        assertEquals(19, outcome.getCandidateUpsertedCount());
        verify(adapter).search(argThat((request) ->
                Integer.valueOf(100).equals(request.getLimit())
        ));
        verify(mapper, times(21)).insertSearchResult(any());
        verify(mapper, times(19)).upsertKeywordProductRelationFromSearch(any());
        verify(mapper).softDeleteDiscoveredKeywordProductRelationsOutsideSet(
                org.mockito.ArgumentMatchers.eq(190001L),
                org.mockito.ArgumentMatchers.argThat((ids) -> ids.size() == 19),
                org.mockito.ArgumentMatchers.eq(601L)
        );

        ArgumentCaptor<CompetitorRankFactInsertCommand> rankCaptor =
                ArgumentCaptor.forClass(CompetitorRankFactInsertCommand.class);
        verify(mapper, times(3)).insertRankFact(rankCaptor.capture());
        List<CompetitorRankFactInsertCommand> facts = rankCaptor.getAllValues();
        CompetitorRankFactInsertCommand rankedCompetitor = facts.get(1);
        assertEquals("COMPETITOR", rankedCompetitor.getTrackedProductType());
        assertEquals("NCONFIRM21", rankedCompetitor.getNoonProductCode());
        assertEquals("RANKED", rankedCompetitor.getRankStatus());
        assertEquals(21, rankedCompetitor.getRankNo());
        assertEquals(100, rankedCompetitor.getScanDepth());
        CompetitorRankFactInsertCommand missingCompetitor = facts.get(2);
        assertEquals("NOT_IN_SCAN_DEPTH", missingCompetitor.getRankStatus());
        assertNull(missingCompetitor.getRankNo());
        assertEquals(100, missingCompetitor.getScanDepth());
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

    @Test
    void truncatesLongSearchResultTitlesBeforeWritingSnapshotColumns() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorKeywordRow keyword = keyword();
        String longTitle = "x".repeat(800);
        NoonSearchPage page = page(result(1, "Z11111111AD", false, longTitle));
        when(adapter.search(any(NoonSearchRequest.class))).thenReturn(page);
        when(mapper.selectCompetitorProductByCode(180123L, "Z11111111AD")).thenReturn(null);
        when(mapper.nextCompetitorProductId()).thenReturn(200012L);
        when(mapper.nextKeywordProductId()).thenReturn(210001L);
        when(mapper.nextSearchResultId()).thenReturn(240001L);
        when(mapper.nextRankFactId()).thenReturn(250001L);
        when(mapper.listConfirmedCompetitorProductsByKeywordId(190001L)).thenReturn(List.of());

        runner.refresh(CompetitorKeywordRefreshContext.builder()
                .searchRunId(220123L)
                .keywordRunId(230123L)
                .keyword(keyword)
                .watchProduct(watchProduct)
                .actorUserId(601L)
                .build());

        ArgumentCaptor<CompetitorSearchResultInsertCommand> searchCaptor =
                ArgumentCaptor.forClass(CompetitorSearchResultInsertCommand.class);
        verify(mapper).insertSearchResult(searchCaptor.capture());
        assertTrue(searchCaptor.getValue().getTitleSnapshot().length() <= 500);

        ArgumentCaptor<CompetitorProductInsertCommand> productCaptor =
                ArgumentCaptor.forClass(CompetitorProductInsertCommand.class);
        verify(mapper).insertCompetitorProduct(productCaptor.capture());
        assertTrue(productCaptor.getValue().getTitleSnapshot().length() <= 500);
    }

    @Test
    void writesLocalizedTitlesAndTagsFromKeywordRankingSearch() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorKeywordRow keyword = keyword();
        NoonSearchResult localized = result(1, "Z11111111AD", false, "English competitor title");
        setString(localized, "setTitleEn", "English competitor title");
        setString(localized, "setTitleAr", "عنوان المنافس");
        setString(localized, "setTagsJson", "[{\"label\":\"Best Seller\"},{\"text\":\"Free Delivery\"}]");
        NoonSearchPage page = page(localized);
        when(adapter.search(any(NoonSearchRequest.class))).thenReturn(page);
        when(mapper.selectCompetitorProductByCode(180123L, "Z11111111AD")).thenReturn(null);
        when(mapper.nextCompetitorProductId()).thenReturn(200012L);
        when(mapper.nextKeywordProductId()).thenReturn(210001L);
        when(mapper.nextSearchResultId()).thenReturn(240001L);
        when(mapper.nextRankFactId()).thenReturn(250001L);
        when(mapper.listConfirmedCompetitorProductsByKeywordId(190001L)).thenReturn(List.of());

        runner.refresh(CompetitorKeywordRefreshContext.builder()
                .searchRunId(220123L)
                .keywordRunId(230123L)
                .keyword(keyword)
                .watchProduct(watchProduct)
                .actorUserId(601L)
                .build());

        ArgumentCaptor<CompetitorSearchResultInsertCommand> searchCaptor =
                ArgumentCaptor.forClass(CompetitorSearchResultInsertCommand.class);
        verify(mapper).insertSearchResult(searchCaptor.capture());
        assertEquals("English competitor title", readString(searchCaptor.getValue(), "getTitleSnapshot"));
        assertEquals("English competitor title", readString(searchCaptor.getValue(), "getTitleEnSnapshot"));
        assertEquals("عنوان المنافس", readString(searchCaptor.getValue(), "getTitleArSnapshot"));
        assertTrue(readString(searchCaptor.getValue(), "getTagsJson").contains("Best Seller"));

        ArgumentCaptor<CompetitorProductInsertCommand> productCaptor =
                ArgumentCaptor.forClass(CompetitorProductInsertCommand.class);
        verify(mapper).insertCompetitorProduct(productCaptor.capture());
        assertEquals("English competitor title", readString(productCaptor.getValue(), "getTitleSnapshot"));
        assertEquals("English competitor title", readString(productCaptor.getValue(), "getTitleEnSnapshot"));
        assertEquals("عنوان المنافس", readString(productCaptor.getValue(), "getTitleArSnapshot"));
        assertTrue(readString(productCaptor.getValue(), "getTagsSnapshotJson").contains("Free Delivery"));
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

    private static void setString(Object target, String methodName, String value) {
        try {
            target.getClass().getMethod(methodName, String.class).invoke(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Missing setter " + methodName, exception);
        }
    }

    private static String readString(Object target, String methodName) {
        try {
            return (String) target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Missing accessor " + methodName, exception);
        }
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
