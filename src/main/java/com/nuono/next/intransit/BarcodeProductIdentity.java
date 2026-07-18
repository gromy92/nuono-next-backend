package com.nuono.next.intransit;

public final class BarcodeProductIdentity {

    private final Long logicalStoreId;
    private final String partnerSku;

    public BarcodeProductIdentity(Long logicalStoreId, String partnerSku) {
        this.logicalStoreId = logicalStoreId;
        this.partnerSku = partnerSku;
    }

    public Long getLogicalStoreId() {
        return logicalStoreId;
    }

    public String getPartnerSku() {
        return partnerSku;
    }
}
