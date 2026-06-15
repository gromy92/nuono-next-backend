package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorKeywordRefreshTransactionRunnerTest {

    @Mock
    private CompetitorAnalysisMapper mapper;

    @Test
    void providerFailureWritesKeywordRunFailureWithoutRankFacts() {
        when(mapper.nextKeywordRunId()).thenReturn(230001L);
        CompetitorKeywordRefreshTransactionRunner runner = new CompetitorKeywordRefreshTransactionRunner(
                mapper,
                (context) -> {
                    throw new NoonSearchProviderException(
                            "RATE_LIMITED",
                            "Noon 前台搜索返回 HTTP 429。",
                            429,
                            "https://www.noon.com/saudi-en/search?q=x",
                            "hash429"
                    );
                }
        );

        CompetitorKeywordRefreshResult result = runner.runKeyword(
                220123L,
                watchProduct(),
                keyword(),
                601L
        );

        assertEquals(false, result.isSuccess());
        assertEquals("RATE_LIMITED", result.getErrorCode());
        ArgumentCaptor<CompetitorKeywordRunInsertCommand> keywordRunCaptor =
                ArgumentCaptor.forClass(CompetitorKeywordRunInsertCommand.class);
        verify(mapper).insertKeywordRun(keywordRunCaptor.capture());
        assertEquals(230001L, keywordRunCaptor.getValue().getId());
        assertEquals("FAILED", keywordRunCaptor.getValue().getProviderStatus());
        assertEquals("RATE_LIMITED", keywordRunCaptor.getValue().getErrorCode());
        assertEquals(Integer.valueOf(429), keywordRunCaptor.getValue().getProviderHttpStatus());
        assertEquals("hash429", keywordRunCaptor.getValue().getResponseHash());
        verify(mapper).markKeywordProviderFailed(190001L, "RATE_LIMITED", "Noon 前台搜索返回 HTTP 429。", 601L);
        verify(mapper, never()).insertRankFact(any());
    }

    @Test
    void providerFailureTruncatesLongErrorMessageBeforeWritingKeywordRun() {
        when(mapper.nextKeywordRunId()).thenReturn(230002L);
        String longMessage = "x".repeat(1_500);
        CompetitorKeywordRefreshTransactionRunner runner = new CompetitorKeywordRefreshTransactionRunner(
                mapper,
                (context) -> {
                    throw new NoonSearchProviderException(
                            "NOON_SEARCH_FAILED",
                            longMessage,
                            500,
                            "https://www.noon.com/saudi-en/search?q=x",
                            "hash500"
                    );
                }
        );

        CompetitorKeywordRefreshResult result = runner.runKeyword(
                220124L,
                watchProduct(),
                keyword(),
                601L
        );

        assertEquals(false, result.isSuccess());
        assertTrue(result.getErrorMessage().length() <= 1024);
        ArgumentCaptor<CompetitorKeywordRunInsertCommand> keywordRunCaptor =
                ArgumentCaptor.forClass(CompetitorKeywordRunInsertCommand.class);
        verify(mapper).insertKeywordRun(keywordRunCaptor.capture());
        assertTrue(keywordRunCaptor.getValue().getErrorMessage().length() <= 1024);
        verify(mapper).markKeywordProviderFailed(
                org.mockito.ArgumentMatchers.eq(190001L),
                org.mockito.ArgumentMatchers.eq("NOON_SEARCH_FAILED"),
                org.mockito.ArgumentMatchers.argThat(message -> message != null && message.length() <= 1024),
                org.mockito.ArgumentMatchers.eq(601L)
        );
    }

    @Test
    void successPersistsRequestedResultLimitForAudit() {
        when(mapper.nextKeywordRunId()).thenReturn(230003L);
        CompetitorKeywordRefreshTransactionRunner runner = new CompetitorKeywordRefreshTransactionRunner(
                mapper,
                (context) -> {
                    CompetitorKeywordRefreshOutcome outcome = CompetitorKeywordRefreshOutcome.success(21);
                    outcome.setRequestedResultLimit(100);
                    outcome.setCandidateUpsertedCount(20);
                    outcome.setRankFactWrittenCount(1);
                    return outcome;
                }
        );

        CompetitorKeywordRefreshResult result = runner.runKeyword(
                220125L,
                watchProduct(),
                keyword(),
                601L
        );

        assertTrue(result.isSuccess());
        ArgumentCaptor<CompetitorKeywordRunInsertCommand> keywordRunCaptor =
                ArgumentCaptor.forClass(CompetitorKeywordRunInsertCommand.class);
        verify(mapper).insertKeywordRun(keywordRunCaptor.capture());
        assertEquals(Integer.valueOf(21), keywordRunCaptor.getValue().getResultCount());
        assertEquals(Integer.valueOf(100), keywordRunCaptor.getValue().getRequestedResultLimit());
        verify(mapper).markKeywordProviderSucceeded(190001L, "SUCCESS", 601L);
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180123L);
        row.setSiteCode("SA");
        row.setSelfNoonProductCode("NSELF0001");
        return row;
    }

    private static CompetitorKeywordRow keyword() {
        CompetitorKeywordRow row = new CompetitorKeywordRow();
        row.setId(190001L);
        row.setWatchProductId(180123L);
        row.setKeyword("laundry basket");
        return row;
    }
}
