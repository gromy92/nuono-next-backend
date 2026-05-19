package com.nuono.next.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.CoreTableStatusMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductKeyContentHistoryAssembler.KeyContentHistoryCandidate;
import com.nuono.next.system.BootstrapProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductProjectionPersistenceService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String BASELINE_SNAPSHOT_TYPE = "baseline";
    private static final List<String> REQUIRED_TABLES = List.of(
            "logical_store",
            "logical_store_site",
            "product_management_id_sequence",
            "product_master",
            "product_variant",
            "product_barcode",
            "product_site_offer",
            "product_group",
            "product_group_member",
            "product_image_asset",
            "product_issue"
    );
    private static final List<String> WORKBENCH_TABLES = List.of(
            "product_master_snapshot",
            "product_master_draft",
            "product_action_log",
            "product_key_content_history"
    );
    private static final List<String> CLASSIFICATION_DICTIONARY_TABLES = List.of(
            "noon_brand_dictionary",
            "noon_product_fulltype_dictionary"
    );
    private static final List<String> EDITABLE_OFFER_FIELDS = List.of(
            "price",
            "salePrice",
            "saleStart",
            "saleEnd",
            "priceMin",
            "priceMax",
            "idWarranty"
    );
    private static final int RECOVERABLE_SNAPSHOT_LOOKBACK_LIMIT = 50;

    private final ProductManagementMapper productManagementMapper;
    private final CoreTableStatusMapper coreTableStatusMapper;
    private final BootstrapProperties bootstrapProperties;
    private final ObjectMapper objectMapper;
    private final ProductKeyContentHistoryAssembler productKeyContentHistoryAssembler;
    private final ProductGroupProjectionService productGroupProjectionService;
    private final ProductProjectionMergePolicy productProjectionMergePolicy = new ProductProjectionMergePolicy();

    public ProductProjectionPersistenceService(
            ProductManagementMapper productManagementMapper,
            CoreTableStatusMapper coreTableStatusMapper,
            BootstrapProperties bootstrapProperties,
            ObjectMapper objectMapper,
            ProductKeyContentHistoryAssembler productKeyContentHistoryAssembler,
            ProductGroupProjectionService productGroupProjectionService
    ) {
        this.productManagementMapper = productManagementMapper;
        this.coreTableStatusMapper = coreTableStatusMapper;
        this.bootstrapProperties = bootstrapProperties;
        this.objectMapper = objectMapper;
        this.productKeyContentHistoryAssembler = productKeyContentHistoryAssembler;
        this.productGroupProjectionService = productGroupProjectionService;
    }

    public void hydrateSnapshotGroupFromCurrentProjection(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            ProductMasterSnapshotView snapshot,
            List<String> warnings
    ) {
        if (productGroupProjectionService == null) {
            return;
        }
        productGroupProjectionService.hydrateSnapshotGroupFromCurrentProjection(
                ownerUserId,
                storeCode,
                skuParent,
                snapshot,
                warnings
        );
    }

    @Transactional
    public void persistInitializationProjection(
            Long ownerUserId,
            String projectCode,
            String projectName,
            String referenceStoreCode,
            List<SiteSeed> siteSeeds,
            List<ProductMasterSeed> productSeeds,
            List<String> warnings
    ) {
        if (ownerUserId == null || !StringUtils.hasText(projectCode) || productSeeds == null || productSeeds.isEmpty()) {
            return;
        }
        if (!ensureProductTablesReady(warnings)) {
            return;
        }

        Long logicalStoreId = ensureLogicalStore(ownerUserId, projectCode, projectName);
        Map<String, Long> siteIdMap = ensureSites(logicalStoreId, referenceStoreCode, siteSeeds, ownerUserId);
        boolean classificationDictionaryReady = ensureClassificationDictionaryTablesReady(warnings);
        for (ProductMasterSeed productSeed : productSeeds) {
            if (!StringUtils.hasText(productSeed.getSkuParent())) {
                continue;
            }
            Long productMasterId = upsertProductMaster(logicalStoreId, ownerUserId, productSeed);
            if (classificationDictionaryReady) {
                upsertClassificationDictionaries(ownerUserId, projectCode, siteIdMap.keySet(), productSeed, ownerUserId);
            }
            productManagementMapper.deleteProductMasterDraftByProductMasterId(productMasterId);
            persistIssues(productMasterId, productSeed.getIssueTags(), ownerUserId);
            persistRepresentativeOffer(productMasterId, siteIdMap, productSeed, ownerUserId);
        }
    }

    @Transactional
    public void persistSnapshotProjection(
            Long ownerUserId,
            ProductMasterSnapshotView snapshot,
            String syncStatus,
            String lastSyncedAt,
            List<String> warnings
    ) {
        if (ownerUserId == null || snapshot == null || !snapshot.isReady()) {
            return;
        }
        if (!ensureProductTablesReady(warnings)) {
            return;
        }

        String projectCode = text(snapshot.getStoreContext().get("projectCode"));
        String projectName = text(snapshot.getStoreContext().get("projectName"));
        String referenceStoreCode = text(snapshot.getStoreContext().get("storeCode"));
        if (!StringUtils.hasText(projectCode) || !StringUtils.hasText(referenceStoreCode)) {
            addWarningOnce(warnings, "当前商品投影缺少 projectCode / storeCode，暂未写入本地商品表。");
            return;
        }

        Long logicalStoreId = ensureLogicalStore(ownerUserId, projectCode, projectName);
        Map<String, Long> siteIdMap = ensureSites(
                logicalStoreId,
                referenceStoreCode,
                buildSiteSeeds(snapshot),
                ownerUserId
        );

        ProductMasterSeed masterSeed = ProductMasterSeed.fromSnapshot(snapshot, syncStatus, lastSyncedAt);
        if (!StringUtils.hasText(masterSeed.getSkuParent())) {
            return;
        }
        Long productMasterId = upsertProductMaster(logicalStoreId, ownerUserId, masterSeed);
        if (ensureClassificationDictionaryTablesReady(warnings)) {
            upsertClassificationDictionaries(ownerUserId, projectCode, siteIdMap.keySet(), masterSeed, ownerUserId);
        }
        preserveMissingSnapshotOfferProjectionFields(productMasterId, snapshot);
        Long baselineSnapshotId = persistBaselineSnapshot(productMasterId, snapshot, lastSyncedAt, ownerUserId, warnings);
        persistStructuredSnapshotProjection(
                logicalStoreId,
                productMasterId,
                baselineSnapshotId,
                snapshot,
                lastSyncedAt,
                ownerUserId,
                warnings
        );

        String partnerSku = text(snapshot.getIdentity().get("partnerSku"));
        if (!StringUtils.hasText(partnerSku)) {
            addWarningOnce(warnings, "当前详情还缺少 partnerSku，本轮只写入商品主档投影，变体和站点经营投影待后续补齐。");
            return;
        }

        VariantSeed referenceVariant = VariantSeed.fromSnapshot(snapshot);
        Long variantId = ensureVariant(productMasterId, ownerUserId, referenceVariant);
        if (variantId == null) {
            return;
        }
        persistBarcode(variantId, extractBarcode(snapshot), ownerUserId);

        boolean wroteSiteOffer = false;
        Map<String, Map<String, Object>> existingProjectionOffers = existingProjectionOffersByStoreCode(productMasterId);
        Map<String, Map<String, Object>> historicalEditableOffers =
                recoverableEditableOffersByStoreCode(productMasterId);
        for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
            String storeCode = text(siteOffer.get("storeCode"));
            Long siteId = siteIdMap.get(storeCode);
            if (siteId == null) {
                continue;
            }
            String normalizedStoreCode = normalize(storeCode);
            Map<String, Object> existingOffer = existingProjectionOffers.get(normalizedStoreCode);
            preserveListOnlyProjectionFields(siteOffer, existingOffer);
            preserveEditableOfferFieldsForIncompleteFetch(
                    siteOffer,
                    existingOffer,
                    historicalEditableOffers.get(normalizedStoreCode)
            );
            upsertSiteOffer(variantId, siteId, siteOffer, lastSyncedAt, ownerUserId);
            wroteSiteOffer = true;
        }

        if (!wroteSiteOffer) {
            addWarningOnce(warnings, "当前详情还没有可落库的站点经营面投影，本轮先只写入商品主档和参考变体。");
        }
    }

    private Map<String, Map<String, Object>> existingProjectionOffersByStoreCode(Long productMasterId) {
        Map<String, Map<String, Object>> offers = new LinkedHashMap<>();
        if (productMasterId == null) {
            return offers;
        }
        List<Map<String, Object>> rows = productManagementMapper.selectProductSiteOfferProjectionRows(productMasterId);
        if (rows == null) {
            return offers;
        }
        for (Map<String, Object> row : rows) {
            String storeCode = normalize(text(row.get("storeCode")));
            if (StringUtils.hasText(storeCode)) {
                offers.put(storeCode, row);
            }
        }
        return offers;
    }

    private Map<String, Map<String, Object>> recoverableEditableOffersByStoreCode(Long productMasterId) {
        Map<String, Map<String, Object>> offers = new LinkedHashMap<>();
        if (productMasterId == null) {
            return offers;
        }
        List<ProductMasterSnapshotRecord> records = productManagementMapper.selectRecentProductMasterSnapshots(
                productMasterId,
                BASELINE_SNAPSHOT_TYPE,
                RECOVERABLE_SNAPSHOT_LOOKBACK_LIMIT
        );
        if (records == null || records.isEmpty()) {
            return offers;
        }
        for (ProductMasterSnapshotRecord record : records) {
            ProductMasterSnapshotView snapshot = readRecoverableSnapshot(record);
            if (snapshot == null || snapshot.getSiteOffers() == null) {
                continue;
            }
            for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
                String storeCode = normalize(text(siteOffer.get("storeCode")));
                if (!StringUtils.hasText(storeCode) || offers.containsKey(storeCode)) {
                    continue;
                }
                if (hasAnyPresentValue(siteOffer, EDITABLE_OFFER_FIELDS)) {
                    offers.put(storeCode, siteOffer);
                }
            }
        }
        return offers;
    }

    private ProductMasterSnapshotView readRecoverableSnapshot(ProductMasterSnapshotRecord record) {
        if (record == null || !StringUtils.hasText(record.getSnapshotJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(record.getSnapshotJson(), ProductMasterSnapshotView.class);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private void hydrateMissingEditableOfferFieldsFromHistory(
            Long productMasterId,
            String referenceStoreCode,
            ProductMasterSnapshotView snapshot
    ) {
        hydrateMissingEditableOfferFieldsFromHistory(productMasterId, referenceStoreCode, snapshot, false, null);
    }

    private void hydrateMissingEditableOfferFieldsFromHistory(
            Long productMasterId,
            String referenceStoreCode,
            ProductMasterSnapshotView snapshot,
            boolean repairProjection,
            Long updatedBy
    ) {
        if (productMasterId == null || snapshot == null) {
            return;
        }
        Map<String, Map<String, Object>> historicalEditableOffers =
                recoverableEditableOffersByStoreCode(productMasterId);
        if (historicalEditableOffers.isEmpty()) {
            return;
        }

        if (snapshot.getSiteOffers() != null) {
            for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
                String storeCode = normalize(text(siteOffer.get("storeCode")));
                boolean missingBeforeHydration = missingEditableOfferFields(siteOffer);
                preserveEditableOfferFieldsForIncompleteFetch(
                        siteOffer,
                        null,
                        historicalEditableOffers.get(storeCode)
                );
                if (repairProjection && missingBeforeHydration && !missingEditableOfferFields(siteOffer)) {
                    patchMissingProductSiteOfferEditableFields(productMasterId, storeCode, siteOffer, updatedBy);
                }
            }
        }

        String normalizedReferenceStoreCode = normalize(firstNonBlank(
                referenceStoreCode,
                text(snapshot.getStoreContext().get("storeCode"))
        ));
        Map<String, Object> referenceOffer = historicalEditableOffers.get(normalizedReferenceStoreCode);
        if (referenceOffer == null && historicalEditableOffers.size() == 1) {
            referenceOffer = historicalEditableOffers.values().iterator().next();
        }
        preserveEditableOfferFieldsForIncompleteFetch(snapshot.getPricing(), null, referenceOffer);
    }

    private void patchMissingProductSiteOfferEditableFields(
            Long productMasterId,
            String storeCode,
            Map<String, Object> siteOffer,
            Long updatedBy
    ) {
        if (
                productMasterId == null
                        || !StringUtils.hasText(storeCode)
                        || siteOffer == null
                        || missingEditableOfferFields(siteOffer)
        ) {
            return;
        }
        productManagementMapper.patchMissingProductSiteOfferEditableFields(
                productMasterId,
                storeCode,
                asBigDecimal(siteOffer.get("price")),
                asBigDecimal(siteOffer.get("salePrice")),
                parseDateTime(text(siteOffer.get("saleStart"))),
                parseDateTime(text(siteOffer.get("saleEnd"))),
                asBigDecimal(siteOffer.get("priceMin")),
                asBigDecimal(siteOffer.get("priceMax")),
                asInteger(siteOffer.get("idWarranty")),
                updatedBy
        );
    }

    private void preserveListOnlyProjectionFields(Map<String, Object> siteOffer, Map<String, Object> existingOffer) {
        if (siteOffer == null || existingOffer == null) {
            return;
        }
        preserveMissingOperationalOfferFields(siteOffer, existingOffer);
        if (siteOffer.get("finalPrice") == null && existingOffer.get("finalPrice") != null) {
            putIfNotNull(siteOffer, "finalPrice", existingOffer.get("finalPrice"));
            putIfNotBlank(siteOffer, "finalPriceSource", text(existingOffer.get("finalPriceSource")));
            putIfNotBlank(siteOffer, "activePromotionCode", text(existingOffer.get("activePromotionCode")));
            putIfNotBlank(siteOffer, "activePromotionName", text(existingOffer.get("activePromotionName")));
            putIfNotBlank(siteOffer, "activePromotionUrl", text(existingOffer.get("activePromotionUrl")));
        }
        putIfNotBlank(siteOffer, "barcode", firstNonBlank(text(siteOffer.get("barcode")), text(existingOffer.get("barcode"))));
        putIfNotBlank(siteOffer, "deliveryMethod", firstNonBlank(text(siteOffer.get("deliveryMethod")), text(existingOffer.get("deliveryMethod"))));
        if (siteOffer.get("isWinningBuybox") == null && existingOffer.get("winningBuyboxFlag") != null) {
            Integer winningFlag = asInteger(existingOffer.get("winningBuyboxFlag"));
            if (winningFlag != null) {
                siteOffer.put("isWinningBuybox", winningFlag > 0);
            }
        }
        putIfNotNull(siteOffer, "viewsCount", firstNonNull(siteOffer.get("viewsCount"), existingOffer.get("viewsCount")));
        putIfNotNull(siteOffer, "unitsSold", firstNonNull(siteOffer.get("unitsSold"), existingOffer.get("unitsSold")));
        putIfNotNull(siteOffer, "salesAmount", firstNonNull(siteOffer.get("salesAmount"), existingOffer.get("salesAmount")));
        putIfNotBlank(siteOffer, "salesCurrency", firstNonBlank(text(siteOffer.get("salesCurrency")), text(existingOffer.get("salesCurrency"))));
    }

    private void preserveMissingEditableOfferFields(Map<String, Object> siteOffer, Map<String, Object> existingOffer) {
        if (siteOffer == null || existingOffer == null) {
            return;
        }
        preserveMissingOperationalOfferFields(siteOffer, existingOffer);
        Map<String, ProductFieldRead<?>> reads = productProjectionMergePolicy.readsFromLegacyMap(
                siteOffer,
                EDITABLE_OFFER_FIELDS
        );
        Map<String, Object> merged = productProjectionMergePolicy.mergeProjectionFields(
                existingOffer,
                reads,
                Set.of()
        );
        for (String field : EDITABLE_OFFER_FIELDS) {
            if (merged.containsKey(field)) {
                siteOffer.put(field, merged.get(field));
            }
        }
    }

    private void preserveEditableOfferFieldsForIncompleteFetch(
            Map<String, Object> siteOffer,
            Map<String, Object> existingOffer,
            Map<String, Object> historicalOffer
    ) {
        if (!missingEditableOfferFields(siteOffer)) {
            return;
        }
        preserveMissingEditableOfferFields(siteOffer, existingOffer);
        preserveMissingEditableOfferFields(siteOffer, historicalOffer);
    }

    private boolean missingEditableOfferFields(Map<String, Object> siteOffer) {
        return !hasAnyPresentValue(siteOffer, EDITABLE_OFFER_FIELDS);
    }

    private void preserveMissingOperationalOfferFields(Map<String, Object> siteOffer, Map<String, Object> existingOffer) {
        if (siteOffer == null || existingOffer == null) {
            return;
        }
        putIfNotBlank(siteOffer, "pskuCode", firstNonBlank(text(siteOffer.get("pskuCode")), text(existingOffer.get("pskuCode"))));
        putIfNotNull(siteOffer, "fbnStock", firstNonNull(siteOffer.get("fbnStock"), existingOffer.get("fbnStock")));
        putIfNotNull(siteOffer, "supermallStock", firstNonNull(siteOffer.get("supermallStock"), existingOffer.get("supermallStock")));
        putIfNotNull(siteOffer, "fbpStock", firstNonNull(siteOffer.get("fbpStock"), existingOffer.get("fbpStock")));
    }

    private void preserveMissingSnapshotOfferProjectionFields(Long productMasterId, ProductMasterSnapshotView snapshot) {
        if (productMasterId == null || snapshot == null) {
            return;
        }
        Map<String, Map<String, Object>> existingProjectionOffers = existingProjectionOffersByStoreCode(productMasterId);
        Map<String, Map<String, Object>> historicalEditableOffers =
                recoverableEditableOffersByStoreCode(productMasterId);
        if (existingProjectionOffers.isEmpty() && historicalEditableOffers.isEmpty()) {
            return;
        }

        if (snapshot.getSiteOffers() != null) {
            for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
                String storeCode = normalize(text(siteOffer.get("storeCode")));
                Map<String, Object> existingOffer = existingProjectionOffers.get(storeCode);
                preserveMissingOperationalOfferFields(siteOffer, existingOffer);
                preserveEditableOfferFieldsForIncompleteFetch(
                        siteOffer,
                        existingOffer,
                        historicalEditableOffers.get(storeCode)
                );
            }
        }

        String referenceStoreCode = normalize(text(snapshot.getStoreContext().get("storeCode")));
        Map<String, Object> referenceOffer = existingProjectionOffers.get(referenceStoreCode);
        Map<String, Object> historicalReferenceOffer = historicalEditableOffers.get(referenceStoreCode);
        preserveEditableOfferFieldsForIncompleteFetch(snapshot.getPricing(), referenceOffer, historicalReferenceOffer);
        preserveMissingOperationalOfferFields(snapshot.getStock(), referenceOffer);
    }

    @Transactional
    public void updateProductMasterStatus(
            Long ownerUserId,
            String projectCode,
            String projectName,
            String skuParent,
            String syncStatus,
            String lastSyncedAt,
            List<String> warnings
    ) {
        if (ownerUserId == null || !StringUtils.hasText(projectCode) || !StringUtils.hasText(skuParent)) {
            return;
        }
        if (!ensureProductTablesReady(warnings)) {
            return;
        }
        Long logicalStoreId = ensureLogicalStore(ownerUserId, projectCode, projectName);
        productManagementMapper.updateProductMasterStatus(
                logicalStoreId,
                skuParent,
                normalizeSyncStatus(syncStatus),
                parseDateTime(lastSyncedAt),
                ownerUserId
        );
    }

    public List<ProductListProjectionRecord> loadProductListProjection(
            Long ownerUserId,
            String storeCode,
            List<String> warnings
    ) {
        if (ownerUserId == null || !StringUtils.hasText(storeCode)) {
            return Collections.emptyList();
        }
        if (!ensureProductTablesReady(warnings)) {
            return Collections.emptyList();
        }
        String normalizedStoreCode = normalize(storeCode);
        reconcileDraftProjectionForList(ownerUserId, normalizedStoreCode);
        return productManagementMapper.selectProductListProjection(ownerUserId, normalizedStoreCode);
    }

    public List<ProductListSummaryView> loadProductListSummaries(
            Long ownerUserId,
            String storeCode,
            List<String> warnings
    ) {
        List<ProductListSummaryView> summaries = new ArrayList<>();
        for (ProductListProjectionRecord record : loadProductListProjection(ownerUserId, storeCode, warnings)) {
            ProductListSummaryView summary = toListSummaryView(record, normalize(storeCode));
            hydrateHistoryMeta(summary, ownerUserId, normalize(storeCode));
            summaries.add(summary);
        }
        return summaries;
    }

    public ProductListSummaryView loadProductListSummary(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            List<String> warnings
    ) {
        ProductListSummaryView summary = new ProductListSummaryView();
        summary.setStoreCode(normalize(storeCode));
        summary.setSkuParent(normalize(skuParent));
        if (ownerUserId == null || !StringUtils.hasText(storeCode) || !StringUtils.hasText(skuParent)) {
            summary.setReady(false);
            summary.setSource("missing");
            summary.setMessage("缺少店铺编码或 skuParent，暂时不能读取列表摘要。");
            return summary;
        }
        if (!ensureProductTablesReady(warnings)) {
            summary.setReady(false);
            summary.setSource("missing");
            summary.setMessage("商品管理本地投影表尚未初始化，暂时不能读取权威列表摘要。");
            summary.setWarnings(copyWarnings(warnings));
            return summary;
        }
        String normalizedStoreCode = normalize(storeCode);
        reconcileDraftProjectionForList(ownerUserId, normalizedStoreCode);
        ProductListProjectionRecord record = productManagementMapper.selectProductListProjectionBySkuParent(
                ownerUserId,
                normalizedStoreCode,
                normalize(skuParent)
        );
        if (record == null) {
            summary.setReady(false);
            summary.setSource("missing");
            summary.setMessage("当前商品摘要投影尚未落库，暂时只能继续使用初始化快照兜底。");
            summary.setWarnings(copyWarnings(warnings));
            return summary;
        }
        ProductListSummaryView readySummary = toListSummaryView(record, normalizedStoreCode);
        hydrateHistoryMeta(readySummary, ownerUserId, normalize(storeCode));
        readySummary.setWarnings(copyWarnings(warnings));
        return readySummary;
    }

    private void reconcileDraftProjectionForList(Long ownerUserId, String storeCode) {
        if (ownerUserId == null || !StringUtils.hasText(storeCode) || !ensureWorkbenchTablesReady(null)) {
            return;
        }
        productManagementMapper.deleteNoopProductMasterDraftsByStoreCode(ownerUserId, storeCode);
        productManagementMapper.markProductMastersWithMeaningfulDraftsByStoreCode(ownerUserId, storeCode, ownerUserId);
    }

    public List<ProductListSummaryView> loadProductGroupCandidateSummaries(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            String keyword,
            List<String> warnings
    ) {
        List<ProductListSummaryView> summaries = new ArrayList<>();
        if (ownerUserId == null || !StringUtils.hasText(storeCode) || !StringUtils.hasText(skuParent)) {
            return summaries;
        }
        if (!ensureProductTablesReady(warnings)) {
            return summaries;
        }
        ProductGroupCandidateContextRecord context = productManagementMapper.selectProductGroupCandidateContext(
                ownerUserId,
                normalize(storeCode),
                normalize(skuParent)
        );
        if (context == null) {
            addWarningOnce(warnings, "当前商品主档投影尚未落库，暂时不能按类目推荐分组候选 SKU。");
            return summaries;
        }
        List<ProductListProjectionRecord> records = productManagementMapper.selectProductGroupCandidates(
                ownerUserId,
                normalize(storeCode),
                normalize(skuParent),
                normalize(context.getBrand()),
                normalize(context.getProductFulltype()),
                normalize(context.getSkuGroup()),
                normalize(keyword),
                80
        );
        for (ProductListProjectionRecord record : records) {
            ProductListSummaryView summary = toListSummaryView(record, normalize(storeCode));
            summaries.add(summary);
        }
        return summaries;
    }

    @Transactional
    public Long persistUploadedImageAsset(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            String url,
            String storageKey,
            String originalFilename,
            String contentType,
            Long sizeBytes,
            String sha256,
            List<String> warnings
    ) {
        if (ownerUserId == null || !StringUtils.hasText(storeCode)
                || !StringUtils.hasText(skuParent) || !StringUtils.hasText(url)) {
            return null;
        }
        if (!ensureProductTablesReady(warnings)) {
            return null;
        }
        Long productMasterId = productManagementMapper.selectProductMasterIdByStoreCode(
                ownerUserId,
                normalize(storeCode),
                normalize(skuParent)
        );
        if (productMasterId == null) {
            addWarningOnce(warnings, "图片已上传，但当前商品主档投影尚未落库，暂时不能写入商品图片资产表。");
            return null;
        }
        Long existingId = productManagementMapper.selectProductImageAssetId(
                productMasterId,
                "upload",
                normalize(url)
        );
        Long imageAssetId = existingId != null ? existingId : productManagementMapper.nextProductImageAssetId();
        productManagementMapper.upsertProductImageAsset(
                imageAssetId,
                productMasterId,
                "upload",
                normalize(url),
                normalize(storageKey),
                normalize(originalFilename),
                normalize(contentType),
                sizeBytes,
                null,
                null,
                normalize(sha256),
                "draft",
                ownerUserId
        );
        return imageAssetId;
    }

    public ProductHistoryView loadProductHistoryView(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            List<String> warnings
    ) {
        ProductHistoryView view = new ProductHistoryView();
        ProductListSummaryView summary = loadProductListSummary(ownerUserId, storeCode, skuParent, warnings);
        view.setListSummary(summary);
        view.setWarnings(copyWarnings(warnings));
        view.setVisibleKeyContentHistoryCount(summary.getVisibleKeyContentHistoryCount());
        view.setPendingKeyContentHistoryCount(summary.getPendingKeyContentHistoryCount());
        view.setPendingKeyContentHistoryVisibleAfter(summary.getPendingKeyContentHistoryVisibleAfter());

        if (ownerUserId == null || !StringUtils.hasText(storeCode) || !StringUtils.hasText(skuParent)) {
            view.setReady(false);
            view.setSource("missing");
            view.setMessage("缺少店铺编码或 skuParent，暂时不能读取商品修改历史。");
            view.setWarnings(copyWarnings(warnings));
            return view;
        }
        if (!ensureProductTablesReady(warnings) || !ensureWorkbenchTablesReady(warnings)) {
            view.setReady(false);
            view.setSource("missing");
            view.setMessage("商品历史本地表尚未初始化，暂时不能读取商品修改历史。");
            view.setWarnings(copyWarnings(warnings));
            return view;
        }

        Long productMasterId = productManagementMapper.selectProductMasterIdByStoreCode(
                ownerUserId,
                normalize(storeCode),
                normalize(skuParent)
        );
        if (productMasterId == null) {
            view.setReady(false);
            view.setSource("missing");
            view.setMessage("当前商品还没有历史读模型，暂时不能读取商品修改历史明细。");
            view.setWarnings(copyWarnings(warnings));
            return view;
        }

        List<Map<String, Object>> publishTaskItems = loadPublishTaskHistoryItems(productMasterId, warnings);
        List<Map<String, Object>> actionItems = loadActionHistoryItems(productMasterId, warnings);
        List<Map<String, Object>> pendingItems = loadPendingKeyContentHistory(productMasterId, warnings);
        List<Map<String, Object>> visibleItems = loadVisibleKeyContentHistory(productMasterId, ownerUserId, warnings);
        List<Map<String, Object>> items = new ArrayList<>();
        items.addAll(publishTaskItems);
        items.addAll(filterDuplicatePublishActionItems(publishTaskItems, actionItems));
        items.addAll(pendingItems);
        items.addAll(visibleItems);
        sortHistoryItemsByPublishedAt(items);
        view.setReady(true);
        view.setSource("history-projection");
        view.setHistoryItems(items);
        view.setVisibleKeyContentHistoryCount(loadVisibleKeyContentHistoryCount(productMasterId));
        view.setPendingKeyContentHistoryCount(loadPendingKeyContentHistoryCount(productMasterId));
        view.setPendingKeyContentHistoryVisibleAfter(
                formatDateTime(loadPendingKeyContentHistoryVisibleAfter(productMasterId))
        );
        if (!items.isEmpty()) {
            view.setMessage("已读取商品修改历史明细。");
        } else if ((view.getPendingKeyContentHistoryCount() != null && view.getPendingKeyContentHistoryCount() > 0)
                || (view.getVisibleKeyContentHistoryCount() != null && view.getVisibleKeyContentHistoryCount() > 0)) {
            view.setMessage("已读取关键内容历史状态；当前完整明细暂未全部可见。");
        } else {
            view.setMessage("当前还没有商品修改历史。");
        }
        view.setWarnings(copyWarnings(warnings));
        return view;
    }

    public PersistedWorkbenchState loadPersistedWorkbenchState(
            Long ownerUserId,
            ProductMasterSnapshotView liveSnapshot,
            List<String> warnings
    ) {
        if (ownerUserId == null || liveSnapshot == null) {
            return null;
        }
        if (!ensureProductTablesReady(warnings) || !ensureWorkbenchTablesReady(warnings)) {
            return null;
        }

        Long productMasterId = resolveProductMasterId(
                ownerUserId,
                text(liveSnapshot.getStoreContext().get("projectCode")),
                text(liveSnapshot.getStoreContext().get("projectName")),
                text(liveSnapshot.getIdentity().get("skuParent"))
        );
        if (productMasterId == null) {
            return null;
        }

        return loadPersistedWorkbenchState(productMasterId, ownerUserId, warnings);
    }

    public ProductMasterSnapshotView loadLatestBaselineSnapshot(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            List<String> warnings
    ) {
        Long productMasterId = resolveProductMasterIdByStoreCode(ownerUserId, storeCode, skuParent, warnings);
        if (productMasterId == null) {
            return null;
        }

        ProductMasterSnapshotRecord baselineRecord =
                productManagementMapper.selectLatestProductMasterSnapshot(productMasterId, BASELINE_SNAPSHOT_TYPE);
        if (baselineRecord == null) {
            return null;
        }
        ProductMasterSnapshotView snapshot =
                readSnapshot(baselineRecord.getSnapshotJson(), "读取本地商品基线快照失败。", warnings);
        hydrateSnapshotFromProjection(productMasterId, storeCode, snapshot);
        hydrateMissingEditableOfferFieldsFromHistory(productMasterId, storeCode, snapshot, true, ownerUserId);
        return snapshot;
    }

    private void hydrateSnapshotFromProjection(
            Long productMasterId,
            String referenceStoreCode,
            ProductMasterSnapshotView snapshot
    ) {
        if (productMasterId == null || snapshot == null) {
            return;
        }
        List<Map<String, Object>> projectionRows =
                productManagementMapper.selectProductSiteOfferProjectionRows(productMasterId);
        if (projectionRows == null || projectionRows.isEmpty()) {
            return;
        }

        Map<String, Map<String, Object>> existingByStoreCode = new LinkedHashMap<>();
        if (snapshot.getSiteOffers() != null) {
            for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
                String storeCode = normalize(text(siteOffer.get("storeCode")));
                if (StringUtils.hasText(storeCode)) {
                    existingByStoreCode.put(storeCode, siteOffer);
                }
            }
        }

        List<Map<String, Object>> hydratedSiteOffers = new ArrayList<>();
        Map<String, Object> referenceOffer = null;
        for (Map<String, Object> projectionRow : projectionRows) {
            String storeCode = normalize(text(projectionRow.get("storeCode")));
            Map<String, Object> siteOffer = new LinkedHashMap<>(
                    existingByStoreCode.getOrDefault(storeCode, new LinkedHashMap<>())
            );
            overlaySiteOfferFromProjection(siteOffer, projectionRow);
            hydratedSiteOffers.add(siteOffer);
            if (referenceOffer == null || (StringUtils.hasText(storeCode) && storeCode.equalsIgnoreCase(referenceStoreCode))) {
                referenceOffer = siteOffer;
            }
        }
        snapshot.setSiteOffers(hydratedSiteOffers);

        if (referenceOffer != null) {
            overlayPricingFromProjection(snapshot.getPricing(), referenceOffer);
            overlayStockFromProjection(snapshot.getStock(), referenceOffer);
            overlayPlatformSignalsFromProjection(snapshot.getPlatformSignals(), referenceOffer);
            String barcode = text(referenceOffer.get("barcode"));
            if (StringUtils.hasText(barcode)) {
                putIfNotBlank(snapshot.getIdentity(), "barcode", barcode);
            }
        }
    }

    private void overlaySiteOfferFromProjection(Map<String, Object> target, Map<String, Object> row) {
        putIfNotBlank(target, "storeCode", text(row.get("storeCode")));
        putIfNotBlank(target, "site", text(row.get("site")));
        putIfNotBlank(target, "partnerSku", text(row.get("partnerSku")));
        putIfNotBlank(target, "pskuCode", text(row.get("pskuCode")));
        putIfNotBlank(target, "offerCode", text(row.get("offerCode")));
        putIfNotBlank(target, "currency", text(row.get("currency")));
        putIfNotNull(target, "price", row.get("price"));
        putIfNotNull(target, "salePrice", row.get("salePrice"));
        putIfNotBlank(target, "saleStart", text(row.get("saleStart")));
        putIfNotBlank(target, "saleEnd", text(row.get("saleEnd")));
        putIfNotNull(target, "priceMin", row.get("priceMin"));
        putIfNotNull(target, "priceMax", row.get("priceMax"));
        putIfNotNull(target, "finalPrice", row.get("finalPrice"));
        putIfNotBlank(target, "finalPriceSource", text(row.get("finalPriceSource")));
        putIfNotBlank(target, "activePromotionCode", text(row.get("activePromotionCode")));
        putIfNotBlank(target, "activePromotionName", text(row.get("activePromotionName")));
        putIfNotBlank(target, "activePromotionUrl", text(row.get("activePromotionUrl")));
        putIfNotBlank(target, "pricingMethod", text(row.get("pricingMethod")));
        putIfNotNull(target, "idWarranty", row.get("idWarranty"));
        putIfNotBlank(target, "offerNote", text(row.get("offerNote")));
        putIfNotBlank(target, "deliveryMethod", text(row.get("deliveryMethod")));
        Integer winningBuyboxFlag = asInteger(row.get("winningBuyboxFlag"));
        if (winningBuyboxFlag != null) {
            target.put("isWinningBuybox", winningBuyboxFlag > 0);
        }
        Integer activeFlag = asInteger(row.get("activeFlag"));
        if (activeFlag != null) {
            target.put("isActive", activeFlag > 0);
        }
        putIfNotBlank(target, "liveStatus", text(row.get("liveStatus")));
        putIfNotBlank(target, "statusCode", text(row.get("statusCode")));
        putIfNotNull(target, "fbnStock", row.get("fbnStock"));
        putIfNotNull(target, "supermallStock", row.get("supermallStock"));
        putIfNotNull(target, "fbpStock", row.get("fbpStock"));
        putIfNotNull(target, "viewsCount", row.get("viewsCount"));
        putIfNotNull(target, "unitsSold", row.get("unitsSold"));
        putIfNotNull(target, "salesAmount", row.get("salesAmount"));
        putIfNotBlank(target, "salesCurrency", text(row.get("salesCurrency")));
        putIfNotBlank(target, "lastSyncedAt", text(row.get("lastSyncedAt")));
        putIfNotBlank(target, "barcode", text(row.get("barcode")));
    }

    private void overlayPricingFromProjection(Map<String, Object> pricing, Map<String, Object> referenceOffer) {
        if (pricing == null || referenceOffer == null) {
            return;
        }
        putIfNotBlank(pricing, "partnerSku", text(referenceOffer.get("partnerSku")));
        putIfNotBlank(pricing, "offerCode", text(referenceOffer.get("offerCode")));
        putIfNotBlank(pricing, "currency", text(referenceOffer.get("currency")));
        putIfNotBlank(pricing, "barcode", text(referenceOffer.get("barcode")));
        putIfNotNull(pricing, "price", referenceOffer.get("price"));
        putIfNotNull(pricing, "salePrice", referenceOffer.get("salePrice"));
        putIfNotBlank(pricing, "saleStart", text(referenceOffer.get("saleStart")));
        putIfNotBlank(pricing, "saleEnd", text(referenceOffer.get("saleEnd")));
        putIfNotNull(pricing, "priceMin", referenceOffer.get("priceMin"));
        putIfNotNull(pricing, "priceMax", referenceOffer.get("priceMax"));
        putIfNotNull(
                pricing,
                "finalPrice",
                firstNonNull(referenceOffer.get("finalPrice"), referenceOffer.get("salePrice"), referenceOffer.get("price"))
        );
        putIfNotBlank(pricing, "finalPriceSource", text(referenceOffer.get("finalPriceSource")));
        putIfNotBlank(pricing, "activePromotionCode", text(referenceOffer.get("activePromotionCode")));
        putIfNotBlank(pricing, "activePromotionName", text(referenceOffer.get("activePromotionName")));
        putIfNotBlank(pricing, "activePromotionUrl", text(referenceOffer.get("activePromotionUrl")));
        putIfNotBlank(pricing, "pricingMethod", text(referenceOffer.get("pricingMethod")));
        putIfNotNull(pricing, "idWarranty", referenceOffer.get("idWarranty"));
        putIfNotBlank(pricing, "priceSource", text(referenceOffer.get("finalPriceSource")));
        putIfNotBlank(pricing, "liveStatus", text(referenceOffer.get("liveStatus")));
        if (referenceOffer.containsKey("isActive")) {
            pricing.put("isActive", referenceOffer.get("isActive"));
        }
    }

    private void overlayStockFromProjection(Map<String, Object> stock, Map<String, Object> referenceOffer) {
        if (stock == null || referenceOffer == null) {
            return;
        }
        putIfNotBlank(stock, "pskuCode", text(referenceOffer.get("pskuCode")));
        putIfNotNull(stock, "fbnStock", referenceOffer.get("fbnStock"));
        putIfNotNull(stock, "supermallStock", referenceOffer.get("supermallStock"));
        putIfNotNull(stock, "fbpStock", referenceOffer.get("fbpStock"));
    }

    private void overlayPlatformSignalsFromProjection(Map<String, Object> platformSignals, Map<String, Object> referenceOffer) {
        if (platformSignals == null || referenceOffer == null) {
            return;
        }
        putIfNotNull(platformSignals, "views", referenceOffer.get("viewsCount"));
        putIfNotNull(platformSignals, "unitsSold", referenceOffer.get("unitsSold"));
        putIfNotNull(platformSignals, "sales", referenceOffer.get("salesAmount"));
        putIfNotBlank(platformSignals, "salesCurrency", text(referenceOffer.get("salesCurrency")));
    }

    public PersistedWorkbenchState loadPersistedWorkbenchState(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            List<String> warnings
    ) {
        Long productMasterId = resolveProductMasterIdByStoreCode(ownerUserId, storeCode, skuParent, warnings);
        if (productMasterId == null) {
            return null;
        }
        return loadPersistedWorkbenchState(productMasterId, ownerUserId, warnings);
    }

    @Transactional
    public void clearInactivePersistedDraft(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            String lastSyncedAt,
            List<String> warnings
    ) {
        Long productMasterId = resolveProductMasterIdByStoreCode(ownerUserId, storeCode, skuParent, warnings);
        if (productMasterId == null) {
            return;
        }
        productManagementMapper.deleteProductMasterDraftByProductMasterId(productMasterId);
        productManagementMapper.updateProductMasterStatusById(
                productMasterId,
                "synced",
                parseDateTime(lastSyncedAt),
                ownerUserId
        );
    }

    private PersistedWorkbenchState loadPersistedWorkbenchState(
            Long productMasterId,
            Long ownerUserId,
            List<String> warnings
    ) {
        PersistedWorkbenchState state = new PersistedWorkbenchState();
        ProductMasterDraftRecord draftRecord =
                productManagementMapper.selectProductMasterDraftByProductMasterId(productMasterId);
        if (draftRecord != null) {
            state.setVersionNo(draftRecord.getVersionNo());
            state.setDirtySiteCodes(parseStringList(draftRecord.getDirtySiteCodesJson()));
            state.setDraftSnapshot(readSnapshot(draftRecord.getDraftJson(), "读取本地商品草稿失败。", warnings));
            hydrateMissingEditableOfferFieldsFromHistory(productMasterId, null, state.getDraftSnapshot());
            ProductMasterSnapshotRecord baselineRecord =
                    productManagementMapper.selectProductMasterSnapshotById(draftRecord.getBaselineSnapshotId());
            if (baselineRecord != null) {
                state.setBaselineSnapshot(
                        readSnapshot(baselineRecord.getSnapshotJson(), "读取本地商品基线快照失败。", warnings)
                );
                hydrateMissingEditableOfferFieldsFromHistory(productMasterId, null, state.getBaselineSnapshot());
            }
        }
        state.setRecentActions(loadRecentActions(productMasterId, warnings));
        state.setKeyContentHistory(loadVisibleKeyContentHistory(productMasterId, ownerUserId, warnings));
        state.setPendingKeyContentHistoryCount(loadPendingKeyContentHistoryCount(productMasterId));
        state.setPendingKeyContentHistoryVisibleAfter(
                formatDateTime(loadPendingKeyContentHistoryVisibleAfter(productMasterId))
        );
        return state;
    }

    private Long resolveProductMasterIdByStoreCode(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            List<String> warnings
    ) {
        if (ownerUserId == null || !StringUtils.hasText(storeCode) || !StringUtils.hasText(skuParent)) {
            return null;
        }
        if (!ensureProductTablesReady(warnings) || !ensureWorkbenchTablesReady(warnings)) {
            return null;
        }
        return productManagementMapper.selectProductMasterIdByStoreCode(
                ownerUserId,
                normalize(storeCode),
                normalize(skuParent)
        );
    }

    @Transactional
    public void persistWorkbenchState(
            Long ownerUserId,
            ProductMasterWorkbenchView workbench,
            List<String> dirtySiteCodes,
            String actionType,
            String targetSiteCode,
            List<String> warnings
    ) {
        persistWorkbenchState(
                ownerUserId,
                workbench,
                dirtySiteCodes,
                actionType,
                targetSiteCode,
                warnings,
                null,
                null
        );
    }

    @Transactional
    public void persistWorkbenchState(
            Long ownerUserId,
            ProductMasterWorkbenchView workbench,
            List<String> dirtySiteCodes,
            String actionType,
            String targetSiteCode,
            List<String> warnings,
            ProductMasterSnapshotView actionBaselineSnapshot,
            ProductMasterSnapshotView actionDraftSnapshot
    ) {
        if (ownerUserId == null || workbench == null) {
            return;
        }
        if (!ensureProductTablesReady(warnings) || !ensureWorkbenchTablesReady(warnings)) {
            return;
        }

        Long productMasterId = resolveProductMasterId(
                ownerUserId,
                text(workbench.getStoreContext().get("projectCode")),
                text(workbench.getStoreContext().get("projectName")),
                text(workbench.getIdentity().get("skuParent"))
        );
        if (productMasterId == null) {
            addWarningOnce(warnings, "当前商品主档投影尚未落库，暂时不能写入基线/草稿/动作日志。");
            return;
        }

        Long baselineSnapshotId = persistBaselineSnapshot(
                productMasterId,
                workbench.getBaselineSnapshot(),
                workbench.getLastSyncedAt(),
                ownerUserId,
                warnings
        );
        if ("rollback-draft".equalsIgnoreCase(actionType) || shouldClearPersistedDraft(actionType, workbench)) {
            productManagementMapper.deleteProductMasterDraftByProductMasterId(productMasterId);
        } else {
            persistDraft(productMasterId, baselineSnapshotId, workbench, dirtySiteCodes, ownerUserId, warnings);
        }
        if (StringUtils.hasText(actionType)) {
            persistActionLog(
                    productMasterId,
                    baselineSnapshotId,
                    workbench,
                    actionType,
                    targetSiteCode,
                    ownerUserId,
                    warnings,
                    actionBaselineSnapshot,
                    actionDraftSnapshot
            );
        }
    }

    private boolean shouldClearPersistedDraft(String actionType, ProductMasterWorkbenchView workbench) {
        return "publish-current".equalsIgnoreCase(actionType)
                && workbench != null
                && "synced".equalsIgnoreCase(normalize(workbench.getSyncStatus()));
    }

    @Transactional
    public void persistPublishedKeyContentHistory(
            Long ownerUserId,
            ProductMasterSnapshotView baselineSnapshot,
            ProductMasterSnapshotView publishedSnapshot,
            String targetSiteCode,
            List<String> warnings
    ) {
        if (ownerUserId == null || baselineSnapshot == null || publishedSnapshot == null) {
            return;
        }
        if (!ensureProductTablesReady(warnings) || !ensureWorkbenchTablesReady(warnings)) {
            return;
        }

        KeyContentHistoryCandidate candidate =
                productKeyContentHistoryAssembler.buildCandidate(
                        baselineSnapshot,
                        publishedSnapshot,
                        targetSiteCode
                );
        if (candidate == null) {
            return;
        }

        Long productMasterId = resolveProductMasterId(
                ownerUserId,
                text(publishedSnapshot.getStoreContext().get("projectCode")),
                text(publishedSnapshot.getStoreContext().get("projectName")),
                text(publishedSnapshot.getIdentity().get("skuParent"))
        );
        if (productMasterId == null) {
            addWarningOnce(warnings, "关键内容变更历史暂未写入：当前商品主档投影尚未落库。");
            return;
        }

        ProductMasterSnapshotRecord latestBaseline =
                productManagementMapper.selectLatestProductMasterSnapshot(productMasterId, BASELINE_SNAPSHOT_TYPE);
        LocalDateTime publishedAt = LocalDateTime.now();
        try {
            productManagementMapper.insertProductKeyContentHistory(
                    productManagementMapper.nextProductKeyContentHistoryId(),
                    productMasterId,
                    latestBaseline != null ? latestBaseline.getId() : null,
                    resolveTargetSiteId(targetSiteCode),
                    "publish-current",
                    "pending",
                    objectMapper.writeValueAsString(candidate.getChangeTypes()),
                    objectMapper.writeValueAsString(candidate.getSummary()),
                    publishedAt,
                    publishedAt.plusHours(12),
                    null,
                    ownerUserId
            );
        } catch (JsonProcessingException exception) {
            addWarningOnce(warnings, "写入关键内容变更历史失败，本轮先只保留发布链路。");
        }
    }

    private boolean ensureProductTablesReady(List<String> warnings) {
        return ensureTablesReady(REQUIRED_TABLES, warnings, "商品管理本地表尚未初始化：");
    }

    private boolean ensureWorkbenchTablesReady(List<String> warnings) {
        return ensureTablesReady(WORKBENCH_TABLES, warnings, "商品详情持久化表尚未初始化：");
    }

    private boolean ensureClassificationDictionaryTablesReady(List<String> warnings) {
        return ensureTablesReady(CLASSIFICATION_DICTIONARY_TABLES, warnings, "商品品牌/类目字典表尚未初始化：");
    }

    private boolean ensureTablesReady(List<String> requiredTables, List<String> warnings, String messagePrefix) {
        List<String> existingTables = coreTableStatusMapper.findExistingTableNames(
                bootstrapProperties.getSchema(),
                requiredTables
        );
        List<String> missingTables = new ArrayList<>();
        for (String table : requiredTables) {
            if (!existingTables.contains(table)) {
                missingTables.add(table);
            }
        }
        if (missingTables.isEmpty()) {
            return true;
        }
        addWarningOnce(
                warnings,
                messagePrefix + String.join(", ", missingTables) + "。如需开始落库，请先执行对应商品管理初始化脚本。"
        );
        return false;
    }

    private Long ensureLogicalStore(Long ownerUserId, String projectCode, String projectName) {
        Long existingId = productManagementMapper.selectLogicalStoreId(ownerUserId, normalize(projectCode));
        Long id = existingId != null ? existingId : productManagementMapper.nextLogicalStoreId();
        productManagementMapper.upsertLogicalStore(
                id,
                ownerUserId,
                normalize(projectCode),
                normalize(projectName),
                "ACTIVE"
        );
        return productManagementMapper.selectLogicalStoreId(ownerUserId, normalize(projectCode));
    }

    private Map<String, Long> ensureSites(
            Long logicalStoreId,
            String referenceStoreCode,
            List<SiteSeed> siteSeeds,
            Long updatedBy
    ) {
        Map<String, Long> siteIdMap = new LinkedHashMap<>();
        if (logicalStoreId == null || siteSeeds == null) {
            return siteIdMap;
        }
        List<String> activeStoreCodes = new ArrayList<>();
        for (SiteSeed siteSeed : siteSeeds) {
            if (!StringUtils.hasText(siteSeed.getStoreCode())) {
                continue;
            }
            activeStoreCodes.add(normalize(siteSeed.getStoreCode()));
            Long existingId = productManagementMapper.selectLogicalStoreSiteId(normalize(siteSeed.getStoreCode()));
            Long id = existingId != null ? existingId : productManagementMapper.nextLogicalStoreSiteId();
            productManagementMapper.upsertLogicalStoreSite(
                    id,
                    logicalStoreId,
                    normalize(siteSeed.getStoreCode()),
                    normalize(siteSeed.getSite()),
                    normalize(referenceStoreCode) != null
                            && normalize(referenceStoreCode).equalsIgnoreCase(normalize(siteSeed.getStoreCode())),
                    siteSeed.isMounted(),
                    normalize(siteSeed.getSiteStatus()),
                    updatedBy
            );
            Long siteId = productManagementMapper.selectLogicalStoreSiteId(normalize(siteSeed.getStoreCode()));
            if (siteId != null) {
                siteIdMap.put(normalize(siteSeed.getStoreCode()), siteId);
            }
        }
        if (!activeStoreCodes.isEmpty()) {
            productManagementMapper.markStaleLogicalStoreSitesDeleted(logicalStoreId, activeStoreCodes, updatedBy);
        }
        return siteIdMap;
    }

    private Long upsertProductMaster(Long logicalStoreId, Long updatedBy, ProductMasterSeed seed) {
        Long existingId = productManagementMapper.selectProductMasterId(logicalStoreId, normalize(seed.getSkuParent()));
        Long id = existingId != null ? existingId : productManagementMapper.nextProductMasterId();
        productManagementMapper.upsertProductMaster(
                id,
                logicalStoreId,
                normalize(seed.getSkuParent()),
                ProductSourceTypeSupport.resolve(seed.getProductSourceType(), seed.getChildSku(), seed.getSkuParent()),
                normalize(seed.getBrandCache()),
                normalize(seed.getTitleCache()),
                normalize(seed.getProductFulltypeCache()),
                normalize(seed.getCoverImageUrl()),
                normalize(seed.getSkuGroup()),
                normalize(seed.getGroupNameCache()),
                normalize(seed.getGroupRef()),
                seed.getGroupMemberCount(),
                seed.getIssueCount() == null ? 0 : seed.getIssueCount(),
                toJson(seed.getIssueSummary()),
                seed.getVariantCountCache(),
                normalizeSyncStatus(seed.getSyncStatus()),
                parseDateTime(seed.getLastSyncedAt()),
                updatedBy
        );
        return productManagementMapper.selectProductMasterId(logicalStoreId, normalize(seed.getSkuParent()));
    }

    private void upsertClassificationDictionaries(
            Long ownerUserId,
            String projectCode,
            Iterable<String> storeCodes,
            ProductMasterSeed seed,
            Long updatedBy
    ) {
        if (ownerUserId == null || seed == null || storeCodes == null || !StringUtils.hasText(projectCode)) {
            return;
        }
        String brandName = normalize(seed.getBrandCache());
        String brandKey = brandKey(brandName);
        String productFulltype = normalize(seed.getProductFulltypeCache());
        FulltypeParts fulltypeParts = FulltypeParts.from(productFulltype);
        LocalDateTime lastSeenAt = parseDateTime(seed.getLastSyncedAt());
        for (String storeCodeValue : storeCodes) {
            String storeCode = normalize(storeCodeValue);
            if (!StringUtils.hasText(storeCode)) {
                continue;
            }
            if (StringUtils.hasText(brandName) && StringUtils.hasText(brandKey)) {
                Long existingId = productManagementMapper.selectNoonBrandDictionaryId(
                        ownerUserId,
                        normalize(projectCode),
                        storeCode,
                        brandKey
                );
                Long id = existingId != null ? existingId : productManagementMapper.nextNoonBrandDictionaryId();
                productManagementMapper.upsertNoonBrandDictionary(
                        id,
                        ownerUserId,
                        normalize(projectCode),
                        storeCode,
                        brandKey,
                        brandName,
                        brandName,
                        null,
                        "product-projection",
                        1,
                        lastSeenAt,
                        updatedBy
                );
            }
            if (StringUtils.hasText(productFulltype)) {
                Long existingId = productManagementMapper.selectNoonProductFulltypeDictionaryId(
                        ownerUserId,
                        normalize(projectCode),
                        storeCode,
                        productFulltype
                );
                Long id = existingId != null ? existingId : productManagementMapper.nextNoonProductFulltypeDictionaryId();
                productManagementMapper.upsertNoonProductFulltypeDictionary(
                        id,
                        ownerUserId,
                        normalize(projectCode),
                        storeCode,
                        productFulltype,
                        fulltypeParts.family,
                        fulltypeParts.productType,
                        fulltypeParts.productSubtype,
                        productFulltype,
                        null,
                        "product-projection",
                        1,
                        lastSeenAt,
                        updatedBy
                );
            }
        }
    }

    private void persistStructuredSnapshotProjection(
            Long logicalStoreId,
            Long productMasterId,
            Long sourceSnapshotId,
            ProductMasterSnapshotView snapshot,
            String lastSyncedAt,
            Long updatedBy,
            List<String> warnings
    ) {
        if (logicalStoreId == null || productMasterId == null || snapshot == null) {
            return;
        }
        productGroupProjectionService.persistGroupProjection(
                logicalStoreId,
                productMasterId,
                sourceSnapshotId,
                snapshot,
                lastSyncedAt,
                updatedBy
        );
        persistImageAssets(productMasterId, stringList(snapshot.getContent().get("images")), updatedBy);
        persistIssues(productMasterId, collectSnapshotIssueTags(snapshot), updatedBy);
    }

    private void persistImageAssets(Long productMasterId, List<String> images, Long updatedBy) {
        if (productMasterId == null || images == null || images.isEmpty()) {
            return;
        }
        List<String> normalizedImages = new ArrayList<>();
        for (String imageUrl : images) {
            String normalizedUrl = normalize(imageUrl);
            if (!StringUtils.hasText(normalizedUrl) || normalizedImages.contains(normalizedUrl)) {
                continue;
            }
            normalizedImages.add(normalizedUrl);
            Long existingId = productManagementMapper.selectProductImageAssetId(productMasterId, "noon", normalizedUrl);
            Long id = existingId != null ? existingId : productManagementMapper.nextProductImageAssetId();
            productManagementMapper.upsertProductImageAsset(
                    id,
                    productMasterId,
                    "noon",
                    normalizedUrl,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    digest(normalizedUrl),
                    "synced",
                    updatedBy
            );
        }
        productManagementMapper.markStaleProductImageAssetsDeleted(productMasterId, "noon", normalizedImages, updatedBy);
    }

    private void persistIssues(Long productMasterId, List<String> issueTags, Long updatedBy) {
        if (productMasterId == null) {
            return;
        }
        List<String> normalizedIssues = new ArrayList<>();
        if (issueTags != null) {
            for (String issueTag : issueTags) {
                String normalizedIssue = normalize(issueTag);
                if (StringUtils.hasText(normalizedIssue) && !normalizedIssues.contains(normalizedIssue)) {
                    normalizedIssues.add(normalizedIssue);
                }
            }
        }
        List<String> activeHashes = new ArrayList<>();
        LocalDateTime seenAt = LocalDateTime.now();
        for (String issue : normalizedIssues) {
            String issueHash = digest("product|" + issue);
            activeHashes.add(issueHash);
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("message", issue);
            productManagementMapper.upsertProductIssue(
                    productManagementMapper.nextProductIssueId(),
                    productMasterId,
                    null,
                    null,
                    "product",
                    "noon",
                    null,
                    issueHash,
                    "warning",
                    issue,
                    issue,
                    toJson(raw),
                    "open",
                    seenAt,
                    updatedBy
            );
        }
        productManagementMapper.markProductIssuesResolvedExcept(productMasterId, activeHashes, seenAt, updatedBy);
        Map<String, Object> issueSummary = new LinkedHashMap<>();
        issueSummary.put("tags", normalizedIssues);
        productManagementMapper.updateProductMasterIssueSummary(
                productMasterId,
                normalizedIssues.size(),
                normalizedIssues.isEmpty() ? null : toJson(issueSummary),
                updatedBy
        );
    }

    private ProductListSummaryView toListSummaryView(ProductListProjectionRecord record, String storeCode) {
        ProductListSummaryView view = new ProductListSummaryView();
        view.setReady(record != null);
        view.setSource(record != null ? "projection" : "missing");
        view.setStoreCode(storeCode);
        if (record == null) {
            return view;
        }
        view.setSkuParent(record.getSkuParent());
        view.setProductSourceType(ProductSourceTypeSupport.resolve(
                record.getProductSourceType(),
                null,
                record.getSkuParent()
        ));
        view.setPartnerSku(record.getPartnerSku());
        view.setPskuCode(record.getPskuCode());
        view.setOfferCode(record.getOfferCode());
        view.setTitle(record.getTitle());
        view.setBrand(record.getBrand());
        view.setImageUrl(record.getImageUrl());
        view.setBarcode(record.getBarcode());
        view.setReferencePrice(record.getReferencePrice());
        view.setOriginalPrice(record.getOriginalPrice());
        view.setSalePrice(record.getSalePrice());
        view.setProductFulltype(record.getProductFulltype());
        view.setSkuGroup(record.getSkuGroup());
        view.setGroupRef(record.getGroupRef());
        view.setGroupRefCanonical(record.getGroupRefCanonical());
        view.setIssueCount(record.getIssueCount());
        view.setIssueTags(new ArrayList<>(record.issueTags()));
        view.setIsActive(record.getCurrentSiteActiveFlag() == null ? null : record.getCurrentSiteActiveFlag() > 0);
        view.setLiveStatus(firstNonBlank(record.getCurrentSiteLiveStatus(), firstListItem(record.liveStatuses())));
        view.setStatusCode(record.getCurrentSiteStatusCode());
        view.setSyncStatus(record.getSyncStatus());
        view.setLastSyncedAt(record.getLastSyncedAt());
        view.setLastDraftSavedAt(record.getLastDraftSavedAt());
        view.setDetailBaselineStatus(firstNonBlank(record.getDetailBaselineStatus(), "missing"));
        view.setDetailBaselineSyncedAt(record.getDetailBaselineSyncedAt());
        if ("ready".equalsIgnoreCase(view.getDetailBaselineStatus())) {
            view.setDetailBaselineMessage("详情基线已准备。");
        } else {
            view.setDetailBaselineMessage("缺少详情基线。");
        }
        view.setVariantCount(record.getVariantCount());
        view.setSiteOfferCount(record.getSiteOfferCount());
        view.setSiteLabels(new ArrayList<>(record.siteLabels()));
        view.setLiveStatuses(new ArrayList<>(record.liveStatuses()));
        view.setTotalFbnStock(record.getTotalFbnStock());
        view.setTotalSupermallStock(record.getTotalSupermallStock());
        view.setTotalFbpStock(record.getTotalFbpStock());
        view.setViewsCount(record.getViewsCount());
        view.setUnitsSold(record.getUnitsSold());
        view.setSalesAmount(record.getSalesAmount());
        view.setSalesCurrency(record.getSalesCurrency());
        view.setMessage("已从本地商品投影读取权威列表摘要。");
        return view;
    }

    private void hydrateHistoryMeta(ProductListSummaryView view, Long ownerUserId, String storeCode) {
        if (view == null) {
            return;
        }
        view.setHistoryMetaReady(false);
        if (ownerUserId == null || !StringUtils.hasText(storeCode) || !StringUtils.hasText(view.getSkuParent())) {
            return;
        }
        if (!ensureProductTablesReady(null) || !ensureWorkbenchTablesReady(null)) {
            return;
        }
        Long productMasterId = productManagementMapper.selectProductMasterIdByStoreCode(
                ownerUserId,
                normalize(storeCode),
                normalize(view.getSkuParent())
        );
        if (productMasterId == null) {
            return;
        }
        view.setLastPublishTask(loadLastPublishTaskSummary(productMasterId, null));
        hydrateSnapshotContentMeta(view, productMasterId);
        productManagementMapper.promoteVisibleProductKeyContentHistory(
                productMasterId,
                LocalDateTime.now(),
                ownerUserId
        );
        view.setHistoryMetaReady(true);
        view.setPendingKeyContentHistoryCount(loadPendingKeyContentHistoryCount(productMasterId));
        view.setVisibleKeyContentHistoryCount(loadVisibleKeyContentHistoryCount(productMasterId));
        view.setPendingKeyContentHistoryVisibleAfter(
                formatDateTime(loadPendingKeyContentHistoryVisibleAfter(productMasterId))
        );
    }

    private Map<String, Object> loadLastPublishTaskSummary(Long productMasterId, List<String> warnings) {
        if (productMasterId == null) {
            return null;
        }
        List<ProductPublishTaskRecord> tasks = productManagementMapper.selectRecentProductPublishTasks(productMasterId);
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }
        ProductPublishTaskRecord task = tasks.get(0);
        String statusLabel = publishTaskListStatusLabel(task.getStatus());
        if (!StringUtils.hasText(statusLabel)) {
            return null;
        }
        ProductMasterSnapshotView baseline = readHistorySnapshot(task.getBaselineJson(), warnings);
        ProductMasterSnapshotView draft = readHistorySnapshot(task.getDraftJson(), warnings);
        Map<String, Object> summary = new LinkedHashMap<>();
        putIfNotNull(summary, "taskId", task.getId());
        putIfNotBlank(summary, "status", normalize(task.getStatus()));
        putIfNotBlank(summary, "statusLabel", statusLabel);
        putIfNotBlank(summary, "resultText", publishTaskHistoryMessage(task));
        putIfNotBlank(summary, "submittedAt", formatDateTime(task.getSubmittedAt()));
        putIfNotBlank(summary, "finishedAt", taskHistoryTime(task));
        putIfNotBlank(summary, "targetSiteCode", task.getCurrentSiteCode());
        putIfNotBlank(summary, "pskuCode", task.getPskuCode());
        putIfNotBlank(summary, "partnerSku", task.getPartnerSku());
        putIfNotNull(summary, "changes", buildProductModificationChanges(baseline, draft, task.getCurrentSiteCode()));
        return summary;
    }

    private String publishTaskListStatusLabel(String status) {
        String normalized = normalize(status);
        if ("synced".equalsIgnoreCase(normalized)) {
            return "发布成功";
        }
        if ("failed".equalsIgnoreCase(normalized)) {
            return "发布失败";
        }
        if ("pending_manual_check".equalsIgnoreCase(normalized)) {
            return "待人工核对";
        }
        if ("queued".equalsIgnoreCase(normalized)
                || "running".equalsIgnoreCase(normalized)
                || "submitted".equalsIgnoreCase(normalized)
                || "verifying".equalsIgnoreCase(normalized)
                || "pending_effective".equalsIgnoreCase(normalized)
                || "write_unknown".equalsIgnoreCase(normalized)
                || "verify_timeout".equalsIgnoreCase(normalized)) {
            return "发布中";
        }
        return null;
    }

    private void hydrateSnapshotContentMeta(ProductListSummaryView view, Long productMasterId) {
        ProductMasterSnapshotRecord baselineRecord =
                productManagementMapper.selectLatestProductMasterSnapshot(productMasterId, BASELINE_SNAPSHOT_TYPE);
        if (baselineRecord == null) {
            return;
        }
        ProductMasterSnapshotView snapshot =
                readSnapshot(baselineRecord.getSnapshotJson(), null, null);
        if (snapshot == null) {
            return;
        }
        List<String> images = stringList(snapshot.getContent().get("images"));
        if (!images.isEmpty()) {
            view.setGalleryImages(images);
            if (!StringUtils.hasText(view.getImageUrl())) {
                view.setImageUrl(images.get(0));
            }
        }
        view.setBarcode(firstNonBlank(view.getBarcode(), extractBarcode(snapshot)));
    }

    private String extractBarcode(ProductMasterSnapshotView snapshot) {
        String directValue = firstNonBlank(
                barcodeValue(snapshot.getIdentity()),
                barcodeValue(snapshot.getPricing())
        );
        if (StringUtils.hasText(directValue)) {
            return directValue;
        }
        for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
            String siteOfferBarcode = barcodeValue(siteOffer);
            if (StringUtils.hasText(siteOfferBarcode)) {
                return siteOfferBarcode;
            }
        }
        for (Map<String, Object> attribute : snapshot.getKeyAttributes()) {
            String code = text(attribute.get("code"));
            if (!StringUtils.hasText(code)) {
                continue;
            }
            String normalizedCode = code.toLowerCase();
            if (!normalizedCode.contains("barcode")
                    && !normalizedCode.contains("gtin")
                    && !normalizedCode.contains("ean")
                    && !normalizedCode.contains("upc")) {
                continue;
            }
            String value = firstNonBlank(
                    text(attribute.get("commonValue")),
                    firstNonBlank(text(attribute.get("enValue")), text(attribute.get("arValue")))
            );
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String barcodeValue(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        return firstNonBlank(
                text(source.get("barcode")),
                text(source.get("gtin")),
                text(source.get("ean")),
                text(source.get("upc"))
        );
    }

    private List<String> copyWarnings(List<String> warnings) {
        return warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
    }

    private String firstListItem(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
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

    private Long ensureVariant(Long productMasterId, Long updatedBy, VariantSeed seed) {
        if (productMasterId == null || seed == null || !StringUtils.hasText(seed.getPartnerSku())) {
            return null;
        }
        Long existingId = productManagementMapper.selectProductVariantIdByPartnerSku(
                productMasterId,
                normalize(seed.getPartnerSku())
        );
        Long id = existingId != null ? existingId : productManagementMapper.nextProductVariantId();
        productManagementMapper.upsertProductVariant(
                id,
                productMasterId,
                normalize(seed.getChildSku()),
                normalize(seed.getPartnerSku()),
                normalize(seed.getSizeEn()),
                normalize(seed.getSizeAr()),
                seed.getVariantIx(),
                updatedBy
        );
        return productManagementMapper.selectProductVariantIdByPartnerSku(productMasterId, normalize(seed.getPartnerSku()));
    }

    private void persistRepresentativeOffer(
            Long productMasterId,
            Map<String, Long> siteIdMap,
            ProductMasterSeed seed,
            Long updatedBy
    ) {
        if (productMasterId == null || seed == null || siteIdMap == null) {
            return;
        }
        VariantSeed variantSeed = seed.toRepresentativeVariantSeed();
        Long variantId = ensureVariant(productMasterId, updatedBy, variantSeed);
        if (variantId == null) {
            return;
        }
        persistBarcode(variantId, seed.getBarcode(), updatedBy);
        if (seed.getSiteOffers().isEmpty()) {
            seed.addSiteOffer(SiteOfferSeed.fromRepresentative(seed));
        }
        Map<String, Map<String, Object>> existingProjectionOffers = existingProjectionOffersByStoreCode(productMasterId);
        for (SiteOfferSeed offerSeed : seed.getSiteOffers()) {
            Long siteId = siteIdMap.get(normalize(offerSeed.getStoreCode()));
            if (siteId == null) {
                continue;
            }
            Map<String, Object> existingOffer = existingProjectionOffers.get(normalize(offerSeed.getStoreCode()));
            Map<String, Object> siteOffer = new LinkedHashMap<>();
            putIfNotBlank(siteOffer, "pskuCode", offerSeed.getPskuCode());
            putIfNotBlank(siteOffer, "offerCode", offerSeed.getOfferCode());
            putIfNotBlank(siteOffer, "currency", offerSeed.getCurrency());
            putIfNotBlank(siteOffer, "barcode", firstNonBlank(offerSeed.getBarcode(), seed.getBarcode()));
            putIfNotBlank(siteOffer, "price", offerSeed.getOriginalPrice());
            putIfNotBlank(siteOffer, "salePrice", offerSeed.getSalePrice());
            putIfNotBlank(siteOffer, "finalPrice", firstNonBlank(offerSeed.getFinalPrice(), seed.getFinalPrice()));
            putIfNotBlank(siteOffer, "finalPriceSource", firstNonBlank(offerSeed.getFinalPriceSource(), seed.getFinalPriceSource()));
            putIfNotBlank(siteOffer, "activePromotionCode", offerSeed.getActivePromotionCode());
            putIfNotBlank(siteOffer, "activePromotionName", offerSeed.getActivePromotionName());
            putIfNotBlank(siteOffer, "activePromotionUrl", offerSeed.getActivePromotionUrl());
            putIfNotBlank(siteOffer, "deliveryMethod", firstNonBlank(offerSeed.getDeliveryMethod(), seed.getDeliveryMethod()));
            putIfNotNull(siteOffer, "isWinningBuybox", firstNonNull(offerSeed.getIsWinningBuybox(), seed.getIsWinningBuybox()));
            putIfNotNull(siteOffer, "viewsCount", firstNonNull(offerSeed.getViewsCount(), seed.getViewsCount()));
            putIfNotNull(siteOffer, "unitsSold", firstNonNull(offerSeed.getUnitsSold(), seed.getUnitsSold()));
            putIfNotBlank(siteOffer, "salesAmount", firstNonBlank(offerSeed.getSalesAmount(), seed.getSalesAmount()));
            putIfNotBlank(siteOffer, "salesCurrency", firstNonBlank(offerSeed.getSalesCurrency(), seed.getSalesCurrency()));
            putIfNotBlank(siteOffer, "liveStatus", offerSeed.getLiveStatus());
            putIfNotBlank(siteOffer, "statusCode", offerSeed.getStatusCode());
            putIfNotNull(siteOffer, "isActive", offerSeed.getIsActive());
            putIfNotNull(siteOffer, "fbnStock", offerSeed.getFbnStock());
            putIfNotNull(siteOffer, "supermallStock", offerSeed.getSupermallStock());
            putIfNotNull(siteOffer, "fbpStock", offerSeed.getFbpStock());
            preserveMissingEditableOfferFields(siteOffer, existingOffer);
            upsertSiteOffer(variantId, siteId, siteOffer, seed.getLastSyncedAt(), updatedBy);
        }
    }

    private void upsertSiteOffer(
            Long variantId,
            Long siteId,
            Map<String, Object> siteOffer,
            String lastSyncedAt,
            Long updatedBy
    ) {
        if (variantId == null || siteId == null || siteOffer == null) {
            return;
        }
        Long existingId = productManagementMapper.selectProductSiteOfferId(variantId, siteId);
        Long id = existingId != null ? existingId : productManagementMapper.nextProductSiteOfferId();
        productManagementMapper.upsertProductSiteOffer(
                id,
                variantId,
                siteId,
                normalize(text(siteOffer.get("pskuCode"))),
                normalize(text(siteOffer.get("offerCode"))),
                normalize(text(siteOffer.get("currency"))),
                asBigDecimal(siteOffer.get("price")),
                asBigDecimal(siteOffer.get("salePrice")),
                parseDateTime(text(siteOffer.get("saleStart"))),
                parseDateTime(text(siteOffer.get("saleEnd"))),
                asBigDecimal(siteOffer.get("priceMin")),
                asBigDecimal(siteOffer.get("priceMax")),
                resolveFinalPrice(siteOffer),
                resolveFinalPriceSource(siteOffer),
                normalize(firstNonBlank(
                        text(siteOffer.get("activePromotionCode")),
                        text(siteOffer.get("promotionCode")),
                        text(siteOffer.get("promoCode")),
                        text(siteOffer.get("campaignCode"))
                )),
                normalize(firstNonBlank(
                        text(siteOffer.get("activePromotionName")),
                        text(siteOffer.get("promotionName")),
                        text(siteOffer.get("promoName")),
                        text(siteOffer.get("campaignName"))
                )),
                normalize(firstNonBlank(text(siteOffer.get("activePromotionUrl")), text(siteOffer.get("promotionUrl")))),
                promotionPayloadJson(siteOffer),
                parseDateTime(lastSyncedAt),
                "manual",
                null,
                null,
                null,
                asInteger(siteOffer.get("idWarranty")),
                normalize(text(siteOffer.get("offerNote"))),
                normalize(text(siteOffer.get("deliveryMethod"))),
                asBoolean(siteOffer.get("isWinningBuybox")),
                asBoolean(siteOffer.get("isActive")),
                normalize(text(siteOffer.get("liveStatus"))),
                normalize(text(siteOffer.get("statusCode"))),
                asInteger(siteOffer.get("fbnStock")),
                asInteger(siteOffer.get("supermallStock")),
                asInteger(siteOffer.get("fbpStock")),
                asLong(siteOffer.get("viewsCount")),
                asLong(siteOffer.get("unitsSold")),
                asBigDecimal(siteOffer.get("salesAmount")),
                normalize(text(siteOffer.get("salesCurrency"))),
                parseDateTime(lastSyncedAt),
                updatedBy
        );
    }

    private void persistBarcode(Long variantId, String barcode, Long updatedBy) {
        String normalizedBarcode = normalize(barcode);
        if (variantId == null || !StringUtils.hasText(normalizedBarcode)) {
            return;
        }
        Long existingId = productManagementMapper.selectProductBarcodeIdByBarcode(normalizedBarcode);
        Long id = existingId != null ? existingId : productManagementMapper.nextProductBarcodeId();
        productManagementMapper.upsertProductBarcode(
                id,
                variantId,
                normalizedBarcode,
                null,
                true,
                updatedBy
        );
    }

    private Long resolveProductMasterId(
            Long ownerUserId,
            String projectCode,
            String projectName,
            String skuParent
    ) {
        if (ownerUserId == null || !StringUtils.hasText(projectCode) || !StringUtils.hasText(skuParent)) {
            return null;
        }
        Long logicalStoreId = ensureLogicalStore(ownerUserId, projectCode, projectName);
        return logicalStoreId == null ? null : productManagementMapper.selectProductMasterId(logicalStoreId, normalize(skuParent));
    }

    private Long persistBaselineSnapshot(
            Long productMasterId,
            ProductMasterSnapshotView snapshot,
            String lastSyncedAt,
            Long updatedBy,
            List<String> warnings
    ) {
        if (productMasterId == null || snapshot == null || !ensureWorkbenchTablesReady(warnings)) {
            return null;
        }
        try {
            String snapshotJson = objectMapper.writeValueAsString(snapshot);
            String snapshotHash = digest(snapshotJson);
            ProductMasterSnapshotRecord latestBaseline =
                    productManagementMapper.selectLatestProductMasterSnapshot(productMasterId, BASELINE_SNAPSHOT_TYPE);
            if (latestBaseline != null && snapshotHash.equals(latestBaseline.getSnapshotHash())) {
                return latestBaseline.getId();
            }
            Long id = productManagementMapper.nextProductMasterSnapshotId();
            productManagementMapper.insertProductMasterSnapshot(
                    id,
                    productMasterId,
                    BASELINE_SNAPSHOT_TYPE,
                    snapshotHash,
                    snapshotJson,
                    parseSnapshotTime(snapshot, lastSyncedAt),
                    updatedBy
            );
            return id;
        } catch (JsonProcessingException exception) {
            addWarningOnce(warnings, "写入商品基线快照失败，当前仅保留投影数据。");
            return null;
        }
    }

    private void persistDraft(
            Long productMasterId,
            Long baselineSnapshotId,
            ProductMasterWorkbenchView workbench,
            List<String> dirtySiteCodes,
            Long updatedBy,
            List<String> warnings
    ) {
        if (productMasterId == null || baselineSnapshotId == null || workbench == null || workbench.getDraftSnapshot() == null) {
            if (productMasterId != null && workbench != null && workbench.getDraftSnapshot() != null && baselineSnapshotId == null) {
                addWarningOnce(warnings, "当前基线快照未写入成功，草稿暂未持久化到本地库。");
            }
            return;
        }
        try {
            ProductMasterDraftRecord existing =
                    productManagementMapper.selectProductMasterDraftByProductMasterId(productMasterId);
            Long id = existing != null && existing.getId() != null
                    ? existing.getId()
                    : productManagementMapper.nextProductMasterDraftId();
            int nextVersion = existing != null && existing.getVersionNo() != null
                    ? existing.getVersionNo() + 1
                    : 1;
            productManagementMapper.upsertProductMasterDraft(
                    id,
                    productMasterId,
                    baselineSnapshotId,
                    nextVersion,
                    objectMapper.writeValueAsString(dirtySiteCodes == null ? Collections.emptyList() : dirtySiteCodes),
                    objectMapper.writeValueAsString(workbench.getDraftSnapshot()),
                    LocalDateTime.now(),
                    updatedBy
            );
        } catch (JsonProcessingException exception) {
            addWarningOnce(warnings, "写入商品草稿失败，本轮仍只保留内存态工作台。");
        }
    }

    private void persistActionLog(
            Long productMasterId,
            Long baselineSnapshotId,
            ProductMasterWorkbenchView workbench,
            String actionType,
            String targetSiteCode,
            Long updatedBy,
            List<String> warnings
    ) {
        persistActionLog(
                productMasterId,
                baselineSnapshotId,
                workbench,
                actionType,
                targetSiteCode,
                updatedBy,
                warnings,
                null,
                null
        );
    }

    private void persistActionLog(
            Long productMasterId,
            Long baselineSnapshotId,
            ProductMasterWorkbenchView workbench,
            String actionType,
            String targetSiteCode,
            Long updatedBy,
            List<String> warnings,
            ProductMasterSnapshotView actionBaselineSnapshot,
            ProductMasterSnapshotView actionDraftSnapshot
    ) {
        if (productMasterId == null || workbench == null || !StringUtils.hasText(actionType)) {
            return;
        }
        try {
            Map<String, Object> summary = new LinkedHashMap<>();
            putIfNotBlank(summary, "message", workbench.getNote());
            putIfNotBlank(summary, "targetSiteCode", targetSiteCode);
            putIfNotNull(summary, "degraded", workbench.isDegraded());
            if (shouldCaptureActionChanges(actionType)) {
                List<Map<String, Object>> changes = buildProductModificationChanges(
                        actionBaselineSnapshot != null ? actionBaselineSnapshot : workbench.getBaselineSnapshot(),
                        actionDraftSnapshot != null ? actionDraftSnapshot : workbench.getDraftSnapshot(),
                        targetSiteCode
                );
                if (!changes.isEmpty()) {
                    putIfNotNull(summary, "changes", changes);
                    putIfNotNull(summary, "changeTypes", changeTypes(changes));
                }
            }
            Long targetSiteId = resolveTargetSiteId(
                    workbench,
                    targetSiteCode
            );
            productManagementMapper.insertProductActionLog(
                    productManagementMapper.nextProductActionLogId(),
                    productMasterId,
                    baselineSnapshotId,
                    targetSiteId,
                    null,
                    normalize(actionType),
                    "last_write_wins",
                    null,
                    stageFor(actionType),
                    normalizeSyncStatus(workbench.getSyncStatus()),
                    "failed".equalsIgnoreCase(normalizeSyncStatus(workbench.getSyncStatus())) ? "validation" : null,
                    resolveOfferIdentity(
                            actionDraftSnapshot != null ? actionDraftSnapshot : workbench.getDraftSnapshot(),
                            targetSiteCode,
                            "pskuCode"
                    ),
                    resolveOfferIdentity(
                            actionDraftSnapshot != null ? actionDraftSnapshot : workbench.getDraftSnapshot(),
                            targetSiteCode,
                            "offerCode"
                    ),
                    0,
                    objectMapper.writeValueAsString(summary),
                    blockedFieldsJson(workbench),
                    null,
                    null,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    updatedBy
            );
        } catch (JsonProcessingException exception) {
            addWarningOnce(warnings, "写入商品动作日志失败，本轮 recent actions 仍只保留在接口返回中。");
        }
    }

    private String blockedFieldsJson(ProductMasterWorkbenchView workbench) throws JsonProcessingException {
        if (workbench == null) {
            return null;
        }
        String status = normalizeSyncStatus(workbench.getSyncStatus());
        if (!"failed".equalsIgnoreCase(status)) {
            return null;
        }
        List<Map<String, Object>> blockedFields = new ArrayList<>();
        if (StringUtils.hasText(workbench.getNote())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", "publish");
            item.put("message", workbench.getNote());
            blockedFields.add(item);
        }
        for (String warning : workbench.getWarnings()) {
            if (!StringUtils.hasText(warning)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", "warning");
            item.put("message", warning);
            blockedFields.add(item);
        }
        if (blockedFields.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(blockedFields);
    }

    private List<Map<String, Object>> loadRecentActions(Long productMasterId, List<String> warnings) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (productMasterId == null) {
            return actions;
        }
        for (ProductActionLogRecord record : productManagementMapper.selectRecentProductActionLogs(productMasterId)) {
            Map<String, Object> action = new LinkedHashMap<>();
            if (StringUtils.hasText(record.getSummaryJson())) {
                try {
                    action.putAll(objectMapper.readValue(
                            record.getSummaryJson(),
                            new TypeReference<LinkedHashMap<String, Object>>() {
                            }
                    ));
                } catch (JsonProcessingException exception) {
                    addWarningOnce(warnings, "读取商品动作日志摘要失败，recent actions 已按基础字段回退。");
                }
            }
            putIfNotBlank(action, "occurredAt", formatDateTime(record.getOccurredAt()));
            putIfNotBlank(action, "actionType", record.getActionType());
            putIfNotBlank(action, "resultStatus", record.getResultStatus());
            putIfNotBlank(action, "pskuCode", record.getPskuCode());
            putIfNotBlank(action, "offerCode", record.getOfferCode());
            actions.add(action);
        }
        return actions;
    }

    private List<Map<String, Object>> loadPublishTaskHistoryItems(Long productMasterId, List<String> warnings) {
        List<Map<String, Object>> history = new ArrayList<>();
        if (productMasterId == null) {
            return history;
        }
        for (ProductPublishTaskRecord task : productManagementMapper.selectRecentProductPublishTasks(productMasterId)) {
            ProductMasterSnapshotView baseline = readHistorySnapshot(task.getBaselineJson(), warnings);
            ProductMasterSnapshotView draft = readHistorySnapshot(task.getDraftJson(), warnings);
            List<Map<String, Object>> changes = buildProductModificationChanges(
                    baseline,
                    draft,
                    task.getCurrentSiteCode()
            );
            if (changes.isEmpty()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            putIfNotBlank(item, "historyKind", "modification");
            putIfNotBlank(item, "source", "publish_task");
            putIfNotNull(item, "taskId", task.getId());
            putIfNotBlank(item, "actionType", "publish-current");
            putIfNotBlank(item, "resultStatus", normalize(task.getStatus()));
            putIfNotBlank(item, "statusLabel", publishTaskStatusLabel(task.getStatus()));
            putIfNotBlank(item, "message", publishTaskHistoryMessage(task));
            putIfNotBlank(item, "targetSiteCode", task.getCurrentSiteCode());
            putIfNotBlank(item, "pskuCode", task.getPskuCode());
            putIfNotBlank(item, "partnerSku", task.getPartnerSku());
            putIfNotBlank(item, "publishedAt", taskHistoryTime(task));
            putIfNotBlank(item, "visibilityStatus", "modification");
            putIfNotNull(item, "changes", changes);
            putIfNotNull(item, "changeTypes", changeTypes(changes));
            history.add(item);
        }
        return history;
    }

    private List<Map<String, Object>> loadActionHistoryItems(Long productMasterId, List<String> warnings) {
        List<Map<String, Object>> history = new ArrayList<>();
        for (Map<String, Object> action : loadRecentActions(productMasterId, warnings)) {
            List<Map<String, Object>> changes = objectMapList(action.get("changes"));
            Map<String, Object> item = new LinkedHashMap<>();
            String actionType = text(action.get("actionType"));
            String resultStatus = text(action.get("resultStatus"));
            boolean successfulDirectPublish =
                    "publish-current".equalsIgnoreCase(actionType) && "synced".equalsIgnoreCase(resultStatus);
            if (changes.isEmpty() && successfulDirectPublish) {
                changes = inferSuccessfulDirectPublishOfferChanges(productMasterId, action, warnings);
            }
            if (changes.isEmpty() && !successfulDirectPublish) {
                continue;
            }
            if ("publish-current".equalsIgnoreCase(actionType)
                    && !"failed".equalsIgnoreCase(resultStatus)
                    && !"synced".equalsIgnoreCase(resultStatus)) {
                continue;
            }
            putIfNotBlank(item, "historyKind", "modification");
            putIfNotBlank(item, "source", "action_log");
            putIfNotBlank(item, "actionType", actionType);
            putIfNotBlank(item, "resultStatus", resultStatus);
            putIfNotBlank(item, "statusLabel", actionResultStatusLabel(actionType, resultStatus));
            putIfNotBlank(item, "message", text(action.get("message")));
            putIfNotBlank(item, "targetSiteCode", text(action.get("targetSiteCode")));
            putIfNotBlank(item, "pskuCode", text(action.get("pskuCode")));
            putIfNotBlank(item, "offerCode", text(action.get("offerCode")));
            putIfNotBlank(item, "publishedAt", text(action.get("occurredAt")));
            putIfNotBlank(item, "visibilityStatus", "modification");
            if (!changes.isEmpty()) {
                putIfNotNull(item, "changes", changes);
                putIfNotNull(item, "changeTypes", changeTypes(changes));
            }
            history.add(item);
        }
        return history;
    }

    private List<Map<String, Object>> inferSuccessfulDirectPublishOfferChanges(
            Long productMasterId,
            Map<String, Object> action,
            List<String> warnings
    ) {
        List<Map<String, Object>> changes = new ArrayList<>();
        if (productMasterId == null || action == null) {
            return changes;
        }
        LocalDateTime occurredAt = parseDateTime(text(action.get("occurredAt")));
        if (occurredAt == null) {
            return changes;
        }

        ProductMasterSnapshotRecord beforeRecord =
                productManagementMapper.selectProductMasterSnapshotBeforeTimeWithOfferPrice(
                        productMasterId,
                        BASELINE_SNAPSHOT_TYPE,
                        occurredAt,
                        60
                );
        List<ProductMasterSnapshotRecord> afterRecords =
                productManagementMapper.selectProductMasterSnapshotsAfterTimeWithOfferPrice(
                        productMasterId,
                        BASELINE_SNAPSHOT_TYPE,
                        occurredAt,
                        2,
                        5
                );
        ProductMasterSnapshotView before = readHistorySnapshot(
                beforeRecord == null ? null : beforeRecord.getSnapshotJson(),
                warnings
        );
        if (before == null || afterRecords == null || afterRecords.isEmpty()) {
            return changes;
        }

        String targetSiteCode = text(action.get("targetSiteCode"));
        for (ProductMasterSnapshotRecord afterRecord : afterRecords) {
            ProductMasterSnapshotView after = readHistorySnapshot(afterRecord.getSnapshotJson(), warnings);
            if (after != null) {
                return buildOfferPriceModificationChanges(before, after, targetSiteCode);
            }
        }
        return changes;
    }

    private List<Map<String, Object>> filterDuplicatePublishActionItems(
            List<Map<String, Object>> publishTaskItems,
            List<Map<String, Object>> actionItems
    ) {
        if (actionItems == null || actionItems.isEmpty() || publishTaskItems == null || publishTaskItems.isEmpty()) {
            return actionItems == null ? Collections.emptyList() : actionItems;
        }

        List<String> taskPublishTimes = new ArrayList<>();
        for (Map<String, Object> taskItem : publishTaskItems) {
            if (!"publish_task".equalsIgnoreCase(text(taskItem.get("source")))) {
                continue;
            }
            String publishedAt = text(taskItem.get("publishedAt"));
            if (StringUtils.hasText(publishedAt)) {
                taskPublishTimes.add(publishedAt);
            }
        }
        if (taskPublishTimes.isEmpty()) {
            return actionItems;
        }

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> actionItem : actionItems) {
            boolean duplicateTaskAction = "action_log".equalsIgnoreCase(text(actionItem.get("source")))
                    && "publish-current".equalsIgnoreCase(text(actionItem.get("actionType")))
                    && "synced".equalsIgnoreCase(text(actionItem.get("resultStatus")))
                    && taskPublishTimes.contains(text(actionItem.get("publishedAt")));
            if (!duplicateTaskAction) {
                filtered.add(actionItem);
            }
        }
        return filtered;
    }

    private ProductMasterSnapshotView readHistorySnapshot(String json, List<String> warnings) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ProductMasterSnapshotView.class);
        } catch (JsonProcessingException exception) {
            addWarningOnce(warnings, "读取发布任务快照失败，部分商品修改历史已跳过。");
            return null;
        }
    }

    private String taskHistoryTime(ProductPublishTaskRecord task) {
        if (task == null) {
            return null;
        }
        return firstNonBlank(
                formatDateTime(task.getFinishedAt()),
                formatDateTime(task.getVerifyFinishedAt()),
                formatDateTime(task.getSubmittedAt()),
                formatDateTime(task.getLockedAt())
        );
    }

    private String publishTaskHistoryMessage(ProductPublishTaskRecord task) {
        String status = task == null ? null : normalize(task.getStatus());
        if ("synced".equalsIgnoreCase(status)) {
            return "发布已完成。";
        }
        if ("failed".equalsIgnoreCase(status)) {
            return firstNonBlank(task.getErrorMessage(), "发布失败，诺诺草稿已保留。");
        }
        if ("pending_manual_check".equalsIgnoreCase(status)) {
            return "发布结果需要人工核对。";
        }
        if ("pending_effective".equalsIgnoreCase(status)) {
            return "Noon 可能延迟生效，系统仍在回读校验。";
        }
        if ("write_unknown".equalsIgnoreCase(status) || "verify_timeout".equalsIgnoreCase(status)) {
            return "发布结果暂未确认，系统只回读校验。";
        }
        if ("queued".equalsIgnoreCase(status)) {
            return "发布已排队。";
        }
        if ("running".equalsIgnoreCase(status) || "submitted".equalsIgnoreCase(status) || "verifying".equalsIgnoreCase(status)) {
            return "发布处理中。";
        }
        return "发布任务已记录。";
    }

    private String publishTaskStatusLabel(String status) {
        String normalized = normalize(status);
        if ("synced".equalsIgnoreCase(normalized)) {
            return "发布成功";
        }
        if ("failed".equalsIgnoreCase(normalized)) {
            return "发布失败";
        }
        if ("pending_manual_check".equalsIgnoreCase(normalized)) {
            return "待人工核对";
        }
        if ("pending_effective".equalsIgnoreCase(normalized)) {
            return "等待生效";
        }
        if ("write_unknown".equalsIgnoreCase(normalized) || "verify_timeout".equalsIgnoreCase(normalized)) {
            return "结果待确认";
        }
        if ("queued".equalsIgnoreCase(normalized)) {
            return "排队中";
        }
        if ("running".equalsIgnoreCase(normalized) || "submitted".equalsIgnoreCase(normalized) || "verifying".equalsIgnoreCase(normalized)) {
            return "发布中";
        }
        if ("cancelled".equalsIgnoreCase(normalized)) {
            return "已取消";
        }
        return StringUtils.hasText(normalized) ? normalized : "已记录";
    }

    private String actionResultStatusLabel(String actionType, String resultStatus) {
        String normalizedStatus = normalize(resultStatus);
        if ("save".equalsIgnoreCase(actionType)) {
            return "已保存草稿";
        }
        if ("rollback-draft".equalsIgnoreCase(actionType)) {
            return "已回滚草稿";
        }
        if ("publish-current".equalsIgnoreCase(actionType) && "failed".equalsIgnoreCase(normalizedStatus)) {
            return "发布失败";
        }
        if ("publish-current".equalsIgnoreCase(actionType) && "pending_manual_check".equalsIgnoreCase(normalizedStatus)) {
            return "待人工核对";
        }
        if ("publish-current".equalsIgnoreCase(actionType) && "synced".equalsIgnoreCase(normalizedStatus)) {
            return "发布成功";
        }
        return StringUtils.hasText(normalizedStatus) ? normalizedStatus : "已记录";
    }

    private boolean shouldCaptureActionChanges(String actionType) {
        return "save".equalsIgnoreCase(actionType) || "publish-current".equalsIgnoreCase(actionType);
    }

    private void sortHistoryItemsByPublishedAt(List<Map<String, Object>> items) {
        if (items == null || items.size() < 2) {
            return;
        }
        items.sort((left, right) -> {
            String leftTime = text(left.get("publishedAt"));
            String rightTime = text(right.get("publishedAt"));
            if (!StringUtils.hasText(leftTime) && !StringUtils.hasText(rightTime)) {
                return 0;
            }
            if (!StringUtils.hasText(leftTime)) {
                return 1;
            }
            if (!StringUtils.hasText(rightTime)) {
                return -1;
            }
            return rightTime.compareTo(leftTime);
        });
    }

    private List<Map<String, Object>> buildProductModificationChanges(
            ProductMasterSnapshotView before,
            ProductMasterSnapshotView after,
            String currentSiteCode
    ) {
        List<Map<String, Object>> changes = new ArrayList<>();
        if (before == null || after == null) {
            return changes;
        }

        addModificationChange(changes, "content", "title_en", "英文标题",
                before.getContent().get("titleEn"), after.getContent().get("titleEn"));
        addModificationChange(changes, "content", "title_ar", "阿语标题",
                before.getContent().get("titleAr"), after.getContent().get("titleAr"));
        addModificationChange(changes, "content", "long_description_en", "英文长描述",
                before.getContent().get("descriptionEn"), after.getContent().get("descriptionEn"));
        addModificationChange(changes, "content", "long_description_ar", "阿语长描述",
                before.getContent().get("descriptionAr"), after.getContent().get("descriptionAr"));
        addModificationChange(changes, "content", "feature_bullet_en", "英文卖点",
                before.getContent().get("highlightsEn"), after.getContent().get("highlightsEn"));
        addModificationChange(changes, "content", "feature_bullet_ar", "阿语卖点",
                before.getContent().get("highlightsAr"), after.getContent().get("highlightsAr"));
        addModificationChange(changes, "content", "images", "图片",
                imageHistoryValue(before.getContent().get("images")), imageHistoryValue(after.getContent().get("images")));
        addModificationChange(changes, "content", "brand", "品牌",
                before.getIdentity().get("brand"), after.getIdentity().get("brand"));
        addModificationChange(changes, "content", "product_fulltype", "类目",
                before.getTaxonomy().get("productFulltype"), after.getTaxonomy().get("productFulltype"));

        changes.addAll(buildOfferModificationChanges(before, after, currentSiteCode));
        addVariantSizeChanges(changes, before.getVariants(), after.getVariants());

        Map<String, Map<String, Object>> beforeAttributes = keyAttributeMap(before.getKeyAttributes());
        Map<String, Map<String, Object>> afterAttributes = keyAttributeMap(after.getKeyAttributes());
        List<String> attributeCodes = new ArrayList<>(beforeAttributes.keySet());
        for (String code : afterAttributes.keySet()) {
            if (!attributeCodes.contains(code)) {
                attributeCodes.add(code);
            }
        }
        for (String code : attributeCodes) {
            if (isDuplicatedHistoryAttribute(code)) {
                continue;
            }
            Map<String, Object> beforeAttribute = beforeAttributes.get(code);
            Map<String, Object> afterAttribute = afterAttributes.get(code);
            addModificationChange(
                    changes,
                    "attribute",
                    code,
                    attributeHistoryLabel(code, beforeAttribute, afterAttribute),
                    keyAttributeHistoryValue(beforeAttribute),
                    keyAttributeHistoryValue(afterAttribute)
            );
        }
        return changes;
    }

    private List<Map<String, Object>> buildOfferModificationChanges(
            ProductMasterSnapshotView before,
            ProductMasterSnapshotView after,
            String currentSiteCode
    ) {
        List<Map<String, Object>> changes = new ArrayList<>();
        Map<String, Object> beforeOffer = siteOfferForHistory(before, currentSiteCode);
        Map<String, Object> afterOffer = siteOfferForHistory(after, currentSiteCode);
        addModificationChange(changes, "offer", "price", "价格",
                offerValue(before, beforeOffer, "price"), offerValue(after, afterOffer, "price"));
        addModificationChange(changes, "offer", "salePrice", "活动价",
                offerValue(before, beforeOffer, "salePrice"), offerValue(after, afterOffer, "salePrice"));
        addModificationChange(changes, "offer", "saleStart", "活动开始",
                offerValue(before, beforeOffer, "saleStart"), offerValue(after, afterOffer, "saleStart"));
        addModificationChange(changes, "offer", "saleEnd", "活动结束",
                offerValue(before, beforeOffer, "saleEnd"), offerValue(after, afterOffer, "saleEnd"));
        addModificationChange(changes, "offer", "priceMin", "价格下限",
                offerValue(before, beforeOffer, "priceMin"), offerValue(after, afterOffer, "priceMin"));
        addModificationChange(changes, "offer", "priceMax", "价格上限",
                offerValue(before, beforeOffer, "priceMax"), offerValue(after, afterOffer, "priceMax"));
        addModificationChange(changes, "offer", "isActive", "在线状态",
                offerValue(before, beforeOffer, "isActive"), offerValue(after, afterOffer, "isActive"));
        addModificationChange(changes, "offer", "idWarranty", "质保",
                offerValue(before, beforeOffer, "idWarranty"), offerValue(after, afterOffer, "idWarranty"));
        addModificationChange(changes, "offer", "offerNote", "经营备注",
                offerValue(before, beforeOffer, "offerNote"), offerValue(after, afterOffer, "offerNote"));
        return changes;
    }

    private List<Map<String, Object>> buildOfferPriceModificationChanges(
            ProductMasterSnapshotView before,
            ProductMasterSnapshotView after,
            String currentSiteCode
    ) {
        List<Map<String, Object>> changes = new ArrayList<>();
        if (before == null || after == null) {
            return changes;
        }
        Map<String, Object> beforeOffer = siteOfferForHistory(before, currentSiteCode);
        Map<String, Object> afterOffer = siteOfferForHistory(after, currentSiteCode);
        addModificationChange(changes, "offer", "price", "价格",
                offerValue(before, beforeOffer, "price"), offerValue(after, afterOffer, "price"));
        addModificationChange(changes, "offer", "salePrice", "活动价",
                offerValue(before, beforeOffer, "salePrice"), offerValue(after, afterOffer, "salePrice"));
        addModificationChange(changes, "offer", "priceMin", "价格下限",
                offerValue(before, beforeOffer, "priceMin"), offerValue(after, afterOffer, "priceMin"));
        addModificationChange(changes, "offer", "priceMax", "价格上限",
                offerValue(before, beforeOffer, "priceMax"), offerValue(after, afterOffer, "priceMax"));
        return changes;
    }

    private void addVariantSizeChanges(
            List<Map<String, Object>> changes,
            List<Map<String, Object>> beforeVariants,
            List<Map<String, Object>> afterVariants
    ) {
        Map<String, Map<String, Object>> beforeBySku = variantHistoryMap(beforeVariants);
        Map<String, Map<String, Object>> afterBySku = variantHistoryMap(afterVariants);
        List<String> childSkus = new ArrayList<>(beforeBySku.keySet());
        for (String childSku : afterBySku.keySet()) {
            if (!childSkus.contains(childSku)) {
                childSkus.add(childSku);
            }
        }
        for (String childSku : childSkus) {
            addModificationChange(
                    changes,
                    "sizes",
                    "variant_size",
                    variantSizeHistoryLabel(childSku),
                    variantSizeHistoryValue(beforeBySku.get(childSku)),
                    variantSizeHistoryValue(afterBySku.get(childSku))
            );
        }
    }

    private Map<String, Map<String, Object>> variantHistoryMap(List<Map<String, Object>> variants) {
        Map<String, Map<String, Object>> byChildSku = new LinkedHashMap<>();
        if (variants == null) {
            return byChildSku;
        }
        int fallbackIndex = 1;
        for (Map<String, Object> variant : variants) {
            if (variant == null) {
                continue;
            }
            String childSku = firstNonBlank(
                    text(variant.get("childSku")),
                    text(variant.get("sku")),
                    text(variant.get("partnerSku")),
                    text(variant.get("pskuCode"))
            );
            if (!StringUtils.hasText(childSku)) {
                childSku = "variant-" + fallbackIndex;
            }
            byChildSku.put(childSku, variant);
            fallbackIndex++;
        }
        return byChildSku;
    }

    private String variantSizeHistoryLabel(String childSku) {
        return StringUtils.hasText(childSku) && !childSku.startsWith("variant-")
                ? "尺码（" + childSku + "）"
                : "尺码";
    }

    private Object variantSizeHistoryValue(Map<String, Object> variant) {
        if (variant == null) {
            return null;
        }
        String sizeEn = firstNonBlank(
                text(variant.get("sizeEn")),
                text(variant.get("size_en")),
                text(variant.get("size")),
                text(variant.get("value"))
        );
        String sizeAr = firstNonBlank(
                text(variant.get("sizeAr")),
                text(variant.get("size_ar")),
                text(variant.get("valueAr"))
        );
        if (StringUtils.hasText(sizeEn) && StringUtils.hasText(sizeAr) && !sizeEn.equals(sizeAr)) {
            return sizeEn + " / " + sizeAr;
        }
        return firstNonBlank(sizeEn, sizeAr);
    }

    private void addModificationChange(
            List<Map<String, Object>> changes,
            String domain,
            String field,
            String label,
            Object beforeValue,
            Object afterValue
    ) {
        if (sameHistoryValue(field, beforeValue, afterValue)) {
            return;
        }
        Map<String, Object> change = new LinkedHashMap<>();
        putIfNotBlank(change, "domain", domain);
        putIfNotBlank(change, "field", field);
        putIfNotBlank(change, "label", label);
        putIfNotBlank(change, "before", historyDisplayValue(field, beforeValue));
        putIfNotBlank(change, "after", historyDisplayValue(field, afterValue));
        changes.add(change);
    }

    private boolean sameHistoryValue(Object beforeValue, Object afterValue) {
        if (beforeValue == null && afterValue == null) {
            return true;
        }
        BigDecimal beforeNumber = asBigDecimal(beforeValue);
        BigDecimal afterNumber = asBigDecimal(afterValue);
        if (beforeNumber != null && afterNumber != null) {
            return beforeNumber.compareTo(afterNumber) == 0;
        }
        return normalizeHistoryText(beforeValue).equals(normalizeHistoryText(afterValue));
    }

    private boolean sameHistoryValue(String field, Object beforeValue, Object afterValue) {
        if (isOfferDateHistoryField(field)) {
            String beforeDate = normalizeHistoryDate(beforeValue);
            String afterDate = normalizeHistoryDate(afterValue);
            if (StringUtils.hasText(beforeDate) || StringUtils.hasText(afterDate)) {
                return Objects.equals(beforeDate, afterDate);
            }
        }
        return sameHistoryValue(beforeValue, afterValue);
    }

    private boolean isOfferDateHistoryField(String field) {
        return "saleStart".equals(field) || "saleEnd".equals(field);
    }

    private String normalizeHistoryDate(Object value) {
        String rawText = text(value);
        if (!StringUtils.hasText(rawText)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawText).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return ZonedDateTime.parse(rawText).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(rawText).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(rawText, TIME_FORMATTER).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(rawText).toString();
        } catch (DateTimeParseException ignored) {
            return rawText;
        }
    }

    private String normalizeHistoryText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List || value instanceof Map) {
            String json = toJson(value);
            return json == null ? String.valueOf(value).trim() : json;
        }
        return String.valueOf(value).trim();
    }

    private String historyDisplayValue(String field, Object value) {
        if (value == null) {
            return "空";
        }
        if ("isActive".equals(field)) {
            Boolean active = asBoolean(value);
            if (active != null) {
                return active ? "在线" : "不在线";
            }
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.isEmpty() ? "空" : list.size() + " 项";
        }
        String text = text(value);
        return StringUtils.hasText(text) ? text : "空";
    }

    private Object imageHistoryValue(Object value) {
        if (value instanceof List) {
            return ((List<?>) value).size() + " 张";
        }
        List<String> images = stringList(value);
        return images.isEmpty() ? null : images.size() + " 张";
    }

    private Object offerValue(ProductMasterSnapshotView snapshot, Map<String, Object> offer, String field) {
        if (offer != null && offer.containsKey(field)) {
            return offer.get(field);
        }
        return snapshot.getPricing().get(field);
    }

    private Map<String, Object> siteOfferForHistory(ProductMasterSnapshotView snapshot, String currentSiteCode) {
        if (snapshot == null || snapshot.getSiteOffers() == null || snapshot.getSiteOffers().isEmpty()) {
            return null;
        }
        String normalizedSiteCode = normalize(currentSiteCode);
        if (StringUtils.hasText(normalizedSiteCode)) {
            for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
                if (normalizedSiteCode.equalsIgnoreCase(text(siteOffer.get("storeCode")))) {
                    return siteOffer;
                }
            }
        }
        for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
            if (Boolean.TRUE.equals(siteOffer.get("reference"))) {
                return siteOffer;
            }
        }
        return snapshot.getSiteOffers().get(0);
    }

    private Map<String, Map<String, Object>> keyAttributeMap(List<Map<String, Object>> attributes) {
        Map<String, Map<String, Object>> byCode = new LinkedHashMap<>();
        if (attributes == null) {
            return byCode;
        }
        for (Map<String, Object> attribute : attributes) {
            String code = normalize(text(attribute.get("code")));
            if (StringUtils.hasText(code)) {
                byCode.put(code, attribute);
            }
        }
        return byCode;
    }

    private boolean isDuplicatedHistoryAttribute(String code) {
        String normalized = normalize(code);
        return "product_title".equalsIgnoreCase(normalized)
                || "long_description".equalsIgnoreCase(normalized)
                || "feature_bullet".equalsIgnoreCase(normalized)
                || "brand".equalsIgnoreCase(normalized)
                || "family".equalsIgnoreCase(normalized)
                || "product_type".equalsIgnoreCase(normalized)
                || "product_subtype".equalsIgnoreCase(normalized)
                || "product_fulltype".equalsIgnoreCase(normalized);
    }

    private Object keyAttributeHistoryValue(Map<String, Object> attribute) {
        if (attribute == null) {
            return null;
        }
        String value = firstNonBlank(
                text(attribute.get("commonValue")),
                text(attribute.get("enValue")),
                text(attribute.get("value")),
                text(attribute.get("valueEn"))
        );
        String unit = firstNonBlank(text(attribute.get("unit")), text(attribute.get("unitValue")));
        if (StringUtils.hasText(value) && StringUtils.hasText(unit)) {
            return value + " " + unit;
        }
        return value;
    }

    private String attributeHistoryLabel(
            String code,
            Map<String, Object> beforeAttribute,
            Map<String, Object> afterAttribute
    ) {
        Map<String, Object> source = afterAttribute != null ? afterAttribute : beforeAttribute;
        String englishLabel = source == null ? null : text(source.get("labelEn"));
        String mappedLabel = attributeHistoryChineseLabel(code);
        return StringUtils.hasText(mappedLabel)
                ? mappedLabel + (StringUtils.hasText(englishLabel) ? " / " + englishLabel : "")
                : firstNonBlank(englishLabel, code);
    }

    private String attributeHistoryChineseLabel(String code) {
        String normalized = normalize(code);
        if ("base_material".equalsIgnoreCase(normalized)) {
            return "基础材质";
        }
        if ("colour_name".equalsIgnoreCase(normalized)) {
            return "颜色名称";
        }
        if ("colour_family".equalsIgnoreCase(normalized)) {
            return "颜色系列";
        }
        if ("number_of_pieces".equalsIgnoreCase(normalized)) {
            return "件数";
        }
        if ("set_includes".equalsIgnoreCase(normalized)) {
            return "套装包含";
        }
        if ("control_method".equalsIgnoreCase(normalized)) {
            return "控制方式";
        }
        if ("model_number".equalsIgnoreCase(normalized)) {
            return "型号";
        }
        if ("model_name".equalsIgnoreCase(normalized)) {
            return "型号名称";
        }
        if ("product_length".equalsIgnoreCase(normalized)) {
            return "商品长度";
        }
        if ("product_height".equalsIgnoreCase(normalized)) {
            return "商品高度";
        }
        if ("product_width_depth".equalsIgnoreCase(normalized)) {
            return "商品宽度/深度";
        }
        if ("product_weight".equalsIgnoreCase(normalized)) {
            return "商品重量";
        }
        return null;
    }

    private List<String> changeTypes(List<Map<String, Object>> changes) {
        List<String> types = new ArrayList<>();
        for (Map<String, Object> change : changes) {
            String domain = text(change.get("domain"));
            String type = StringUtils.hasText(domain) ? domain : text(change.get("field"));
            if (StringUtils.hasText(type) && !types.contains(type)) {
                types.add(type);
            }
        }
        return types;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> objectMapList(Object value) {
        List<Map<String, Object>> records = new ArrayList<>();
        if (!(value instanceof List)) {
            return records;
        }
        for (Object item : (List<?>) value) {
            if (item instanceof Map) {
                records.add(new LinkedHashMap<>((Map<String, Object>) item));
            }
        }
        return records;
    }

    private List<Map<String, Object>> loadVisibleKeyContentHistory(
            Long productMasterId,
            Long ownerUserId,
            List<String> warnings
    ) {
        List<Map<String, Object>> history = new ArrayList<>();
        if (productMasterId == null) {
            return history;
        }

        productManagementMapper.promoteVisibleProductKeyContentHistory(
                productMasterId,
                LocalDateTime.now(),
                ownerUserId
        );

        for (ProductKeyContentHistoryRecord record :
                productManagementMapper.selectVisibleProductKeyContentHistories(productMasterId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            if (StringUtils.hasText(record.getSummaryJson())) {
                try {
                    item.putAll(objectMapper.readValue(
                            record.getSummaryJson(),
                            new TypeReference<LinkedHashMap<String, Object>>() {
                            }
                    ));
                } catch (JsonProcessingException exception) {
                    addWarningOnce(warnings, "读取关键内容变更历史失败，已按基础字段回退。");
                }
            }
            if (StringUtils.hasText(record.getChangeTypesJson())) {
                try {
                    item.put(
                            "changeTypes",
                            objectMapper.readValue(record.getChangeTypesJson(), new TypeReference<List<String>>() {
                            })
                    );
                } catch (JsonProcessingException exception) {
                    addWarningOnce(warnings, "读取关键内容变更类型失败，已按基础字段回退。");
                }
            }
            putIfNotBlank(item, "sourceActionType", record.getSourceActionType());
            putIfNotBlank(item, "visibilityStatus", record.getVisibilityStatus());
            putIfNotBlank(item, "publishedAt", formatDateTime(record.getPublishedAt()));
            putIfNotBlank(item, "visibleAfter", formatDateTime(record.getVisibleAfter()));
            putIfNotBlank(item, "visibleAt", formatDateTime(record.getVisibleAt()));
            history.add(item);
        }
        return history;
    }

    private List<Map<String, Object>> loadPendingKeyContentHistory(
            Long productMasterId,
            List<String> warnings
    ) {
        List<Map<String, Object>> history = new ArrayList<>();
        if (productMasterId == null) {
            return history;
        }
        for (ProductKeyContentHistoryRecord record :
                productManagementMapper.selectPendingProductKeyContentHistories(productMasterId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            if (StringUtils.hasText(record.getSummaryJson())) {
                try {
                    item.putAll(objectMapper.readValue(
                            record.getSummaryJson(),
                            new TypeReference<LinkedHashMap<String, Object>>() {
                            }
                    ));
                } catch (JsonProcessingException exception) {
                    addWarningOnce(warnings, "读取待转正式关键内容历史失败，已按基础字段回退。");
                }
            }
            if (StringUtils.hasText(record.getChangeTypesJson())) {
                try {
                    item.put(
                            "changeTypes",
                            objectMapper.readValue(record.getChangeTypesJson(), new TypeReference<List<String>>() {
                            })
                    );
                } catch (JsonProcessingException exception) {
                    addWarningOnce(warnings, "读取待转正式关键内容变更类型失败，已按基础字段回退。");
                }
            }
            putIfNotBlank(item, "sourceActionType", record.getSourceActionType());
            putIfNotBlank(item, "visibilityStatus", record.getVisibilityStatus());
            putIfNotBlank(item, "publishedAt", formatDateTime(record.getPublishedAt()));
            putIfNotBlank(item, "visibleAfter", formatDateTime(record.getVisibleAfter()));
            putIfNotBlank(item, "visibleAt", formatDateTime(record.getVisibleAt()));
            history.add(item);
        }
        return history;
    }

    private Integer loadPendingKeyContentHistoryCount(Long productMasterId) {
        if (productMasterId == null) {
            return 0;
        }
        Integer count = productManagementMapper.countPendingProductKeyContentHistories(productMasterId);
        return count == null ? 0 : count;
    }

    private Integer loadVisibleKeyContentHistoryCount(Long productMasterId) {
        if (productMasterId == null) {
            return 0;
        }
        Integer count = productManagementMapper.countVisibleProductKeyContentHistories(productMasterId);
        return count == null ? 0 : count;
    }

    private LocalDateTime loadPendingKeyContentHistoryVisibleAfter(Long productMasterId) {
        if (productMasterId == null) {
            return null;
        }
        return productManagementMapper.selectEarliestPendingProductKeyContentVisibleAfter(productMasterId);
    }

    private Long resolveTargetSiteId(String targetSiteCode) {
        return StringUtils.hasText(targetSiteCode)
                ? productManagementMapper.selectLogicalStoreSiteId(normalize(targetSiteCode))
                : null;
    }

    private ProductMasterSnapshotView readSnapshot(String snapshotJson, String warningMessage, List<String> warnings) {
        if (!StringUtils.hasText(snapshotJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(snapshotJson, ProductMasterSnapshotView.class);
        } catch (JsonProcessingException exception) {
            addWarningOnce(warnings, warningMessage);
            return null;
        }
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException exception) {
            return new ArrayList<>();
        }
    }

    private List<String> stringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                String text = text(item);
                if (StringUtils.hasText(text)) {
                    values.add(text);
                }
            }
            return values;
        }
        String text = text(value);
        if (StringUtils.hasText(text)) {
            values.add(text);
        }
        return values;
    }

    private List<Map<String, Object>> objectList(Object value) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (!(value instanceof List<?>)) {
            return values;
        }
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                String key = text(entry.getKey());
                if (StringUtils.hasText(key)) {
                    normalized.put(key, entry.getValue());
                }
            }
            values.add(normalized);
        }
        return values;
    }

    private List<String> collectSnapshotIssueTags(ProductMasterSnapshotView snapshot) {
        List<String> issues = new ArrayList<>();
        if (snapshot == null) {
            return issues;
        }
        Map<String, Object> platformSignals = snapshot.getPlatformSignals();
        addIssueTags(issues, stringList(platformSignals.get("rejectionReasons")));
        addIssueTags(issues, stringList(platformSignals.get("affectingAttributes")));
        String completeness = text(platformSignals.get("completenessLocalized"));
        if (StringUtils.hasText(completeness) && !"complete".equalsIgnoreCase(completeness)) {
            addIssueTags(issues, List.of(completeness));
        }
        return issues;
    }

    private void addIssueTags(List<String> target, List<String> values) {
        if (target == null || values == null) {
            return;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (StringUtils.hasText(normalized) && !target.contains(normalized)) {
                target.add(normalized);
            }
        }
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

    private boolean hasAnyPresentValue(Map<String, Object> source, List<String> keys) {
        if (source == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof String) {
                if (StringUtils.hasText((String) value)) {
                    return true;
                }
                continue;
            }
            return true;
        }
        return false;
    }

    private String resolveOfferIdentity(ProductMasterSnapshotView snapshot, String targetSiteCode, String field) {
        if (snapshot == null) {
            return null;
        }
        for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
            String siteCode = text(siteOffer.get("storeCode"));
            if (!StringUtils.hasText(targetSiteCode) || targetSiteCode.equalsIgnoreCase(siteCode)) {
                return text(siteOffer.get(field));
            }
        }
        return null;
    }

    private Long resolveTargetSiteId(ProductMasterWorkbenchView workbench, String targetSiteCode) {
        if (workbench == null || !StringUtils.hasText(targetSiteCode)) {
            return null;
        }
        return productManagementMapper.selectLogicalStoreSiteId(normalize(targetSiteCode));
    }

    private String stageFor(String actionType) {
        if ("publish-current".equalsIgnoreCase(actionType)) {
            return "publish";
        }
        if ("pull".equalsIgnoreCase(actionType)) {
            return "sync";
        }
        if ("rollback-draft".equalsIgnoreCase(actionType)) {
            return "rollback";
        }
        return "workbench";
    }

    private LocalDateTime parseSnapshotTime(ProductMasterSnapshotView snapshot, String fallbackTime) {
        String fetchedAt = snapshot != null ? text(snapshot.getStoreContext().get("fetchedAt")) : null;
        LocalDateTime parsed = parseDateTime(fetchedAt);
        if (parsed != null) {
            return parsed;
        }
        parsed = parseDateTime(fallbackTime);
        return parsed != null ? parsed : LocalDateTime.now();
    }

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte current : encoded) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前环境不支持 SHA-256。", exception);
        }
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : TIME_FORMATTER.format(value);
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (target == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        target.put(key, value);
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (target == null || !StringUtils.hasText(key) || value == null) {
            return;
        }
        target.put(key, value);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private BigDecimal resolveFinalPrice(Map<String, Object> siteOffer) {
        BigDecimal explicitFinalPrice = asBigDecimal(siteOffer.get("finalPrice"));
        if (explicitFinalPrice != null) {
            return explicitFinalPrice;
        }
        BigDecimal promoPrice = asBigDecimal(firstNonNull(
                siteOffer.get("promoPrice"),
                firstNonNull(siteOffer.get("promotionPrice"), siteOffer.get("dealPrice"))
        ));
        if (promoPrice != null) {
            return promoPrice;
        }
        BigDecimal salePrice = asBigDecimal(siteOffer.get("salePrice"));
        return salePrice != null ? salePrice : asBigDecimal(siteOffer.get("price"));
    }

    private String resolveFinalPriceSource(Map<String, Object> siteOffer) {
        String explicitSource = normalize(text(siteOffer.get("finalPriceSource")));
        if (StringUtils.hasText(explicitSource)) {
            String normalizedSource = explicitSource.toLowerCase();
            if (normalizedSource.contains("promo") || normalizedSource.contains("campaign") || normalizedSource.contains("deal")) {
                return "promo";
            }
            if (normalizedSource.contains("sale")) {
                return "sale";
            }
            if (normalizedSource.contains("base")) {
                return finalPriceBelowReference(siteOffer) ? "promo" : "base";
            }
        }
        String promotionCode = firstNonBlank(
                text(siteOffer.get("activePromotionCode")),
                text(siteOffer.get("promotionCode")),
                text(siteOffer.get("promoCode")),
                text(siteOffer.get("campaignCode")),
                text(siteOffer.get("activePromotionName")),
                text(siteOffer.get("promotionName")),
                text(siteOffer.get("promoName"))
        );
        if (StringUtils.hasText(promotionCode)) {
            return "promo";
        }
        if (finalPriceBelowReference(siteOffer)) {
            return "promo";
        }
        if (asBigDecimal(siteOffer.get("salePrice")) != null) {
            return "sale";
        }
        if (asBigDecimal(siteOffer.get("price")) != null) {
            return "base";
        }
        return "unknown";
    }

    private boolean finalPriceBelowReference(Map<String, Object> siteOffer) {
        BigDecimal finalPrice = resolveFinalPrice(siteOffer);
        if (finalPrice == null) {
            return false;
        }
        BigDecimal salePrice = asBigDecimal(siteOffer.get("salePrice"));
        if (salePrice != null && finalPrice.compareTo(salePrice) < 0) {
            return true;
        }
        BigDecimal price = asBigDecimal(siteOffer.get("price"));
        return price != null && finalPrice.compareTo(price) < 0;
    }

    private String promotionPayloadJson(Map<String, Object> siteOffer) {
        Map<String, Object> promotion = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : siteOffer.entrySet()) {
            String key = entry.getKey();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String normalizedKey = key.toLowerCase();
            if (normalizedKey.contains("promo")
                    || normalizedKey.contains("promotion")
                    || normalizedKey.contains("campaign")
                    || normalizedKey.contains("deal")) {
                promotion.put(key, entry.getValue());
            }
        }
        return promotion.isEmpty() ? null : toJson(promotion);
    }

    private List<SiteSeed> buildSiteSeeds(ProductMasterSnapshotView snapshot) {
        List<SiteSeed> seeds = new ArrayList<>();
        if (snapshot == null) {
            return seeds;
        }
        String referenceStoreCode = text(snapshot.getStoreContext().get("storeCode"));
        String referenceSite = text(snapshot.getStoreContext().get("site"));
        if (StringUtils.hasText(referenceStoreCode)) {
            seeds.add(new SiteSeed(referenceStoreCode, referenceSite, null, true));
        }
        for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
            String storeCode = text(siteOffer.get("storeCode"));
            if (!StringUtils.hasText(storeCode)) {
                continue;
            }
            seeds.add(new SiteSeed(
                    storeCode,
                    text(siteOffer.get("site")),
                    text(siteOffer.get("statusCode")),
                    true
            ));
        }
        return dedupeSites(seeds);
    }

    private List<SiteSeed> dedupeSites(List<SiteSeed> seeds) {
        Map<String, SiteSeed> byStoreCode = new LinkedHashMap<>();
        for (SiteSeed seed : seeds) {
            if (!StringUtils.hasText(seed.getStoreCode())) {
                continue;
            }
            byStoreCode.put(normalize(seed.getStoreCode()), seed);
        }
        return new ArrayList<>(byStoreCode.values());
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String brandKey(String value) {
        String normalized = normalize(value);
        return StringUtils.hasText(normalized) ? normalized.toLowerCase(Locale.ROOT) : null;
    }

    private String normalizeSyncStatus(String value) {
        String normalized = normalize(value);
        return "conflict".equalsIgnoreCase(normalized) ? "draft" : normalized;
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDateTime.parse(trimmed, TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (DateTimeParseException exception) {
            // fall through
        }
        try {
            return ZonedDateTime.parse(trimmed).toLocalDateTime();
        } catch (DateTimeParseException exception) {
            // fall through
        }
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException exception) {
            // fall through
        }
        try {
            return LocalDate.parse(trimmed).atStartOfDay();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value));
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "active".equalsIgnoreCase(text);
    }

    private void addWarningOnce(List<String> warnings, String message) {
        if (warnings == null || !StringUtils.hasText(message)) {
            return;
        }
        if (!warnings.contains(message)) {
            warnings.add(message);
        }
    }

    public static class SiteSeed {
        private final String storeCode;
        private final String site;
        private final String siteStatus;
        private final boolean mounted;

        public SiteSeed(String storeCode, String site, String siteStatus, boolean mounted) {
            this.storeCode = storeCode;
            this.site = site;
            this.siteStatus = siteStatus;
            this.mounted = mounted;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public String getSite() {
            return site;
        }

        public String getSiteStatus() {
            return siteStatus;
        }

        public boolean isMounted() {
            return mounted;
        }
    }

    public static class ProductMasterSeed {
        private String skuParent;
        private String productSourceType;
        private String brandCache;
        private String titleCache;
        private String productFulltypeCache;
        private String coverImageUrl;
        private String skuGroup;
        private String groupNameCache;
        private String groupRef;
        private Integer groupMemberCount;
        private Integer issueCount;
        private Map<String, Object> issueSummary;
        private Integer variantCountCache;
        private String syncStatus;
        private String lastSyncedAt;
        private String partnerSku;
        private String childSku;
        private String barcode;
        private String pskuCode;
        private String offerCode;
        private String referenceStoreCode;
        private String originalPrice;
        private String salePrice;
        private String finalPrice;
        private String finalPriceSource;
        private String deliveryMethod;
        private Boolean isWinningBuybox;
        private Long viewsCount;
        private Long unitsSold;
        private String salesAmount;
        private String salesCurrency;
        private String liveStatus;
        private String statusCode;
        private Boolean isActive;
        private Integer fbnStock;
        private Integer supermallStock;
        private Integer fbpStock;
        private List<String> issueTags = new ArrayList<>();
        private List<SiteOfferSeed> siteOffers = new ArrayList<>();

        public static ProductMasterSeed fromSnapshot(
                ProductMasterSnapshotView snapshot,
                String syncStatus,
                String lastSyncedAt
        ) {
            ProductMasterSeed seed = new ProductMasterSeed();
            seed.setSkuParent(stringValue(snapshot.getIdentity().get("skuParent")));
            seed.setProductSourceType(ProductSourceTypeSupport.resolve(
                    stringValue(snapshot.getIdentity().get("productSourceType")),
                    stringValue(snapshot.getIdentity().get("childSku")),
                    stringValue(snapshot.getIdentity().get("skuParent"))
            ));
            seed.setBrandCache(stringValue(snapshot.getIdentity().get("brand")));
            seed.setTitleCache(stringValue(snapshot.getContent().get("titleEn")));
            seed.setProductFulltypeCache(stringValue(snapshot.getTaxonomy().get("productFulltype")));
            seed.setCoverImageUrl(firstString(snapshot.getContent().get("images")));
            seed.setSkuGroup(stringValue(snapshot.getGroup().get("skuGroup")));
            seed.setGroupNameCache(firstNonBlankStatic(
                    stringValue(snapshot.getGroup().get("groupName")),
                    stringValue(snapshot.getGroup().get("groupRef")),
                    stringValue(snapshot.getGroup().get("groupRefCanonical")),
                    stringValue(snapshot.getGroup().get("skuGroup"))
            ));
            seed.setGroupRef(stringValue(snapshot.getGroup().get("groupRef")));
            seed.setGroupMemberCount(intValue(snapshot.getGroup().get("memberCount")));
            seed.setIssueCount(0);
            seed.setVariantCountCache(intValue(snapshot.getIdentity().get("variantCount")));
            seed.setSyncStatus(syncStatus);
            seed.setLastSyncedAt(lastSyncedAt);
            return seed;
        }

        private static String firstNonBlankStatic(String... values) {
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

        private static String stringValue(Object value) {
            if (value == null) {
                return null;
            }
            String text = String.valueOf(value).trim();
            return StringUtils.hasText(text) ? text : null;
        }

        private static Integer intValue(Object value) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            String text = stringValue(value);
            if (!StringUtils.hasText(text)) {
                return null;
            }
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        private static String firstString(Object value) {
            if (!(value instanceof List<?>)) {
                return null;
            }
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return null;
            }
            Object first = list.get(0);
            return stringValue(first);
        }

        public String getSkuParent() {
            return skuParent;
        }

        public void setSkuParent(String skuParent) {
            this.skuParent = skuParent;
        }

        public String getProductSourceType() {
            return productSourceType;
        }

        public void setProductSourceType(String productSourceType) {
            this.productSourceType = productSourceType;
        }

        public String getBrandCache() {
            return brandCache;
        }

        public void setBrandCache(String brandCache) {
            this.brandCache = brandCache;
        }

        public String getTitleCache() {
            return titleCache;
        }

        public void setTitleCache(String titleCache) {
            this.titleCache = titleCache;
        }

        public String getProductFulltypeCache() {
            return productFulltypeCache;
        }

        public void setProductFulltypeCache(String productFulltypeCache) {
            this.productFulltypeCache = productFulltypeCache;
        }

        public String getCoverImageUrl() {
            return coverImageUrl;
        }

        public void setCoverImageUrl(String coverImageUrl) {
            this.coverImageUrl = coverImageUrl;
        }

        public String getSkuGroup() {
            return skuGroup;
        }

        public void setSkuGroup(String skuGroup) {
            this.skuGroup = skuGroup;
        }

        public String getGroupNameCache() {
            return groupNameCache;
        }

        public void setGroupNameCache(String groupNameCache) {
            this.groupNameCache = groupNameCache;
        }

        public String getGroupRef() {
            return groupRef;
        }

        public void setGroupRef(String groupRef) {
            this.groupRef = groupRef;
        }

        public Integer getGroupMemberCount() {
            return groupMemberCount;
        }

        public void setGroupMemberCount(Integer groupMemberCount) {
            this.groupMemberCount = groupMemberCount;
        }

        public Integer getIssueCount() {
            return issueCount;
        }

        public void setIssueCount(Integer issueCount) {
            this.issueCount = issueCount;
        }

        public Map<String, Object> getIssueSummary() {
            return issueSummary;
        }

        public void setIssueSummary(Map<String, Object> issueSummary) {
            this.issueSummary = issueSummary;
        }

        public Integer getVariantCountCache() {
            return variantCountCache;
        }

        public void setVariantCountCache(Integer variantCountCache) {
            this.variantCountCache = variantCountCache;
        }

        public String getSyncStatus() {
            return syncStatus;
        }

        public void setSyncStatus(String syncStatus) {
            this.syncStatus = syncStatus;
        }

        public String getLastSyncedAt() {
            return lastSyncedAt;
        }

        public void setLastSyncedAt(String lastSyncedAt) {
            this.lastSyncedAt = lastSyncedAt;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public void setPartnerSku(String partnerSku) {
            this.partnerSku = partnerSku;
        }

        public String getChildSku() {
            return childSku;
        }

        public void setChildSku(String childSku) {
            this.childSku = childSku;
        }

        public String getBarcode() {
            return barcode;
        }

        public void setBarcode(String barcode) {
            this.barcode = barcode;
        }

        public String getPskuCode() {
            return pskuCode;
        }

        public void setPskuCode(String pskuCode) {
            this.pskuCode = pskuCode;
        }

        public String getOfferCode() {
            return offerCode;
        }

        public void setOfferCode(String offerCode) {
            this.offerCode = offerCode;
        }

        public String getReferenceStoreCode() {
            return referenceStoreCode;
        }

        public void setReferenceStoreCode(String referenceStoreCode) {
            this.referenceStoreCode = referenceStoreCode;
        }

        public String getOriginalPrice() {
            return originalPrice;
        }

        public void setOriginalPrice(String originalPrice) {
            this.originalPrice = originalPrice;
        }

        public String getSalePrice() {
            return salePrice;
        }

        public void setSalePrice(String salePrice) {
            this.salePrice = salePrice;
        }

        public String getFinalPrice() {
            return finalPrice;
        }

        public void setFinalPrice(String finalPrice) {
            this.finalPrice = finalPrice;
        }

        public String getFinalPriceSource() {
            return finalPriceSource;
        }

        public void setFinalPriceSource(String finalPriceSource) {
            this.finalPriceSource = finalPriceSource;
        }

        public String getDeliveryMethod() {
            return deliveryMethod;
        }

        public void setDeliveryMethod(String deliveryMethod) {
            this.deliveryMethod = deliveryMethod;
        }

        public Boolean getIsWinningBuybox() {
            return isWinningBuybox;
        }

        public void setIsWinningBuybox(Boolean winningBuybox) {
            isWinningBuybox = winningBuybox;
        }

        public Long getViewsCount() {
            return viewsCount;
        }

        public void setViewsCount(Long viewsCount) {
            this.viewsCount = viewsCount;
        }

        public Long getUnitsSold() {
            return unitsSold;
        }

        public void setUnitsSold(Long unitsSold) {
            this.unitsSold = unitsSold;
        }

        public String getSalesAmount() {
            return salesAmount;
        }

        public void setSalesAmount(String salesAmount) {
            this.salesAmount = salesAmount;
        }

        public String getSalesCurrency() {
            return salesCurrency;
        }

        public void setSalesCurrency(String salesCurrency) {
            this.salesCurrency = salesCurrency;
        }

        public String getLiveStatus() {
            return liveStatus;
        }

        public void setLiveStatus(String liveStatus) {
            this.liveStatus = liveStatus;
        }

        public String getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(String statusCode) {
            this.statusCode = statusCode;
        }

        public Boolean getIsActive() {
            return isActive;
        }

        public void setIsActive(Boolean isActive) {
            this.isActive = isActive;
        }

        public Integer getFbnStock() {
            return fbnStock;
        }

        public void setFbnStock(Integer fbnStock) {
            this.fbnStock = fbnStock;
        }

        public Integer getSupermallStock() {
            return supermallStock;
        }

        public void setSupermallStock(Integer supermallStock) {
            this.supermallStock = supermallStock;
        }

        public Integer getFbpStock() {
            return fbpStock;
        }

        public void setFbpStock(Integer fbpStock) {
            this.fbpStock = fbpStock;
        }

        public List<String> getIssueTags() {
            return issueTags;
        }

        public void setIssueTags(List<String> issueTags) {
            this.issueTags = issueTags == null ? new ArrayList<>() : issueTags;
            this.issueCount = this.issueTags.size();
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("tags", this.issueTags);
            this.issueSummary = this.issueTags.isEmpty() ? null : summary;
        }

        public List<SiteOfferSeed> getSiteOffers() {
            return siteOffers;
        }

        public void setSiteOffers(List<SiteOfferSeed> siteOffers) {
            this.siteOffers = siteOffers == null ? new ArrayList<>() : siteOffers;
        }

        public void addSiteOffer(SiteOfferSeed siteOffer) {
            if (siteOffer != null && StringUtils.hasText(siteOffer.getStoreCode())) {
                siteOffers.add(siteOffer);
            }
        }

        public VariantSeed toRepresentativeVariantSeed() {
            VariantSeed seed = new VariantSeed();
            seed.setPartnerSku(partnerSku);
            seed.setChildSku(childSku);
            return seed;
        }
    }

    private static class FulltypeParts {
        private final String family;
        private final String productType;
        private final String productSubtype;

        private FulltypeParts(String family, String productType, String productSubtype) {
            this.family = family;
            this.productType = productType;
            this.productSubtype = productSubtype;
        }

        private static FulltypeParts from(String productFulltype) {
            if (!StringUtils.hasText(productFulltype)) {
                return new FulltypeParts(null, null, null);
            }
            String[] rawParts = productFulltype.split("-");
            List<String> parts = new ArrayList<>();
            for (String rawPart : rawParts) {
                String part = rawPart == null ? null : rawPart.trim();
                if (StringUtils.hasText(part)) {
                    parts.add(part);
                }
            }
            String family = parts.isEmpty() ? null : parts.get(0);
            String productType = parts.size() > 1 ? parts.get(1) : null;
            String productSubtype = parts.size() > 2 ? String.join("-", parts.subList(2, parts.size())) : null;
            return new FulltypeParts(family, productType, productSubtype);
        }
    }

    public static class SiteOfferSeed {
        private String storeCode;
        private String pskuCode;
        private String offerCode;
        private String currency;
        private String barcode;
        private String originalPrice;
        private String salePrice;
        private String finalPrice;
        private String finalPriceSource;
        private String activePromotionCode;
        private String activePromotionName;
        private String activePromotionUrl;
        private String deliveryMethod;
        private Boolean isWinningBuybox;
        private Long viewsCount;
        private Long unitsSold;
        private String salesAmount;
        private String salesCurrency;
        private String liveStatus;
        private String statusCode;
        private Boolean isActive;
        private Integer fbnStock;
        private Integer supermallStock;
        private Integer fbpStock;

        public static SiteOfferSeed fromRepresentative(ProductMasterSeed seed) {
            SiteOfferSeed siteOfferSeed = new SiteOfferSeed();
            siteOfferSeed.setStoreCode(seed.getReferenceStoreCode());
            siteOfferSeed.setPskuCode(seed.getPskuCode());
            siteOfferSeed.setOfferCode(seed.getOfferCode());
            siteOfferSeed.setBarcode(seed.getBarcode());
            siteOfferSeed.setOriginalPrice(seed.getOriginalPrice());
            siteOfferSeed.setSalePrice(seed.getSalePrice());
            siteOfferSeed.setFinalPrice(seed.getFinalPrice());
            siteOfferSeed.setFinalPriceSource(seed.getFinalPriceSource());
            siteOfferSeed.setDeliveryMethod(seed.getDeliveryMethod());
            siteOfferSeed.setIsWinningBuybox(seed.getIsWinningBuybox());
            siteOfferSeed.setViewsCount(seed.getViewsCount());
            siteOfferSeed.setUnitsSold(seed.getUnitsSold());
            siteOfferSeed.setSalesAmount(seed.getSalesAmount());
            siteOfferSeed.setSalesCurrency(seed.getSalesCurrency());
            siteOfferSeed.setLiveStatus(seed.getLiveStatus());
            siteOfferSeed.setStatusCode(seed.getStatusCode());
            siteOfferSeed.setIsActive(seed.getIsActive());
            siteOfferSeed.setFbnStock(seed.getFbnStock());
            siteOfferSeed.setSupermallStock(seed.getSupermallStock());
            siteOfferSeed.setFbpStock(seed.getFbpStock());
            return siteOfferSeed;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public String getPskuCode() {
            return pskuCode;
        }

        public void setPskuCode(String pskuCode) {
            this.pskuCode = pskuCode;
        }

        public String getOfferCode() {
            return offerCode;
        }

        public void setOfferCode(String offerCode) {
            this.offerCode = offerCode;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getBarcode() {
            return barcode;
        }

        public void setBarcode(String barcode) {
            this.barcode = barcode;
        }

        public String getOriginalPrice() {
            return originalPrice;
        }

        public void setOriginalPrice(String originalPrice) {
            this.originalPrice = originalPrice;
        }

        public String getSalePrice() {
            return salePrice;
        }

        public void setSalePrice(String salePrice) {
            this.salePrice = salePrice;
        }

        public String getFinalPrice() {
            return finalPrice;
        }

        public void setFinalPrice(String finalPrice) {
            this.finalPrice = finalPrice;
        }

        public String getFinalPriceSource() {
            return finalPriceSource;
        }

        public void setFinalPriceSource(String finalPriceSource) {
            this.finalPriceSource = finalPriceSource;
        }

        public String getActivePromotionCode() {
            return activePromotionCode;
        }

        public void setActivePromotionCode(String activePromotionCode) {
            this.activePromotionCode = activePromotionCode;
        }

        public String getActivePromotionName() {
            return activePromotionName;
        }

        public void setActivePromotionName(String activePromotionName) {
            this.activePromotionName = activePromotionName;
        }

        public String getActivePromotionUrl() {
            return activePromotionUrl;
        }

        public void setActivePromotionUrl(String activePromotionUrl) {
            this.activePromotionUrl = activePromotionUrl;
        }

        public String getDeliveryMethod() {
            return deliveryMethod;
        }

        public void setDeliveryMethod(String deliveryMethod) {
            this.deliveryMethod = deliveryMethod;
        }

        public Boolean getIsWinningBuybox() {
            return isWinningBuybox;
        }

        public void setIsWinningBuybox(Boolean winningBuybox) {
            isWinningBuybox = winningBuybox;
        }

        public Long getViewsCount() {
            return viewsCount;
        }

        public void setViewsCount(Long viewsCount) {
            this.viewsCount = viewsCount;
        }

        public Long getUnitsSold() {
            return unitsSold;
        }

        public void setUnitsSold(Long unitsSold) {
            this.unitsSold = unitsSold;
        }

        public String getSalesAmount() {
            return salesAmount;
        }

        public void setSalesAmount(String salesAmount) {
            this.salesAmount = salesAmount;
        }

        public String getSalesCurrency() {
            return salesCurrency;
        }

        public void setSalesCurrency(String salesCurrency) {
            this.salesCurrency = salesCurrency;
        }

        public String getLiveStatus() {
            return liveStatus;
        }

        public void setLiveStatus(String liveStatus) {
            this.liveStatus = liveStatus;
        }

        public String getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(String statusCode) {
            this.statusCode = statusCode;
        }

        public Boolean getIsActive() {
            return isActive;
        }

        public void setIsActive(Boolean isActive) {
            this.isActive = isActive;
        }

        public Integer getFbnStock() {
            return fbnStock;
        }

        public void setFbnStock(Integer fbnStock) {
            this.fbnStock = fbnStock;
        }

        public Integer getSupermallStock() {
            return supermallStock;
        }

        public void setSupermallStock(Integer supermallStock) {
            this.supermallStock = supermallStock;
        }

        public Integer getFbpStock() {
            return fbpStock;
        }

        public void setFbpStock(Integer fbpStock) {
            this.fbpStock = fbpStock;
        }
    }

    public static class VariantSeed {
        private String childSku;
        private String partnerSku;
        private String sizeEn;
        private String sizeAr;
        private Integer variantIx;

        public static VariantSeed fromSnapshot(ProductMasterSnapshotView snapshot) {
            VariantSeed seed = new VariantSeed();
            seed.setChildSku(textValue(snapshot.getIdentity().get("childSku")));
            seed.setPartnerSku(textValue(snapshot.getIdentity().get("partnerSku")));
            for (Map<String, Object> variant : snapshot.getVariants()) {
                String childSku = textValue(variant.get("childSku"));
                if (StringUtils.hasText(seed.getChildSku()) && seed.getChildSku().equalsIgnoreCase(childSku)) {
                    seed.setSizeEn(textValue(variant.get("sizeEn")));
                    seed.setSizeAr(textValue(variant.get("sizeAr")));
                    seed.setVariantIx(intValue(variant.get("variantIndex")));
                    return seed;
                }
            }
            if (!snapshot.getVariants().isEmpty()) {
                Map<String, Object> first = snapshot.getVariants().get(0);
                seed.setChildSku(firstNonBlank(seed.getChildSku(), textValue(first.get("childSku"))));
                seed.setSizeEn(textValue(first.get("sizeEn")));
                seed.setSizeAr(textValue(first.get("sizeAr")));
                seed.setVariantIx(intValue(first.get("variantIndex")));
            }
            return seed;
        }

        private static String firstNonBlank(String left, String right) {
            return StringUtils.hasText(left) ? left : right;
        }

        private static String textValue(Object value) {
            if (value == null) {
                return null;
            }
            String text = String.valueOf(value).trim();
            return StringUtils.hasText(text) ? text : null;
        }

        private static Integer intValue(Object value) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            String text = textValue(value);
            if (!StringUtils.hasText(text)) {
                return null;
            }
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        public String getChildSku() {
            return childSku;
        }

        public void setChildSku(String childSku) {
            this.childSku = childSku;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public void setPartnerSku(String partnerSku) {
            this.partnerSku = partnerSku;
        }

        public String getSizeEn() {
            return sizeEn;
        }

        public void setSizeEn(String sizeEn) {
            this.sizeEn = sizeEn;
        }

        public String getSizeAr() {
            return sizeAr;
        }

        public void setSizeAr(String sizeAr) {
            this.sizeAr = sizeAr;
        }

        public Integer getVariantIx() {
            return variantIx;
        }

        public void setVariantIx(Integer variantIx) {
            this.variantIx = variantIx;
        }
    }

    public static class PersistedWorkbenchState {
        private ProductMasterSnapshotView baselineSnapshot;
        private ProductMasterSnapshotView draftSnapshot;
        private Integer versionNo;
        private List<String> dirtySiteCodes = new ArrayList<>();
        private List<Map<String, Object>> recentActions = new ArrayList<>();
        private List<Map<String, Object>> keyContentHistory = new ArrayList<>();
        private Integer pendingKeyContentHistoryCount;
        private String pendingKeyContentHistoryVisibleAfter;

        public ProductMasterSnapshotView getBaselineSnapshot() {
            return baselineSnapshot;
        }

        public void setBaselineSnapshot(ProductMasterSnapshotView baselineSnapshot) {
            this.baselineSnapshot = baselineSnapshot;
        }

        public ProductMasterSnapshotView getDraftSnapshot() {
            return draftSnapshot;
        }

        public void setDraftSnapshot(ProductMasterSnapshotView draftSnapshot) {
            this.draftSnapshot = draftSnapshot;
        }

        public Integer getVersionNo() {
            return versionNo;
        }

        public void setVersionNo(Integer versionNo) {
            this.versionNo = versionNo;
        }

        public List<String> getDirtySiteCodes() {
            return dirtySiteCodes;
        }

        public void setDirtySiteCodes(List<String> dirtySiteCodes) {
            this.dirtySiteCodes = dirtySiteCodes;
        }

        public List<Map<String, Object>> getRecentActions() {
            return recentActions;
        }

        public void setRecentActions(List<Map<String, Object>> recentActions) {
            this.recentActions = recentActions;
        }

        public List<Map<String, Object>> getKeyContentHistory() {
            return keyContentHistory;
        }

        public void setKeyContentHistory(List<Map<String, Object>> keyContentHistory) {
            this.keyContentHistory = keyContentHistory;
        }

        public Integer getPendingKeyContentHistoryCount() {
            return pendingKeyContentHistoryCount;
        }

        public void setPendingKeyContentHistoryCount(Integer pendingKeyContentHistoryCount) {
            this.pendingKeyContentHistoryCount = pendingKeyContentHistoryCount;
        }

        public String getPendingKeyContentHistoryVisibleAfter() {
            return pendingKeyContentHistoryVisibleAfter;
        }

        public void setPendingKeyContentHistoryVisibleAfter(String pendingKeyContentHistoryVisibleAfter) {
            this.pendingKeyContentHistoryVisibleAfter = pendingKeyContentHistoryVisibleAfter;
        }
    }
}
