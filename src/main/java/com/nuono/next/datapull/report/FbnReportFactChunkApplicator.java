package com.nuono.next.datapull.report;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.FbnReportBulkMapper;
import java.time.LocalDateTime;
import java.util.Objects;

/** DP-07-B multi-table writer; children stay hidden until its final import header is inserted. */
final class FbnReportFactChunkApplicator implements ReportFactChunkApplicator {
    private final FbnReportBulkMapper mapper;

    FbnReportFactChunkApplicator(FbnReportBulkMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public boolean supports(OperationCode operationCode) {
        return operationCode == OperationCode.DP07B;
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
        long importId = containerId(intent, stage, nowUtc);
        ReportFactIdBlock rowIds = new ReportFactIdBlock(
                "official_warehouse_report_row", 624000L, rowCount
        );
        ReportFactIdBlock receiptIds = new ReportFactIdBlock(
                "official_warehouse_inbound_receipt_line", 625000L, rowCount
        );
        mapper.reserveIds(rowIds);
        mapper.reserveIds(receiptIds);
        int reportRows = mapper.insertReportRows(
                intent.getTaskId(), afterRowNumber, throughRowNumber,
                importId, rowIds.firstId(), nowUtc
        );
        int receiptRows = mapper.insertReceiptLines(
                intent.getTaskId(), afterRowNumber, throughRowNumber,
                importId, rowIds.firstId(), receiptIds.firstId(), nowUtc
        );
        if (reportRows != rowCount || receiptRows != rowCount) {
            throw new ReportApplyContractException("DP07B_SET_BASED_INSERT_MISMATCH");
        }
        FbnReportChunkProof proof = Objects.requireNonNull(
                mapper.selectChunkProof(importId, afterRowNumber, throughRowNumber),
                "DP07B target proof"
        );
        if (value(proof.getReportRows()) != rowCount
                || value(proof.getReceiptRows()) != rowCount
                || value(proof.getWarningRows()) > rowCount) {
            throw new ReportApplyContractException("DP07B_SET_BASED_APPLY_MISMATCH");
        }
        return value(proof.getWarningRows());
    }

    @Override
    public void finalizeContainer(
            ExportReportIntent intent,
            ReportStageState stage,
            LocalDateTime nowUtc
    ) {
        if (value(stage.getAcceptedRowCount()) == 0L) {
            return;
        }
        Long importId = Objects.requireNonNull(
                stage.getFactContainerId(),
                "DP07B fact container ID"
        );
        int deactivated = mapper.deactivatePreviousImportHeaders(
                intent.getTaskId(),
                importId,
                nowUtc
        );
        if (deactivated < 0) {
            throw new ReportApplyContractException("DP07B_HEADER_DEACTIVATION_INVALID");
        }
        if (mapper.insertImportHeader(
                intent.getTaskId(), importId, nowUtc
        ) != 1 || mapper.countActiveImportHeader(importId) != 1L) {
            throw new ReportApplyContractException("DP07B_IMPORT_HEADER_MISMATCH");
        }
    }

    private long containerId(
            ExportReportIntent intent,
            ReportStageState stage,
            LocalDateTime nowUtc
    ) {
        if (stage.getFactContainerId() != null) {
            return stage.getFactContainerId();
        }
        ReportFactIdBlock importId = new ReportFactIdBlock(
                "official_warehouse_report_import", 623000L, 1L
        );
        mapper.reserveIds(importId);
        long firstId = importId.firstId();
        if (mapper.initializeContainerId(intent, firstId, nowUtc) != 1) {
            throw new IllegalStateException("DP07B fact container CAS was rejected");
        }
        stage.setFactContainerId(firstId);
        return firstId;
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }
}
