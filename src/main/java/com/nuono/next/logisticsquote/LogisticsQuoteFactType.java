package com.nuono.next.logisticsquote;

import java.util.Arrays;

public enum LogisticsQuoteFactType {
    SERVICE_LINE("logistics_service_line", "logistics_service_line"),
    CARGO_CATEGORY("logistics_cargo_category", "logistics_cargo_category"),
    PRICE_RULE("logistics_base_price", "logistics_price_rule"),
    SURCHARGE_RULE("logistics_surcharge", "logistics_surcharge_rule"),
    BILLING_RULE("logistics_billing_rule", "logistics_billing_rule"),
    WAREHOUSE_FEE_RULE("logistics_warehouse_service_fee", "logistics_warehouse_fee_rule"),
    RESTRICTION_RULE("logistics_restriction", "logistics_restriction_rule");

    private final String itemType;
    private final String tableName;

    LogisticsQuoteFactType(String itemType, String tableName) {
        this.itemType = itemType;
        this.tableName = tableName;
    }

    public String itemType() {
        return itemType;
    }

    public String tableName() {
        return tableName;
    }

    public static LogisticsQuoteFactType fromItemType(String itemType) {
        return Arrays.stream(values())
                .filter(type -> type.itemType.equals(itemType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported logistics quote fact item type: " + itemType));
    }
}
