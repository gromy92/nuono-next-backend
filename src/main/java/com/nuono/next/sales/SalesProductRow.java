package com.nuono.next.sales;

import com.nuono.next.product.ProductImageUrlSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class SalesProductRow {

    private final String partnerSku;
    private final String sku;
    private final String productTitle;
    private final LocalDate latestFactDate;
    private final List<String> sourceSystems;
    private final String brand;
    private final String productFulltype;
    private final String imageUrl;
    private final Integer currentStock;
    private final Integer fbnStock;
    private final Integer supermallStock;
    private final Integer fbpStock;
    private final BigDecimal stockCoverDays;
    private final boolean dimensionMatched;
    private final String dimensionSource;
    private final List<String> dimensionQualityCodes;
    private final List<String> dataQualityCodes;
    private final SalesAnalyticsSummary summary;
    private final SalesAnalyticsSummary latestSummary;

    public SalesProductRow(
            String partnerSku,
            String sku,
            String productTitle,
            LocalDate latestFactDate,
            List<String> sourceSystems,
            SalesAnalyticsSummary summary
    ) {
        this(
                partnerSku,
                sku,
                productTitle,
                latestFactDate,
                sourceSystems,
                summary,
                null,
                null,
                false,
                null,
                List.of("product_dimension_missing"),
                List.of("sales_fact_ready", "product_dimension_missing"),
                summary
        );
    }

    public SalesProductRow(
            String partnerSku,
            String sku,
            String productTitle,
            LocalDate latestFactDate,
            List<String> sourceSystems,
            SalesAnalyticsSummary summary,
            String brand,
            String productFulltype,
            boolean dimensionMatched,
            String dimensionSource,
            List<String> dimensionQualityCodes,
            List<String> dataQualityCodes
    ) {
        this(
                partnerSku,
                sku,
                productTitle,
                latestFactDate,
                sourceSystems,
                summary,
                brand,
                productFulltype,
                dimensionMatched,
                dimensionSource,
                dimensionQualityCodes,
                dataQualityCodes,
                summary
        );
    }

    public SalesProductRow(
            String partnerSku,
            String sku,
            String productTitle,
            LocalDate latestFactDate,
            List<String> sourceSystems,
            SalesAnalyticsSummary summary,
            String brand,
            String productFulltype,
            boolean dimensionMatched,
            String dimensionSource,
            List<String> dimensionQualityCodes,
            List<String> dataQualityCodes,
            SalesAnalyticsSummary latestSummary
    ) {
        this(
                partnerSku,
                sku,
                productTitle,
                latestFactDate,
                sourceSystems,
                summary,
                brand,
                productFulltype,
                dimensionMatched,
                dimensionSource,
                dimensionQualityCodes,
                dataQualityCodes,
                latestSummary,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public SalesProductRow(
            String partnerSku,
            String sku,
            String productTitle,
            LocalDate latestFactDate,
            List<String> sourceSystems,
            SalesAnalyticsSummary summary,
            String brand,
            String productFulltype,
            boolean dimensionMatched,
            String dimensionSource,
            List<String> dimensionQualityCodes,
            List<String> dataQualityCodes,
            SalesAnalyticsSummary latestSummary,
            String imageUrl,
            Integer currentStock,
            Integer fbnStock,
            Integer supermallStock,
            Integer fbpStock,
            BigDecimal stockCoverDays
    ) {
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.productTitle = productTitle;
        this.latestFactDate = latestFactDate;
        this.sourceSystems = sourceSystems == null ? List.of() : List.copyOf(sourceSystems);
        this.brand = brand;
        this.productFulltype = productFulltype;
        this.imageUrl = ProductImageUrlSupport.normalize(imageUrl);
        this.currentStock = currentStock;
        this.fbnStock = fbnStock;
        this.supermallStock = supermallStock;
        this.fbpStock = fbpStock;
        this.stockCoverDays = stockCoverDays;
        this.dimensionMatched = dimensionMatched;
        this.dimensionSource = dimensionSource;
        this.dimensionQualityCodes = dimensionQualityCodes == null ? List.of() : List.copyOf(dimensionQualityCodes);
        this.dataQualityCodes = dataQualityCodes == null ? List.of() : List.copyOf(dataQualityCodes);
        this.summary = summary;
        this.latestSummary = latestSummary == null ? summary : latestSummary;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public String getSku() {
        return sku;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public LocalDate getLatestFactDate() {
        return latestFactDate;
    }

    public List<String> getSourceSystems() {
        return sourceSystems;
    }

    public String getBrand() {
        return brand;
    }

    public String getProductFulltype() {
        return productFulltype;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public Integer getFbnStock() {
        return fbnStock;
    }

    public Integer getSupermallStock() {
        return supermallStock;
    }

    public Integer getFbpStock() {
        return fbpStock;
    }

    public BigDecimal getStockCoverDays() {
        return stockCoverDays;
    }

    public boolean isDimensionMatched() {
        return dimensionMatched;
    }

    public String getDimensionSource() {
        return dimensionSource;
    }

    public List<String> getDimensionQualityCodes() {
        return dimensionQualityCodes;
    }

    public List<String> getDataQualityCodes() {
        return dataQualityCodes;
    }

    public int getNetUnits() {
        return summary.getNetUnits();
    }

    public int getGrossUnits() {
        return summary.getGrossUnits();
    }

    public int getShippedUnits() {
        return summary.getShippedUnits();
    }

    public int getCancelledUnits() {
        return summary.getCancelledUnits();
    }

    public BigDecimal getRevenueShipped() {
        return summary.getRevenueShipped();
    }

    public int getYourVisitors() {
        return summary.getYourVisitors();
    }

    public int getTotalVisitors() {
        return summary.getTotalVisitors();
    }

    public BigDecimal getConversionVisitorsPercentage() {
        return summary.getConversionVisitorsPercentage();
    }

    public BigDecimal getBuyBoxVisitorPercentage() {
        return summary.getBuyBoxVisitorPercentage();
    }

    public boolean isBusinessMetricsAvailable() {
        return summary.isBusinessMetricsAvailable();
    }

    public int getLatestNetUnits() {
        return latestSummary.getNetUnits();
    }

    public int getLatestGrossUnits() {
        return latestSummary.getGrossUnits();
    }

    public int getLatestShippedUnits() {
        return latestSummary.getShippedUnits();
    }

    public int getLatestCancelledUnits() {
        return latestSummary.getCancelledUnits();
    }

    public BigDecimal getLatestRevenueShipped() {
        return latestSummary.getRevenueShipped();
    }

    public int getLatestYourVisitors() {
        return latestSummary.getYourVisitors();
    }

    public BigDecimal getLatestConversionVisitorsPercentage() {
        return latestSummary.getConversionVisitorsPercentage();
    }

    public boolean isLatestBusinessMetricsAvailable() {
        return latestSummary.isBusinessMetricsAvailable();
    }
}
