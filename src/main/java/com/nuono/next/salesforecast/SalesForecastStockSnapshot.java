package com.nuono.next.salesforecast;

public class SalesForecastStockSnapshot {

    private final String partnerSku;
    private final String sku;
    private final Integer currentStock;

    public SalesForecastStockSnapshot(String partnerSku, String sku, Integer currentStock) {
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.currentStock = currentStock;
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
}
