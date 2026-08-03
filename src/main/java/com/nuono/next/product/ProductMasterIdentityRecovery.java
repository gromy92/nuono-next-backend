package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import org.springframework.util.StringUtils;

final class ProductMasterIdentityRecovery {

    private final ProductManagementMapper productManagementMapper;

    ProductMasterIdentityRecovery(ProductManagementMapper productManagementMapper) {
        this.productManagementMapper = productManagementMapper;
    }

    Long resolve(Long logicalStoreId, ProductIdentity identity, String currentZCode) {
        if (identity == null || !identity.isComplete()) {
            return persistedOrNull(productManagementMapper.selectProductMasterId(logicalStoreId, currentZCode));
        }
        Long partnerMatch = productManagementMapper.selectProductMasterIdByStorePartnerSku(
                identity.logicalStoreId(),
                identity.partnerSku()
        );
        if (isPersisted(partnerMatch)) {
            return partnerMatch;
        }
        if (!StringUtils.hasText(currentZCode)) {
            return null;
        }
        return persistedOrNull(productManagementMapper.selectUnclaimedProductMasterIdBySkuParent(
                logicalStoreId,
                currentZCode
        ));
    }

    private Long persistedOrNull(Long id) {
        return isPersisted(id) ? id : null;
    }

    private boolean isPersisted(Long id) {
        return id != null && id > 0;
    }
}
