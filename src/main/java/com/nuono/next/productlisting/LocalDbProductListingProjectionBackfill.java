package com.nuono.next.productlisting;

import com.nuono.next.infrastructure.mapper.ProductListingProjectionMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductImageAssetRoleUpdateCommand;
import com.nuono.next.product.ProductImageProfileSaveCommand;
import com.nuono.next.product.ProductImageProfileService;
import com.nuono.next.product.ProductMasterIdentityRecord;
import com.nuono.next.product.ProductMasterSnapshotView;
import com.nuono.next.product.ProductProjectionPersistenceService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbProductListingProjectionBackfill implements ProductListingProjectionBackfill {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int LISTING_STARTED_SOURCE_MAX_LENGTH = 40;
    private static final String REBUILD_INHERITED_SOURCE_PREFIX = "product_rebuild_inherited:";

    private final ProductProjectionPersistenceService projectionPersistenceService;
    private final ProductListingProjectionMapper projectionMapper;
    private final ProductImageProfileService productImageProfileService;
    private final ProductManagementMapper productManagementMapper;
    private final ProductListingDraftSnapshotAssembler draftSnapshotAssembler;

    public LocalDbProductListingProjectionBackfill(
            ProductProjectionPersistenceService projectionPersistenceService,
            ProductListingProjectionMapper projectionMapper,
            ProductImageProfileService productImageProfileService,
            ProductManagementMapper productManagementMapper
    ) {
        this.projectionPersistenceService = projectionPersistenceService;
        this.projectionMapper = projectionMapper;
        this.productImageProfileService = productImageProfileService;
        this.productManagementMapper = productManagementMapper;
        this.draftSnapshotAssembler = new ProductListingDraftSnapshotAssembler();
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
        ProductMasterSnapshotView snapshot = draftSnapshotAssembler.toDraftSnapshot(
                record,
                draft,
                storeContext,
                partnerSku
        );
        projectionPersistenceService.persistSnapshotProjection(
                record.getOwnerUserId(),
                snapshot,
                "draft",
                now,
                new ArrayList<>()
        );
        reconcileProductBarcodes(
                record.getOwnerUserId(),
                record.getStoreCode(),
                partnerSku,
                ProductListingDraftProjectionFields.from(draft, partnerSku).barcode()
        );
    }

    @Override
    public boolean backfillSuccessfulListing(
            ProductListingTaskRecord task,
            ProductListingDraftCommand draft,
            ProductListingNoonWriteResult result
    ) {
        if (task == null || draft == null || result == null || !result.isSuccess()) {
            return false;
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
            return false;
        }

        ProductListingStoreProjectionContext storeContext =
                projectionMapper.selectStoreContext(task.getOwnerUserId(), task.getStoreCode());
        if (storeContext == null || !StringUtils.hasText(storeContext.getProjectCode())) {
            return false;
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
        reconcileProductBarcodes(
                task.getOwnerUserId(),
                task.getStoreCode(),
                partnerSku,
                ProductListingDraftProjectionFields.from(draft, partnerSku).barcode()
        );
        syncProductImageProfile(task, draft, partnerSku);
        return true;
    }

    private void reconcileProductBarcodes(
            Long ownerUserId,
            String storeCode,
            String partnerSku,
            String retainedBarcode
    ) {
        ProductMasterIdentityRecord identity = productManagementMapper.selectProductMasterIdentityByStorePartnerSku(
                ownerUserId,
                storeCode,
                partnerSku
        );
        if (identity == null || identity.getProductMasterId() == null) {
            return;
        }
        if (StringUtils.hasText(retainedBarcode)) {
            productManagementMapper.markOtherProductBarcodesDeletedByProductMasterId(
                    identity.getProductMasterId(),
                    retainedBarcode.trim(),
                    ownerUserId
            );
            return;
        }
        productManagementMapper.markProductBarcodesDeletedByProductMasterId(
                identity.getProductMasterId(),
                ownerUserId
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
        ProductListingDraftProjectionFields fields = ProductListingDraftProjectionFields.from(draft, partnerSku);
        ProductProjectionPersistenceService.ProductMasterSeed seed =
                new ProductProjectionPersistenceService.ProductMasterSeed();
        seed.setSkuParent(skuParent);
        seed.setProductSourceType(fields.productSourceType());
        seed.setPartnerSku(fields.partnerSku());
        seed.setChildSku(fields.partnerSku());
        seed.setSizeEn(fields.sizeEn());
        seed.setSizeAr(fields.sizeAr());
        seed.setBarcode(fields.barcode());
        seed.setPskuCode(pskuCode);
        seed.setOfferCode(skuParent);
        seed.setReferenceStoreCode(task.getStoreCode());
        seed.setBrandCache(fields.brand());
        seed.setTitleCache(fields.titleEn());
        seed.setTitleCnCache(fields.titleCn());
        seed.setProductFulltypeCache(fields.productFullType());
        seed.setCoverImageUrl(fields.coverImageUrl());
        seed.setImageUrls(fields.imageUrls());
        seed.setVariantCountCache(1);
        seed.setSyncStatus("synced");
        seed.setLastSyncedAt(now);
        seed.setOriginalPrice(fields.priceText());
        seed.setSalePrice(fields.salePriceText());
        seed.setFinalPrice(fields.finalPriceText());
        seed.setFinalPriceSource(fields.finalPriceSource());
        seed.setIsActive(fields.isActive());
        seed.setStatusCode("listing_written");
        seed.setLiveStatus("local_projection_from_listing");
        ProductProjectionPersistenceService.SiteOfferSeed offerSeed =
                ProductProjectionPersistenceService.SiteOfferSeed.fromRepresentative(seed);
        offerSeed.setCurrency(currencyForSite(storeContext.getSite()));
        offerSeed.setListingStartedAt(listingStartedAt(task, draft));
        offerSeed.setListingStartedSource(listingStartedSource(draft));
        seed.setSiteOffers(List.of(offerSeed));
        return seed;
    }

    private void syncProductImageProfile(
            ProductListingTaskRecord task,
            ProductListingDraftCommand draft,
            String partnerSku
    ) {
        ProductListingDraftProjectionFields fields = ProductListingDraftProjectionFields.from(draft, partnerSku);
        List<ProductListingImageRoleAssignment> assignments = fields.imageRoleAssignments();
        if (assignments.isEmpty() || productImageProfileService == null || productManagementMapper == null) {
            return;
        }

        ProductMasterIdentityRecord identity = productManagementMapper.selectProductMasterIdentityByStorePartnerSku(
                task.getOwnerUserId(),
                task.getStoreCode(),
                partnerSku
        );
        ProductImageProfileSaveCommand profileCommand = new ProductImageProfileSaveCommand();
        profileCommand.setOwnerUserId(task.getOwnerUserId());
        profileCommand.setStoreCode(task.getStoreCode());
        profileCommand.setPskuCode(partnerSku);
        profileCommand.setProductIdentityKey("psku:" + partnerSku);
        profileCommand.setProductMasterId(identity == null ? null : identity.getProductMasterId());
        profileCommand.setProductTitle(firstNonBlank(fields.titleEn(), fields.titleCn(), partnerSku));
        profileCommand.setTitleEn(fields.titleEn());
        profileCommand.setTitleAr(fields.titleAr());
        profileCommand.setBrand(fields.brand());
        profileCommand.setOperatorUserId(task.getOwnerUserId());

        List<ProductImageAssetRoleUpdateCommand> roleCommands = new ArrayList<>();
        for (ProductListingImageRoleAssignment assignment : assignments) {
            ProductImageAssetRoleUpdateCommand roleCommand = new ProductImageAssetRoleUpdateCommand();
            roleCommand.setImageUrl(assignment.getImageUrl());
            roleCommand.setImageRole(assignment.getImageRole());
            roleCommand.setSortOrder(assignment.getSortOrder());
            roleCommands.add(roleCommand);
        }
        productImageProfileService.saveAndSyncAssetRoles(profileCommand, roleCommands);
    }

    private String listingStartedAt(ProductListingTaskRecord task, ProductListingDraftCommand draft) {
        if (draft != null && StringUtils.hasText(draft.getInheritedListingStartedAt())) {
            return normalize(draft.getInheritedListingStartedAt());
        }
        LocalDateTime completedAt = task == null ? null : task.getCompletedAt();
        return TIME_FORMATTER.format(completedAt == null ? LocalDateTime.now() : completedAt);
    }

    private String listingStartedSource(ProductListingDraftCommand draft) {
        if (draft != null && StringUtils.hasText(draft.getInheritedListingStartedAt())) {
            String source = inheritedListingSourceAlias(normalize(draft.getInheritedListingStartedSource()));
            return fitListingStartedSource(StringUtils.hasText(source)
                    ? REBUILD_INHERITED_SOURCE_PREFIX + source
                    : "product_rebuild_inherited");
        }
        return "product_listing";
    }

    private String inheritedListingSourceAlias(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        if ("product_listing".equalsIgnoreCase(source)) {
            return "listing";
        }
        return source;
    }

    private String fitListingStartedSource(String source) {
        String normalized = normalize(source);
        if (!StringUtils.hasText(normalized) || normalized.length() <= LISTING_STARTED_SOURCE_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, LISTING_STARTED_SOURCE_MAX_LENGTH);
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

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
    }
}
