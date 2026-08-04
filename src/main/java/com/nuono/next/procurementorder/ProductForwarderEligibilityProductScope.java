package com.nuono.next.procurementorder;

public class ProductForwarderEligibilityProductScope {

    public final Long ownerUserId;
    public final Long logicalStoreId;
    public final Long productMasterId;
    public final Long productVariantId;
    public final String sourceStoreCode;
    public final String partnerSku;
    public final String siteCode;
    public final String forwarderCode;
    public final String transportMode;

    public ProductForwarderEligibilityProductScope(
            Long ownerUserId,
            Long logicalStoreId,
            Long productMasterId,
            Long productVariantId,
            String sourceStoreCode,
            String partnerSku,
            String siteCode,
            String forwarderCode,
            String transportMode
    ) {
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.productMasterId = productMasterId;
        this.productVariantId = productVariantId;
        this.sourceStoreCode = sourceStoreCode;
        this.partnerSku = partnerSku;
        this.siteCode = siteCode;
        this.forwarderCode = forwarderCode;
        this.transportMode = transportMode;
    }
}
