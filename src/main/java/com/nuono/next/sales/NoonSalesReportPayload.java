package com.nuono.next.sales;

public class NoonSalesReportPayload {

    private final String sourceFilename;
    private final String csv;

    public NoonSalesReportPayload(String sourceFilename, String csv) {
        this.sourceFilename = sourceFilename;
        this.csv = csv;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public String getCsv() {
        return csv;
    }
}
