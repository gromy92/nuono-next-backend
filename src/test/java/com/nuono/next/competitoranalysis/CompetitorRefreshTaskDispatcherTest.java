package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorRefreshTaskDispatcherTest {
    @Mock
    private CompetitorAnalysisMapper mapper;
    @Mock
    private OperationalTaskService operationalTaskService;

    private List<Runnable> submitted;
    private OperationalTask task;
    private CompetitorSearchRunRow run;

    @BeforeEach
    void setUp() {
        submitted = new ArrayList<>();
        task = new OperationalTask();
        task.setId(150001L);
        run = new CompetitorSearchRunRow();
        run.setId(220001L);
    }

    @Test
    void sameProcessDoesNotQueueTheSameTaskTwiceBeforeItRuns() {
        CompetitorRefreshTaskDispatcher dispatcher = dispatcher();

        assertTrue(dispatcher.submit("501::store", task, run, "running", () -> { }));
        assertFalse(dispatcher.submit("501::store", task, run, "running", () -> { }));

        assertEquals(1, submitted.size());
    }

    @Test
    void twoProcessesCanResubmitButOnlyTheDatabaseClaimExecutesOnce() {
        CompetitorRefreshTaskDispatcher oldProcess = dispatcher();
        CompetitorRefreshTaskDispatcher restartedProcess = dispatcher();
        AtomicInteger executions = new AtomicInteger();
        when(operationalTaskService.claimQueued(150001L, "running")).thenReturn(true, false);
        when(mapper.markSearchRunRunning(220001L)).thenReturn(1);

        assertTrue(oldProcess.submit("501::store", task, run, "running", executions::incrementAndGet));
        assertTrue(restartedProcess.submit("501::store", task, run, "running", executions::incrementAndGet));
        submitted.forEach(Runnable::run);

        assertEquals(1, executions.get());
        verify(mapper, times(1)).markSearchRunRunning(220001L);
    }

    @Test
    void holdRaisedWhileWaitingLeavesTaskQueuedAndReleasesReservation() {
        CompetitorRefreshTaskDispatcher dispatcher = dispatcher();
        AtomicInteger executions = new AtomicInteger();

        assertTrue(dispatcher.submit(
                "501::store",
                task,
                run,
                "running",
                () -> false,
                executions::incrementAndGet
        ));
        submitted.remove(0).run();

        assertEquals(0, executions.get());
        verify(operationalTaskService, never()).claimQueued(150001L, "running");
        assertTrue(dispatcher.submit("501::store", task, run, "running", () -> { }));
    }

    @Test
    void searchRunClaimConflictPreventsExternalExecution() {
        CompetitorRefreshTaskDispatcher dispatcher = dispatcher();
        AtomicInteger executions = new AtomicInteger();
        when(operationalTaskService.claimQueued(150001L, "running")).thenReturn(true);
        when(mapper.markSearchRunRunning(220001L)).thenReturn(0);

        dispatcher.submit("501::store", task, run, "running", executions::incrementAndGet);
        submitted.get(0).run();

        assertEquals(0, executions.get());
        verify(operationalTaskService).fail(
                150001L,
                "COMPETITOR_SEARCH_RUN_CLAIM_CONFLICT",
                "刷新执行记录状态冲突，任务未执行。"
        );
    }

    @Test
    void oneBusyAccountCannotConsumeEveryDispatcherReservation() {
        CompetitorRefreshTaskDispatcher dispatcher = dispatcher();
        for (long index = 1L; index <= 50L; index++) {
            task.setId(index);
            run.setId(1000L + index);
            assertTrue(dispatcher.submit("busy", task, run, "running", () -> { }));
        }
        task.setId(51L);
        run.setId(1051L);
        assertFalse(dispatcher.submit("busy", task, run, "running", () -> { }));
        assertTrue(dispatcher.submit("other", task, run, "running", () -> { }));
        assertEquals(51, submitted.size());
    }

    private CompetitorRefreshTaskDispatcher dispatcher() {
        return new CompetitorRefreshTaskDispatcher(
                mapper,
                operationalTaskService,
                (accountKey, runnable) -> submitted.add(runnable)
        );
    }
}
