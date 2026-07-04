package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import org.mockito.InOrder;
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
    void enabledSchedulerRecoversStaleRefreshTasksBeforeSubmittingScopes() {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        CompetitorWatchProductScopeRow scope = scope();
        when(mapper.listRefreshableWatchProductScopes(100)).thenReturn(List.of(scope));

        assertEquals(1, scheduler.runDetailOnce());

        InOrder inOrder = inOrder(refreshService, mapper);
        inOrder.verify(refreshService).recoverStaleRefreshTasks();
        inOrder.verify(mapper).listRefreshableWatchProductScopes(100);
        inOrder.verify(refreshService).requestScheduledDetailMonitoring(501L, "STR108065-NSA", "SA");
    }

    @Test
    void enabledCompensationRetriesRecentTransientRankKeywordFailures() {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "compensationLookbackHours", 24);
        ReflectionTestUtils.setField(scheduler, "maxCompensationKeywordsPerTick", 50);
        when(refreshService.retryRecentTransientRankKeywordFailures(Duration.ofHours(24), 50)).thenReturn(10);

        assertEquals(10, scheduler.runRankFailureCompensationOnce());

        verify(refreshService).retryRecentTransientRankKeywordFailures(Duration.ofHours(24), 50);
        verifyNoInteractions(mapper);
    }

    @Test
    void scheduledAnnotationsUseFourDailyRankAndDailyDetailDefaults() throws Exception {
        Method rankMethod = CompetitorAnalysisMonitoringScheduler.class.getDeclaredMethod("runScheduledRankMonitoring");
        Method detailMethod = CompetitorAnalysisMonitoringScheduler.class.getDeclaredMethod("runScheduledDetailMonitoring");
        Method compensationMethod = CompetitorAnalysisMonitoringScheduler.class.getDeclaredMethod("runScheduledRankFailureCompensation");

        assertEquals(
                "${nuono.competitor-analysis.monitor.scheduler.rank-cron:0 0 0,6,12,18 * * *}",
                rankMethod.getAnnotation(Scheduled.class).cron()
        );
        assertEquals(
                "${nuono.competitor-analysis.monitor.scheduler.detail-cron:0 0 2 * * *}",
                detailMethod.getAnnotation(Scheduled.class).cron()
        );
        assertEquals(
                "${nuono.competitor-analysis.monitor.scheduler.compensation-fixed-delay-ms:600000}",
                compensationMethod.getAnnotation(Scheduled.class).fixedDelayString()
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
