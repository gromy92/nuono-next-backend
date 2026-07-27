package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CompetitorBrowserObservationRankChannelTest {

    @Test
    void appendsMissingSponsoredEvidenceWithoutReusingTheDomPosition() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        CompetitorAnalysisService service = new CompetitorAnalysisService(mapper);
        CompetitorKeywordRunRow run = keywordRun();
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope());
        when(mapper.selectLatestSucceededKeywordRunByKeywordId(190001L)).thenReturn(run);
        when(mapper.lockKeywordRunForBrowserObservation(230017L)).thenReturn(230017L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct());
        when(mapper.selectNextSearchResultPosition(230017L)).thenReturn(101);
        when(mapper.nextSearchResultId()).thenReturn(240021L);
        when(mapper.selectCompetitorProductByCode(180123L, "N51360862A")).thenReturn(null);
        when(mapper.nextCompetitorProductId()).thenReturn(200021L);
        when(mapper.nextKeywordProductId()).thenReturn(210021L);

        CompetitorBrowserObservationResultView result =
                service.applyBrowserObservations(context(), 190001L, command());

        assertEquals(1, result.getSearchResultInsertedCount());
        assertEquals(1, result.getCompetitorInsertedCount());
        assertEquals(0, result.getRankFactUpdatedCount());
        ArgumentCaptor<CompetitorSearchResultInsertCommand> raw =
                ArgumentCaptor.forClass(CompetitorSearchResultInsertCommand.class);
        verify(mapper).insertSearchResult(raw.capture());
        assertEquals(240021L, raw.getValue().getId());
        assertEquals(101, raw.getValue().getResultPosition());
        assertEquals(Boolean.TRUE, raw.getValue().getSponsored());
        assertTrue(raw.getValue().getRawResultJson().contains("\"observedPosition\":2"));
        ArgumentCaptor<CompetitorProductInsertCommand> product =
                ArgumentCaptor.forClass(CompetitorProductInsertCommand.class);
        verify(mapper).insertCompetitorProduct(product.capture());
        assertEquals("PENDING", product.getValue().getReviewStatus());
        verify(mapper, never()).insertRankFact(any());
        InOrder order = inOrder(mapper);
        order.verify(mapper).lockKeywordRunForBrowserObservation(230017L);
        order.verify(mapper).selectWatchProductForRefresh(180123L);
        order.verify(mapper).selectBrowserSponsoredSearchResultByCode(230017L, "N51360862A");
    }

    @Test
    void keepsOrganicEvidenceAndWritesASeparateSponsoredFact() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        CompetitorAnalysisService service = new CompetitorAnalysisService(mapper);
        CompetitorKeywordRunRow run = keywordRun();
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope());
        when(mapper.selectLatestSucceededKeywordRunByKeywordId(190001L)).thenReturn(run);
        when(mapper.lockKeywordRunForBrowserObservation(230017L)).thenReturn(230017L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct());
        when(mapper.selectNextSearchResultPosition(230017L)).thenReturn(101);
        when(mapper.nextSearchResultId()).thenReturn(240020L);
        when(mapper.selectCompetitorProductByCode(180123L, "N51360862A")).thenReturn(confirmedProduct());
        when(mapper.nextKeywordProductId()).thenReturn(210020L);
        when(mapper.selectRankFactId(230017L, "COMPETITOR", "N51360862A", "SPONSORED"))
                .thenReturn(null);
        when(mapper.nextRankFactId()).thenReturn(250020L);
        when(mapper.insertRankFact(any())).thenReturn(1);

        CompetitorBrowserObservationResultView result =
                service.applyBrowserObservations(context(), 190001L, command());

        assertEquals(1, result.getSearchResultInsertedCount());
        assertEquals(0, result.getSearchResultUpdatedCount());
        assertEquals(1, result.getRankFactUpdatedCount());
        ArgumentCaptor<CompetitorRankFactInsertCommand> fact =
                ArgumentCaptor.forClass(CompetitorRankFactInsertCommand.class);
        verify(mapper).insertRankFact(fact.capture());
        assertEquals("COMPETITOR", fact.getValue().getTrackedProductType());
        assertEquals("SPONSORED", fact.getValue().getRankChannel());
        assertEquals(1, fact.getValue().getRankNo());
        assertEquals(240020L, fact.getValue().getSourceResultId());
        assertEquals(run.getCapturedAt(), fact.getValue().getFactTime());
    }

    @Test
    void repeatedSubmissionReusesSponsoredEvidenceAndRankFact() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        CompetitorAnalysisService service = new CompetitorAnalysisService(mapper);
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope());
        when(mapper.selectLatestSucceededKeywordRunByKeywordId(190001L)).thenReturn(keywordRun());
        when(mapper.lockKeywordRunForBrowserObservation(230017L)).thenReturn(230017L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct());
        when(mapper.selectBrowserSponsoredSearchResultByCode(230017L, "N51360862A"))
                .thenReturn(searchResult(240099L));
        when(mapper.updateSponsoredSearchResultFromBrowser(any())).thenReturn(1);
        when(mapper.selectCompetitorProductByCode(180123L, "N51360862A")).thenReturn(confirmedProduct());
        when(mapper.nextKeywordProductId()).thenReturn(210020L);
        when(mapper.selectRankFactId(230017L, "COMPETITOR", "N51360862A", "SPONSORED"))
                .thenReturn(250099L);
        when(mapper.updateSponsoredRankFact(any())).thenReturn(1);

        CompetitorBrowserObservationResultView result =
                service.applyBrowserObservations(context(), 190001L, command());

        assertEquals(0, result.getSearchResultInsertedCount());
        assertEquals(1, result.getSearchResultUpdatedCount());
        assertEquals(1, result.getRankFactUpdatedCount());
        verify(mapper, never()).insertSearchResult(any());
        verify(mapper, never()).insertRankFact(any());
        verify(mapper, never()).nextSearchResultId();
        verify(mapper, never()).nextRankFactId();
        ArgumentCaptor<CompetitorRankFactInsertCommand> fact =
                ArgumentCaptor.forClass(CompetitorRankFactInsertCommand.class);
        verify(mapper).updateSponsoredRankFact(fact.capture());
        assertEquals(250099L, fact.getValue().getId());
        assertEquals(240099L, fact.getValue().getSourceResultId());
    }

    @Test
    void rejectsAStaleRunBeforeLockingOrWritingEvidence() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        CompetitorAnalysisService service = new CompetitorAnalysisService(mapper);
        CompetitorKeywordRunRow staleRun = keywordRun();
        staleRun.setCapturedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusMinutes(31));
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope());
        when(mapper.selectLatestSucceededKeywordRunByKeywordId(190001L)).thenReturn(staleRun);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.applyBrowserObservations(context(), 190001L, command()));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("COMPETITOR_BROWSER_OBSERVATION_RUN_STALE", error.getReason());
        verify(mapper, never()).lockKeywordRunForBrowserObservation(any());
        verify(mapper, never()).insertSearchResult(any());
        verify(mapper, never()).insertRankFact(any());
    }

    private static CompetitorBrowserObservationCommand command() {
        CompetitorBrowserObservationItem item = new CompetitorBrowserObservationItem();
        item.setNoonProductCode("N51360862A");
        item.setPosition(2);
        item.setSponsored(true);
        CompetitorBrowserObservationCommand command = new CompetitorBrowserObservationCommand();
        command.setItems(List.of(item));
        return command;
    }

    private static CompetitorKeywordScopeRow keywordScope() {
        CompetitorKeywordScopeRow row = new CompetitorKeywordScopeRow();
        row.setKeywordId(190001L);
        row.setWatchProductId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorKeywordRunRow keywordRun() {
        CompetitorKeywordRunRow row = new CompetitorKeywordRunRow();
        row.setId(230017L);
        row.setSearchRunId(220013L);
        row.setKeywordId(190001L);
        row.setCapturedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusMinutes(1).withNano(0));
        return row;
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setSelfNoonProductCode("NSELF0001");
        return row;
    }

    private static CompetitorProductRow confirmedProduct() {
        CompetitorProductRow row = new CompetitorProductRow();
        row.setId(200020L);
        row.setWatchProductId(180123L);
        row.setNoonProductCode("N51360862A");
        row.setReviewStatus("CONFIRMED");
        return row;
    }

    private static CompetitorSearchResultObservationRow searchResult(Long id) {
        CompetitorSearchResultObservationRow row = new CompetitorSearchResultObservationRow();
        row.setId(id);
        return row;
    }

    private static BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 501L))
                .build();
    }
}
