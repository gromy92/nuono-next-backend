package com.nuono.next.salesforecast;

public class SalesForecastExportView {

    private final String filename;
    private final String contentType;
    private final String content;

    public SalesForecastExportView(String filename, String contentType, String content) {
        this.filename = filename;
        this.contentType = contentType;
        this.content = content;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public String getContent() {
        return content;
    }
}
