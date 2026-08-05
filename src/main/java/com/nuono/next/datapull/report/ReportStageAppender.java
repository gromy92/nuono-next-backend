package com.nuono.next.datapull.report;

import com.nuono.next.infrastructure.mapper.ReportFactApplyMapper;
import com.nuono.next.infrastructure.mapper.ReportStageMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;

/** Bounded validation/staging implementation; caller supplies the 10-second transaction. */
final class ReportStageAppender {
    private static final int MAX_STAGE_ROWS = 200;
    private final ReportStageMapper stageMapper;
    private final ReportFactApplyMapper applyMapper;

    ReportStageAppender(ReportStageMapper stageMapper, ReportFactApplyMapper applyMapper) {
        this.stageMapper = Objects.requireNonNull(stageMapper, "stageMapper");
        this.applyMapper = Objects.requireNonNull(applyMapper, "applyMapper");
    }

    ReportImportResult append(
            ExportReportIntent intent,
            ReportStageChunk chunk,
            LocalDateTime nowUtc
    ) {
        ExportReportIntent safeIntent = Objects.requireNonNull(intent, "intent");
        ReportStageChunk safeChunk = Objects.requireNonNull(chunk, "chunk");
        requireBoundedContiguousRows(safeChunk);
        ReportApplyTaskRow task = applyMapper.selectTaskForUpdate(safeIntent.getTaskId());
        if (!isCurrent(task, safeIntent, nowUtc)) {
            return ReportImportResult.staleFence();
        }
        ReportApplyMarkerRow marker = applyMapper.selectMarker(safeIntent.getTaskId());
        if (marker != null) {
            requireSameMarker(marker, safeIntent);
            return ReportImportResult.applied();
        }
        ReportStageState state = stageMapper.selectStageForUpdate(safeIntent.getTaskId());
        if (state == null) {
            stageMapper.insertStageIfAbsent(
                    safeIntent, safeChunk.getArtifactKey(), safeChunk.getArtifactSha256(),
                    safeChunk.getHeaderJson(), safeChunk.getExpectedByteOffset(),
                    safeChunk.getDeclaredRowCount(), nowUtc
            );
            state = Objects.requireNonNull(
                    stageMapper.selectStageForUpdate(safeIntent.getTaskId()),
                    "report stage"
            );
        }
        requireSameStage(state, safeIntent, safeChunk);
        if (!safeChunk.getRows().isEmpty()
                && safeChunk.getRows().get(0).getRowNumber()
                != Math.addExact(value(state.getSourceRowCount()), 1L)) {
            throw new IllegalArgumentException("report stage row sequence does not resume");
        }
        ReportImportResult terminal = existingStageResult(state);
        if (terminal != null) {
            return terminal;
        }
        if (!Objects.equals(state.getNextByteOffset(), safeChunk.getExpectedByteOffset())) {
            throw new IllegalStateException("report stage byte cursor drift");
        }
        if (hasContainerError(safeChunk.getRows())) {
            poisonExactly(safeIntent, "REPORT_ROW_OUTSIDE_CONTAINER", nowUtc);
            return ReportImportResult.contractError("REPORT_ROW_OUTSIDE_CONTAINER");
        }
        List<ReportStageRowRecord> records = resolveIdentityConflicts(
                safeIntent.getTaskId(), safeChunk.getRows()
        );
        insertRowsExactly(safeIntent.getTaskId(), records, nowUtc);
        long[] decisions = nextDecisionCounts(state, records);
        long sourceRows = Math.addExact(value(state.getSourceRowCount()), records.size());
        if (sourceRows > safeChunk.getDeclaredRowCount()
                || (safeChunk.isEndOfFile() && sourceRows != safeChunk.getDeclaredRowCount())) {
            poisonExactly(safeIntent, "REPORT_PROVIDER_ROW_COUNT_CONFLICT", nowUtc);
            return ReportImportResult.contractError("REPORT_PROVIDER_ROW_COUNT_CONFLICT");
        }
        String nextState = safeChunk.isEndOfFile()
                ? (sourceRows == 0L ? "EMPTY_UNPROVEN" : "SEALED")
                : "VALIDATING";
        if (stageMapper.advanceStage(
                safeIntent, safeChunk.getArtifactKey(), safeChunk.getArtifactSha256(),
                safeChunk.getExpectedByteOffset(), safeChunk.getNextByteOffset(), sourceRows,
                decisions[0], decisions[1], decisions[2], nextState, nowUtc
        ) != 1) {
            throw new IllegalStateException("report stage progress CAS was rejected");
        }
        return "EMPTY_UNPROVEN".equals(nextState)
                ? ReportImportResult.awaitingAuthoritativeEmptyProof()
                : ReportImportResult.inProgress();
    }

    private ReportImportResult existingStageResult(ReportStageState state) {
        if ("APPLIED".equals(state.getState())) {
            throw new IllegalStateException("report stage is applied without its marker");
        }
        if ("POISONED".equals(state.getState())) {
            return ReportImportResult.contractError(state.getPoisonCode());
        }
        if ("EMPTY_UNPROVEN".equals(state.getState())) {
            return ReportImportResult.awaitingAuthoritativeEmptyProof();
        }
        if ("SEALED".equals(state.getState())) {
            return ReportImportResult.inProgress();
        }
        if (!"VALIDATING".equals(state.getState())) {
            throw new IllegalStateException("report stage state is unsupported");
        }
        return null;
    }

    private boolean hasContainerError(List<ReportPlannedRow> rows) {
        for (ReportPlannedRow row : rows) {
            if (row.getDecision() == ReportPlannedRow.Decision.CONTAINER_CONTRACT_ERROR) {
                return true;
            }
        }
        return false;
    }

    private void requireBoundedContiguousRows(ReportStageChunk chunk) {
        List<ReportPlannedRow> rows = chunk.getRows();
        if (rows.size() > MAX_STAGE_ROWS) {
            throw new IllegalArgumentException("report stage row bound exceeded");
        }
        for (int index = 0; index < rows.size(); index++) {
            long expected = Math.addExact(rows.get(0).getRowNumber(), index);
            if (rows.get(index).getRowNumber() != expected) {
                throw new IllegalArgumentException("report stage row sequence is not contiguous");
            }
        }
    }

    private long[] nextDecisionCounts(
            ReportStageState state,
            List<ReportStageRowRecord> records
    ) {
        long[] result = {
                value(state.getAcceptedRowCount()),
                value(state.getBusinessSkippedRowCount()),
                value(state.getIdentitySkippedRowCount())
        };
        for (ReportStageRowRecord record : records) {
            if ("ACCEPTED".equals(record.getDecision())) {
                result[0]++;
            } else if ("BUSINESS_SKIP".equals(record.getDecision())) {
                result[1]++;
            } else if ("LATER_IDENTITY_CONFLICT".equals(record.getDecision())) {
                result[2]++;
            }
        }
        return result;
    }

    private List<ReportStageRowRecord> resolveIdentityConflicts(
            long taskId,
            List<ReportPlannedRow> planned
    ) {
        List<ReportStageRowRecord> records = new ArrayList<>(planned.size());
        LinkedHashSet<String> requested = new LinkedHashSet<>();
        for (ReportPlannedRow row : planned) {
            ReportStageRowRecord record = ReportStageRowRecord.from(taskId, row);
            records.add(record);
            if (record.getAcceptedIdentitySha256() != null) {
                requested.add(record.getAcceptedIdentitySha256());
            }
        }
        Set<String> seen = new HashSet<>();
        if (!requested.isEmpty()) {
            seen.addAll(stageMapper.selectExistingAcceptedIdentities(
                    taskId, List.copyOf(requested)
            ));
        }
        for (ReportStageRowRecord record : records) {
            String identity = record.getAcceptedIdentitySha256();
            if (identity != null && !seen.add(identity)) {
                record.markLaterIdentityConflict();
            }
        }
        return records;
    }

    private void insertRowsExactly(
            long taskId,
            List<ReportStageRowRecord> records,
            LocalDateTime nowUtc
    ) {
        if (records.isEmpty()) {
            return;
        }
        try {
            requireExactInsert(stageMapper.insertStageRows(records, nowUtc), records.size());
        } catch (DuplicateKeyException concurrentIdentityConflict) {
            if (!reclassifyDatabaseIdentityConflicts(taskId, records)) {
                throw concurrentIdentityConflict;
            }
            try {
                requireExactInsert(stageMapper.insertStageRows(records, nowUtc), records.size());
            } catch (DuplicateKeyException nonIdentityConflict) {
                throw new IllegalStateException(
                        "report stage row identity retry was rejected",
                        nonIdentityConflict
                );
            }
        }
    }

    private boolean reclassifyDatabaseIdentityConflicts(
            long taskId,
            List<ReportStageRowRecord> records
    ) {
        LinkedHashSet<String> requested = new LinkedHashSet<>();
        for (ReportStageRowRecord record : records) {
            if (record.getAcceptedIdentitySha256() != null) {
                requested.add(record.getAcceptedIdentitySha256());
            }
        }
        if (requested.isEmpty()) {
            return false;
        }
        Set<String> existing = new HashSet<>(stageMapper.selectExistingAcceptedIdentities(
                taskId,
                List.copyOf(requested)
        ));
        boolean reclassified = false;
        for (ReportStageRowRecord record : records) {
            if (existing.contains(record.getAcceptedIdentitySha256())) {
                record.markLaterIdentityConflict();
                reclassified = true;
            }
        }
        return reclassified;
    }

    private void requireExactInsert(int inserted, int expected) {
        if (inserted != expected) {
            throw new IllegalStateException("report stage row insert count mismatch");
        }
    }

    private void poisonExactly(
            ExportReportIntent intent,
            String poisonCode,
            LocalDateTime nowUtc
    ) {
        if (stageMapper.poisonStage(intent, poisonCode, nowUtc) != 1) {
            throw new IllegalStateException("report stage poison CAS was rejected");
        }
    }

    private void requireSameStage(
            ReportStageState stage,
            ExportReportIntent intent,
            ReportStageChunk chunk
    ) {
        if (!Objects.equals(stage.getTaskId(), intent.getTaskId())
                || stage.getOperationCode() != intent.getOperationCode()
                || !Objects.equals(stage.getArtifactKey(), chunk.getArtifactKey())
                || !Objects.equals(stage.getArtifactSha256(), chunk.getArtifactSha256())
                || !Objects.equals(stage.getHeaderJson(), chunk.getHeaderJson())
                || value(stage.getDeclaredRowCount()) != chunk.getDeclaredRowCount()) {
            throw new IllegalStateException("report stage artifact binding drift");
        }
    }

    private boolean isCurrent(
            ReportApplyTaskRow task,
            ExportReportIntent intent,
            LocalDateTime nowUtc
    ) {
        return task != null && Objects.equals(task.getTaskId(), intent.getTaskId())
                && task.getOperationCode() == intent.getOperationCode()
                && Objects.equals(task.getScopeKey(), intent.getScopeKey())
                && Objects.equals(task.getBusinessWindowKey(), intent.getBusinessWindowKey())
                && Objects.equals(task.getFenceEpoch(), intent.getFenceEpoch())
                && "RUNNING".equals(task.getState())
                && Objects.equals(task.getLeaseOwner(), intent.getLeaseOwner())
                && task.getLeaseUntil() != null && task.getLeaseUntil().isAfter(nowUtc);
    }

    private void requireSameMarker(ReportApplyMarkerRow marker, ExportReportIntent intent) {
        if (!Objects.equals(marker.getTaskId(), intent.getTaskId())
                || marker.getOperationCode() != intent.getOperationCode()
                || !Objects.equals(marker.getScopeKey(), intent.getScopeKey())
                || !Objects.equals(marker.getBusinessWindowKey(), intent.getBusinessWindowKey())
                || marker.getAppliedFenceEpoch() == null || marker.getAppliedFenceEpoch() < 1L) {
            throw new IllegalStateException("report fact marker identity drift");
        }
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }
}
