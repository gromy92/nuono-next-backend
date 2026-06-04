package com.nuono.next.productlisting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductListingValidator {

    public List<ProductListingValidationIssue> validate(ProductListingDraftCommand command) {
        ProductListingDraftCommand safeCommand = command == null ? new ProductListingDraftCommand() : command;
        List<ProductListingValidationIssue> issues = new ArrayList<>();

        requireText(issues, "storeCode", safeCommand.getStoreCode());
        requireText(issues, "psku", safeCommand.getPsku());
        requireLong(issues, "idProductFullType", safeCommand.getIdProductFullType());
        requireText(issues, "productTitleEn", safeCommand.getProductTitleEn());
        requireImages(issues, safeCommand.getImageUrls());
        requireAmount(issues, "price", safeCommand.getPrice());
        requireAmount(issues, "purchasePrice", safeCommand.getPurchasePrice());
        requireText(issues, "supplyEvidenceType", safeCommand.getSupplyEvidenceType());

        return issues;
    }

    private void requireText(List<ProductListingValidationIssue> issues, String fieldKey, String value) {
        if (value == null || value.trim().isEmpty()) {
            issues.add(required(fieldKey));
        }
    }

    private void requireLong(List<ProductListingValidationIssue> issues, String fieldKey, Long value) {
        if (value == null) {
            issues.add(required(fieldKey));
        }
    }

    private void requireAmount(List<ProductListingValidationIssue> issues, String fieldKey, BigDecimal value) {
        if (value == null) {
            issues.add(required(fieldKey));
        }
    }

    private void requireImages(List<ProductListingValidationIssue> issues, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.stream().noneMatch(this::hasText)) {
            issues.add(required("imageUrls"));
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private ProductListingValidationIssue required(String fieldKey) {
        return new ProductListingValidationIssue(fieldKey, "error", "required", "Required field is missing.");
    }
}
