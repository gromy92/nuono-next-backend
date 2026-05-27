package com.nuono.next.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class SalesProductDetail {

    private final String partnerSku;
    private final String sku;
    private final String productTitle;
    private final LocalDate latestFactDate;
    private final List<String> sourceSystems;
    private final SalesAnalyticsSummary summary;
    private final List<DailySalesFact> facts;
    private final String imageUrl;
    private final Integer currentStock;
    private final Integer fbnStock;
    private final Integer supermallStock;
    private final Integer fbpStock;
    private final BigDecimal stockCoverDays;
    private final List<SalesPriceTrendBucket> priceTrend;
    private final SalesPriceTrendState priceTrendState;
    private final SalesHistoryCoverage historyCoverage;

    public SalesProductDetail(
            String partnerSku,
            String sku,
            String productTitle,
            LocalDate latestFactDate,
            List<String> sourceSystems,
            SalesAnalyticsSummary summary,
            List<DailySalesFact> facts
    ) {
        this(partnerSku, sku, productTitle, latestFactDate, sourceSystems, summary, facts, null, null, null, null, null, null);
    }

    public SalesProductDetail(
            String partnerSku,
            String sku,
            String productTitle,
            LocalDate latestFactDate,
            List<String> sourceSystems,
            SalesAnalyticsSummary summary,
            List<DailySalesFact> facts,
            String imageUrl,
            Integer currentStock,
            Integer fbnStock,
            Integer supermallStock,
            Integer fbpStock,
            BigDecimal stockCoverDays
    ) {
        this(
                partnerSku,
                sku,
                productTitle,
                latestFactDate,
                sourceSystems,
                summary,
                facts,
                imageUrl,
                currentStock,
                fbnStock,
                supermallStock,
                fbpStock,
                stockCoverDays,
                List.of(),
                SalesPriceTrendState.noOrderPriceFacts(),
                null
        );
    }

    public SalesProductDetail(
            String partnerSku,
            String sku,
            String productTitle,
            LocalDate latestFactDate,
            List<String> sourceSystems,
            SalesAnalyticsSummary summary,
            List<DailySalesFact> facts,
            String imageUrl,
            Integer currentStock,
            Integer fbnStock,
            Integer supermallStock,
            Integer fbpStock,
            BigDecimal stockCoverDays,
            List<SalesPriceTrendBucket> priceTrend,
            SalesPriceTrendState priceTrendState
    ) {
        this(
                partnerSku,
                sku,
                productTitle,
                latestFactDate,
                sourceSystems,
                summary,
                facts,
                imageUrl,
                currentStock,
                fbnStock,
                supermallStock,
                fbpStock,
                stockCoverDays,
                priceTrend,
                priceTrendState,
                null
        );
    }

    public SalesProductDetail(
            String partnerSku,
            String sku,
            String productTitle,
            LocalDate latestFactDate,
            List<String> sourceSystems,
            SalesAnalyticsSummary summary,
            List<DailySalesFact> facts,
            String imageUrl,
            Integer currentStock,
            Integer fbnStock,
            Integer supermallStock,
            Integer fbpStock,
            BigDecimal stockCoverDays,
            List<SalesPriceTrendBucket> priceTrend,
            SalesPriceTrendState priceTrendState,
            SalesHistoryCoverage historyCoverage
    ) {
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.productTitle = productTitle;
        this.latestFactDate = latestFactDate;
        this.sourceSystems = sourceSystems == null ? List.of() : List.copyOf(sourceSystems);
        this.summary = summary;
        this.facts = facts == null ? List.of() : List.copyOf(facts);
        this.imageUrl = imageUrl;
        this.currentStock = currentStock;
        this.fbnStock = fbnStock;
        this.supermallStock = supermallStock;
        this.fbpStock = fbpStock;
        this.stockCoverDays = stockCoverDays;
        this.priceTrend = priceTrend == null ? List.of() : List.copyOf(priceTrend);
        this.priceTrendState = priceTrendState == null ? SalesPriceTrendState.noOrderPriceFacts() : priceTrendState;
        this.historyCoverage = historyCoverage;
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

    public SalesAnalyticsSummary getSummary() {
        return summary;
    }

    public List<DailySalesFact> getFacts() {
        return facts;
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

    public List<SalesPriceTrendBucket> getPriceTrend() {
        return priceTrend;
    }

    public SalesPriceTrendState getPriceTrendState() {
        return priceTrendState;
    }

    public SalesHistoryCoverage getHistoryCoverage() {
        return historyCoverage;
    }
}
