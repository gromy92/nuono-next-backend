package com.nuono.next.product;

import java.time.LocalDateTime;
import java.util.Map;

public final class ProductActiveStateEvidence {
    private String source;
    private String syncedAt;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(String syncedAt) {
        this.syncedAt = syncedAt;
    }

    public void copyFrom(ProductActiveStateEvidence evidence) {
        if (evidence == null) return;
        source = evidence.source;
        syncedAt = evidence.syncedAt;
    }

    public static void record(Map<String, Object> target, String source) {
        if (target == null) return;
        target.put("activeStateSource", source);
        target.put("activeStateSyncedAt", LocalDateTime.now().toString());
    }

    public static String syncedAt(Map<String, Object> source, String fallback) {
        if (source == null) return fallback;
        Object value = source.get("activeStateSyncedAt");
        String text = value == null ? null : String.valueOf(value).trim();
        return text == null || text.isEmpty() ? fallback : text;
    }
}
