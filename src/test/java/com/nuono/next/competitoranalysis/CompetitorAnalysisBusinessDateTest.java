package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorAnalysisBusinessDateTest {

    private static final Clock BEFORE_SHANGHAI_MIDNIGHT =
            Clock.fixed(Instant.parse("2026-07-26T15:59:59Z"), ZoneOffset.UTC);
    private static final Clock AT_SHANGHAI_MIDNIGHT =
            Clock.fixed(Instant.parse("2026-07-26T16:00:00Z"), ZoneOffset.UTC);

    @Mock
    private CompetitorAnalysisMapper mapper;

    @Test
    void dashboardKeepsShanghaiDateBeforeUtcDayBoundary() {
        assertDashboardWindow(
                BEFORE_SHANGHAI_MIDNIGHT,
                LocalDate.of(2026, 7, 25),
                LocalDate.of(2026, 7, 26)
        );
    }

    @Test
    void dashboardAdvancesShanghaiDateAtUtcDayBoundary() {
        assertDashboardWindow(
                AT_SHANGHAI_MIDNIGHT,
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 7, 27)
        );
    }

    @Test
    void rankHistoryStartsFromShanghaiBusinessDayAtUtcDayBoundary() {
        CompetitorWatchProductRow watchProduct = new CompetitorWatchProductRow();
        watchProduct.setId(180123L);
        watchProduct.setStoreCode("STR108065-NSA");
        CompetitorKeywordScopeRow keyword = new CompetitorKeywordScopeRow();
        keyword.setKeywordId(190001L);
        keyword.setWatchProductId(180123L);
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct);
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keyword);
        CompetitorAnalysisService service = new CompetitorAnalysisService(mapper, null, AT_SHANGHAI_MIDNIGHT);
        ArgumentCaptor<LocalDateTime> fromTime = ArgumentCaptor.forClass(LocalDateTime.class);

        service.rankHistory(operatorContext(), 180123L, 190001L, 7);

        verify(mapper).listRankHistoryByWatchProductIdAndKeywordId(
                eq(180123L),
                eq(190001L),
                fromTime.capture(),
                eq(1000)
        );
        assertEquals(LocalDateTime.of(2026, 7, 21, 0, 0), fromTime.getValue());
    }

    private void assertDashboardWindow(Clock clock, LocalDate fromDate, LocalDate toDate) {
        CompetitorAnalysisService service = new CompetitorAnalysisService(mapper, null, clock);

        service.dashboard(operatorContext(), "STR108065-NSA", "SA", 1, "up");

        verify(mapper).selectLatestRankFactDate(501L, "STR108065-NSA", "SA", toDate);
        verify(mapper).listRankChanges(
                501L,
                "STR108065-NSA",
                "SA",
                "SELF",
                fromDate,
                toDate,
                "UP",
                100
        );
    }

    private static BusinessAccessContext operatorContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 501L))
                .menuPaths(Set.of("/operations/competitor-analysis"))
                .build();
    }
}
