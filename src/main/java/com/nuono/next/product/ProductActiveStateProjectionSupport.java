package com.nuono.next.product;

import java.util.Map;

final class ProductActiveStateProjectionSupport {
    private ProductActiveStateProjectionSupport() {
    }

    static void hydrate(Map<String, Object> target, Map<String, Object> row) {
        Integer activeFlag = integer(row == null ? null : row.get("activeFlag"));
        if (activeFlag != null) target.put("isActive", activeFlag > 0);
        putText(target, "activeStateSource", row == null ? null : row.get("activeStateSource"));
        putText(target, "activeStateSyncedAt", row == null ? null : row.get("activeStateSyncedAt"));
    }

    static void write(Map<String, Object> target, Boolean isActive, ProductActiveStateEvidence evidence) {
        if (target == null) return;
        if (isActive != null) target.put("isActive", isActive);
        putText(target, "activeStateSource", evidence == null ? null : evidence.getSource());
        putText(target, "activeStateSyncedAt", evidence == null ? null : evidence.getSyncedAt());
    }

    private static Integer integer(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void putText(Map<String, Object> target, String key, Object value) {
        if (target == null || value == null) return;
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) target.put(key, text);
    }
}
