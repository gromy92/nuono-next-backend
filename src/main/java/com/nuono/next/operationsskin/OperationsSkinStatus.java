package com.nuono.next.operationsskin;

public enum OperationsSkinStatus {
    ACTIVE,
    INACTIVE;

    public static String normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return ACTIVE.name();
        }
        String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
        for (OperationsSkinStatus status : values()) {
            if (status.name().equals(normalized)) {
                return normalized;
            }
        }
        throw new IllegalArgumentException("皮肤状态不支持。");
    }
}
