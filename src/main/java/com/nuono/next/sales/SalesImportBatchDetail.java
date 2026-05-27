package com.nuono.next.sales;

import java.util.List;

public class SalesImportBatchDetail {

    private final SalesImportBatchRecord batch;
    private final List<SalesImportExceptionRecord> exceptions;

    public SalesImportBatchDetail(SalesImportBatchRecord batch, List<SalesImportExceptionRecord> exceptions) {
        this.batch = batch;
        this.exceptions = exceptions == null ? List.of() : List.copyOf(exceptions);
    }

    public SalesImportBatchRecord getBatch() {
        return batch;
    }

    public List<SalesImportExceptionRecord> getExceptions() {
        return exceptions;
    }
}
