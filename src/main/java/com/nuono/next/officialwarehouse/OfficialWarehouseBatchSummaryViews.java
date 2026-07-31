package com.nuono.next.officialwarehouse;

import java.util.ArrayList;
import java.util.List;

public final class OfficialWarehouseBatchSummaryViews {

    private OfficialWarehouseBatchSummaryViews() {
    }

    public static class BatchProductSummaryView {
        public Integer totalQuantity;
        public Integer totalSkuCount;
        public Integer totalLineCount;
        public StoreProductSummaryView currentStore;
        public List<StoreProductSummaryView> otherStores = new ArrayList<>();
        public Integer unassignedQuantity;
        public Integer unassignedSkuCount;
        public Boolean attributionWarning;
    }

    public static class StoreProductSummaryView {
        public String storeCode;
        public String storeName;
        public String siteCode;
        public Integer totalQuantity;
        public Integer totalSkuCount;
        public Integer bookableQuantity;
        public Integer bookableSkuCount;
        public Integer blockedQuantity;
        public Integer blockedSkuCount;
        public Integer missingDimensionQuantity;
        public Integer missingDimensionSkuCount;
        public List<ProductIssueView> blockedItems = new ArrayList<>();
        public List<ProductIssueView> missingDimensionItems = new ArrayList<>();
    }

    public static class ProductIssueView {
        public String partnerSku;
        public String title;
        public Integer quantity;
        public List<String> reasons = new ArrayList<>();
    }
}
