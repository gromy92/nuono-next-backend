package com.nuono.next.datapull.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.ReportFactApplyMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MyBatisReportFactApplyGuardTest {
    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void currentLeaseAppliesFactsThenPersistsMarker() {
        ReportFactApplyMapper mapper = mock(ReportFactApplyMapper.class);
        ExportReportIntent intent = intent();
        when(mapper.selectTaskForUpdate(intent.getTaskId())).thenReturn(liveTask(intent));
        when(mapper.insertMarkerIfLive(eq(intent), any(LocalDateTime.class))).thenReturn(1);
        AtomicInteger imports = new AtomicInteger();
        MyBatisReportFactApplyGuard guard = guard(mapper);

        ReportImportResult result = guard.apply(intent, () -> {
            imports.incrementAndGet();
            return ReportImportResult.applied();
        });

        assertThat(result.getStatus()).isEqualTo(ReportImportResult.Status.APPLIED);
        assertThat(imports.get()).isEqualTo(1);
        verify(mapper).insertMarkerIfLive(intent, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void staleLeaseSkipsFactsAndMarker() {
        ReportFactApplyMapper mapper = mock(ReportFactApplyMapper.class);
        ExportReportIntent intent = intent();
        ReportApplyTaskRow expired = liveTask(intent);
        expired.setLeaseUntil(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(mapper.selectTaskForUpdate(intent.getTaskId())).thenReturn(expired);
        AtomicInteger imports = new AtomicInteger();

        ReportImportResult result = guard(mapper).apply(intent, () -> {
            imports.incrementAndGet();
            return ReportImportResult.applied();
        });

        assertThat(result.getStatus()).isEqualTo(ReportImportResult.Status.STALE_FENCE);
        assertThat(imports.get()).isZero();
        verify(mapper, never()).insertMarkerIfLive(any(), any());
    }

    @Test
    void existingMarkerMakesUnknownCommitRetryIdempotent() {
        ReportFactApplyMapper mapper = mock(ReportFactApplyMapper.class);
        ExportReportIntent intent = intent();
        when(mapper.selectTaskForUpdate(intent.getTaskId())).thenReturn(liveTask(intent));
        when(mapper.selectMarker(intent.getTaskId())).thenReturn(marker(intent));
        AtomicInteger imports = new AtomicInteger();

        ReportImportResult result = guard(mapper).apply(intent, () -> {
            imports.incrementAndGet();
            return ReportImportResult.applied();
        });

        assertThat(result.getStatus()).isEqualTo(ReportImportResult.Status.APPLIED);
        assertThat(imports.get()).isZero();
        verify(mapper, never()).insertMarkerIfLive(any(), any());
    }

    @Test
    void markerIdentityDriftFailsClosed() {
        ReportFactApplyMapper mapper = mock(ReportFactApplyMapper.class);
        ExportReportIntent intent = intent();
        ReportApplyMarkerRow marker = marker(intent);
        marker.setBusinessWindowKey("DP02:date-range:wrong");
        when(mapper.selectTaskForUpdate(intent.getTaskId())).thenReturn(liveTask(intent));
        when(mapper.selectMarker(intent.getTaskId())).thenReturn(marker);

        assertThatThrownBy(() -> guard(mapper).apply(intent, ReportImportResult::applied))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("report fact marker identity drift");
    }

    private MyBatisReportFactApplyGuard guard(ReportFactApplyMapper mapper) {
        return new MyBatisReportFactApplyGuard(mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ExportReportIntent intent() {
        return ReportBridgeTestSupport.intent(OperationCode.DP02, "NOON_REPORT_ORDER");
    }

    private ReportApplyTaskRow liveTask(ExportReportIntent intent) {
        ReportApplyTaskRow row = new ReportApplyTaskRow();
        row.setTaskId(intent.getTaskId());
        row.setOperationCode(intent.getOperationCode());
        row.setScopeKey(intent.getScopeKey());
        row.setBusinessWindowKey(intent.getBusinessWindowKey());
        row.setFenceEpoch(intent.getFenceEpoch());
        row.setState("RUNNING");
        row.setLeaseOwner(intent.getLeaseOwner());
        row.setLeaseUntil(LocalDateTime.ofInstant(NOW.plusSeconds(60), ZoneOffset.UTC));
        return row;
    }

    private ReportApplyMarkerRow marker(ExportReportIntent intent) {
        ReportApplyMarkerRow row = new ReportApplyMarkerRow();
        row.setTaskId(intent.getTaskId());
        row.setOperationCode(intent.getOperationCode());
        row.setScopeKey(intent.getScopeKey());
        row.setBusinessWindowKey(intent.getBusinessWindowKey());
        row.setAppliedFenceEpoch(intent.getFenceEpoch());
        return row;
    }
}
