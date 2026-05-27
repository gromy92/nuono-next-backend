package com.nuono.next.sales;

import java.util.List;

public class NoonProductViewsSalesReportParseResult {

    private final List<NoonProductViewsSalesReportRow> rows;
    private final List<SalesImportExceptionRecord> exceptions;
    private final int totalRows;

    public NoonProductViewsSalesReportParseResult(
            List<NoonProductViewsSalesReportRow> rows,
            List<SalesImportExceptionRecord> exceptions,
            int totalRows
    ) {
        this.rows = rows == null ? List.of() : List.copyOf(rows);
        this.exceptions = exceptions == null ? List.of() : List.copyOf(exceptions);
        this.totalRows = totalRows;
    }

    public List<NoonProductViewsSalesReportRow> getRows() {
        return rows;
    }

    public List<SalesImportExceptionRecord> getExceptions() {
        return exceptions;
    }

    public int getTotalRows() {
        return totalRows;
    }
}
