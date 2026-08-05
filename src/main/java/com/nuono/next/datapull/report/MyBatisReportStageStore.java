package com.nuono.next.datapull.report;

import com.nuono.next.infrastructure.mapper.FbnReportBulkMapper;
import com.nuono.next.infrastructure.mapper.LegacyReportFactBulkMapper;
import com.nuono.next.infrastructure.mapper.ReportFactApplyMapper;
import com.nuono.next.infrastructure.mapper.ReportStageMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Deep Fact Writer Implementation: bounded staging transactions plus one constant-statement seal.
 */
public class MyBatisReportStageStore implements ReportStageStore {
    static final int APPLY_CHUNK_ROWS = 200;
    private final ReportStageMapper stageMapper;
    private final ReportFactApplyMapper applyMapper;
    private final List<ReportFactChunkApplicator> factApplicators;
    private final ReportStageAppender stageAppender;
    private final Clock clock;

    public MyBatisReportStageStore(
            ReportStageMapper stageMapper,
            ReportFactApplyMapper applyMapper,
            LegacyReportFactBulkMapper legacyMapper,
            FbnReportBulkMapper fbnMapper
    ) {
        this(
                stageMapper,
                applyMapper,
                List.of(
                        new LegacyReportFactChunkApplicator(legacyMapper),
                        new FbnReportFactChunkApplicator(fbnMapper)
                ),
                Clock.systemUTC()
        );
    }

    MyBatisReportStageStore(
            ReportStageMapper stageMapper,
            ReportFactApplyMapper applyMapper,
            List<ReportFactChunkApplicator> factApplicators,
            Clock clock
    ) {
        this.stageMapper = Objects.requireNonNull(stageMapper, "stageMapper");
        this.applyMapper = Objects.requireNonNull(applyMapper, "applyMapper");
        this.factApplicators = List.copyOf(factApplicators);
        this.stageAppender = new ReportStageAppender(stageMapper, applyMapper);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ReportStageState load(long taskId) {
        return stageMapper.selectStage(taskId);
    }

    @Override
    @Transactional(timeout = 10)
    public ReportImportResult stage(ExportReportIntent intent, ReportStageChunk chunk) {
        return stageAppender.append(intent, chunk, nowUtc());
    }

    @Override
    @Transactional(timeout = 10)
    public ReportImportResult applySealed(ExportReportIntent intent) {
        ExportReportIntent safeIntent = Objects.requireNonNull(intent, "intent");
        LocalDateTime now = nowUtc();
        ReportApplyTaskRow task = applyMapper.selectTaskForUpdate(safeIntent.getTaskId());
        if (!isCurrent(task, safeIntent, now)) {
            return ReportImportResult.staleFence();
        }
        ReportApplyMarkerRow marker = applyMapper.selectMarker(safeIntent.getTaskId());
        if (marker != null) {
            requireSameMarker(marker, safeIntent);
            return ReportImportResult.applied();
        }
        ReportStageState stage = requireStage(
                stageMapper.selectStageForUpdate(safeIntent.getTaskId())
        );
        requireStageIntent(stage, safeIntent);
        if ("POISONED".equals(stage.getState())) {
            return ReportImportResult.contractError(stage.getPoisonCode());
        }
        if ("EMPTY_UNPROVEN".equals(stage.getState())) {
            return ReportImportResult.awaitingAuthoritativeEmptyProof();
        }
        if (!"SEALED".equals(stage.getState())) {
            return ReportImportResult.inProgress();
        }

        long accepted = value(stage.getAcceptedRowCount());
        if (!exactAccounting(stage)) {
            return rollbackContract("REPORT_STAGE_ACCOUNTING_MISMATCH");
        }
        long cursor = value(stage.getApplyRowCursor());
        long applied = value(stage.getAppliedRowCount());
        long warnings = value(stage.getAppliedWarningCount());
        ReportStageApplySlice slice = Objects.requireNonNull(
                stageMapper.selectNextApplySlice(
                        safeIntent.getTaskId(),
                        cursor,
                        APPLY_CHUNK_ROWS
                ),
                "report apply slice"
        );
        long chunkRows = value(slice.getRowCount());
        if (chunkRows > 0L) {
            Long through = Objects.requireNonNull(
                    slice.getLastRowNumber(),
                    "report apply slice boundary"
            );
            if (chunkRows > APPLY_CHUNK_ROWS || through <= cursor) {
                return rollbackContract("REPORT_APPLY_SLICE_INVALID");
            }
            final long chunkWarnings;
            try {
                chunkWarnings = factApplicator(safeIntent).applyChunk(
                        safeIntent, stage, cursor, through, chunkRows, now
                );
            } catch (ReportApplyContractException contractFailure) {
                return rollbackContract(contractFailure.getCode());
            }
            long nextApplied = Math.addExact(applied, chunkRows);
            long nextWarnings = Math.addExact(warnings, chunkWarnings);
            if (nextApplied > accepted
                    || stageMapper.advanceApplyCursor(
                    safeIntent,
                    cursor,
                    through,
                    applied,
                    nextApplied,
                    warnings,
                    nextWarnings,
                    now
            ) != 1) {
                throw new IllegalStateException("report apply cursor CAS was rejected");
            }
            return ReportImportResult.inProgress();
        }
        if (applied != accepted) {
            return rollbackContract("REPORT_APPLY_CURSOR_ACCOUNTING_MISMATCH");
        }
        try {
            factApplicator(safeIntent).finalizeContainer(safeIntent, stage, now);
        } catch (ReportApplyContractException contractFailure) {
            return rollbackContract(contractFailure.getCode());
        }
        if (applyMapper.insertMarkerIfLive(safeIntent, now) != 1) {
            throw new IllegalStateException("report marker rejected after set-based fact apply");
        }
        if (stageMapper.markApplied(safeIntent, now) != 1) {
            throw new IllegalStateException("report stage seal CAS was rejected");
        }
        return ReportImportResult.applied();
    }

    private ReportFactChunkApplicator factApplicator(ExportReportIntent intent) {
        ReportFactChunkApplicator matched = null;
        for (ReportFactChunkApplicator candidate : factApplicators) {
            if (candidate.supports(intent.getOperationCode())) {
                if (matched != null) {
                    throw new IllegalStateException("multiple report fact writers claim one operation");
                }
                matched = candidate;
            }
        }
        return Objects.requireNonNull(matched, "report fact writer");
    }

    private boolean exactAccounting(ReportStageState stage) {
        long source = value(stage.getSourceRowCount());
        long accepted = value(stage.getAcceptedRowCount());
        long business = value(stage.getBusinessSkippedRowCount());
        long identity = value(stage.getIdentitySkippedRowCount());
        return source == value(stage.getDeclaredRowCount())
                && source == accepted + business + identity;
    }

    private ReportImportResult rollbackContract(String code) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return ReportImportResult.contractError(code);
    }

    private ReportStageState requireStage(ReportStageState stage) {
        return Objects.requireNonNull(stage, "report stage");
    }

    private void requireStageIntent(ReportStageState stage, ExportReportIntent intent) {
        if (!Objects.equals(stage.getTaskId(), intent.getTaskId())
                || stage.getOperationCode() != intent.getOperationCode()) {
            throw new IllegalStateException("report stage task identity drift");
        }
    }

    private boolean isCurrent(
            ReportApplyTaskRow task,
            ExportReportIntent intent,
            LocalDateTime now
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
                && task.getLeaseUntil().isAfter(now);
    }

    private void requireSameMarker(ReportApplyMarkerRow marker, ExportReportIntent intent) {
        if (!Objects.equals(marker.getTaskId(), intent.getTaskId())
                || marker.getOperationCode() != intent.getOperationCode()
                || !Objects.equals(marker.getScopeKey(), intent.getScopeKey())
                || !Objects.equals(marker.getBusinessWindowKey(), intent.getBusinessWindowKey())
                || marker.getAppliedFenceEpoch() == null
                || marker.getAppliedFenceEpoch() < 1L) {
            throw new IllegalStateException("report fact marker identity drift");
        }
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
