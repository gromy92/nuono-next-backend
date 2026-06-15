package com.nuono.next.productlisting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
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
        requirePositive(issues, "quantity", safeCommand.getQuantity());

        return issues;
    }

    public List<ProductListingValidationIssue> validateWithWarnings(ProductListingDraftCommand command) {
        List<ProductListingValidationIssue> issues = validate(command);
        ProductListingDraftCommand safeCommand = command == null ? new ProductListingDraftCommand() : command;
        if (safeCommand.getOptionalPurchaseOrderId() == null) {
            issues.add(new ProductListingValidationIssue(
                    "optionalPurchaseOrderId",
                    "warning",
                    "purchase_order_not_linked",
                    "Purchase order is not linked."
            ));
        }
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
        } else if (value.compareTo(BigDecimal.ZERO) <= 0) {
            issues.add(invalidNumber(fieldKey));
        }
    }

    private void requirePositive(List<ProductListingValidationIssue> issues, String fieldKey, Integer value) {
        if (value != null && value <= 0) {
            issues.add(invalidNumber(fieldKey));
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

    private ProductListingValidationIssue invalidNumber(String fieldKey) {
        return new ProductListingValidationIssue(fieldKey, "error", "invalid_number", "Number must be greater than zero.");
    }
}
