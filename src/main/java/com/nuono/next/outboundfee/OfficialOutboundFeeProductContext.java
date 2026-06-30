package com.nuono.next.outboundfee;

import java.math.BigDecimal;

public class OfficialOutboundFeeProductContext {

    private final String skuId;
    private final Long variantId;
    private final OfficialOutboundFeeProductSpecRecord effectiveSpec;
    private final BigDecimal salePrice;

    public OfficialOutboundFeeProductContext(
            String skuId,
            Long variantId,
            OfficialOutboundFeeProductSpecRecord effectiveSpec,
            BigDecimal salePrice
    ) {
        this.skuId = skuId;
        this.variantId = variantId;
        this.effectiveSpec = effectiveSpec;
        this.salePrice = salePrice;
    }

    public String getSkuId() {
        return skuId;
    }

    public Long getVariantId() {
        return variantId;
    }

    public OfficialOutboundFeeProductSpecRecord getEffectiveSpec() {
        return effectiveSpec;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }
}
