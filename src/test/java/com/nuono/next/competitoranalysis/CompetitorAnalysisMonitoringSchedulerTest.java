package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Duration;
import org.mockito.InOrder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
    private CompetitorAnalysisRefreshService refreshService;

    private CompetitorAnalysisMonitoringScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CompetitorAnalysisMonitoringScheduler(refreshService);
    }

    @Test
    void disabledSchedulerDoesNotSubmitAnyScope() {
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        assertEquals(0, scheduler.runRankOnce());
        assertEquals(0, scheduler.runDetailOnce());

        verifyNoInteractions(refreshService);
    }

    @Test
    void enabledRankSchedulerSubmitsOnlyRankMonitoring() {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        when(refreshService.runScheduledRankCycle()).thenReturn(1);

        assertEquals(1, scheduler.runRankOnce());

        verify(refreshService).runScheduledRankCycle();
        verify(refreshService, never()).runScheduledDetailCycle();
    }

    @Test
    void enabledRankSchedulerReportsAllScopesCompletedByTheDurableCycle() {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        when(refreshService.runScheduledRankCycle()).thenReturn(101);

        assertEquals(101, scheduler.runRankOnce());
        verify(refreshService).runScheduledRankCycle();
    }

    @Test
    void enabledDetailSchedulerSubmitsOnlyDetailMonitoring() {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        when(refreshService.runScheduledDetailCycle()).thenReturn(1);

        assertEquals(1, scheduler.runDetailOnce());

        verify(refreshService).runScheduledDetailCycle();
        verify(refreshService, never()).runScheduledRankCycle();
    }

    @Test
    void enabledSchedulerRecoversStaleRefreshTasksBeforeSubmittingScopes() {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        when(refreshService.runScheduledDetailCycle()).thenReturn(1);

        assertEquals(1, scheduler.runDetailOnce());

        InOrder inOrder = inOrder(refreshService);
        inOrder.verify(refreshService).resumeQueuedRefreshTasks();
        inOrder.verify(refreshService).recoverStaleRefreshTasks();
        inOrder.verify(refreshService).runScheduledDetailCycle();
    }

    @Test
    void enabledStartupAndPeriodicRecoveryResumeQueuedAndReplaceStaleRunningTasks() {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        when(refreshService.resumeQueuedRefreshTasks()).thenReturn(4);
        when(refreshService.recoverStaleRefreshTasks()).thenReturn(1);

        assertEquals(5, scheduler.runStartupRecoveryOnce());
        assertEquals(5, scheduler.runTaskRecoveryOnce());

        InOrder inOrder = inOrder(refreshService);
        inOrder.verify(refreshService).resumeQueuedRefreshTasks();
        inOrder.verify(refreshService).recoverStaleRefreshTasks();
        inOrder.verify(refreshService).resumeQueuedRefreshTasks();
        inOrder.verify(refreshService).recoverStaleRefreshTasks();
    }

    @Test
    void disabledAutomaticCyclesStillRecoverDurableManualTasks() {
        ReflectionTestUtils.setField(scheduler, "enabled", false);
        when(refreshService.resumeQueuedRefreshTasks()).thenReturn(2);

        assertEquals(2, scheduler.runStartupRecoveryOnce());

        verify(refreshService).resumeQueuedRefreshTasks();
        verify(refreshService).recoverStaleRefreshTasks();
    }

    @Test
    void startupRecoveryFailureDoesNotAbortApplicationReadiness() {
        when(refreshService.resumeQueuedRefreshTasks())
                .thenThrow(new IllegalStateException("database unavailable"));

        assertDoesNotThrow(scheduler::resumeQueuedRefreshTasksAfterStartup);

        verify(refreshService).resumeQueuedRefreshTasks();
    }

    @Test
    void enabledCompensationRetriesRecentTransientRankKeywordFailures() {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "compensationLookbackHours", 24);
        ReflectionTestUtils.setField(scheduler, "maxCompensationKeywordsPerTick", 50);
        when(refreshService.retryRecentTransientRankKeywordFailures(Duration.ofHours(24), 50)).thenReturn(10);

        assertEquals(10, scheduler.runRankFailureCompensationOnce());

        verify(refreshService).retryRecentTransientRankKeywordFailures(Duration.ofHours(24), 50);
    }

    @Test
    void scheduledAnnotationsUseFourDailyRankAndDailyDetailDefaults() throws Exception {
        Method rankMethod = CompetitorAnalysisMonitoringScheduler.class.getDeclaredMethod("runScheduledRankMonitoring");
        Method detailMethod = CompetitorAnalysisMonitoringScheduler.class.getDeclaredMethod("runScheduledDetailMonitoring");
        Method compensationMethod = CompetitorAnalysisMonitoringScheduler.class.getDeclaredMethod("runScheduledRankFailureCompensation");
        Method recoveryMethod = CompetitorAnalysisMonitoringScheduler.class.getDeclaredMethod("runScheduledTaskRecovery");
        Method startupMethod = CompetitorAnalysisMonitoringScheduler.class.getDeclaredMethod("resumeQueuedRefreshTasksAfterStartup");

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
        assertEquals(
                "${nuono.competitor-analysis.monitor.scheduler.task-recovery-fixed-delay-ms:60000}",
                recoveryMethod.getAnnotation(Scheduled.class).fixedDelayString()
        );
        assertEquals(ApplicationReadyEvent.class, startupMethod.getAnnotation(EventListener.class).value()[0]);
    }

}
