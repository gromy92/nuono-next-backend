package com.nuono.next.logisticsquote;

import java.util.LinkedHashMap;
import java.util.Map;

public class LogisticsQuotePublishedItem {

    private final String itemType;
    private final String naturalKey;
    private final Map<String, Object> payload;
    private final LogisticsQuoteFactSourceLineage sourceLineage;

    public LogisticsQuotePublishedItem(
            String itemType,
            String naturalKey,
            Map<String, Object> payload,
            LogisticsQuoteFactSourceLineage sourceLineage
    ) {
        this.itemType = itemType;
        this.naturalKey = naturalKey;
        this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        this.sourceLineage = sourceLineage;
    }

    public String getItemType() {
        return itemType;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public Map<String, Object> getPayload() {
        return new LinkedHashMap<>(payload);
    }

    public LogisticsQuoteFactSourceLineage getSourceLineage() {
        return sourceLineage;
    }
}
