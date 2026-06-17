package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;

public class ProductVariantLogisticsProfileListView {
    public boolean ready;
    public Long ownerUserId;
    public String storeCode;
    public String skuParent;
    public List<ProductVariantLogisticsProfileView> items = new ArrayList<>();
}
