package com.nuono.next.salesforecast;

public class SalesForecastStockSnapshot {

    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String partnerSku;
    private final String sku;
    private final Integer currentStock;
    private final String brand;
    private final String productFulltype;
    private final String productFamily;
    private final String productTitle;

    public SalesForecastStockSnapshot(String partnerSku, String sku, Integer currentStock) {
        this(null, null, null, partnerSku, sku, currentStock, null, null, null, null);
    }

    public SalesForecastStockSnapshot(
            String partnerSku,
            String sku,
            Integer currentStock,
            String brand,
            String productFulltype,
            String productFamily
    ) {
        this(null, null, null, partnerSku, sku, currentStock, brand, productFulltype, productFamily, null);
    }

    public SalesForecastStockSnapshot(
            String partnerSku,
            String sku,
            Integer currentStock,
            String brand,
            String productFulltype,
            String productFamily,
            String productTitle
    ) {
        this(null, null, null, partnerSku, sku, currentStock, brand, productFulltype, productFamily, productTitle);
    }

    public SalesForecastStockSnapshot(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String partnerSku,
            String sku,
            Integer currentStock,
            String brand,
            String productFulltype,
            String productFamily,
            String productTitle
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.currentStock = currentStock;
        this.brand = brand;
        this.productFulltype = productFulltype;
        this.productFamily = productFamily;
        this.productTitle = productTitle;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public String getSku() {
        return sku;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public String getBrand() {
        return brand;
    }

    public String getProductFulltype() {
        return productFulltype;
    }

    public String getProductFamily() {
        return productFamily;
    }

    public String getProductTitle() {
        return productTitle;
    }
}
