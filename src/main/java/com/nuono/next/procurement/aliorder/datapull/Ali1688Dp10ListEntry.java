package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;

/** One classified list position with its immutable pre-validation provider fingerprint. */
final class Ali1688Dp10ListEntry {
    private final int ordinal;
    private final Ali1688HistoricalOrderProvider.OrderSnapshot order;
    private final Ali1688Dp10ItemState state;
    private final String sanitizedCode;
    private final String rawFingerprint;

    Ali1688Dp10ListEntry(
            int ordinal,
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            Ali1688Dp10ItemState state,
            String sanitizedCode,
            String rawFingerprint
    ) {
        if (ordinal < 0 || state == null || !isFingerprint(rawFingerprint)) {
            throw new IllegalArgumentException("invalid DP-10 list entry");
        }
        this.ordinal = ordinal;
        this.order = order;
        this.state = state;
        this.sanitizedCode = sanitizedCode;
        this.rawFingerprint = rawFingerprint;
    }

    int getOrdinal() { return ordinal; }
    Ali1688HistoricalOrderProvider.OrderSnapshot getOrder() { return order; }
    Ali1688Dp10ItemState getState() { return state; }
    String getSanitizedCode() { return sanitizedCode; }
    String getRawFingerprint() { return rawFingerprint; }

    private boolean isFingerprint(String value) {
        if (value == null || value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char item = value.charAt(index);
            if (!(item >= '0' && item <= '9') && !(item >= 'a' && item <= 'f')) return false;
        }
        return true;
    }
}
