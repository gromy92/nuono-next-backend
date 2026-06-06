package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
