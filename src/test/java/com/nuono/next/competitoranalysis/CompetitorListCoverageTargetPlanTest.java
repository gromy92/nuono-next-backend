package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorListCoverageTargetPlanTest {

    @Mock
    private CompetitorAnalysisMapper mapper;

    @Test
    void defersExactSearchWhenTodaysTop200CoverageIsIncomplete() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        when(mapper.hasCompleteRankScanCoverage(
                eq(180123L),
                any(LocalDate.class)
        )).thenReturn(false);
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L))
                .thenReturn(List.of(product(200001L, "ZWAITING01")));

        List<CompetitorProductDetailPlanEntry> targets =
                CompetitorProductDetailTargetPlan.initial(
                        mapper,
                        watchProduct,
                        true
                );

        assertEquals(2, targets.size());
        assertTrue(targets.get(0).isDeferred());
        assertTrue(targets.get(1).isDeferred());
    }

    @Test
    void exactSearchesOnlyCodesMissingFromTodaysTop200Facts() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorProductRow ranked = product(200001L, "ZRANKED001");
        CompetitorProductRow missing = product(200002L, "ZOUTSIDE01");
        when(mapper.hasCompleteRankScanCoverage(
                eq(180123L),
                any(LocalDate.class)
        )).thenReturn(true);
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L))
                .thenReturn(List.of(ranked, missing));
        when(mapper.hasRankedFactInTop200(
                eq(180123L),
                eq("NSELF0001"),
                any(LocalDate.class)
        )).thenReturn(true);
        when(mapper.hasRankedFactInTop200(
                eq(180123L),
                eq("ZRANKED001"),
                any(LocalDate.class)
        )).thenReturn(true);
        when(mapper.hasRankedFactInTop200(
                eq(180123L),
                eq("ZOUTSIDE01"),
                any(LocalDate.class)
        )).thenReturn(false);
        when(mapper.hasCompleteListTitlesToday(
                eq(180123L),
                eq("NSELF0001"),
                any(LocalDate.class)
        )).thenReturn(true);
        when(mapper.hasCompleteListTitlesToday(
                eq(180123L),
                eq("ZRANKED001"),
                any(LocalDate.class)
        )).thenReturn(true);

        List<CompetitorProductDetailPlanEntry> targets =
                CompetitorProductDetailTargetPlan.initial(
                        mapper,
                        watchProduct,
                        true
                );

        assertEquals(1, targets.size());
        assertEquals(
                "ZOUTSIDE01",
                targets.get(0).target.getNoonProductCode()
        );
        assertEquals(
                200002L,
                targets.get(0).target.getCompetitorProductId()
        );
    }

    @Test
    void exactSearchesARankedCodeWhenOneListTitleIsMissing() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorProductRow ranked = product(200001L, "ZRANKED001");
        when(mapper.hasCompleteRankScanCoverage(
                eq(180123L),
                any(LocalDate.class)
        )).thenReturn(true);
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L))
                .thenReturn(List.of(ranked));
        when(mapper.hasRankedFactInTop200(
                eq(180123L),
                eq("NSELF0001"),
                any(LocalDate.class)
        )).thenReturn(true);
        when(mapper.hasCompleteListTitlesToday(
                eq(180123L),
                eq("NSELF0001"),
                any(LocalDate.class)
        )).thenReturn(true);
        when(mapper.hasRankedFactInTop200(
                eq(180123L),
                eq("ZRANKED001"),
                any(LocalDate.class)
        )).thenReturn(true);
        when(mapper.hasCompleteListTitlesToday(
                eq(180123L),
                eq("ZRANKED001"),
                any(LocalDate.class)
        )).thenReturn(false);

        List<CompetitorProductDetailPlanEntry> targets =
                CompetitorProductDetailTargetPlan.initial(
                        mapper,
                        watchProduct,
                        true
                );

        assertEquals(1, targets.size());
        assertEquals(
                "ZRANKED001",
                targets.get(0).target.getNoonProductCode()
        );
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180123L);
        row.setSelfNoonProductCode("NSELF0001");
        return row;
    }

    private static CompetitorProductRow product(Long id, String code) {
        CompetitorProductRow row = new CompetitorProductRow();
        row.setId(id);
        row.setWatchProductId(180123L);
        row.setNoonProductCode(code);
        row.setReviewStatus("CONFIRMED");
        return row;
    }
}
