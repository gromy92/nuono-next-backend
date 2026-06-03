package com.nuono.next.productanalysis;

public class ProductLifecycleAnalysisSummaryView {

    private final String storeCode;
    private final String siteCode;
    private final int totalProductCount;
    private final int readyProductCount;
    private final int missingParameterProductCount;
    private final int expectedLifecycleChangeProductCount;
    private final int forecastWindowDays;

    public ProductLifecycleAnalysisSummaryView(
            String storeCode,
            String siteCode,
            int totalProductCount,
            int readyProductCount,
            int missingParameterProductCount
    ) {
        this(storeCode, siteCode, totalProductCount, readyProductCount, missingParameterProductCount, 0, 90);
    }

    public ProductLifecycleAnalysisSummaryView(
            String storeCode,
            String siteCode,
            int totalProductCount,
            int readyProductCount,
            int missingParameterProductCount,
            int expectedLifecycleChangeProductCount,
            int forecastWindowDays
    ) {
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.totalProductCount = totalProductCount;
        this.readyProductCount = readyProductCount;
        this.missingParameterProductCount = missingParameterProductCount;
        this.expectedLifecycleChangeProductCount = expectedLifecycleChangeProductCount;
        this.forecastWindowDays = forecastWindowDays;
    }

    public static ProductLifecycleAnalysisSummaryView empty(String storeCode, String siteCode) {
        return new ProductLifecycleAnalysisSummaryView(storeCode, siteCode, 0, 0, 0);
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public int getTotalProductCount() {
        return totalProductCount;
    }

    public int getReadyProductCount() {
        return readyProductCount;
    }

    public int getMissingParameterProductCount() {
        return missingParameterProductCount;
    }

    public int getExpectedLifecycleChangeProductCount() {
        return expectedLifecycleChangeProductCount;
    }

    public int getForecastWindowDays() {
        return forecastWindowDays;
    }
}
