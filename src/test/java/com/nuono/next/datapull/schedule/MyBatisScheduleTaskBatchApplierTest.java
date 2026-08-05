package com.nuono.next.datapull.schedule;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.persistence.MyBatisDataPullTaskBatchStore;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScheduleScanMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleTaskBatchMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleTaskPlanMapper;
import com.nuono.next.infrastructure.mapper.DataPullTaskCompactionMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyBatisScheduleTaskBatchApplierTest {

    @Test
    void scopeWithoutDueWindowIsDeletedAfterPlanning() {
        Fixture fixture = new Fixture();
        ScheduleSourceStageRow scope = stage("scope-a");
        when(fixture.scans.lockActiveEpoch(OperationCode.DP02)).thenReturn(epoch(4));
        when(fixture.mapper.listScheduleStageAfter(
                OperationCode.DP02, 4, null, 64
        )).thenReturn(List.of(scope));
        when(fixture.mapper.listLatestSlots(OperationCode.DP02, List.of("scope-a")))
                .thenReturn(List.of());
        when(fixture.schedule.missedSlotsPage(
                "scope-a", Instant.parse("2026-08-03T00:00:00Z"),
                Instant.parse("2026-08-04T00:00:00Z"), 64
        )).thenReturn(new ScheduleSlotPage(List.of(), false, 64));
        when(fixture.mapper.deleteCompletedScheduleStages(
                OperationCode.DP02, 4, List.of("scope-a")
        )).thenReturn(1);
        when(fixture.mapper.advanceSchedulePhase(
                OperationCode.DP02, 4, 0, null, "scope-a", "SCHEDULING"
        )).thenReturn(1);

        assertTrue(fixture.applier.advance(
                fixture.job, fixture.schedule, Instant.parse("2026-08-04T00:00:00Z")
        ).isEmpty());

        verify(fixture.mapper).deleteCompletedScheduleStages(
                OperationCode.DP02, 4, List.of("scope-a")
        );
        verify(fixture.mapper, never()).updateRunningScheduleStages(
                OperationCode.DP02, 4, List.of()
        );
    }

    @Test
    void emptyStageAdvancesHeaderToComplete() {
        Fixture fixture = new Fixture();
        when(fixture.scans.lockActiveEpoch(OperationCode.DP02)).thenReturn(epoch(5));
        when(fixture.mapper.listScheduleStageAfter(
                OperationCode.DP02, 5, null, 64
        )).thenReturn(List.of());
        when(fixture.mapper.findPendingScheduleAtOrBefore(
                OperationCode.DP02, 5, null
        )).thenReturn(null);
        when(fixture.mapper.advanceSchedulePhase(
                OperationCode.DP02, 5, 0, null, null, "COMPLETE"
        )).thenReturn(1);

        fixture.applier.advance(
                fixture.job, fixture.schedule, Instant.parse("2026-08-04T00:00:00Z")
        );

        verify(fixture.mapper).advanceSchedulePhase(
                OperationCode.DP02, 5, 0, null, null, "COMPLETE"
        );
    }

    @Test
    void bindingCompleteOperationRequiresItsModulePayloadAdapter() {
        DataPullScheduleScanMapper scans = mock(DataPullScheduleScanMapper.class);
        DataPullScheduleTaskPlanMapper mapper = mock(DataPullScheduleTaskPlanMapper.class);
        DataPullJob job = mock(DataPullJob.class);
        DataPullSchedule schedule = mock(DataPullSchedule.class);
        when(job.operationCode()).thenReturn(OperationCode.DP08A);
        when(schedule.operationCode()).thenReturn(OperationCode.DP08A);
        when(scans.lockActiveEpoch(OperationCode.DP08A)).thenReturn(
                epoch(OperationCode.DP08A, 6, "COMPLETE")
        );
        MyBatisScheduleTaskBatchApplier applier = new MyBatisScheduleTaskBatchApplier(
                scans,
                mapper,
                new MyBatisDataPullTaskBatchStore(
                        mock(DataPullScheduleTaskBatchMapper.class),
                        mock(DataPullTaskCompactionMapper.class)
                ),
                new ScheduleTaskPayloadBinderRegistry(List.of())
        );

        assertThatThrownBy(() -> applier.advance(
                job,
                schedule,
                Instant.parse("2026-08-04T00:00:00Z")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("DP_SCHEDULE_PAYLOAD_BINDER_MISSING:DP08A");

        verify(mapper, never()).listScheduleStageAfter(
                OperationCode.DP08A, 6, null, 64
        );
    }

    private static ScheduleSourceEpochRow epoch(long number) {
        return epoch(OperationCode.DP02, number, "NOT_REQUIRED");
    }

    private static ScheduleSourceEpochRow epoch(
            OperationCode operation,
            long number,
            String bindingCloseState
    ) {
        ScheduleSourceEpochRow row = new ScheduleSourceEpochRow();
        row.setOperationCode(operation);
        row.setEpochNo(number);
        row.setEpochState("SCHEDULING");
        row.setBindingCloseState(bindingCloseState);
        row.setReconcileUntilUtc(LocalDateTime.of(2026, 8, 4, 0, 0));
        row.setVersion(0L);
        return row;
    }

    private static ScheduleSourceStageRow stage(String scopeKey) {
        ScheduleSourceStageRow row = new ScheduleSourceStageRow();
        row.setScopeKey(scopeKey);
        row.setReconcileAfterUtc(LocalDateTime.of(2026, 8, 3, 0, 0));
        return row;
    }

    private static final class Fixture {
        private final DataPullScheduleScanMapper scans = mock(DataPullScheduleScanMapper.class);
        private final DataPullScheduleTaskPlanMapper mapper = mock(
                DataPullScheduleTaskPlanMapper.class
        );
        private final DataPullSchedule schedule = mock(DataPullSchedule.class);
        private final DataPullJob job = mock(DataPullJob.class);
        private final MyBatisScheduleTaskBatchApplier applier;

        private Fixture() {
            when(job.operationCode()).thenReturn(OperationCode.DP02);
            when(schedule.operationCode()).thenReturn(OperationCode.DP02);
            MyBatisDataPullTaskBatchStore tasks = new MyBatisDataPullTaskBatchStore(
                    mock(DataPullScheduleTaskBatchMapper.class),
                    mock(DataPullTaskCompactionMapper.class)
            );
            applier = new MyBatisScheduleTaskBatchApplier(
                    scans,
                    mapper,
                    tasks,
                    new ScheduleTaskPayloadBinderRegistry(List.of())
            );
        }
    }
}
