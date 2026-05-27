package com.nuono.next.sales;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LegacySalesBackfillService {

    public static final String SOURCE_SYSTEM = "legacy_product_sales_data";

    private final LegacySalesBackfillRowProvider rowProvider;
    private final SalesFactRepository salesFactRepository;

    public LegacySalesBackfillService(
            LegacySalesBackfillRowProvider rowProvider,
            SalesFactRepository salesFactRepository
    ) {
        this.rowProvider = rowProvider;
        this.salesFactRepository = salesFactRepository;
    }

    public LegacySalesBackfillResult backfill(LegacySalesBackfillCommand command) {
        List<LegacySalesBackfillRow> sourceRows = rowProvider.fetch(command);
        List<SalesImportExceptionRecord> exceptions = new ArrayList<>();
        List<LegacySalesBackfillRow> rows = filterMappedRows(command, sourceRows, exceptions);
        LocalDate reportDateFrom = minDate(rows);
        LocalDate reportDateTo = maxDate(rows);
        int totalRows = sourceRows.size();
        int successRows = rows.size();
        int failureRows = exceptions.size();
        String status = resolveStatus(totalRows, successRows, failureRows);
        String failureSummary = failureRows == 0 ? null : failureRows + " row(s) excluded from sales facts";
        SalesImportBatch batch = new SalesImportBatch(
                SOURCE_SYSTEM,
                sourceFilename(command),
                command.getOwnerUserId(),
                command.getLogicalStoreId(),
                command.getStoreCode(),
                command.getSiteCode(),
                reportDateFrom,
                reportDateTo,
                totalRows,
                successRows,
                failureRows,
                status,
                failureSummary
        );
        long sourceBatchId = salesFactRepository.saveBatch(batch);
        List<SalesImportExceptionRecord> batchExceptions = withBatchContext(sourceBatchId, command, exceptions);
        if (!batchExceptions.isEmpty()) {
            salesFactRepository.saveExceptions(sourceBatchId, batchExceptions);
        }
        rows.stream()
                .map(row -> toFact(command, sourceBatchId, row))
                .forEach(salesFactRepository::upsert);
        return new LegacySalesBackfillResult(
                SOURCE_SYSTEM,
                sourceBatchId,
                totalRows,
                successRows,
                failureRows,
                reportDateFrom,
                reportDateTo,
                status,
                failureSummary,
                batchExceptions
        );
    }

    private List<LegacySalesBackfillRow> filterMappedRows(
            LegacySalesBackfillCommand command,
            List<LegacySalesBackfillRow> sourceRows,
            List<SalesImportExceptionRecord> exceptions
    ) {
        List<LegacySalesBackfillRow> accepted = new ArrayList<>();
        for (int i = 0; i < sourceRows.size(); i++) {
            LegacySalesBackfillRow row = sourceRows.get(i);
            SalesImportExceptionRecord exception = mappingException(command, row, i + 2);
            if (exception != null) {
                exceptions.add(exception);
                continue;
            }
            accepted.add(row);
        }
        return accepted;
    }

    private SalesImportExceptionRecord mappingException(
            LegacySalesBackfillCommand command,
            LegacySalesBackfillRow row,
            int rowNumber
    ) {
        if (!same(command.getLegacyOwnerUserId(), row.getLegacyOwnerUserId())) {
            return exception(rowNumber, "legacyOwnerUserId", String.valueOf(row.getLegacyOwnerUserId()), row, "旧系统 owner 与本次回填范围不一致。");
        }
        if (!same(command.getLegacyStoreCode(), row.getLegacyStoreCode())) {
            return exception(rowNumber, "legacyStoreCode", row.getLegacyStoreCode(), row, "旧系统店铺与本次回填范围不一致。");
        }
        if (!same(command.getSiteCode(), row.getSiteCode())) {
            return exception(rowNumber, "siteCode", row.getSiteCode(), row, "旧系统站点与本次回填范围不一致。");
        }
        if (row.getFactDate() == null) {
            return exception(rowNumber, "factDate", null, row, "旧系统行缺少销量日期。");
        }
        if (command.getDateFrom() != null && row.getFactDate().isBefore(command.getDateFrom())) {
            return exception(rowNumber, "factDate", row.getFactDate().toString(), row, "旧系统销量日期早于本次回填范围。");
        }
        if (command.getDateTo() != null && row.getFactDate().isAfter(command.getDateTo())) {
            return exception(rowNumber, "factDate", row.getFactDate().toString(), row, "旧系统销量日期晚于本次回填范围。");
        }
        if (isBlank(row.getPartnerSku())) {
            return exception(rowNumber, "partnerSku", row.getPartnerSku(), row, "旧系统行缺少 Partner SKU。");
        }
        if (isBlank(row.getSku())) {
            return exception(rowNumber, "sku", row.getSku(), row, "旧系统行缺少 SKU。");
        }
        return null;
    }

    private SalesImportExceptionRecord exception(
            int rowNumber,
            String fieldName,
            String sourceValue,
            LegacySalesBackfillRow row,
            String message
    ) {
        return new SalesImportExceptionRecord(
                null,
                null,
                sourceFilename(row),
                null,
                null,
                null,
                rowNumber,
                "mapping_failed",
                fieldName,
                sourceValue,
                sourceFilename(row),
                message,
                "确认旧系统行可唯一映射到新系统 owner/store/site/Partner SKU 后再重跑回填。"
        );
    }

    private List<SalesImportExceptionRecord> withBatchContext(
            long sourceBatchId,
            LegacySalesBackfillCommand command,
            List<SalesImportExceptionRecord> exceptions
    ) {
        List<SalesImportExceptionRecord> records = new ArrayList<>();
        for (SalesImportExceptionRecord exception : exceptions) {
            records.add(new SalesImportExceptionRecord(
                    exception.getId(),
                    sourceBatchId,
                    exception.getSourceFilename(),
                    command.getOwnerUserId(),
                    command.getStoreCode(),
                    command.getSiteCode(),
                    exception.getRowNumber(),
                    exception.getExceptionType(),
                    exception.getFieldName(),
                    exception.getSourceValue(),
                    exception.getSourceContext(),
                    exception.getMessage(),
                    exception.getResolutionHint()
            ));
        }
        return records;
    }

    private DailySalesFact toFact(LegacySalesBackfillCommand command, long sourceBatchId, LegacySalesBackfillRow row) {
        return new DailySalesFact(
                SOURCE_SYSTEM,
                sourceBatchId,
                command.getOwnerUserId(),
                command.getLogicalStoreId(),
                command.getStoreCode(),
                command.getSiteCode(),
                row.getFactDate(),
                row.getPartnerSku(),
                row.getSku(),
                row.getSkuConfig(),
                row.getSiteCode(),
                row.getCurrencyCode(),
                row.getProductTitle(),
                null,
                null,
                row.getGrossUnits(),
                row.getShippedUnits(),
                row.getCancelledUnits(),
                netUnits(row),
                row.getRevenueShipped(),
                null,
                null,
                null
        );
    }

    private int netUnits(LegacySalesBackfillRow row) {
        int gross = valueOrZero(row.getGrossUnits());
        int shipped = valueOrZero(row.getShippedUnits());
        int cancelled = valueOrZero(row.getCancelledUnits());
        return Math.max(Math.max(gross - cancelled, shipped), 0);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String resolveStatus(int totalRows, int successRows, int failureRows) {
        if (totalRows == 0) {
            return "empty";
        }
        if (failureRows == 0) {
            return "imported";
        }
        if (successRows == 0) {
            return "failed";
        }
        return "imported_with_exceptions";
    }

    private boolean same(Long expected, Long actual) {
        if (expected == null) {
            return actual == null;
        }
        return expected.equals(actual);
    }

    private boolean same(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return actual == null || actual.isBlank();
        }
        return actual != null && expected.equalsIgnoreCase(actual);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private LocalDate minDate(List<LegacySalesBackfillRow> rows) {
        return rows.stream()
                .map(LegacySalesBackfillRow::getFactDate)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private LocalDate maxDate(List<LegacySalesBackfillRow> rows) {
        return rows.stream()
                .map(LegacySalesBackfillRow::getFactDate)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private String sourceFilename(LegacySalesBackfillCommand command) {
        return "legacy_owner=" + command.getLegacyOwnerUserId()
                + ";legacy_store=" + command.getLegacyStoreCode();
    }

    private String sourceFilename(LegacySalesBackfillRow row) {
        if (!isBlank(row.getSourceRowId())) {
            return row.getSourceRowId();
        }
        return "legacy_owner=" + row.getLegacyOwnerUserId()
                + ";legacy_store=" + row.getLegacyStoreCode();
    }
}
