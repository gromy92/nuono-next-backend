package com.nuono.next.product;

import java.util.Set;

public final class ProductVariantSpecLogisticsType {

    public static final String UNKNOWN = "unknown";
    public static final String NONE = "none";

    private static final Set<String> BATTERY_MAGNETIC = Set.of(
            UNKNOWN, NONE, "battery", "magnetic", "battery_and_magnetic"
    );
    private static final Set<String> LIQUID_POWDER = Set.of(
            UNKNOWN, NONE, "liquid", "powder", "liquid_and_powder"
    );

    private ProductVariantSpecLogisticsType() {
    }

    public static String normalizeBatteryMagnetic(String value) {
        return normalize(value, BATTERY_MAGNETIC, "带电/磁属性不合法");
    }

    public static String normalizeLiquidPowder(String value) {
        return normalize(value, LIQUID_POWDER, "液体/粉末属性不合法");
    }

    private static String normalize(String value, Set<String> allowed, String message) {
        String normalized = value == null || value.isBlank() ? UNKNOWN : value.trim().toLowerCase();
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
