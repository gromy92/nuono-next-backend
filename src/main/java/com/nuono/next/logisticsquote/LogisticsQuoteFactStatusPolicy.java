package com.nuono.next.logisticsquote;

import java.math.BigDecimal;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

public class LogisticsQuoteFactStatusPolicy {

    public String initialStatus(LogisticsQuoteFactType factType, Map<String, Object> payload) {
        if (LogisticsQuoteFactType.CARGO_CATEGORY == factType && bool(payload.get("manualConfirmRequired"))) {
            return LogisticsQuoteFactStatus.PENDING_MANUAL_CONFIRM.value();
        }
        return LogisticsQuoteFactStatus.ACTIVE.value();
    }

    public String conflictStatus() {
        return LogisticsQuoteFactStatus.CONFLICT.value();
    }

    public boolean isConflictingDuplicate(String previousSignature, String newSignature) {
        return previousSignature != null && newSignature != null && !previousSignature.equals(newSignature);
    }

    public String landingScopeKey(LogisticsQuoteFactType factType, String naturalKey) {
        return factType.tableName() + "|" + naturalKey;
    }

    public String payloadSignature(LogisticsQuoteFactType factType, Map<String, Object> payload) {
        TreeMap<String, String> normalized = new TreeMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            normalized.put(entry.getKey(), normalizedValue(entry.getValue()));
        }
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(factType.tableName());
        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            joiner.add(entry.getKey() + "=" + entry.getValue());
        }
        return joiner.toString();
    }

    private String normalizedValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value)).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value).trim();
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = value == null ? null : String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }
}
