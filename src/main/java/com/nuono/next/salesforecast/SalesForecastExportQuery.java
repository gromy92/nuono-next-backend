package com.nuono.next.salesforecast;

public class SalesForecastExportQuery {

    private final int forecastWindow;
    private final String searchKeyword;
    private final String riskFilter;
    private final String confidenceFilter;

    public SalesForecastExportQuery(
            int forecastWindow,
            String searchKeyword,
            String riskFilter,
            String confidenceFilter
    ) {
        this.forecastWindow = forecastWindow;
        this.searchKeyword = searchKeyword;
        this.riskFilter = riskFilter;
        this.confidenceFilter = confidenceFilter;
    }

    public int getForecastWindow() {
        return forecastWindow;
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    public String getRiskFilter() {
        return riskFilter;
    }

    public String getConfidenceFilter() {
        return confidenceFilter;
    }
}
