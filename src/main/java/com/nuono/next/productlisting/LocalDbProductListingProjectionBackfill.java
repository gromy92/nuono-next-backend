package com.nuono.next.productlisting;

import com.nuono.next.infrastructure.mapper.ProductListingProjectionMapper;
import com.nuono.next.product.ProductMasterSnapshotView;
import com.nuono.next.product.ProductProjectionPersistenceService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbProductListingProjectionBackfill implements ProductListingProjectionBackfill {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductProjectionPersistenceService projectionPersistenceService;
    private final ProductListingProjectionMapper projectionMapper;

    public LocalDbProductListingProjectionBackfill(
            ProductProjectionPersistenceService projectionPersistenceService,
            ProductListingProjectionMapper projectionMapper
    ) {
        this.projectionPersistenceService = projectionPersistenceService;
        this.projectionMapper = projectionMapper;
    }

    @Override
    public void backfillDraftListing(
            ProductListingDraftRecord record,
            ProductListingDraftCommand draft
    ) {
        if (record == null || draft == null) {
            return;
        }
        String partnerSku = normalize(draft.getPsku());
        if (!StringUtils.hasText(partnerSku)
                || record.getOwnerUserId() == null
                || !StringUtils.hasText(record.getStoreCode())) {
            return;
        }

        ProductListingStoreProjectionContext storeContext =
                projectionMapper.selectStoreContext(record.getOwnerUserId(), record.getStoreCode());
        if (storeContext == null || !StringUtils.hasText(storeContext.getProjectCode())) {
            return;
        }

        String now = TIME_FORMATTER.format(LocalDateTime.now());
        ProductMasterSnapshotView snapshot = draftSnapshot(
                record,
                draft,
                storeContext,
                partnerSku,
                localDraftSkuParent(partnerSku)
        );
        projectionPersistenceService.persistSnapshotProjection(
                record.getOwnerUserId(),
                snapshot,
                "draft",
                now,
                new ArrayList<>()
        );
    }

    @Override
    public void backfillSuccessfulListing(
            ProductListingTaskRecord task,
            ProductListingDraftCommand draft,
            ProductListingNoonWriteResult result
    ) {
        if (task == null || draft == null || result == null || !result.isSuccess()) {
            return;
        }
        String partnerSku = normalize(draft.getPsku());
        Map<String, String> references = noonReferences(result);
        String skuParent = references.get("skuParent");
        String pskuCode = references.get("pskuCode");
        if (!StringUtils.hasText(partnerSku)
                || !StringUtils.hasText(skuParent)
                || !StringUtils.hasText(pskuCode)
                || task.getOwnerUserId() == null
                || !StringUtils.hasText(task.getStoreCode())) {
            return;
        }

        ProductListingStoreProjectionContext storeContext =
                projectionMapper.selectStoreContext(task.getOwnerUserId(), task.getStoreCode());
        if (storeContext == null || !StringUtils.hasText(storeContext.getProjectCode())) {
            return;
        }

        List<ProductProjectionPersistenceService.SiteSeed> siteSeeds = siteSeeds(
                task.getOwnerUserId(),
                storeContext
        );
        ProductProjectionPersistenceService.ProductMasterSeed productSeed = productSeed(
                task,
                draft,
                storeContext,
                partnerSku,
                skuParent,
                pskuCode
        );
        projectionPersistenceService.persistInitializationProjection(
                task.getOwnerUserId(),
                storeContext.getProjectCode(),
                storeContext.getProjectName(),
                task.getStoreCode(),
                siteSeeds,
                List.of(productSeed),
                new ArrayList<>(),
                true
        );
    }

    private List<ProductProjectionPersistenceService.SiteSeed> siteSeeds(
            Long ownerUserId,
            ProductListingStoreProjectionContext storeContext
    ) {
        List<ProductListingStoreProjectionContext> contexts =
                projectionMapper.selectProjectStoreContexts(ownerUserId, storeContext.getProjectCode());
        if (contexts == null || contexts.isEmpty()) {
            contexts = List.of(storeContext);
        }
        List<ProductProjectionPersistenceService.SiteSeed> seeds = new ArrayList<>();
        for (ProductListingStoreProjectionContext context : contexts) {
            String storeCode = normalize(context.getStoreCode());
            if (!StringUtils.hasText(storeCode)) {
                continue;
            }
            seeds.add(new ProductProjectionPersistenceService.SiteSeed(
                    storeCode,
                    normalize(context.getSite()),
                    "ACTIVE",
                    true
            ));
        }
        if (seeds.isEmpty()) {
            seeds.add(new ProductProjectionPersistenceService.SiteSeed(
                    normalize(storeContext.getStoreCode()),
                    normalize(storeContext.getSite()),
                    "ACTIVE",
                    true
            ));
        }
        return seeds;
    }

    private ProductProjectionPersistenceService.ProductMasterSeed productSeed(
            ProductListingTaskRecord task,
            ProductListingDraftCommand draft,
            ProductListingStoreProjectionContext storeContext,
            String partnerSku,
            String skuParent,
            String pskuCode
    ) {
        String now = TIME_FORMATTER.format(LocalDateTime.now());
        ProductProjectionPersistenceService.ProductMasterSeed seed =
                new ProductProjectionPersistenceService.ProductMasterSeed();
        seed.setSkuParent(skuParent);
        seed.setProductSourceType("SELF_BUILT");
        seed.setPartnerSku(partnerSku);
        seed.setChildSku(partnerSku);
        seed.setBarcode(firstNonBlank(draft.getBarcode(), barcodeFromAttributes(draft.getKeyAttributes())));
        seed.setPskuCode(pskuCode);
        seed.setOfferCode(skuParent);
        seed.setReferenceStoreCode(task.getStoreCode());
        seed.setBrandCache(firstNonBlank(draft.getProductBrand(), draft.getProductBrandCode()));
        seed.setTitleCache(draft.getProductTitleEn());
        seed.setTitleCnCache(draft.getProductTitleCn());
        seed.setProductFulltypeCache(draft.getProductFullType());
        seed.setCoverImageUrl(firstImage(draft.getImageUrls()));
        seed.setVariantCountCache(1);
        seed.setSyncStatus("synced");
        seed.setLastSyncedAt(now);
        seed.setOriginalPrice(decimalText(draft.getPrice()));
        seed.setSalePrice(decimalText(draft.getSalePrice()));
        seed.setFinalPrice(firstNonBlank(decimalText(draft.getSalePrice()), decimalText(draft.getPrice())));
        seed.setFinalPriceSource(draft.getSalePrice() == null ? "listing_price" : "listing_sale_price");
        seed.setIsActive(draft.getIsActive());
        seed.setStatusCode("listing_written");
        seed.setLiveStatus("local_projection_from_listing");
        ProductProjectionPersistenceService.SiteOfferSeed offerSeed =
                ProductProjectionPersistenceService.SiteOfferSeed.fromRepresentative(seed);
        offerSeed.setCurrency(currencyForSite(storeContext.getSite()));
        offerSeed.setListingStartedAt(listingStartedAt(task));
        offerSeed.setListingStartedSource("product_listing");
        seed.setSiteOffers(List.of(offerSeed));
        return seed;
    }

    private String listingStartedAt(ProductListingTaskRecord task) {
        LocalDateTime completedAt = task == null ? null : task.getCompletedAt();
        return TIME_FORMATTER.format(completedAt == null ? LocalDateTime.now() : completedAt);
    }

    private ProductMasterSnapshotView draftSnapshot(
            ProductListingDraftRecord record,
            ProductListingDraftCommand draft,
            ProductListingStoreProjectionContext storeContext,
            String partnerSku,
            String skuParent
    ) {
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
        putIfNotBlank(identity, "skuParent", skuParent);
        putIfNotBlank(identity, "partnerSku", partnerSku);
        putIfNotBlank(identity, "childSku", partnerSku);
        putIfNotBlank(identity, "productSourceType", "SELF_BUILT");
        putIfNotBlank(identity, "brand", firstNonBlank(draft.getProductBrand(), draft.getProductBrandCode()));
        putIfNotBlank(identity, "brandCode", draft.getProductBrandCode());
        putIfNotBlank(identity, "barcode", firstNonBlank(draft.getBarcode(), barcodeFromAttributes(draft.getKeyAttributes())));
        putIfNotNull(identity, "variantCount", 1);
        putIfNotBlank(identity, "listingDraftNo", record.getDraftNo());
        putIfNotNull(identity, "listingDraftId", record.getId());
        snapshot.setIdentity(identity);

        Map<String, Object> taxonomy = new LinkedHashMap<>();
        putIfNotNull(taxonomy, "idProductFullType", draft.getIdProductFullType());
        putIfNotBlank(taxonomy, "productFulltype", draft.getProductFullType());
        putIfNotBlank(taxonomy, "family", draft.getFamily());
        putIfNotBlank(taxonomy, "productType", draft.getProductType());
        putIfNotBlank(taxonomy, "productSubtype", draft.getProductSubType());
        snapshot.setTaxonomy(taxonomy);

        Map<String, Object> content = new LinkedHashMap<>();
        putIfNotBlank(content, "titleCn", draft.getProductTitleCn());
        putIfNotBlank(content, "titleZh", draft.getProductTitleCn());
        putIfNotBlank(content, "titleEn", draft.getProductTitleEn());
        putIfNotBlank(content, "titleAr", draft.getProductTitleAr());
        putIfNotBlank(content, "descriptionCn", draft.getProductDescriptionCn());
        putIfNotBlank(content, "descriptionEn", draft.getProductDescriptionEn());
        putIfNotBlank(content, "descriptionAr", draft.getProductDescriptionAr());
        putIfNotNull(content, "highlightsCn", draft.getProductHighlightsCn());
        putIfNotNull(content, "highlightsEn", draft.getProductHighlightsEn());
        putIfNotNull(content, "highlightsAr", draft.getProductHighlightsAr());
        putIfNotNull(content, "images", draft.getImageUrls());
        snapshot.setContent(content);

        if (draft.getKeyAttributes() != null) {
            snapshot.setKeyAttributes(new ArrayList<>(draft.getKeyAttributes()));
        }

        Map<String, Object> variant = new LinkedHashMap<>();
        putIfNotBlank(variant, "partnerSku", partnerSku);
        putIfNotBlank(variant, "childSku", partnerSku);
        putIfNotBlank(variant, "barcode", firstNonBlank(draft.getBarcode(), barcodeFromAttributes(draft.getKeyAttributes())));
        putIfNotNull(variant, "isActive", draft.getIsActive());
        snapshot.setVariants(List.of(variant));

        Map<String, Object> pricing = new LinkedHashMap<>();
        putIfNotBlank(pricing, "partnerSku", partnerSku);
        putIfNotBlank(pricing, "currency", currencyForSite(storeContext.getSite()));
        putIfNotNull(pricing, "price", decimalText(draft.getPrice()));
        putIfNotNull(pricing, "salePrice", decimalText(draft.getSalePrice()));
        putIfNotNull(pricing, "priceMin", decimalText(draft.getPriceMin()));
        putIfNotNull(pricing, "priceMax", decimalText(draft.getPriceMax()));
        putIfNotNull(pricing, "purchasePrice", decimalText(draft.getPurchasePrice()));
        putIfNotBlank(pricing, "saleStart", draft.getSaleStart());
        putIfNotBlank(pricing, "saleEnd", draft.getSaleEnd());
        snapshot.setPricing(pricing);

        Map<String, Object> stock = new LinkedHashMap<>();
        putIfNotNull(stock, "fbp", draft.getFbp());
        putIfNotBlank(stock, "warehouseId", draft.getWarehouseId());
        putIfNotBlank(stock, "warehouseCode", draft.getWarehouseCode());
        putIfNotNull(stock, "quantity", draft.getQuantity());
        snapshot.setStock(stock);

        Map<String, Object> siteOffer = new LinkedHashMap<>();
        putIfNotBlank(siteOffer, "storeCode", record.getStoreCode());
        putIfNotBlank(siteOffer, "site", storeContext.getSite());
        putIfNotBlank(siteOffer, "partnerSku", partnerSku);
        putIfNotBlank(siteOffer, "childSku", partnerSku);
        putIfNotBlank(siteOffer, "currency", currencyForSite(storeContext.getSite()));
        putIfNotNull(siteOffer, "price", decimalText(draft.getPrice()));
        putIfNotNull(siteOffer, "salePrice", decimalText(draft.getSalePrice()));
        putIfNotNull(siteOffer, "priceMin", decimalText(draft.getPriceMin()));
        putIfNotNull(siteOffer, "priceMax", decimalText(draft.getPriceMax()));
        putIfNotBlank(siteOffer, "saleStart", draft.getSaleStart());
        putIfNotBlank(siteOffer, "saleEnd", draft.getSaleEnd());
        putIfNotNull(siteOffer, "idWarranty", draft.getIdWarranty());
        putIfNotBlank(siteOffer, "offerNote", draft.getOfferNote());
        putIfNotNull(siteOffer, "isActive", draft.getIsActive());
        putIfNotBlank(siteOffer, "statusCode", "listing_draft");
        putIfNotBlank(siteOffer, "liveStatus", "draft");
        if (Boolean.TRUE.equals(draft.getFbp())) {
            putIfNotNull(siteOffer, "fbpStock", draft.getQuantity());
        }
        snapshot.setSiteOffers(List.of(siteOffer));
        return snapshot;
    }

    private String localDraftSkuParent(String partnerSku) {
        String sanitized = partnerSku.replaceAll("[^A-Za-z0-9_-]", "-");
        if (sanitized.length() > 70) {
            sanitized = sanitized.substring(0, 70);
        }
        String hash = Integer.toHexString(partnerSku.hashCode()).toUpperCase(Locale.ROOT);
        return "LOCAL-" + sanitized + "-" + hash;
    }

    private Map<String, String> noonReferences(ProductListingNoonWriteResult result) {
        Map<String, String> references = new LinkedHashMap<>();
        if (result == null || result.getSteps() == null) {
            return references;
        }
        for (ProductListingNoonWriteStepResult step : result.getSteps()) {
            if (step == null || !"succeeded".equals(step.getStatus())) {
                continue;
            }
            Map<String, String> current = parseReference(step.getExternalReference());
            if ("verify_noon_readback".equals(step.getStepKey())) {
                references.putAll(current);
            } else {
                current.forEach(references::putIfAbsent);
            }
        }
        return references;
    }

    private Map<String, String> parseReference(String externalReference) {
        Map<String, String> parsed = new LinkedHashMap<>();
        if (!StringUtils.hasText(externalReference)) {
            return parsed;
        }
        for (String part : externalReference.split(";")) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            int separator = part.indexOf('=');
            if (separator <= 0 || separator >= part.length() - 1) {
                continue;
            }
            String key = part.substring(0, separator).trim();
            String value = part.substring(separator + 1).trim();
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                parsed.put(key, value);
            }
        }
        return parsed;
    }

    private String barcodeFromAttributes(List<Map<String, Object>> keyAttributes) {
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

    private String currencyForSite(String site) {
        String normalizedSite = normalize(site);
        if ("AE".equalsIgnoreCase(normalizedSite)) {
            return "AED";
        }
        if ("SA".equalsIgnoreCase(normalizedSite)) {
            return "SAR";
        }
        return null;
    }

    private String firstImage(List<String> imageUrls) {
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

    private String decimalText(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String firstNonBlank(String... values) {
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

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
