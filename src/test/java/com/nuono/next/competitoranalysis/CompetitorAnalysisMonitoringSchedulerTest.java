package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CompetitorAnalysisMonitoringSchedulerTest {

    @Mock
    private CompetitorAnalysisMapper mapper;

    @Mock
    private CompetitorAnalysisRefreshService refreshService;

    private CompetitorAnalysisMonitoringScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CompetitorAnalysisMonitoringScheduler(mapper, refreshService);
        ReflectionTestUtils.setField(scheduler, "maxScopesPerTick", 100);
    }

    @Test
    void disabledSchedulerDoesNotSubmitAnyScope() {
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        assertEquals(0, scheduler.runRankOnce());
        assertEquals(0, scheduler.runDetailOnce());

        verifyNoInteractions(mapper, refreshService);
    }

    @Test
    void enabledRankSchedulerSubmitsOnlyRankMonitoring() {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        CompetitorWatchProductScopeRow scope = scope();
        when(mapper.listRefreshableWatchProductScopes(100)).thenReturn(List.of(scope));

        assertEquals(1, scheduler.runRankOnce());

        verify(refreshService).requestScheduledRankMonitoring(501L, "STR108065-NSA", "SA");
        verify(refreshService, never()).requestScheduledDetailMonitoring(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void enabledDetailSchedulerSubmitsOnlyDetailMonitoring() {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        CompetitorWatchProductScopeRow scope = scope();
        when(mapper.listRefreshableWatchProductScopes(100)).thenReturn(List.of(scope));

        assertEquals(1, scheduler.runDetailOnce());

        verify(refreshService).requestScheduledDetailMonitoring(501L, "STR108065-NSA", "SA");
        verify(refreshService, never()).requestScheduledRankMonitoring(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void scheduledAnnotationsUseDailyRankAndTwoHourDetailDefaults() throws Exception {
        Method rankMethod = CompetitorAnalysisMonitoringScheduler.class.getDeclaredMethod("runScheduledRankMonitoring");
        Method detailMethod = CompetitorAnalysisMonitoringScheduler.class.getDeclaredMethod("runScheduledDetailMonitoring");

        assertEquals(
                "${nuono.competitor-analysis.monitor.scheduler.rank-cron:0 0 8 * * *}",
                rankMethod.getAnnotation(Scheduled.class).cron()
        );
        assertEquals(
                "${nuono.competitor-analysis.monitor.scheduler.detail-cron:0 0 0/2 * * *}",
                detailMethod.getAnnotation(Scheduled.class).cron()
        );
    }

    private static CompetitorWatchProductScopeRow scope() {
        CompetitorWatchProductScopeRow row = new CompetitorWatchProductScopeRow();
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        return row;
    }
}
