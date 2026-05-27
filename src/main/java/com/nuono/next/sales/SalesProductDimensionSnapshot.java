package com.nuono.next.sales;

public class SalesProductDimensionSnapshot {

    private final String partnerSku;
    private final String sku;
    private final String brand;
    private final String productFulltype;
    private final String imageUrl;
    private final Integer currentStock;
    private final Integer fbnStock;
    private final Integer supermallStock;
    private final Integer fbpStock;

    public SalesProductDimensionSnapshot(
            String partnerSku,
            String sku,
            String brand,
            String productFulltype
    ) {
        this(partnerSku, sku, brand, productFulltype, null, null, null, null, null);
    }

    public SalesProductDimensionSnapshot(
            String partnerSku,
            String sku,
            String brand,
            String productFulltype,
            Integer currentStock
    ) {
        this(partnerSku, sku, brand, productFulltype, null, currentStock, null, null, null);
    }

    public SalesProductDimensionSnapshot(
            String partnerSku,
            String sku,
            String brand,
            String productFulltype,
            String imageUrl,
            Integer currentStock,
            Integer fbnStock,
            Integer supermallStock,
            Integer fbpStock
    ) {
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.brand = brand;
        this.productFulltype = productFulltype;
        this.imageUrl = imageUrl;
        this.currentStock = currentStock;
        this.fbnStock = fbnStock;
        this.supermallStock = supermallStock;
        this.fbpStock = fbpStock;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public String getSku() {
        return sku;
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
}
