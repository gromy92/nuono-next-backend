package com.nuono.next.outboundfee;

public enum OfficialOutboundFeeCalculationFailure {
    MISSING_DIMENSIONS,
    MISSING_WEIGHT,
    MISSING_SALE_PRICE,
    POLICY_NOT_FOUND,
    CLASSIFICATION_NOT_FOUND,
    SLAB_NOT_FOUND,
    CURRENCY_MISMATCH
}
