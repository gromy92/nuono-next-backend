package com.nuono.next.datapull.report;

import com.nuono.next.infrastructure.mapper.ReportFactApplyMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/** Production Implementation of the report import fence and unknown-commit marker. */
public class MyBatisReportFactApplyGuard implements ReportFactApplyGuard {
    private final ReportFactApplyMapper mapper;
    private final Clock clock;

    public MyBatisReportFactApplyGuard(ReportFactApplyMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    MyBatisReportFactApplyGuard(ReportFactApplyMapper mapper, Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public ReportImportResult apply(
            ExportReportIntent intent,
            Supplier<ReportImportResult> factImport
    ) {
        ExportReportIntent value = Objects.requireNonNull(intent, "intent");
        ReportApplyTaskRow task = mapper.selectTaskForUpdate(value.getTaskId());
        if (!isCurrent(task, value, nowUtc())) {
            return ReportImportResult.staleFence();
        }
        ReportApplyMarkerRow marker = mapper.selectMarker(value.getTaskId());
        if (marker != null) {
            requireSameMarker(marker, value);
            return ReportImportResult.applied();
        }

        ReportImportResult result = Objects.requireNonNull(
                Objects.requireNonNull(factImport, "factImport").get(),
                "report fact import result"
        );
        if (result.getStatus() != ReportImportResult.Status.APPLIED) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return result;
        }
        if (mapper.insertMarkerIfLive(value, nowUtc()) != 1) {
            throw new IllegalStateException("report fact marker rejected after fact import");
        }
        return result;
    }

    private boolean isCurrent(
            ReportApplyTaskRow task,
            ExportReportIntent intent,
            LocalDateTime nowUtc
    ) {
        return task != null
                && Objects.equals(task.getTaskId(), intent.getTaskId())
                && task.getOperationCode() == intent.getOperationCode()
                && Objects.equals(task.getScopeKey(), intent.getScopeKey())
                && Objects.equals(task.getBusinessWindowKey(), intent.getBusinessWindowKey())
                && Objects.equals(task.getFenceEpoch(), intent.getFenceEpoch())
                && "RUNNING".equals(task.getState())
                && Objects.equals(task.getLeaseOwner(), intent.getLeaseOwner())
                && task.getLeaseUntil() != null
                && task.getLeaseUntil().isAfter(nowUtc);
    }

    private void requireSameMarker(
            ReportApplyMarkerRow marker,
            ExportReportIntent intent
    ) {
        if (!Objects.equals(marker.getTaskId(), intent.getTaskId())
                || marker.getOperationCode() != intent.getOperationCode()
                || !Objects.equals(marker.getScopeKey(), intent.getScopeKey())
                || !Objects.equals(marker.getBusinessWindowKey(), intent.getBusinessWindowKey())
                || marker.getAppliedFenceEpoch() == null
                || marker.getAppliedFenceEpoch() < 1L) {
            throw new IllegalStateException("report fact marker identity drift");
        }
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
