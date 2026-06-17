package com.nuono.next.permission.access;

import java.util.List;

public enum BusinessCapability {
    PRODUCT_MASTER(List.of("/api/sku/manage", "/product/groups", "/product/manage")),
    PRODUCT_LISTING(List.of("/purchase/listing", "/api/product-listing")),
    PROCUREMENT(List.of("/api/purchase/order", "/purchase/order")),
    ALI1688_HISTORICAL_ORDERS(List.of(
            "/api/procurement/ali1688-orders",
            "/purchase/ali1688-orders",
            "/purchase/ali1688-sku-purchase-history"
    )),
    STORE_SYNC(List.of("/api/user/role", "/user/store-noon")),
    MASTER_DATA_SYSTEM(List.of("/system/role", "/system/menu")),
    SALES_DATA(List.of("/data/sales-analysis", "/data/sales-forecast", "/data/sales", "/data/order-analysis", "/api/sales-forecast", "/api/order-finance")),
    SYSTEM_REPORTS(List.of("/system-reports", "/api/system-reports", "/noon-call", "/api/noon-call")),
    OPERATIONS_COMPETITOR_ANALYSIS(List.of("/operations/competitor-analysis", "/api/competitor-analysis")),
    ADVANCED_OPERATIONS_CONFIG(List.of("/operations/config", "/api/operations-config")),
    FILE_MANAGEMENT_SYSTEM(List.of("/system/file-management", "/system/ai-file-parse")),
    FILE_MANAGEMENT_BUSINESS(List.of("/system/file-management", "/system/ai-file-parse")),
    LOGISTICS_QUOTE(List.of("/purchase/logistics-quote")),
    WAREHOUSE_DISPATCH(List.of("/warehouse/dispatch", "/api/warehouse/dispatch")),
    IN_TRANSIT_GOODS(List.of("/purchase/in-transit-goods", "/api/in-transit-goods")),
    PROFIT(List.of("/api/sku/cost"));

    private final List<String> menuPathPrefixes;

    BusinessCapability(List<String> menuPathPrefixes) {
        this.menuPathPrefixes = menuPathPrefixes;
    }

    public List<String> getMenuPathPrefixes() {
        return menuPathPrefixes;
    }
}
