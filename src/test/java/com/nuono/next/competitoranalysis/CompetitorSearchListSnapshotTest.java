package com.nuono.next.competitoranalysis;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonFrontendSearchAdapter;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorSearchListSnapshotTest {
    @Mock private CompetitorAnalysisMapper mapper;
    @Mock private NoonFrontendSearchAdapter adapter;
    @Mock private CompetitorProductSnapshotService snapshotService;

    @Test
    void recordsSelfAndConfirmedListSnapshotsFromTop200() {
        CompetitorWatchProductRow watch = new CompetitorWatchProductRow();
        watch.setId(180123L);
        watch.setSelfNoonProductCode("NSELF0001");
        CompetitorKeywordRow keyword = new CompetitorKeywordRow();
        keyword.setId(190123L);
        keyword.setKeyword("storage");
        CompetitorProductRow confirmed = new CompetitorProductRow();
        confirmed.setId(200010L);
        confirmed.setNoonProductCode("NCONFIRM01");
        confirmed.setReviewStatus("CONFIRMED");
        NoonSearchPage page = new NoonSearchPage();
        page.setCapturedAt(LocalDateTime.parse("2026-07-29T00:00:00"));
        page.setResults(List.of(
                result("NSELF0001", 1),
                result("NCONFIRM01", 21)
        ));
        when(adapter.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(page);
        when(mapper.selectCompetitorProductByCode(
                180123L,
                "NCONFIRM01"
        )).thenReturn(confirmed);
        when(mapper.listConfirmedCompetitorProductsByKeywordId(190123L))
                .thenReturn(List.of(confirmed));

        new CompetitorSearchRefreshRunner(
                mapper,
                adapter,
                snapshotService
        ).refresh(CompetitorKeywordRefreshContext.builder()
                .searchRunId(220123L)
                .keywordRunId(230123L)
                .watchProduct(watch)
                .keyword(keyword)
                .actorUserId(601L)
                .build());

        verify(snapshotService).recordSearchSnapshots(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(page),
                argThat(items -> items.keySet().equals(
                        Set.of("NSELF0001", "NCONFIRM01")
                )),
                argThat(ids -> Long.valueOf(200010L).equals(
                        ids.get("NCONFIRM01")
                ))
        );
    }

    private static NoonSearchResult result(String code, int rank) {
        NoonSearchResult result = new NoonSearchResult();
        result.setNoonProductCode(code);
        result.setCodeType("N_CODE");
        result.setPosition(rank);
        result.setRankPosition(rank);
        result.setTitleEn(code);
        result.setCurrencyCode("SAR");
        return result;
    }
}
