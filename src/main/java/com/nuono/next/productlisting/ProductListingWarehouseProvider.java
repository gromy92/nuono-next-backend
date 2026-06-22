package com.nuono.next.productlisting;

import java.util.List;

public interface ProductListingWarehouseProvider {
    List<ProductListingWarehouseView> listWarehouses(Long ownerUserId, String storeCode);
}
