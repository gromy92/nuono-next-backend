package com.nuono.next.datapull.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.ReportFactApplyMapper;
import com.nuono.next.infrastructure.mapper.ReportStageMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class ReportStageAppenderTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 0, 0);

    private final ReportStageMapper stages = mock(ReportStageMapper.class);
    private final ReportFactApplyMapper facts = mock(ReportFactApplyMapper.class);
    private final ExportReportIntent intent = ReportBridgeTestSupport.intent(
            OperationCode.DP02,
            "NOON_REPORT_ORDER"
    );
    private final ReportStageAppender appender = new ReportStageAppender(stages, facts);

    @BeforeEach
    void currentTaskAndStage() {
        when(facts.selectTaskForUpdate(intent.getTaskId())).thenReturn(currentTask());
        when(facts.selectMarker(intent.getTaskId())).thenReturn(null);
        when(stages.selectStageForUpdate(intent.getTaskId())).thenReturn(stage(2L));
    }

    @Test
    void databaseAcceptedIdentityConflictReclassifiesTheWholeAtomicChunk() {
        ReportPlannedRow conflict = ReportPlannedRow.accepted(1L, "same", "{\"id\":1}");
        ReportPlannedRow retained = ReportPlannedRow.accepted(2L, "other", "{\"id\":2}");
        String conflictDigest = conflict.getIdentitySha256();
        when(stages.selectExistingAcceptedIdentities(eq(intent.getTaskId()), anyList()))
                .thenReturn(Collections.emptyList(), List.of(conflictDigest));
        when(stages.insertStageRows(anyList(), eq(NOW)))
                .thenThrow(new DuplicateKeyException("accepted identity"))
                .thenReturn(2);
        when(stages.advanceStage(
                any(), any(), any(), anyLong(), anyLong(), anyLong(),
                anyLong(), anyLong(), anyLong(), any(), eq(NOW), anyBoolean()
        )).thenReturn(1);

        ReportImportResult result = appender.append(
                intent,
                chunk(2L, false, List.of(conflict, retained)),
                NOW
        );

        assertThat(result.getStatus()).isEqualTo(ReportImportResult.Status.IN_PROGRESS);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReportStageRowRecord>> rows = ArgumentCaptor.forClass(List.class);
        verify(stages, org.mockito.Mockito.times(2)).insertStageRows(rows.capture(), eq(NOW));
        assertThat(rows.getAllValues().get(1))
                .extracting(ReportStageRowRecord::getDecision)
                .containsExactly("LATER_IDENTITY_CONFLICT", "ACCEPTED");
        assertThat(rows.getAllValues().get(1).get(0).getAcceptedIdentitySha256()).isNull();
        verify(stages).advanceStage(
                intent,
                "artifact",
                "sha",
                0L,
                10L,
                2L,
                1L,
                0L,
                1L,
                "VALIDATING",
                NOW,
                false
        );
    }

    @Test
    void containerPoisonMustUpdateExactlyOneStage() {
        when(stages.selectStageForUpdate(intent.getTaskId())).thenReturn(stage(1L));
        when(stages.poisonStage(intent, "REPORT_ROW_OUTSIDE_CONTAINER", NOW)).thenReturn(0);

        assertThatThrownBy(() -> appender.append(
                intent,
                chunk(1L, false, List.of(ReportPlannedRow.containerError(1L))),
                NOW
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("report stage poison CAS was rejected");
        verify(facts, never()).insertMarkerIfLive(any(), any());
    }

    @Test
    void containerCapacityFailurePoisonsBeforeSealOrMarker() {
        when(stages.selectStageForUpdate(intent.getTaskId())).thenReturn(stage(1L));
        when(stages.poisonStage(intent, "REPORT_ROW_OUTSIDE_CONTAINER", NOW)).thenReturn(1);

        ReportImportResult result = appender.append(
                intent,
                chunk(1L, true, List.of(ReportPlannedRow.containerError(1L))),
                NOW
        );

        assertThat(result.getStatus()).isEqualTo(ReportImportResult.Status.CONTRACT_ERROR);
        assertThat(result.getSanitizedCode()).isEqualTo("REPORT_ROW_OUTSIDE_CONTAINER");
        verify(stages, never()).advanceStage(
                any(), any(), any(), anyLong(), anyLong(), anyLong(),
                anyLong(), anyLong(), anyLong(), any(), any(), anyBoolean());
        verify(facts, never()).insertMarkerIfLive(any(), any());
    }

    @Test
    void providerCountPoisonMustUpdateExactlyOneStage() {
        when(stages.selectStageForUpdate(intent.getTaskId())).thenReturn(stage(1L));
        when(stages.selectExistingAcceptedIdentities(eq(intent.getTaskId()), anyList()))
                .thenReturn(Collections.emptyList());
        when(stages.insertStageRows(anyList(), eq(NOW))).thenReturn(2);
        when(stages.poisonStage(intent, "REPORT_PROVIDER_ROW_COUNT_CONFLICT", NOW))
                .thenReturn(0);

        assertThatThrownBy(() -> appender.append(
                intent,
                chunk(1L, false, List.of(
                        ReportPlannedRow.accepted(1L, "one", "{}"),
                        ReportPlannedRow.accepted(2L, "two", "{}")
                )),
                NOW
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("report stage poison CAS was rejected");
    }

    @Test
    void stageEntryRejectsMoreThanTwoHundredPhysicalRows() {
        List<ReportPlannedRow> rows = new ArrayList<>();
        for (long rowNumber = 1L; rowNumber <= 201L; rowNumber++) {
            rows.add(ReportPlannedRow.businessSkip(rowNumber));
        }

        assertThatThrownBy(() -> appender.append(intent, chunk(201L, false, rows), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("report stage row bound exceeded");
    }

    @Test
    void locallyCountedCompleteArtifactSealsWithObservedPhysicalRows() {
        when(stages.selectStageForUpdate(intent.getTaskId()))
                .thenReturn(stage(Long.MAX_VALUE));
        when(stages.selectExistingAcceptedIdentities(eq(intent.getTaskId()), anyList()))
                .thenReturn(Collections.emptyList());
        when(stages.insertStageRows(anyList(), eq(NOW))).thenReturn(2);
        when(stages.advanceStage(
                any(), any(), any(), anyLong(), anyLong(), anyLong(),
                anyLong(), anyLong(), anyLong(), any(), eq(NOW), eq(true)
        )).thenReturn(1);

        ReportImportResult result = appender.append(
                intent,
                localChunk(true, List.of(
                        ReportPlannedRow.accepted(1L, "one", "{}"),
                        ReportPlannedRow.businessSkip(2L)
                )),
                NOW
        );

        assertThat(result.getStatus()).isEqualTo(ReportImportResult.Status.IN_PROGRESS);
        verify(stages).advanceStage(
                intent, "artifact", "sha", 0L, 10L, 2L,
                1L, 1L, 0L, "SEALED", NOW, true
        );
        verify(stages, never()).poisonStage(
                intent, "REPORT_PROVIDER_ROW_COUNT_CONFLICT", NOW
        );
    }

    @Test
    void locallyCountedEmptyArtifactWaitsWithoutApplyingFacts() {
        when(stages.selectStageForUpdate(intent.getTaskId()))
                .thenReturn(stage(Long.MAX_VALUE));
        when(stages.advanceStage(
                any(), any(), any(), anyLong(), anyLong(), anyLong(),
                anyLong(), anyLong(), anyLong(), any(), eq(NOW), eq(true)
        )).thenReturn(1);

        ReportImportResult result = appender.append(
                intent,
                localChunk(true, List.of()),
                NOW
        );

        assertThat(result.getStatus()).isEqualTo(
                ReportImportResult.Status.AWAITING_AUTHORITATIVE_EMPTY_PROOF
        );
        verify(stages).advanceStage(
                intent, "artifact", "sha", 0L, 10L, 0L,
                0L, 0L, 0L, "EMPTY_UNPROVEN", NOW, true
        );
        verify(facts, never()).insertMarkerIfLive(any(), any());
    }

    private ReportStageChunk chunk(
            long declaredRows,
            boolean endOfFile,
            List<ReportPlannedRow> rows
    ) {
        return new ReportStageChunk(
                "artifact",
                "sha",
                declaredRows,
                false,
                "[\"id\"]",
                0L,
                10L,
                endOfFile,
                rows
        );
    }

    private ReportStageChunk localChunk(
            boolean endOfFile,
            List<ReportPlannedRow> rows
    ) {
        return new ReportStageChunk(
                "artifact", "sha", Long.MAX_VALUE, true, "[\"id\"]",
                0L, 10L, endOfFile, rows
        );
    }

    private ReportStageState stage(long declaredRows) {
        ReportStageState stage = new ReportStageState();
        stage.setTaskId(intent.getTaskId());
        stage.setOperationCode(intent.getOperationCode());
        stage.setArtifactKey("artifact");
        stage.setArtifactSha256("sha");
        stage.setHeaderJson("[\"id\"]");
        stage.setNextByteOffset(0L);
        stage.setDeclaredRowCount(declaredRows);
        stage.setSourceRowCount(0L);
        stage.setAcceptedRowCount(0L);
        stage.setBusinessSkippedRowCount(0L);
        stage.setIdentitySkippedRowCount(0L);
        stage.setState("VALIDATING");
        return stage;
    }

    private ReportApplyTaskRow currentTask() {
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
}
