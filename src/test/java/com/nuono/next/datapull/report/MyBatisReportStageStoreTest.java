package com.nuono.next.datapull.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.ReportFactApplyMapper;
import com.nuono.next.infrastructure.mapper.ReportStageMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MyBatisReportStageStoreTest {
    private static final Instant NOW_INSTANT = Instant.parse("2026-08-02T00:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(
            NOW_INSTANT,
            ZoneOffset.UTC
    );

    private final ReportStageMapper stages = mock(ReportStageMapper.class);
    private final ReportFactApplyMapper facts = mock(ReportFactApplyMapper.class);
    private final RecordingApplicator applicator = new RecordingApplicator();
    private final ExportReportIntent intent = ReportBridgeTestSupport.intent(
            OperationCode.DP02,
            "NOON_REPORT_ORDER"
    );
    private final MyBatisReportStageStore store = new MyBatisReportStageStore(
            stages,
            facts,
            List.of(applicator),
            Clock.fixed(NOW_INSTANT, ZoneOffset.UTC)
    );

    @BeforeEach
    void currentTask() {
        when(facts.selectTaskForUpdate(intent.getTaskId())).thenReturn(currentTaskRow());
        when(facts.selectMarker(intent.getTaskId())).thenReturn(null);
    }

    @Test
    void oneAdvanceAppliesAtMostTwoHundredRowsAndMovesTheSameDurableCursor() {
        when(stages.selectStageForUpdate(intent.getTaskId())).thenReturn(stage(250L, 0L));
        when(stages.selectNextApplySlice(intent.getTaskId(), 0L, 200))
                .thenReturn(slice(200L, 200L));
        when(stages.advanceApplyCursor(
                intent,
                0L,
                200L,
                0L,
                200L,
                0L,
                5L,
                NOW
        )).thenReturn(1);
        applicator.chunkWarnings = 5L;

        ReportImportResult result = store.applySealed(intent);

        assertThat(result.getStatus()).isEqualTo(ReportImportResult.Status.IN_PROGRESS);
        assertThat(applicator.chunkRows).containsExactly(200L);
        verify(facts, never()).insertMarkerIfLive(intent, NOW);
        verify(stages, never()).markApplied(intent, NOW);
    }

    @Test
    void onlyTheNoRemainderAdvanceFinalizesThenMarksApplied() {
        ReportStageState complete = stage(200L, 200L);
        complete.setApplyRowCursor(200L);
        complete.setAppliedWarningCount(5L);
        when(stages.selectStageForUpdate(intent.getTaskId())).thenReturn(complete);
        when(stages.selectNextApplySlice(intent.getTaskId(), 200L, 200))
                .thenReturn(slice(0L, null));
        when(facts.insertMarkerIfLive(intent, NOW)).thenReturn(1);
        when(stages.markApplied(intent, NOW)).thenReturn(1);

        ReportImportResult result = store.applySealed(intent);

        assertThat(result.getStatus()).isEqualTo(ReportImportResult.Status.APPLIED);
        assertThat(applicator.finalizeCalls).isEqualTo(1);
        InOrder order = inOrder(facts, stages);
        order.verify(facts).insertMarkerIfLive(intent, NOW);
        order.verify(stages).markApplied(intent, NOW);
    }

    private ReportStageApplySlice slice(Long rows, Long through) {
        ReportStageApplySlice slice = new ReportStageApplySlice();
        slice.setRowCount(rows);
        slice.setLastRowNumber(through);
        return slice;
    }

    private ReportStageState stage(long accepted, long applied) {
        ReportStageState stage = new ReportStageState();
        stage.setTaskId(intent.getTaskId());
        stage.setOperationCode(intent.getOperationCode());
        stage.setState("SEALED");
        stage.setDeclaredRowCount(accepted);
        stage.setSourceRowCount(accepted);
        stage.setAcceptedRowCount(accepted);
        stage.setBusinessSkippedRowCount(0L);
        stage.setIdentitySkippedRowCount(0L);
        stage.setApplyRowCursor(applied);
        stage.setAppliedRowCount(applied);
        stage.setAppliedWarningCount(0L);
        return stage;
    }

    private ReportApplyTaskRow currentTaskRow() {
        ReportApplyTaskRow task = new ReportApplyTaskRow();
        task.setTaskId(intent.getTaskId());
        task.setOperationCode(intent.getOperationCode());
        task.setScopeKey(intent.getScopeKey());
        task.setBusinessWindowKey(intent.getBusinessWindowKey());
        task.setFenceEpoch(intent.getFenceEpoch());
        task.setState("RUNNING");
        task.setLeaseOwner(intent.getLeaseOwner());
        task.setLeaseUntil(NOW.plusMinutes(1));
        return task;
    }

    private static final class RecordingApplicator implements ReportFactChunkApplicator {
        private final java.util.ArrayList<Long> chunkRows = new java.util.ArrayList<>();
        private long chunkWarnings;
        private int finalizeCalls;

        @Override
        public boolean supports(OperationCode operationCode) {
            return operationCode == OperationCode.DP02;
        }

        @Override
        public long applyChunk(
                ExportReportIntent intent,
                ReportStageState stage,
                long afterRowNumber,
                long throughRowNumber,
                long rowCount,
                LocalDateTime nowUtc
        ) {
            chunkRows.add(rowCount);
            return chunkWarnings;
        }

        @Override
        public void finalizeContainer(
                ExportReportIntent intent,
                ReportStageState stage,
                LocalDateTime nowUtc
        ) {
            finalizeCalls++;
        }
    }
}
