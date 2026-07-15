package com.nuono.next.productlisting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ProductListingValidator {

    private static final int NOON_IMAGE_MIN_WIDTH = 660;
    private static final double NOON_IMAGE_TARGET_ASPECT_RATIO = 0.73D;
    private static final double NOON_IMAGE_ASPECT_RATIO_TOLERANCE = 0.02D;
    private static final Pattern OFFICIAL_NOON_FULLTYPE_CODE =
            Pattern.compile("^[a-z0-9_]+-[a-z0-9_]+-[a-z0-9_]+$");

    public List<ProductListingValidationIssue> validate(ProductListingDraftCommand command) {
        ProductListingDraftCommand safeCommand = command == null ? new ProductListingDraftCommand() : command;
        List<ProductListingValidationIssue> issues = new ArrayList<>();

        for (ProductListingDraftRequirement requirement : ProductListingDraftRequirement.validationRequirements()) {
            validateRequirement(issues, requirement, safeCommand);
        }
        validateProductFullType(issues, safeCommand);
        validatePricingRules(issues, safeCommand);
        validateNoonImageRequirements(issues, safeCommand);

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
        addSourceImageSizeWarnings(issues, safeCommand);
        return issues;
    }

    private void validateRequirement(
            List<ProductListingValidationIssue> issues,
            ProductListingDraftRequirement requirement,
            ProductListingDraftCommand command
    ) {
        Object value = requirement.valueFrom(command);
        if (requirement.getKind() == ProductListingDraftRequirement.Kind.TEXT) {
            requireText(issues, requirement.getFieldKey(), (String) value);
        } else if (requirement.getKind() == ProductListingDraftRequirement.Kind.AMOUNT) {
            requireAmount(issues, requirement.getFieldKey(), (BigDecimal) value);
        } else if (requirement.getKind() == ProductListingDraftRequirement.Kind.OPTIONAL_POSITIVE_INTEGER) {
            requirePositive(issues, requirement.getFieldKey(), (Integer) value);
        }
    }

    private void requireText(List<ProductListingValidationIssue> issues, String fieldKey, String value) {
        if (value == null || value.trim().isEmpty()) {
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

    private void validateProductFullType(List<ProductListingValidationIssue> issues, ProductListingDraftCommand command) {
        String productFullType = text(command.getProductFullType());
        if (productFullType.isEmpty()) {
            return;
        }
        if (!OFFICIAL_NOON_FULLTYPE_CODE.matcher(productFullType).matches()) {
            issues.add(error(
                    "productFullType",
                    "invalid_product_fulltype",
                    "商品 Fulltype 必须选择 Noon 官方类目编码，例如 family-product_type-product_subtype，不能使用展示类目名称。"
            ));
        }
    }

    private void validatePricingRules(List<ProductListingValidationIssue> issues, ProductListingDraftCommand command) {
        BigDecimal price = command.getPrice();
        BigDecimal salePrice = command.getSalePrice();
        BigDecimal priceMin = command.getPriceMin();
        BigDecimal priceMax = command.getPriceMax();

        if (salePrice != null && price != null && salePrice.compareTo(price) >= 0) {
            issues.add(error(
                    "salePrice",
                    "sale_price_must_be_lower_than_price",
                    "Sale price must be lower than base price."
            ));
        }
        validatePriceWithinRange(issues, "price", price, priceMin, priceMax, "price_below_min", "price_above_max");
        validatePriceWithinRange(issues, "salePrice", salePrice, priceMin, priceMax, "sale_price_below_min", "sale_price_above_max");
    }

    private void validatePriceWithinRange(
            List<ProductListingValidationIssue> issues,
            String fieldKey,
            BigDecimal value,
            BigDecimal priceMin,
            BigDecimal priceMax,
            String belowMinCode,
            String aboveMaxCode
    ) {
        if (value == null) {
            return;
        }
        if (priceMin != null && value.compareTo(priceMin) < 0) {
            issues.add(error(fieldKey, belowMinCode, "Price must be between price min and price max."));
        }
        if (priceMax != null && value.compareTo(priceMax) > 0) {
            issues.add(error(fieldKey, aboveMaxCode, "Price must be between price min and price max."));
        }
    }

    private void validateNoonImageRequirements(List<ProductListingValidationIssue> issues, ProductListingDraftCommand command) {
        List<String> imageUrls = command.getImageUrls();
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        Map<String, Map<String, Object>> metadataByUrl = imageMetadataByUrl(command.getImageAssetMetadata());
        int imageIndex = 0;
        for (String imageUrl : imageUrls) {
            String normalizedImageUrl = text(imageUrl);
            if (normalizedImageUrl.isEmpty()) {
                continue;
            }
            imageIndex++;
            Map<String, Object> metadata = metadataByUrl.get(normalizedImageUrl);
            Integer width = positiveInteger(metadata == null ? null : metadata.get("width"));
            Integer height = positiveInteger(metadata == null ? null : metadata.get("height"));
            if (width == null || height == null) {
                issues.add(error(
                        "imageUrls",
                        "noon_image_dimension_missing",
                        "商品图 " + imageIndex + " 缺少尺寸信息，请在图片管理中读取或适配后再上架。"
                ));
                continue;
            }
            if (width < NOON_IMAGE_MIN_WIDTH) {
                issues.add(error(
                        "imageUrls",
                        "noon_image_width_too_small",
                        "商品图 " + imageIndex + " 宽度 " + width + "px，低于 Noon 最低 " + NOON_IMAGE_MIN_WIDTH + "px。"
                ));
            }
            double aspectRatio = (double) width / (double) height;
            if (Math.abs(aspectRatio - NOON_IMAGE_TARGET_ASPECT_RATIO) > NOON_IMAGE_ASPECT_RATIO_TOLERANCE) {
                issues.add(error(
                        "imageUrls",
                        "noon_image_aspect_ratio_mismatch",
                        "商品图 " + imageIndex + " 比例不符合 Noon 0.73，请适配后再上架。"
                ));
            }
        }
    }

    private void addSourceImageSizeWarnings(List<ProductListingValidationIssue> issues, ProductListingDraftCommand command) {
        List<String> imageUrls = command.getImageUrls();
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        Map<String, Map<String, Object>> metadataByUrl = imageMetadataByUrl(command.getImageAssetMetadata());
        int imageIndex = 0;
        for (String imageUrl : imageUrls) {
            String normalizedImageUrl = text(imageUrl);
            if (normalizedImageUrl.isEmpty()) {
                continue;
            }
            imageIndex++;
            Map<String, Object> metadata = metadataByUrl.get(normalizedImageUrl);
            if (metadata != null && Boolean.TRUE.equals(metadata.get("sourceTooSmall"))) {
                issues.add(new ProductListingValidationIssue(
                        "imageUrls",
                        "warning",
                        "noon_image_source_too_small",
                        "商品图 " + imageIndex + " 原图小于 660px，已适配为最低尺寸，请人工确认清晰度。"
                ));
            }
        }
    }

    private Map<String, Map<String, Object>> imageMetadataByUrl(List<Map<String, Object>> metadata) {
        Map<String, Map<String, Object>> byUrl = new HashMap<>();
        if (metadata == null) {
            return byUrl;
        }
        for (Map<String, Object> item : metadata) {
            if (item == null) {
                continue;
            }
            String imageUrl = text(item.get("imageUrl"));
            if (imageUrl.isEmpty()) {
                continue;
            }
            byUrl.put(imageUrl, item);
        }
        return byUrl;
    }

    private Integer positiveInteger(Object value) {
        if (value instanceof Number) {
            int intValue = ((Number) value).intValue();
            return intValue > 0 ? intValue : null;
        }
        String text = text(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            int parsed = (int) Math.round(Double.parseDouble(text));
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private ProductListingValidationIssue required(String fieldKey) {
        return new ProductListingValidationIssue(fieldKey, "error", "required", "Required field is missing.");
    }

    private ProductListingValidationIssue invalidNumber(String fieldKey) {
        return new ProductListingValidationIssue(fieldKey, "error", "invalid_number", "Number must be greater than zero.");
    }

    private ProductListingValidationIssue error(String fieldKey, String code, String message) {
        return new ProductListingValidationIssue(fieldKey, "error", code, message);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
