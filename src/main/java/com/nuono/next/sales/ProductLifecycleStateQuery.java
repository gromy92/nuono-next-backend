package com.nuono.next.sales;

import java.util.Objects;

public class ProductLifecycleStateQuery {

    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String partnerSku;
    private final String sku;

    public ProductLifecycleStateQuery(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String partnerSku,
            String sku
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.partnerSku = partnerSku;
        this.sku = sku;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public String getSku() {
        return sku;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProductLifecycleStateQuery)) {
            return false;
        }
        ProductLifecycleStateQuery that = (ProductLifecycleStateQuery) o;
        return Objects.equals(ownerUserId, that.ownerUserId)
                && Objects.equals(storeCode, that.storeCode)
                && Objects.equals(siteCode, that.siteCode)
                && Objects.equals(partnerSku, that.partnerSku)
                && Objects.equals(sku, that.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerUserId, storeCode, siteCode, partnerSku, sku);
    }
}
