package com.nuono.next.outboundfee;

import java.util.LinkedHashMap;
import java.util.Map;

public class OfficialOutboundFeePublishedItem {

    private final String itemType;
    private final String naturalKey;
    private final Map<String, Object> payload;
    private final OfficialOutboundFeeSourceLineage sourceLineage;

    public OfficialOutboundFeePublishedItem(
            String itemType,
            String naturalKey,
            Map<String, Object> payload,
            OfficialOutboundFeeSourceLineage sourceLineage
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

    public OfficialOutboundFeeSourceLineage getSourceLineage() {
        return sourceLineage;
    }
}
