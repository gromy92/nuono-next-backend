package com.nuono.next.product;

import java.util.Objects;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductIdentity)) {
            return false;
        }
        ProductIdentity that = (ProductIdentity) other;
        return Objects.equals(logicalStoreId, that.logicalStoreId)
                && Objects.equals(partnerSku, that.partnerSku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logicalStoreId, partnerSku);
    }
}
