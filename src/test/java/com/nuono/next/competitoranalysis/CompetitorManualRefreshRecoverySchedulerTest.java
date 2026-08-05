package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorManualRefreshRecoverySchedulerTest {

    @Mock
    private CompetitorAnalysisRefreshService refreshService;

    private CompetitorManualRefreshRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CompetitorManualRefreshRecoveryScheduler(refreshService);
    }

    @Test
    void recoversOnlyUserRequestedRefreshTasks() {
        when(refreshService.resumeQueuedManualRefreshTasks()).thenReturn(2);
        when(refreshService.recoverStaleManualRefreshTasks()).thenReturn(1);

        assertEquals(3, scheduler.runOnce());

        verify(refreshService).resumeQueuedManualRefreshTasks();
        verify(refreshService).recoverStaleManualRefreshTasks();
    }

    @Test
    void startupRecoveryFailureDoesNotAbortApplicationReadiness() {
        when(refreshService.resumeQueuedManualRefreshTasks())
                .thenThrow(new IllegalStateException("database unavailable"));

        assertDoesNotThrow(scheduler::recoverAfterStartup);

        verify(refreshService).resumeQueuedManualRefreshTasks();
    }
}
