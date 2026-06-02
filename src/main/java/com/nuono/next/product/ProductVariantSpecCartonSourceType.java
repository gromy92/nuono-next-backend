package com.nuono.next.product;

import java.util.Set;

public final class ProductVariantSpecCartonSourceType {

    public static final String NONE = "none";
    public static final String FACTORY_CARTON = "factory_carton";
    public static final String WAREHOUSE_MEASURED = "warehouse_measured";
    public static final String DERIVED_FROM_WAREHOUSE = "derived_from_warehouse";

    private static final Set<String> ALL = Set.of(NONE, FACTORY_CARTON, WAREHOUSE_MEASURED, DERIVED_FROM_WAREHOUSE);

    private ProductVariantSpecCartonSourceType() {
    }

    public static String normalize(String value) {
        String normalized = value == null || value.isBlank() ? NONE : value.trim().toLowerCase();
        if (!ALL.contains(normalized)) {
            throw new IllegalArgumentException("箱规来源不合法");
        }
        return normalized;
    }
}
