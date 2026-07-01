package com.nuono.next.product;

import org.springframework.util.StringUtils;

public final class ProductIdentity {

    private final Long logicalStoreId;
    private final String partnerSku;

    public ProductIdentity(Long logicalStoreId, String partnerSku) {
        this.logicalStoreId = logicalStoreId;
        this.partnerSku = StringUtils.hasText(partnerSku) ? partnerSku.trim() : null;
    }

    public Long logicalStoreId() {
        return logicalStoreId;
    }

    public String partnerSku() {
        return partnerSku;
    }

    public boolean isComplete() {
        return logicalStoreId != null && StringUtils.hasText(partnerSku);
    }
}
