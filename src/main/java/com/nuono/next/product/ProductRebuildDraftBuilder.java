package com.nuono.next.product;

import com.nuono.next.productlisting.ProductListingDraftCommand;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

class ProductRebuildDraftBuilder {

    ProductListingDraftCommand build(
            Long sourceProductMasterId,
            String storeCode,
            ProductMasterSnapshotView snapshot
    ) {
        if (snapshot == null || !snapshot.isReady()) {
            throw new IllegalArgumentException("当前商品详情基线不可用，暂时不能重建。");
        }
        String productSourceType = text(snapshot.getIdentity().get("productSourceType"));
        if (!"SELF_BUILT".equalsIgnoreCase(productSourceType)) {
            throw new IllegalArgumentException("商品重建当前只支持自建品 SELF_BUILT。");
        }
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setStoreCode(normalize(storeCode));
        draft.setSourceType("PRODUCT_REBUILD");
        draft.setSourceRefId(sourceProductMasterId);
        draft.setRebuildSourceProductMasterId(sourceProductMasterId);
        draft.setPsku(text(snapshot.getIdentity().get("partnerSku")));
        draft.setIdProductFullType(longValue(snapshot.getTaxonomy().get("idProductFullType")));
        draft.setProductFullType(text(snapshot.getTaxonomy().get("productFulltype")));
        draft.setFamily(text(snapshot.getTaxonomy().get("family")));
        draft.setProductType(text(snapshot.getTaxonomy().get("productType")));
        draft.setProductSubType(text(snapshot.getTaxonomy().get("productSubtype")));
        draft.setProductBrand(text(snapshot.getIdentity().get("brand")));
        draft.setProductBrandCode(text(snapshot.getIdentity().get("brandCode")));
        draft.setProductTitleCn(firstNonBlank(
                text(snapshot.getContent().get("titleCn")),
                text(snapshot.getContent().get("titleZh"))
        ));
        draft.setProductTitleEn(text(snapshot.getContent().get("titleEn")));
        draft.setProductTitleAr(text(snapshot.getContent().get("titleAr")));
        draft.setProductDescriptionCn(firstNonBlank(
                text(snapshot.getContent().get("descriptionCn")),
                text(snapshot.getContent().get("descriptionZh"))
        ));
        draft.setProductDescriptionEn(text(snapshot.getContent().get("descriptionEn")));
        draft.setProductDescriptionAr(text(snapshot.getContent().get("descriptionAr")));
        draft.setProductHighlightsCn(stringList(firstNonNull(
                snapshot.getContent().get("highlightsCn"),
                snapshot.getContent().get("highlightsZh")
        )));
        draft.setProductHighlightsEn(stringList(snapshot.getContent().get("highlightsEn")));
        draft.setProductHighlightsAr(stringList(snapshot.getContent().get("highlightsAr")));
        draft.setImageUrls(listingImageUrls(snapshot.getContent().get("images")));
        draft.setKeyAttributes(snapshot.getKeyAttributes());

        Map<String, Object> siteOffer = selectSiteOffer(snapshot, storeCode);
        draft.setPrice(decimal(firstNonNull(siteOffer.get("price"), snapshot.getPricing().get("price"))));
        draft.setPriceMin(decimal(siteOffer.get("priceMin")));
        draft.setPriceMax(decimal(siteOffer.get("priceMax")));
        draft.setSalePrice(decimal(firstNonNull(siteOffer.get("salePrice"), snapshot.getPricing().get("salePrice"))));
        draft.setSaleStart(text(siteOffer.get("saleStart")));
        draft.setSaleEnd(text(siteOffer.get("saleEnd")));
        draft.setPurchasePrice(decimal(firstNonNull(
                snapshot.getPricing().get("purchasePrice"),
                siteOffer.get("purchasePrice"),
                draft.getPrice()
        )));
        draft.setSupplyEvidenceType(firstNonBlank(
                text(snapshot.getStock().get("supplyEvidenceType")),
                text(snapshot.getPricing().get("supplyEvidenceType")),
                text(siteOffer.get("supplyEvidenceType")),
                "PRODUCT_REBUILD"
        ));
        draft.setSupplyEvidenceRefId(longValue(firstNonNull(
                snapshot.getStock().get("supplyEvidenceRefId"),
                snapshot.getPricing().get("supplyEvidenceRefId"),
                siteOffer.get("supplyEvidenceRefId")
        )));
        draft.setOptionalPurchaseOrderId(longValue(firstNonNull(
                snapshot.getStock().get("optionalPurchaseOrderId"),
                snapshot.getPricing().get("optionalPurchaseOrderId"),
                siteOffer.get("optionalPurchaseOrderId")
        )));
        draft.setIdWarranty(intValue(firstNonNull(siteOffer.get("idWarranty"), snapshot.getPricing().get("idWarranty"))));
        draft.setIsActive(booleanValue(siteOffer.get("isActive")));
        draft.setOfferNote(text(siteOffer.get("offerNote")));
        draft.setBarcode(firstNonBlank(
                text(snapshot.getPricing().get("barcode")),
                barcodeFromAttributes(snapshot.getKeyAttributes())
        ));
        draft.setInheritedListingStartedAt(text(siteOffer.get("listingStartedAt")));
        draft.setInheritedListingStartedSource(stripRebuildInheritedPrefix(text(siteOffer.get("listingStartedSource"))));
        return draft;
    }

    private Map<String, Object> selectSiteOffer(ProductMasterSnapshotView snapshot, String storeCode) {
        String normalizedStoreCode = normalize(storeCode);
        if (snapshot.getSiteOffers() != null) {
            for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
                if (siteOffer != null && normalizedStoreCode.equals(normalize(text(siteOffer.get("storeCode"))))) {
                    return siteOffer;
                }
            }
            for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
                if (siteOffer != null) {
                    return siteOffer;
                }
            }
        }
        return Map.of();
    }

    private String barcodeFromAttributes(List<Map<String, Object>> keyAttributes) {
        if (keyAttributes == null) {
            return null;
        }
        for (Map<String, Object> attribute : keyAttributes) {
            if (attribute == null || !"barcode".equalsIgnoreCase(text(attribute.get("code")))) {
                continue;
            }
            String value = firstNonBlank(
                    text(attribute.get("commonValue")),
                    text(attribute.get("enValue"))
            );
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String stripRebuildInheritedPrefix(String source) {
        String normalized = normalize(source);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String prefix = "product_rebuild_inherited:";
        if (normalized.toLowerCase().startsWith(prefix)) {
            return normalized.substring(prefix.length());
        }
        return normalized;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?>)) {
            return List.of();
        }
        return ((List<?>) value).stream()
                .map(this::text)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private List<String> listingImageUrls(Object value) {
        return stringList(value).stream()
                .filter(this::isDownloadableImageUrl)
                .collect(Collectors.toList());
    }

    private boolean isDownloadableImageUrl(String value) {
        String normalized = normalize(value);
        return StringUtils.hasText(normalized)
                && (normalized.startsWith("https://") || normalized.startsWith("http://"));
    }

    private BigDecimal decimal(Object value) {
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return new BigDecimal(text.replace(",", ""));
    }

    private Long longValue(Object value) {
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return Long.parseLong(text);
    }

    private Integer intValue(Object value) {
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return Integer.parseInt(text);
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return Boolean.parseBoolean(text);
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String normalize(String value) {
        String text = text(value);
        return text == null ? null : text.trim();
    }
}
