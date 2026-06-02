package com.nuono.next.product;

import java.util.Set;

public final class ProductVariantSpecSourceType {

    public static final String ALI1688 = "ali1688";
    public static final String WAREHOUSE = "warehouse";
    public static final String NOON_OFFICIAL = "noon_official";

    private static final Set<String> ALL = Set.of(ALI1688, WAREHOUSE, NOON_OFFICIAL);
    private static final Set<String> EFFECTIVE_ALLOWED = Set.of(ALI1688, WAREHOUSE);

    private ProductVariantSpecSourceType() {
    }

    public static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (!ALL.contains(normalized)) {
            throw new IllegalArgumentException("规格来源不合法");
        }
        return normalized;
    }

    public static String normalizeEffective(String value) {
        String normalized = normalize(value);
        if (!EFFECTIVE_ALLOWED.contains(normalized)) {
            throw new IllegalArgumentException("Noon 官方测量不能设为经营生效规格");
        }
        return normalized;
    }

    public static boolean isNoonOfficial(String value) {
        return NOON_OFFICIAL.equals(value);
    }
}
