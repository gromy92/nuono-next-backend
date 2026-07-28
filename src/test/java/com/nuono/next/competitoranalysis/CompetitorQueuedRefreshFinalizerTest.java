package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTaskService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class CompetitorQueuedRefreshFinalizerTest {
    @Mock
    private CompetitorAnalysisMapper mapper;
    @Mock
    private OperationalTaskService operationalTaskService;

    @Test
    void locksTaskBeforeRunAndFinalizesBothTogether() {
        when(mapper.lockQueuedRefreshTask(150001L)).thenReturn(150001L);
        when(mapper.lockQueuedRefreshRun(150001L, 220001L, 180001L))
                .thenReturn(220001L);
        when(mapper.failQueuedRefreshRun(
                150001L, 220001L, 180001L, "WATCH_MISSING", "missing"
        )).thenReturn(1);
        CompetitorRefreshExecutionFinalizer finalizer =
                CompetitorRefreshExecutionFinalizer.unfenced(mapper, operationalTaskService);

        assertTrue(finalizer.failQueued(
                150001L, 220001L, 180001L, "WATCH_MISSING", "missing"
        ));

        InOrder order = inOrder(mapper, operationalTaskService);
        order.verify(mapper).lockQueuedRefreshTask(150001L);
        order.verify(mapper).lockQueuedRefreshRun(150001L, 220001L, 180001L);
        order.verify(mapper).failQueuedRefreshRun(
                150001L, 220001L, 180001L, "WATCH_MISSING", "missing"
        );
        order.verify(operationalTaskService).fail(150001L, "WATCH_MISSING", "missing");
    }

    @Test
    void staleQueuedSnapshotCannotMutateTaskOrRun() {
        when(mapper.lockQueuedRefreshTask(150001L)).thenReturn(null);
        CompetitorRefreshExecutionFinalizer finalizer =
                CompetitorRefreshExecutionFinalizer.unfenced(mapper, operationalTaskService);

        assertFalse(finalizer.failQueued(
                150001L, 220001L, 180001L, "WATCH_MISSING", "missing"
        ));

        verify(mapper, never()).lockQueuedRefreshRun(150001L, 220001L, 180001L);
        verify(mapper, never()).failQueuedRefreshRun(
                150001L, 220001L, 180001L, "WATCH_MISSING", "missing"
        );
        verify(operationalTaskService, never()).fail(150001L, "WATCH_MISSING", "missing");
    }

    @Test
    void queuedFinalizationIsATransactionalBoundary() throws Exception {
        Method method = CompetitorRefreshExecutionFinalizer.class.getMethod(
                "failQueued",
                Long.class,
                Long.class,
                Long.class,
                String.class,
                String.class
        );
        assertTrue(method.isAnnotationPresent(Transactional.class));
    }
}
