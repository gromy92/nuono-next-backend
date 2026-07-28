package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorRefreshTaskFactoryValidationTest {
    private static final LocalDateTime STALE_BEFORE =
            LocalDateTime.parse("2026-06-06T07:30:00");

    @Mock
    private CompetitorAnalysisMapper mapper;
    @Mock
    private OperationalTaskService operationalTaskService;

    @Test
    void malformedReplacementPayloadFailsBeforeClaimingTheStaleGeneration() {
        OperationalTask staleTask = staleTask();
        staleTask.setPayloadJson("[");

        assertThrows(
                CompetitorRefreshRecoveryPayloadException.class,
                () -> replace(staleTask, staleRun())
        );

        verifyNoClaimOrReplacement();
        verify(mapper, never()).markActiveSearchRunFailedForTask(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void mismatchedRecoveryIdentityFailsBeforeClaimingOrReplacing() {
        OperationalTask staleTask = staleTask();
        staleTask.setNaturalKey("watchProduct:999999:detail");

        assertThrows(
                CompetitorRefreshRecoveryIdentityException.class,
                () -> replace(staleTask, staleRun())
        );

        verifyNoClaimOrReplacement();
    }

    @Test
    void unknownTriggerModeFailsClosedBeforeClaimingStaleTask() {
        CompetitorSearchRunRow staleRun = staleRun();
        staleRun.setTriggerMode("UNKNOWN_MODE");

        assertThrows(
                CompetitorRefreshRecoveryIdentityException.class,
                () -> replace(staleTask(), staleRun)
        );

        verifyNoClaimOrReplacement();
    }

    private void replace(OperationalTask staleTask, CompetitorSearchRunRow staleRun) {
        new CompetitorRefreshTaskFactory(mapper, operationalTaskService).replaceStale(
                staleTask,
                staleRun,
                watchProduct(),
                STALE_BEFORE,
                501L,
                CompetitorRefreshExecutionMode.SCHEDULED_DETAIL,
                null,
                0,
                ignored -> { }
        );
    }

    private void verifyNoClaimOrReplacement() {
        verify(operationalTaskService, never()).failStaleRunning(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(operationalTaskService, never()).queue(any(), any(), any());
    }

    private static OperationalTask staleTask() {
        OperationalTask task = new OperationalTask();
        task.setId(150001L);
        task.setTaskType(CompetitorAnalysisRefreshService.TASK_TYPE);
        task.setNaturalKey("watchProduct:180001:detail");
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setUpdatedAt(LocalDateTime.parse("2026-06-06T07:20:00"));
        return task;
    }

    private static CompetitorSearchRunRow staleRun() {
        CompetitorSearchRunRow run = new CompetitorSearchRunRow();
        run.setId(220001L);
        run.setTaskId(150001L);
        run.setWatchProductId(180001L);
        run.setStatus("RUNNING");
        run.setTriggerMode("SCHEDULED_DETAIL_MONITOR");
        run.setRequestedBy(501L);
        return run;
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180001L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        return row;
    }
}
