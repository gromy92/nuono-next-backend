package com.nuono.next.productselection;

public interface ProductSelectionMarketplaceSourceCollector {

    boolean supports(String platform);

    ProductSelectionSourceCollectionResult collect(ProductSelectionSourceCollectionRow row);
}
