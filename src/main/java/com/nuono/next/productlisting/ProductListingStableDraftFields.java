package com.nuono.next.productlisting;

import org.springframework.util.StringUtils;

final class ProductListingStableDraftFields {

    private ProductListingStableDraftFields() {
    }

    static void preserve(
            ProductListingDraftCommand command,
            ProductListingDraftCommand previous
    ) {
        if (command == null || previous == null) {
            return;
        }
        boolean incomingHasProductFullType =
                StringUtils.hasText(command.getProductFullType());
        if (incomingHasProductFullType) {
            command.setIdProductFullType(null);
            if (looksLikeProductFullTypeCode(
                    command.getProductFullType())) {
                command.setFamily(null);
                command.setProductType(null);
                command.setProductSubType(null);
            }
        } else {
            command.setProductFullType(previous.getProductFullType());
            boolean sameProductFullType = sameText(
                    command.getProductFullType(),
                    previous.getProductFullType()
            );
            if (sameProductFullType
                    && command.getIdProductFullType() == null) {
                command.setIdProductFullType(
                        previous.getIdProductFullType()
                );
            }
            if (sameProductFullType
                    && !StringUtils.hasText(command.getFamily())) {
                command.setFamily(previous.getFamily());
            }
            if (sameProductFullType
                    && !StringUtils.hasText(command.getProductType())) {
                command.setProductType(previous.getProductType());
            }
            if (sameProductFullType
                    && !StringUtils.hasText(command.getProductSubType())) {
                command.setProductSubType(previous.getProductSubType());
            }
        }
        if (!StringUtils.hasText(command.getProductBrand())) {
            command.setProductBrand(previous.getProductBrand());
        }
        if (!StringUtils.hasText(command.getProductBrandCode())) {
            command.setProductBrandCode(previous.getProductBrandCode());
        }
    }

    private static boolean sameText(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private static boolean looksLikeProductFullTypeCode(String value) {
        return StringUtils.hasText(value)
                && value.contains("-")
                && value.contains("_");
    }
}
