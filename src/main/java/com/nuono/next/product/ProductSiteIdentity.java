package com.nuono.next.product;

import java.util.Objects;
import org.springframework.util.StringUtils;

public final class ProductSiteIdentity {

    private final Long logicalStoreId;
    private final String partnerSku;
    private final String siteCode;

    public ProductSiteIdentity(Long logicalStoreId, String partnerSku, String siteCode) {
        this.logicalStoreId = logicalStoreId;
        this.partnerSku = StringUtils.hasText(partnerSku) ? partnerSku.trim() : null;
        this.siteCode = StringUtils.hasText(siteCode) ? siteCode.trim() : null;
    }

    public Long logicalStoreId() {
        return logicalStoreId;
    }

    public String partnerSku() {
        return partnerSku;
    }

    public String siteCode() {
        return siteCode;
    }

    public boolean isComplete() {
        return logicalStoreId != null && StringUtils.hasText(partnerSku) && StringUtils.hasText(siteCode);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductSiteIdentity)) {
            return false;
        }
        ProductSiteIdentity that = (ProductSiteIdentity) other;
        return Objects.equals(logicalStoreId, that.logicalStoreId)
                && Objects.equals(partnerSku, that.partnerSku)
                && Objects.equals(siteCode, that.siteCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logicalStoreId, partnerSku, siteCode);
    }
}
