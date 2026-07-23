package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import org.springframework.util.StringUtils;

final class ProductBarcodeProjectionWriter {
    private final ProductManagementMapper productManagementMapper;

    ProductBarcodeProjectionWriter(ProductManagementMapper productManagementMapper) {
        this.productManagementMapper = productManagementMapper;
    }

    void persist(
            Long variantId,
            Long productMasterId,
            Long logicalStoreId,
            String partnerSku,
            String barcode,
            Long updatedBy
    ) {
        String normalizedBarcode = normalize(barcode);
        String normalizedPartnerSku = normalize(partnerSku);
        if (variantId == null
                || productMasterId == null
                || logicalStoreId == null
                || !StringUtils.hasText(normalizedPartnerSku)
                || !StringUtils.hasText(normalizedBarcode)) {
            return;
        }
        Long activeId = productManagementMapper.selectProductBarcodeIdByBarcode(normalizedBarcode);
        Long id = activeId != null ? activeId : productManagementMapper.nextProductBarcodeId();
        productManagementMapper.upsertProductBarcode(
                id,
                variantId,
                productMasterId,
                logicalStoreId,
                normalizedPartnerSku,
                normalizedBarcode,
                null,
                true,
                updatedBy
        );
        Long persistedProductMasterId =
                productManagementMapper.selectProductBarcodeProductMasterIdByBarcode(normalizedBarcode);
        if (!productMasterId.equals(persistedProductMasterId)) {
            throw new IllegalStateException(
                    "Barcode " + normalizedBarcode + " is already assigned to another product."
            );
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
