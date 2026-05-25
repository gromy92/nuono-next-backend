package com.nuono.next.outboundfee;

import java.util.Arrays;

public enum OfficialOutboundFeeFactType {
    SIZE_CLASSIFICATION("outbound_size_classification_rule", "official_outbound_size_classification_rule"),
    FEE_WEIGHT_SLAB("outbound_fee_weight_slab_rule", "official_outbound_fee_weight_slab_rule"),
    CALCULATION_POLICY("outbound_fee_calculation_policy", "official_outbound_fee_calculation_policy");

    private final String itemType;
    private final String tableName;

    OfficialOutboundFeeFactType(String itemType, String tableName) {
        this.itemType = itemType;
        this.tableName = tableName;
    }

    public String itemType() {
        return itemType;
    }

    public String tableName() {
        return tableName;
    }

    public static OfficialOutboundFeeFactType fromItemType(String itemType) {
        return Arrays.stream(values())
                .filter(type -> type.itemType.equals(itemType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported official outbound fee item type: " + itemType));
    }
}
