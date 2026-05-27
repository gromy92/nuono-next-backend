package com.nuono.next.salesforecast;

public class SalesForecastEmptyState {

    private final String code;
    private final String title;
    private final String description;

    public SalesForecastEmptyState(String code, String title, String description) {
        this.code = code;
        this.title = title;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
