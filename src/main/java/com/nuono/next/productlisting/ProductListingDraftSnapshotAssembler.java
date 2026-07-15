package com.nuono.next.productlisting;

import com.nuono.next.product.ProductMasterSnapshotView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

class ProductListingDraftSnapshotAssembler {

    ProductMasterSnapshotView toDraftSnapshot(
            ProductListingDraftRecord record,
            ProductListingDraftCommand draft,
            ProductListingStoreProjectionContext storeContext,
            String partnerSku
    ) {
        ProductListingDraftProjectionFields fields = ProductListingDraftProjectionFields.from(draft, partnerSku);
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setMode("listing-draft");
        snapshot.setReady(true);
        snapshot.setMessage("本地上架草稿已保存，可继续编辑后发布。");

        Map<String, Object> context = new LinkedHashMap<>();
        putIfNotNull(context, "ownerUserId", record.getOwnerUserId());
        putIfNotBlank(context, "projectCode", storeContext.getProjectCode());
        putIfNotBlank(context, "projectName", storeContext.getProjectName());
        putIfNotBlank(context, "storeCode", record.getStoreCode());
        putIfNotBlank(context, "site", storeContext.getSite());
        snapshot.setStoreContext(context);

        Map<String, Object> identity = new LinkedHashMap<>();
        putIfNotBlank(identity, "skuParent", localDraftSkuParent(partnerSku));
        putIfNotBlank(identity, "partnerSku", fields.partnerSku());
        putIfNotBlank(identity, "childSku", fields.partnerSku());
        putIfNotBlank(identity, "productSourceType", fields.productSourceType());
        putIfNotBlank(identity, "brand", fields.brand());
        putIfNotBlank(identity, "brandCode", fields.brandCode());
        putIfNotBlank(identity, "barcode", fields.barcode());
        putIfNotNull(identity, "variantCount", 1);
        putIfNotBlank(identity, "listingDraftNo", record.getDraftNo());
        putIfNotNull(identity, "listingDraftId", record.getId());
        snapshot.setIdentity(identity);

        Map<String, Object> taxonomy = new LinkedHashMap<>();
        putIfNotNull(taxonomy, "idProductFullType", fields.idProductFullType());
        putIfNotBlank(taxonomy, "productFulltype", fields.productFullType());
        putIfNotBlank(taxonomy, "family", fields.family());
        putIfNotBlank(taxonomy, "productType", fields.productType());
        putIfNotBlank(taxonomy, "productSubtype", fields.productSubType());
        snapshot.setTaxonomy(taxonomy);

        Map<String, Object> content = new LinkedHashMap<>();
        putIfNotBlank(content, "titleCn", fields.titleCn());
        putIfNotBlank(content, "titleZh", fields.titleCn());
        putIfNotBlank(content, "titleEn", fields.titleEn());
        putIfNotBlank(content, "titleAr", fields.titleAr());
        putIfNotBlank(content, "descriptionCn", fields.descriptionCn());
        putIfNotBlank(content, "descriptionEn", fields.descriptionEn());
        putIfNotBlank(content, "descriptionAr", fields.descriptionAr());
        putIfNotNull(content, "highlightsCn", fields.highlightsCn());
        putIfNotNull(content, "highlightsEn", fields.highlightsEn());
        putIfNotNull(content, "highlightsAr", fields.highlightsAr());
        putIfNotNull(content, "images", fields.imageUrls());
        snapshot.setContent(content);

        if (draft.getKeyAttributes() != null) {
            snapshot.setKeyAttributes(new ArrayList<>(draft.getKeyAttributes()));
        }

        Map<String, Object> variant = new LinkedHashMap<>();
        putIfNotBlank(variant, "partnerSku", fields.partnerSku());
        putIfNotBlank(variant, "childSku", fields.partnerSku());
        putIfNotBlank(variant, "sizeEn", fields.sizeEn());
        putIfNotBlank(variant, "sizeAr", fields.sizeAr());
        putIfNotBlank(variant, "displaySize", fields.displaySize());
        putIfNotBlank(variant, "barcode", fields.barcode());
        putIfNotNull(variant, "isActive", fields.isActive());
        snapshot.setVariants(List.of(variant));

        Map<String, Object> pricing = new LinkedHashMap<>();
        putIfNotBlank(pricing, "partnerSku", fields.partnerSku());
        putIfNotBlank(pricing, "currency", currencyForSite(storeContext.getSite()));
        putIfNotNull(pricing, "price", fields.priceText());
        putIfNotNull(pricing, "salePrice", fields.salePriceText());
        putIfNotNull(pricing, "priceMin", fields.priceMinText());
        putIfNotNull(pricing, "priceMax", fields.priceMaxText());
        putIfNotNull(pricing, "purchasePrice", fields.purchasePriceText());
        putIfNotBlank(pricing, "saleStart", fields.saleStart());
        putIfNotBlank(pricing, "saleEnd", fields.saleEnd());
        snapshot.setPricing(pricing);

        Map<String, Object> stock = new LinkedHashMap<>();
        putIfNotNull(stock, "fbp", fields.fbp());
        putIfNotBlank(stock, "warehouseId", fields.warehouseId());
        putIfNotBlank(stock, "warehouseCode", fields.warehouseCode());
        putIfNotNull(stock, "quantity", fields.quantity());
        snapshot.setStock(stock);

        Map<String, Object> siteOffer = new LinkedHashMap<>();
        putIfNotBlank(siteOffer, "storeCode", record.getStoreCode());
        putIfNotBlank(siteOffer, "site", storeContext.getSite());
        putIfNotBlank(siteOffer, "partnerSku", fields.partnerSku());
        putIfNotBlank(siteOffer, "childSku", fields.partnerSku());
        putIfNotBlank(siteOffer, "currency", currencyForSite(storeContext.getSite()));
        putIfNotNull(siteOffer, "price", fields.priceText());
        putIfNotNull(siteOffer, "salePrice", fields.salePriceText());
        putIfNotNull(siteOffer, "priceMin", fields.priceMinText());
        putIfNotNull(siteOffer, "priceMax", fields.priceMaxText());
        putIfNotBlank(siteOffer, "saleStart", fields.saleStart());
        putIfNotBlank(siteOffer, "saleEnd", fields.saleEnd());
        putIfNotNull(siteOffer, "idWarranty", fields.idWarranty());
        putIfNotBlank(siteOffer, "offerNote", fields.offerNote());
        putIfNotNull(siteOffer, "isActive", fields.isActive());
        putIfNotBlank(siteOffer, "statusCode", "listing_draft");
        putIfNotBlank(siteOffer, "liveStatus", "draft");
        if (Boolean.TRUE.equals(fields.fbp())) {
            putIfNotNull(siteOffer, "fbpStock", fields.quantity());
        }
        snapshot.setSiteOffers(List.of(siteOffer));
        return snapshot;
    }

    String currencyForSite(String site) {
        String normalizedSite = normalize(site);
        if ("AE".equalsIgnoreCase(normalizedSite)) {
            return "AED";
        }
        if ("SA".equalsIgnoreCase(normalizedSite)) {
            return "SAR";
        }
        return null;
    }

    private String localDraftSkuParent(String partnerSku) {
        String sanitized = partnerSku.replaceAll("[^A-Za-z0-9_-]", "-");
        if (sanitized.length() > 70) {
            sanitized = sanitized.substring(0, 70);
        }
        String hash = Integer.toHexString(partnerSku.hashCode()).toUpperCase(Locale.ROOT);
        return "LOCAL-" + sanitized + "-" + hash;
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
