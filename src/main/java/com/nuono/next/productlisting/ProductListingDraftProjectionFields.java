package com.nuono.next.productlisting;

import com.nuono.next.product.ProductImageRole;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

class ProductListingDraftProjectionFields {

    private final ProductListingDraftCommand draft;
    private final String partnerSku;
    private final String brand;
    private final String barcode;
    private final String sizeEn;
    private final String sizeAr;
    private final String displaySize;
    private final String priceText;
    private final String salePriceText;
    private final String finalPriceText;
    private final String finalPriceSource;
    private final String coverImageUrl;

    private ProductListingDraftProjectionFields(ProductListingDraftCommand draft, String partnerSku) {
        this.draft = draft == null ? new ProductListingDraftCommand() : draft;
        this.partnerSku = normalize(partnerSku);
        this.brand = firstNonBlank(this.draft.getProductBrand(), this.draft.getProductBrandCode());
        this.barcode = firstNonBlank(this.draft.getBarcode(), barcodeFromAttributes(this.draft.getKeyAttributes()));
        this.sizeEn = firstNonBlank(this.draft.getSizeEn(), "Default");
        this.sizeAr = normalize(this.draft.getSizeAr());
        this.displaySize = firstNonBlank(this.draft.getSizeEn(), this.draft.getSizeAr(), "Default");
        this.priceText = decimalText(this.draft.getPrice());
        this.salePriceText = decimalText(this.draft.getSalePrice());
        this.finalPriceText = firstNonBlank(salePriceText, priceText);
        this.finalPriceSource = this.draft.getSalePrice() == null ? "listing_price" : "listing_sale_price";
        this.coverImageUrl = firstImage(this.draft.getImageUrls());
    }

    static ProductListingDraftProjectionFields from(ProductListingDraftCommand draft, String partnerSku) {
        return new ProductListingDraftProjectionFields(draft, partnerSku);
    }

    String partnerSku() {
        return partnerSku;
    }

    String productSourceType() {
        return "SELF_BUILT";
    }

    String brand() {
        return brand;
    }

    String brandCode() {
        return normalize(draft.getProductBrandCode());
    }

    String barcode() {
        return barcode;
    }

    Long idProductFullType() {
        return draft.getIdProductFullType();
    }

    String productFullType() {
        return normalize(draft.getProductFullType());
    }

    String family() {
        return normalize(draft.getFamily());
    }

    String productType() {
        return normalize(draft.getProductType());
    }

    String productSubType() {
        return normalize(draft.getProductSubType());
    }

    String titleCn() {
        return normalize(draft.getProductTitleCn());
    }

    String titleEn() {
        return normalize(draft.getProductTitleEn());
    }

    String titleAr() {
        return normalize(draft.getProductTitleAr());
    }

    String descriptionCn() {
        return normalize(draft.getProductDescriptionCn());
    }

    String descriptionEn() {
        return normalize(draft.getProductDescriptionEn());
    }

    String descriptionAr() {
        return normalize(draft.getProductDescriptionAr());
    }

    List<String> highlightsCn() {
        return draft.getProductHighlightsCn();
    }

    List<String> highlightsEn() {
        return draft.getProductHighlightsEn();
    }

    List<String> highlightsAr() {
        return draft.getProductHighlightsAr();
    }

    List<String> imageUrls() {
        return draft.getImageUrls();
    }

    List<ProductListingImageRoleAssignment> imageRoleAssignments() {
        List<String> images = normalizedImageUrls();
        if (images.isEmpty()) {
            return List.of();
        }
        Map<String, ProductImageRole> requestedRoles = new LinkedHashMap<>();
        if (draft.getImageRoleAssignments() != null) {
            for (ProductListingImageRoleAssignment assignment : draft.getImageRoleAssignments()) {
                if (assignment == null) {
                    continue;
                }
                String imageUrl = normalize(assignment.getImageUrl());
                ProductImageRole role = normalizeImageRole(assignment.getImageRole());
                if (StringUtils.hasText(imageUrl) && role != null) {
                    requestedRoles.put(imageUrl, role);
                }
            }
        }
        List<ProductListingImageRoleAssignment> normalizedAssignments = new ArrayList<>();
        for (int index = 0; index < images.size(); index++) {
            String imageUrl = images.get(index);
            ProductImageRole role = requestedRoles.get(imageUrl);
            if (index == 0) {
                role = ProductImageRole.MAIN;
            } else if (role == null || role == ProductImageRole.MAIN) {
                role = ProductImageRole.DETAIL;
            }
            ProductListingImageRoleAssignment assignment = new ProductListingImageRoleAssignment();
            assignment.setImageUrl(imageUrl);
            assignment.setImageRole(role);
            assignment.setSortOrder(index);
            normalizedAssignments.add(assignment);
        }
        return normalizedAssignments;
    }

    String coverImageUrl() {
        return coverImageUrl;
    }

    String sizeEn() {
        return sizeEn;
    }

    String sizeAr() {
        return sizeAr;
    }

    String displaySize() {
        return displaySize;
    }

    String priceText() {
        return priceText;
    }

    String salePriceText() {
        return salePriceText;
    }

    String priceMinText() {
        return decimalText(draft.getPriceMin());
    }

    String priceMaxText() {
        return decimalText(draft.getPriceMax());
    }

    String purchasePriceText() {
        return decimalText(draft.getPurchasePrice());
    }

    String finalPriceText() {
        return finalPriceText;
    }

    String finalPriceSource() {
        return finalPriceSource;
    }

    String saleStart() {
        return normalize(draft.getSaleStart());
    }

    String saleEnd() {
        return normalize(draft.getSaleEnd());
    }

    Boolean fbp() {
        return draft.getFbp();
    }

    String warehouseId() {
        return normalize(draft.getWarehouseId());
    }

    String warehouseCode() {
        return normalize(draft.getWarehouseCode());
    }

    Integer quantity() {
        return draft.getQuantity();
    }

    Integer idWarranty() {
        return draft.getIdWarranty();
    }

    String offerNote() {
        return normalize(draft.getOfferNote());
    }

    Boolean isActive() {
        return draft.getIsActive();
    }

    private static String barcodeFromAttributes(List<Map<String, Object>> keyAttributes) {
        if (keyAttributes == null) {
            return null;
        }
        for (Map<String, Object> attribute : keyAttributes) {
            if (attribute == null) {
                continue;
            }
            String code = normalize(String.valueOf(attribute.get("code")));
            if (!StringUtils.hasText(code) || !code.toLowerCase(Locale.ROOT).contains("barcode")) {
                continue;
            }
            String value = firstNonBlank(
                    text(attribute.get("commonValue")),
                    text(attribute.get("enValue")),
                    text(attribute.get("arValue"))
            );
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String firstImage(List<String> imageUrls) {
        if (imageUrls == null) {
            return null;
        }
        for (String imageUrl : imageUrls) {
            if (StringUtils.hasText(imageUrl)) {
                return imageUrl.trim();
            }
        }
        return null;
    }

    private List<String> normalizedImageUrls() {
        List<String> imageUrls = draft.getImageUrls();
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            String value = normalize(imageUrl);
            if (StringUtils.hasText(value) && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private static ProductImageRole normalizeImageRole(ProductImageRole role) {
        if (role == ProductImageRole.MAIN
                || role == ProductImageRole.SIZE
                || role == ProductImageRole.DETAIL
                || role == ProductImageRole.PACKAGE) {
            return role;
        }
        return null;
    }

    private static String decimalText(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
