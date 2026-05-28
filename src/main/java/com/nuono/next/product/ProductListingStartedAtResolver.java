package com.nuono.next.product;

public class ProductListingStartedAtResolver {

    public ProductListingStartedAtResolution resolve(ProductListingStartedAtSignals signals) {
        if (signals == null) {
            return new ProductListingStartedAtResolution(null, "data_missing");
        }
        if (!signals.isSalesFactDataAvailable()) {
            return new ProductListingStartedAtResolution(null, "data_missing");
        }
        if (signals.getFirstPvDate() != null) {
            return new ProductListingStartedAtResolution(signals.getFirstPvDate().atStartOfDay(), "pv");
        }
        if (signals.getFirstInventoryAt() != null) {
            return new ProductListingStartedAtResolution(signals.getFirstInventoryAt(), "inventory");
        }
        if (signals.getFirstSalesDate() != null) {
            return new ProductListingStartedAtResolution(signals.getFirstSalesDate().atStartOfDay(), "sales");
        }
        if (signals.getFirstPurchaseAt() != null) {
            return new ProductListingStartedAtResolution(signals.getFirstPurchaseAt(), "purchase");
        }
        return new ProductListingStartedAtResolution(null, "not_listed");
    }
}
