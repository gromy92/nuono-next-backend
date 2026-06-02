package com.nuono.next.sales;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class NoonSalesCsvImportService {

    public static final String SOURCE_SYSTEM = "noon_productviewsandsalesdata";

    private final NoonProductViewsSalesReportParser parser;
    private final SalesFactRepository salesFactRepository;

    public NoonSalesCsvImportService(
            NoonProductViewsSalesReportParser parser,
            SalesFactRepository salesFactRepository
    ) {
        this.parser = parser;
        this.salesFactRepository = salesFactRepository;
    }

    public NoonSalesCsvImportResult importCsv(NoonSalesCsvImportCommand command) {
        NoonProductViewsSalesReportParseResult parseResult;
        try {
            parseResult = parser.parseWithQuality(command.getCsv(), command.getSourceFilename());
        } catch (IllegalArgumentException exception) {
            return saveFailedBatch(command, exception.getMessage());
        }
        List<SalesImportExceptionRecord> parseExceptions = new ArrayList<>(parseResult.getExceptions());
        List<NoonProductViewsSalesReportRow> rows = filterMappedRows(command, parseResult.getRows(), parseExceptions);
        LocalDate reportDateFrom = minDate(rows);
        LocalDate reportDateTo = maxDate(rows);
        int totalRows = parseResult.getTotalRows();
        int successRows = rows.size();
        int failureRows = parseExceptions.size();
        String status = resolveStatus(totalRows, successRows, failureRows);
        SalesImportBatch batch = new SalesImportBatch(
                SOURCE_SYSTEM,
                command.getSourceFilename(),
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
                failureRows == 0 ? null : failureRows + " row(s) excluded from sales facts"
        );
        long sourceBatchId = salesFactRepository.saveBatch(batch);
        List<SalesImportExceptionRecord> exceptions = parseExceptions.stream()
                .map(exception -> exception.withBatchContext(sourceBatchId, command))
                .collect(Collectors.toList());
        if (!exceptions.isEmpty()) {
            salesFactRepository.saveExceptions(sourceBatchId, exceptions);
        }
        rows.stream()
                .map(row -> toFact(command, sourceBatchId, row))
                .forEach(salesFactRepository::upsert);
        if ("empty".equals(status)) {
            salesFactRepository.markSiteOffersNotListedForEmptyReport(
                    command.getOwnerUserId(),
                    command.getStoreCode(),
                    command.getSiteCode(),
                    command.getOwnerUserId()
            );
        }

        return new NoonSalesCsvImportResult(
                SOURCE_SYSTEM,
                sourceBatchId,
                command.getSourceFilename(),
                totalRows,
                successRows,
                failureRows,
                reportDateFrom,
                reportDateTo,
                status,
                failureRows == 0 ? null : failureRows + " row(s) excluded from sales facts",
                exceptions
        );
    }

    private NoonSalesCsvImportResult saveFailedBatch(NoonSalesCsvImportCommand command, String failureSummary) {
        int totalRows = countDataRows(command.getCsv());
        SalesImportBatch batch = new SalesImportBatch(
                SOURCE_SYSTEM,
                command.getSourceFilename(),
                command.getOwnerUserId(),
                command.getLogicalStoreId(),
                command.getStoreCode(),
                command.getSiteCode(),
                null,
                null,
                totalRows,
                0,
                totalRows,
                "failed",
                failureSummary
        );
        long sourceBatchId = salesFactRepository.saveBatch(batch);
        return new NoonSalesCsvImportResult(
                SOURCE_SYSTEM,
                sourceBatchId,
                command.getSourceFilename(),
                totalRows,
                0,
                totalRows,
                null,
                null,
                "failed",
                failureSummary
        );
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

    private List<NoonProductViewsSalesReportRow> filterMappedRows(
            NoonSalesCsvImportCommand command,
            List<NoonProductViewsSalesReportRow> rows,
            List<SalesImportExceptionRecord> exceptions
    ) {
        List<NoonProductViewsSalesReportRow> accepted = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            NoonProductViewsSalesReportRow row = rows.get(i);
            String countryCode = row.getCountryCode();
            if (countryCode != null && !countryCode.isBlank()
                    && command.getSiteCode() != null
                    && !countryCode.equalsIgnoreCase(command.getSiteCode())) {
                exceptions.add(new SalesImportExceptionRecord(
                        null,
                        null,
                        command.getSourceFilename(),
                        null,
                        null,
                        null,
                        i + 2,
                        "mapping_failed",
                        "Country_Code",
                        countryCode,
                        row.getVisitDate() + "," + row.getPartnerSku() + "," + row.getSku(),
                        "Country_Code " + countryCode + " does not match import site " + command.getSiteCode(),
                        "确认该行属于本次导入站点后再重导，或选择正确站点导入。"
                ));
                continue;
            }
            accepted.add(row);
        }
        return accepted;
    }

    private DailySalesFact toFact(NoonSalesCsvImportCommand command, long sourceBatchId, NoonProductViewsSalesReportRow row) {
        return new DailySalesFact(
                SOURCE_SYSTEM,
                sourceBatchId,
                command.getOwnerUserId(),
                command.getLogicalStoreId(),
                command.getStoreCode(),
                command.getSiteCode(),
                row.getVisitDate(),
                row.getPartnerSku(),
                row.getSku(),
                row.getSkuConfig(),
                row.getCountryCode(),
                row.getCurrencyCode(),
                row.getProductTitle(),
                row.getYourVisitors(),
                row.getTotalVisitors(),
                row.getGrossUnits(),
                row.getShippedUnits(),
                row.getCancelledUnits(),
                row.getNetUnits(),
                row.getRevenueShipped(),
                row.getBuyBoxVisitorPercentage(),
                row.getConversionVisitorsPercentage(),
                row.getAspShippedPercentage()
        );
    }

    private LocalDate minDate(List<NoonProductViewsSalesReportRow> rows) {
        return rows.stream()
                .map(NoonProductViewsSalesReportRow::getVisitDate)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private LocalDate maxDate(List<NoonProductViewsSalesReportRow> rows) {
        return rows.stream()
                .map(NoonProductViewsSalesReportRow::getVisitDate)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private int countDataRows(String csv) {
        if (csv == null || csv.isBlank()) {
            return 0;
        }
        String[] lines = csv.split("\\R");
        int count = 0;
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                count++;
            }
        }
        return count;
    }
}
