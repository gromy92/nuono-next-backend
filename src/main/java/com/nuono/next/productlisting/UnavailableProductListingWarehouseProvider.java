package com.nuono.next.productlisting;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UnavailableProductListingWarehouseProvider implements ProductListingWarehouseProvider {
    @Override
    public List<ProductListingWarehouseView> listWarehouses(Long ownerUserId, String storeCode) {
        throw new IllegalStateException("Product listing Noon warehouse provider is not configured.");
    }
}
