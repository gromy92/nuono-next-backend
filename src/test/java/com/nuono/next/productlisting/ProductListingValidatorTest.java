package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingValidatorTest {

    private final ProductListingValidator validator = new ProductListingValidator();

    @Test
    void doesNotRequirePurchaseOrderForReadyDraft() {
        ProductListingDraftCommand command = validCommand();
        command.setOptionalPurchaseOrderId(null);

        assertTrue(validator.validate(command).isEmpty());
    }

    @Test
    void blocksMissingCostAndSupplyEvidence() {
        ProductListingDraftCommand command = validCommand();
        command.setPurchasePrice(null);
        command.setSupplyEvidenceType(null);

        List<ProductListingValidationIssue> issues = validator.validate(command);

        assertIssue(issues, "purchasePrice", "required");
        assertIssue(issues, "supplyEvidenceType", "required");
    }

    @Test
    void blocksMissingNoonListingFields() {
        ProductListingDraftCommand command = new ProductListingDraftCommand();

        List<ProductListingValidationIssue> issues = validator.validate(command);

        assertIssue(issues, "storeCode", "required");
        assertIssue(issues, "psku", "required");
        assertIssue(issues, "idProductFullType", "required");
        assertIssue(issues, "productTitleEn", "required");
        assertIssue(issues, "imageUrls", "required");
        assertIssue(issues, "price", "required");
    }

    @Test
    void blocksZeroOrNegativeNumbers() {
        ProductListingDraftCommand command = validCommand();
        command.setPrice(BigDecimal.ZERO);
        command.setPurchasePrice(new BigDecimal("-1.00"));
        command.setQuantity(0);

        List<ProductListingValidationIssue> issues = validator.validate(command);

        assertIssue(issues, "price", "invalid_number");
        assertIssue(issues, "purchasePrice", "invalid_number");
        assertIssue(issues, "quantity", "invalid_number");
    }

    private ProductListingDraftCommand validCommand() {
        ProductListingDraftCommand command = new ProductListingDraftCommand();
        command.setStoreCode("STR240053-NSA");
        command.setPsku("NN-TEST-PSKU");
        command.setIdProductFullType(3066L);
        command.setProductTitleEn("Wired headphones with microphone");
        command.setImageUrls(List.of("https://example.test/images/sku-main.jpg"));
        command.setPrice(new BigDecimal("49.90"));
        command.setPurchasePrice(new BigDecimal("19.90"));
        command.setSupplyEvidenceType("1688_OFFER");
        command.setOptionalPurchaseOrderId(70001L);
        command.setQuantity(100);
        return command;
    }

    private void assertIssue(List<ProductListingValidationIssue> issues, String fieldKey, String code) {
        assertTrue(
                issues.stream().anyMatch(issue ->
                        fieldKey.equals(issue.getFieldKey()) && code.equals(issue.getCode())),
                "Expected issue " + fieldKey + "/" + code + " in " + describe(issues)
        );
    }

    private String describe(List<ProductListingValidationIssue> issues) {
        StringBuilder builder = new StringBuilder();
        for (ProductListingValidationIssue issue : issues) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(issue.getFieldKey()).append("/").append(issue.getCode());
        }
        return builder.toString();
    }
}
