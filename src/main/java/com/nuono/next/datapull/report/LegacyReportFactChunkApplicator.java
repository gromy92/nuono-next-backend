package com.nuono.next.datapull.report;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.LegacyReportFactBulkMapper;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;

/** DP-01/02/03 bounded set-based Fact Writer implementation. */
final class LegacyReportFactChunkApplicator implements ReportFactChunkApplicator {
    private static final EnumSet<OperationCode> OPERATIONS = EnumSet.of(
            OperationCode.DP01, OperationCode.DP02, OperationCode.DP03
    );

    private final LegacyReportFactBulkMapper mapper;

    LegacyReportFactChunkApplicator(LegacyReportFactBulkMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public boolean supports(OperationCode operationCode) {
        return OPERATIONS.contains(operationCode);
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
        ReportFactIdBlock block;
        long proof;
        switch (intent.getOperationCode()) {
            case DP01:
                block = new ReportFactIdBlock("daily_sales_fact", 100000L, rowCount);
                mapper.reserveSalesFactIds(block);
                mapper.applySalesFacts(intent.getTaskId(), afterRowNumber, throughRowNumber,
                        block.firstId(), nowUtc);
                proof = mapper.countAppliedSalesFacts(
                        intent.getTaskId(), afterRowNumber, throughRowNumber
                );
                break;
            case DP02:
                block = new ReportFactIdBlock("order_line_fact", 200000L, rowCount);
                mapper.reserveOrderFactIds(block);
                mapper.applyOrderFacts(intent.getTaskId(), afterRowNumber, throughRowNumber,
                        block.firstId(), nowUtc);
                proof = mapper.countAppliedOrderFacts(
                        intent.getTaskId(), afterRowNumber, throughRowNumber
                );
                break;
            case DP03:
                block = new ReportFactIdBlock("finance_transaction_fact", 300000L, rowCount);
                mapper.reserveFinanceFactIds(block);
                mapper.applyFinanceFacts(intent.getTaskId(), afterRowNumber, throughRowNumber,
                        block.firstId(), nowUtc);
                proof = mapper.countAppliedFinanceFacts(
                        intent.getTaskId(), afterRowNumber, throughRowNumber
                );
                break;
            default:
                throw new IllegalStateException("legacy report writer operation mismatch");
        }
        if (proof != rowCount) {
            throw new ReportApplyContractException("REPORT_SET_BASED_APPLY_MISMATCH");
        }
        return 0L;
    }
}
