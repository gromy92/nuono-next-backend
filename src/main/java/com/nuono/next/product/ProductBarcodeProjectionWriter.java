package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.util.StringUtils;

final class ProductBarcodeProjectionWriter {
    static final String NOON_PBARCODE = "NOON_PBARCODE";
    static final String PARTNER_SKU_ALIAS = "PARTNER_SKU_ALIAS";

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
        persistAll(
                variantId,
                productMasterId,
                logicalStoreId,
                partnerSku,
                StringUtils.hasText(barcode) ? List.of(barcode) : List.of(),
                updatedBy
        );
    }

    void persistAll(
            Long variantId,
            Long productMasterId,
            Long logicalStoreId,
            String partnerSku,
            List<String> barcodes,
            Long updatedBy
    ) {
        String normalizedPartnerSku = normalize(partnerSku);
        if (variantId == null
                || productMasterId == null
                || logicalStoreId == null
                || !StringUtils.hasText(normalizedPartnerSku)) {
            return;
        }
        Set<String> normalizedBarcodes = new LinkedHashSet<>();
        if (barcodes != null) {
            for (String barcode : barcodes) {
                String normalizedBarcode = normalize(barcode);
                if (StringUtils.hasText(normalizedBarcode)) {
                    normalizedBarcodes.add(normalizedBarcode);
                }
            }
        }
        if (normalizedBarcodes.isEmpty()) {
            return;
        }

        // A lone value equal to partnerSku is ambiguous: some real Noon Pbarcodes equal the PSKU.
        // Only classify it as an alias when the same payload also supplies another barcode.
        boolean partnerSkuCanBeIdentifiedAsAlias = normalizedBarcodes.size() > 1
                && normalizedBarcodes.contains(normalizedPartnerSku);
        boolean primaryAssigned = false;
        for (String normalizedBarcode : normalizedBarcodes) {
            boolean partnerSkuAlias = partnerSkuCanBeIdentifiedAsAlias
                    && normalizedPartnerSku.equals(normalizedBarcode);
            String barcodeType = partnerSkuAlias ? PARTNER_SKU_ALIAS : NOON_PBARCODE;
            boolean primary = !partnerSkuAlias && !primaryAssigned;
            persistOne(
                    variantId,
                    productMasterId,
                    logicalStoreId,
                    normalizedPartnerSku,
                    normalizedBarcode,
                    barcodeType,
                    primary,
                    updatedBy
            );
            primaryAssigned = primaryAssigned || primary;
        }
    }

    private void persistOne(
            Long variantId,
            Long productMasterId,
            Long logicalStoreId,
            String normalizedPartnerSku,
            String normalizedBarcode,
            String barcodeType,
            boolean primary,
            Long updatedBy
    ) {
        Long activeId = productManagementMapper.selectProductBarcodeIdByBarcode(logicalStoreId, normalizedBarcode);
        Long id = activeId != null ? activeId : productManagementMapper.nextProductBarcodeId();
        productManagementMapper.upsertProductBarcode(
                id,
                variantId,
                productMasterId,
                logicalStoreId,
                normalizedPartnerSku,
                normalizedBarcode,
                barcodeType,
                primary,
                updatedBy
        );
        Long persistedProductMasterId =
                productManagementMapper.selectProductBarcodeProductMasterIdByBarcode(logicalStoreId, normalizedBarcode);
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
