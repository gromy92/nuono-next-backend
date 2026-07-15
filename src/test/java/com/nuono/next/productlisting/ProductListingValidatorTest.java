package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        assertIssue(issues, "productFullType", "required");
        assertIssue(issues, "productTitleEn", "required");
        assertNoIssue(issues, "imageUrls", "required");
        assertIssue(issues, "price", "required");
    }

    @Test
    void blocksDisplayCategoryNameAsProductFulltype() {
        ProductListingDraftCommand command = validCommand();
        command.setProductFullType("Kitchen Utensils & Gadgets");

        List<ProductListingValidationIssue> issues = validator.validate(command);

        assertIssue(issues, "productFullType", "invalid_product_fulltype");
    }

    @Test
    void acceptsOfficialNoonProductFulltypeCode() {
        ProductListingDraftCommand command = validCommand();
        command.setProductFullType("kitchen-utensils_gadgets-kitchen_tools");

        List<ProductListingValidationIssue> issues = validator.validate(command);

        assertNoIssue(issues, "productFullType", "invalid_product_fulltype");
    }

    @Test
    void imagesAreOptionalForListingUpload() {
        ProductListingDraftCommand command = validCommand();
        command.setImageUrls(List.of());

        List<ProductListingValidationIssue> issues = validator.validate(command);

        assertNoIssue(issues, "imageUrls", "required");
    }

    @Test
    void blocksActiveImagesWithoutDimensionMetadata() {
        ProductListingDraftCommand command = validCommand();
        clearImageAssetMetadataIfSupported(command);

        List<ProductListingValidationIssue> issues = validator.validate(command);

        assertIssue(issues, "imageUrls", "noon_image_dimension_missing");
    }

    @Test
    void blocksActiveImagesBelowNoonMinimumWidth() {
        ProductListingDraftCommand command = validCommand();
        setImageAssetMetadata(command, List.of(Map.of(
                "imageUrl", "https://example.test/images/sku-main.jpg",
                "width", 640,
                "height", 877
        )));

        List<ProductListingValidationIssue> issues = validator.validate(command);

        assertIssue(issues, "imageUrls", "noon_image_width_too_small");
    }

    @Test
    void blocksActiveImagesOutsideNoonAspectRatio() {
        ProductListingDraftCommand command = validCommand();
        setImageAssetMetadata(command, List.of(Map.of(
                "imageUrl", "https://example.test/images/sku-main.jpg",
                "width", 660,
                "height", 760
        )));

        List<ProductListingValidationIssue> issues = validator.validate(command);

        assertIssue(issues, "imageUrls", "noon_image_aspect_ratio_mismatch");
    }

    @Test
    void ignoresImageMetadataThatIsNotUsedForListing() {
        ProductListingDraftCommand command = validCommand();
        command.setImageUrls(List.of("https://example.test/images/sku-main.jpg"));
        setImageAssetMetadataIfSupported(command, List.of(
                Map.of(
                        "imageUrl", "https://example.test/images/sku-main.jpg",
                        "width", 1247,
                        "height", 1706
                ),
                Map.of(
                        "imageUrl", "https://example.test/images/unused.jpg",
                        "width", 320,
                        "height", 320
                )
        ));

        List<ProductListingValidationIssue> issues = validator.validate(command);

        assertNoIssue(issues, "imageUrls", "noon_image_width_too_small");
        assertNoIssue(issues, "imageUrls", "noon_image_aspect_ratio_mismatch");
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

    @Test
    void blocksSalePriceThatIsNotLowerThanBasePrice() {
        ProductListingDraftCommand command = validCommand();
        command.setPrice(new BigDecimal("59.99"));
        command.setSalePrice(new BigDecimal("59.99"));

        List<ProductListingValidationIssue> issues = validator.validate(command);

        assertIssue(issues, "salePrice", "sale_price_must_be_lower_than_price");
    }

    @Test
    void blocksBasePriceOutsideAllowedRange() {
        ProductListingDraftCommand command = validCommand();
        command.setPrice(new BigDecimal("60.01"));
        command.setPriceMin(new BigDecimal("20.00"));
        command.setPriceMax(new BigDecimal("60.00"));

        List<ProductListingValidationIssue> issues = validator.validate(command);

        assertIssue(issues, "price", "price_above_max");
    }

    @Test
    void blocksSalePriceOutsideAllowedRange() {
        ProductListingDraftCommand command = validCommand();
        command.setPrice(new BigDecimal("59.99"));
        command.setSalePrice(new BigDecimal("19.99"));
        command.setPriceMin(new BigDecimal("20.00"));
        command.setPriceMax(new BigDecimal("60.00"));

        List<ProductListingValidationIssue> issues = validator.validate(command);

        assertIssue(issues, "salePrice", "sale_price_below_min");
    }

    @Test
    void validateWithWarningsDoesNotDecideRuntimeNoonWriteCapability() {
        ProductListingDraftCommand command = validCommand();
        command.setSalePrice(new BigDecimal("47.50"));
        command.setSaleStart("2026-07-01");
        command.setSaleEnd("2026-07-07");
        command.setOfferNote("Launch quantity prepared by operator.");

        List<ProductListingValidationIssue> issues = validator.validateWithWarnings(command);

        assertTrue(issues.stream().noneMatch(issue -> "offer_stock_not_written".equals(issue.getCode())),
                "Runtime Noon write warnings should be added by ProductListingService with real-write properties.");
    }

    @Test
    void requiredFieldsAreDeclaredAsReusableListingCompletenessContract() {
        Set<String> requiredFieldKeys = ProductListingDraftRequirement.requiredFieldKeys();

        assertTrue(requiredFieldKeys.containsAll(List.of(
                "storeCode",
                "psku",
                "productFullType",
                "productTitleEn",
                "price",
                "purchasePrice",
                "supplyEvidenceType"
        )));
        assertTrue(ProductListingDraftRequirement.optionalPositiveFieldKeys().contains("quantity"));
    }

    private ProductListingDraftCommand validCommand() {
        ProductListingDraftCommand command = new ProductListingDraftCommand();
        command.setStoreCode("STR240053-NSA");
        command.setPsku("NN-TEST-PSKU");
        command.setProductFullType("electronic_accessories-headphones-wired_headphones");
        command.setProductTitleEn("Wired headphones with microphone");
        command.setImageUrls(List.of("https://example.test/images/sku-main.jpg"));
        command.setPrice(new BigDecimal("49.90"));
        command.setPurchasePrice(new BigDecimal("19.90"));
        command.setSupplyEvidenceType("1688_OFFER");
        command.setOptionalPurchaseOrderId(70001L);
        command.setQuantity(100);
        setImageAssetMetadataIfSupported(command, List.of(Map.of(
                "imageUrl", "https://example.test/images/sku-main.jpg",
                "width", 1247,
                "height", 1706
        )));
        return command;
    }

    private void assertIssue(List<ProductListingValidationIssue> issues, String fieldKey, String code) {
        assertTrue(
                issues.stream().anyMatch(issue ->
                        fieldKey.equals(issue.getFieldKey()) && code.equals(issue.getCode())),
                "Expected issue " + fieldKey + "/" + code + " in " + describe(issues)
        );
    }

    private void assertIssue(List<ProductListingValidationIssue> issues, String fieldKey, String code, String severity) {
        assertTrue(
                issues.stream().anyMatch(issue ->
                        fieldKey.equals(issue.getFieldKey())
                                && code.equals(issue.getCode())
                                && severity.equals(issue.getSeverity())),
                "Expected issue " + fieldKey + "/" + code + "/" + severity + " in " + describe(issues)
        );
    }

    private void assertNoIssue(List<ProductListingValidationIssue> issues, String fieldKey, String code) {
        assertTrue(
                issues.stream().noneMatch(issue ->
                        fieldKey.equals(issue.getFieldKey()) && code.equals(issue.getCode())),
                "Did not expect issue " + fieldKey + "/" + code + " in " + describe(issues)
        );
    }

    @SuppressWarnings("unchecked")
    private void setImageAssetMetadata(ProductListingDraftCommand command, List<Map<String, Object>> metadata) {
        try {
            ProductListingDraftCommand.class
                    .getMethod("setImageAssetMetadata", List.class)
                    .invoke(command, metadata);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("ProductListingDraftCommand should accept image asset metadata", e);
        }
    }

    private void setImageAssetMetadataIfSupported(ProductListingDraftCommand command, List<Map<String, Object>> metadata) {
        try {
            ProductListingDraftCommand.class
                    .getMethod("setImageAssetMetadata", List.class)
                    .invoke(command, metadata);
        } catch (NoSuchMethodException ignored) {
            // Red phase: the command does not support image metadata yet.
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("ProductListingDraftCommand should accept image asset metadata", e);
        }
    }

    private void clearImageAssetMetadataIfSupported(ProductListingDraftCommand command) {
        setImageAssetMetadataIfSupported(command, null);
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
