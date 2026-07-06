package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.LocalDbStoreInitializationService;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductReadModelService {

    private final ProductManagementMapper productManagementMapper;
    private final StoreSyncMapper storeSyncMapper;
    private final ProductProjectionPersistenceService productProjectionPersistenceService;
    private final ProductDetailBaselineBackfillService productDetailBaselineBackfillService;

    public ProductReadModelService(
            ProductManagementMapper productManagementMapper,
            StoreSyncMapper storeSyncMapper,
            ProductProjectionPersistenceService productProjectionPersistenceService,
            ProductDetailBaselineBackfillService productDetailBaselineBackfillService
    ) {
        this.productManagementMapper = productManagementMapper;
        this.storeSyncMapper = storeSyncMapper;
        this.productProjectionPersistenceService = productProjectionPersistenceService;
        this.productDetailBaselineBackfillService = productDetailBaselineBackfillService;
    }

    public ProductListDatasetView loadListDataset(ProductMasterFetchCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能读取商品工作台。");
        }
        String storeCode = normalize(command.getStoreCode());
        requireText(storeCode, "缺少店铺编码，暂时不能读取商品工作台。");
        StoreSyncStoreRecord store = resolveProductOwnerStore(
                command.getOwnerUserId(),
                storeCode,
                "当前店铺不在选中的老板名下。"
        );
        storeCode = normalize(store.getStoreCode());

        List<String> warnings = new ArrayList<>();
        List<ProductListSummaryView> summaries = productProjectionPersistenceService.loadProductListSummaries(
                command.getOwnerUserId(),
                storeCode,
                warnings
        );

        LinkedHashMap<String, LocalDbStoreInitializationService.StoreInitializationProductListItemView> itemsByProductIdentity =
                new LinkedHashMap<>();
        for (ProductListSummaryView summary : summaries) {
            String identityKey = productIdentityKey(storeCode, summary);
            if (summary == null || !StringUtils.hasText(identityKey)) {
                continue;
            }
            LocalDbStoreInitializationService.StoreInitializationProductListItemView current =
                    itemsByProductIdentity.getOrDefault(identityKey, createListItemFromSummary(summary));
            itemsByProductIdentity.put(identityKey, mergeListItemWithSummary(current, summary));
        }

        List<LocalDbStoreInitializationService.StoreInitializationProductListItemView> items =
                new ArrayList<>(itemsByProductIdentity.values());

        ProductListDatasetView view = new ProductListDatasetView();
        view.setOwnerUserId(command.getOwnerUserId());
        view.setProjectName(store.getProjectName());
        view.setProjectCode(store.getProjectCode());
        view.setStoreCode(storeCode);
        view.setItems(items);
        applyDatasetStats(view, items);
        view.setWarnings(new ArrayList<>(warnings));

        view.setReady(true);
        if (!items.isEmpty()) {
            view.setSource("projection-primary");
            view.setMessage("商品摘要已就绪；列表展示本地商品投影，详情工作台按本地基线和草稿打开。");
        } else {
            view.setSource("projection-empty");
            view.setMessage("本地商品投影暂无数据；商品列表读取不依赖店铺初始化状态。");
        }

        view.setLastDatasetSyncedAt(resolveLastDatasetSyncedAt(items, null));
        return view;
    }

    public ProductGroupCandidatesView loadGroupCandidates(ProductMasterFetchCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能读取同类目候选商品。");
        }
        String storeCode = normalize(command.getStoreCode());
        String skuParent = normalize(command.getSkuParent());
        requireText(storeCode, "缺少店铺编码，暂时不能读取同类目候选商品。");
        requireText(skuParent, "缺少 skuParent，暂时不能读取同类目候选商品。");
        StoreSyncStoreRecord store = resolveProductOwnerStore(
                command.getOwnerUserId(),
                storeCode,
                "当前店铺不在选中的老板名下。"
        );
        storeCode = normalize(store.getStoreCode());

        List<String> warnings = new ArrayList<>();
        List<ProductListSummaryView> summaries = productProjectionPersistenceService.loadProductGroupCandidateSummaries(
                command.getOwnerUserId(),
                storeCode,
                skuParent,
                null,
                warnings
        );
        List<LocalDbStoreInitializationService.StoreInitializationProductListItemView> items = new ArrayList<>();
        for (ProductListSummaryView summary : summaries) {
            items.add(createListItemFromSummary(summary));
        }

        ProductGroupCandidatesView view = new ProductGroupCandidatesView();
        view.setReady(true);
        view.setSource("projection-primary");
        view.setOwnerUserId(command.getOwnerUserId());
        view.setStoreCode(storeCode);
        view.setSkuParent(skuParent);
        view.setItems(items);
        view.setWarnings(warnings);
        if (items.isEmpty()) {
            view.setMessage("当前没有找到同品牌、同类目的可加入 SKU。");
        } else {
            view.setMessage("已按当前商品品牌和类目匹配可加入分组的 SKU。");
        }
        return view;
    }

    private StoreSyncStoreRecord resolveProductOwnerStore(Long ownerUserId, String storeCode, String errorMessage) {
        String normalizedStoreCode = normalize(storeCode);
        StoreSyncStoreRecord store = storeSyncMapper.selectOwnerStore(ownerUserId, normalizedStoreCode);
        if (store == null) {
            store = storeSyncMapper.selectOwnerProjectionStore(ownerUserId, normalizedStoreCode);
        }
        if (store == null || !StringUtils.hasText(store.getStoreCode())) {
            throw new IllegalArgumentException(errorMessage);
        }
        return store;
    }

    private void applyDetailBaselineBackfillStates(
            Long ownerUserId,
            String storeCode,
            List<ProductListSummaryView> summaries
    ) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }
        for (ProductListSummaryView summary : summaries) {
            applyDetailBaselineBackfillState(ownerUserId, storeCode, summary);
        }
    }

    private void applyDetailBaselineBackfillState(
            Long ownerUserId,
            String storeCode,
            ProductListSummaryView summary
    ) {
        if (summary == null || ownerUserId == null || !StringUtils.hasText(storeCode) || !StringUtils.hasText(summary.getSkuParent())) {
            return;
        }
        if ("ready".equalsIgnoreCase(summary.getDetailBaselineStatus())
                || "public_detail_readonly".equalsIgnoreCase(summary.getDetailBaselineStatus())) {
            return;
        }
        if (productDetailBaselineBackfillService == null) {
            return;
        }
        ProductDetailBaselineBackfillService.BackfillState state =
                productDetailBaselineBackfillService.state(ownerUserId, storeCode, summary.getSkuParent());
        if (state == null || !StringUtils.hasText(state.getStatus())) {
            return;
        }
        summary.setDetailBaselineStatus(state.getStatus());
        summary.setDetailBaselineMessage(state.getMessage());
    }

    private void applyDatasetStats(
            ProductListDatasetView view,
            List<LocalDbStoreInitializationService.StoreInitializationProductListItemView> items
    ) {
        int syncedCount = 0;
        int draftCount = 0;
        int conflictCount = 0;
        int failedCount = 0;
        int liveCount = 0;
        int groupedCount = 0;
        int pendingPriceCount = 0;
        int historyReadyCount = 0;
        for (LocalDbStoreInitializationService.StoreInitializationProductListItemView item : items) {
            String syncStatus = normalize(item.getSyncStatus());
            if ("draft".equals(syncStatus)) {
                draftCount++;
            } else if ("conflict".equals(syncStatus)) {
                draftCount++;
            } else if ("failed".equals(syncStatus)) {
                failedCount++;
            } else {
                syncedCount++;
            }
            if (isLiveStatusActive(item.getLiveStatus())
                    || (item.getLiveStatus() == null && item.getLiveStatuses().stream().anyMatch(this::isLiveStatusActive))) {
                liveCount++;
            }
            if (StringUtils.hasText(item.getGroupRef())) {
                groupedCount++;
            }
            if (!StringUtils.hasText(item.getReferencePrice())) {
                pendingPriceCount++;
            }
            if (Boolean.TRUE.equals(item.getHistoryMetaReady())) {
                historyReadyCount++;
            }
        }
        view.setTotalItems(items.size());
        view.setSyncedCount(syncedCount);
        view.setDraftCount(draftCount);
        view.setConflictCount(conflictCount);
        view.setFailedCount(failedCount);
        view.setLiveCount(liveCount);
        view.setGroupedCount(groupedCount);
        view.setPendingPriceCount(pendingPriceCount);
        view.setHistoryReadyCount(historyReadyCount);
    }

    private LocalDbStoreInitializationService.StoreInitializationProductListItemView createListItemFromSummary(
            ProductListSummaryView summary
    ) {
        LocalDbStoreInitializationService.StoreInitializationProductListItemView item =
                new LocalDbStoreInitializationService.StoreInitializationProductListItemView();
        item.setSkuParent(firstNonBlank(summary.getCurrentZCode(), summary.getSkuParent()));
        item.setCurrentZCode(firstNonBlank(summary.getCurrentZCode(), summary.getSkuParent()));
        item.setProductSourceType(summary.getProductSourceType());
        item.setReferenceStoreCode(summary.getStoreCode());
        item.setSiteLabels(new ArrayList<>());
        item.setLiveStatuses(new ArrayList<>());
        item.setIssueTags(new ArrayList<>());
        return mergeListItemWithSummary(item, summary);
    }

    private LocalDbStoreInitializationService.StoreInitializationProductListItemView mergeListItemWithSummary(
            LocalDbStoreInitializationService.StoreInitializationProductListItemView item,
            ProductListSummaryView summary
    ) {
        boolean currentZRow = isCurrentZRow(item, summary);
        item.setSkuParent(firstNonBlank(item.getSkuParent(), summary.getCurrentZCode(), summary.getSkuParent()));
        item.setCurrentZCode(firstNonBlank(item.getCurrentZCode(), summary.getCurrentZCode(), summary.getSkuParent()));
        item.setProductSourceType(mergeCurrentZValue(item.getProductSourceType(), summary.getProductSourceType(), currentZRow));
        item.setPartnerSku(mergeCurrentZValue(item.getPartnerSku(), summary.getPartnerSku(), currentZRow));
        item.setPskuCode(mergeCurrentZValue(item.getPskuCode(), summary.getPskuCode(), currentZRow));
        item.setOfferCode(mergeCurrentZValue(item.getOfferCode(), summary.getOfferCode(), currentZRow));
        item.setReferenceStoreCode(mergeCurrentZValue(item.getReferenceStoreCode(), summary.getStoreCode(), currentZRow));
        item.setTitle(mergeCurrentZValue(item.getTitle(), summary.getTitle(), currentZRow));
        item.setTitleCn(mergeCurrentZValue(item.getTitleCn(), summary.getTitleCn(), currentZRow));
        item.setBrand(mergeCurrentZValue(item.getBrand(), summary.getBrand(), currentZRow));
        item.setImageUrl(mergeCurrentZValue(item.getImageUrl(), summary.getImageUrl(), currentZRow));
        if (!summary.getGalleryImages().isEmpty() && (currentZRow || item.getGalleryImages().isEmpty())) {
            item.setGalleryImages(new ArrayList<>(summary.getGalleryImages()));
        }
        item.setBarcode(mergeCurrentZValue(item.getBarcode(), summary.getBarcode(), currentZRow));
        item.setReferencePrice(mergeCurrentZValue(item.getReferencePrice(), summary.getReferencePrice(), currentZRow));
        item.setOriginalPrice(mergeCurrentZValue(item.getOriginalPrice(), summary.getOriginalPrice(), currentZRow));
        item.setSalePrice(mergeCurrentZValue(item.getSalePrice(), summary.getSalePrice(), currentZRow));
        item.setProductFulltype(mergeCurrentZValue(item.getProductFulltype(), summary.getProductFulltype(), currentZRow));
        item.setSkuGroup(mergeCurrentZValue(item.getSkuGroup(), summary.getSkuGroup(), currentZRow));
        item.setGroupRef(mergeCurrentZValue(item.getGroupRef(), summary.getGroupRef(), currentZRow));
        item.setGroupRefCanonical(mergeCurrentZValue(item.getGroupRefCanonical(), summary.getGroupRefCanonical(), currentZRow));
        item.setLiveStatus(mergeCurrentZValue(item.getLiveStatus(), summary.getLiveStatus(), currentZRow));
        item.setStatusCode(mergeCurrentZValue(item.getStatusCode(), summary.getStatusCode(), currentZRow));
        item.setListingStartedAt(mergeCurrentZValue(item.getListingStartedAt(), summary.getListingStartedAt(), currentZRow));
        item.setListingStartedSource(mergeCurrentZValue(item.getListingStartedSource(), summary.getListingStartedSource(), currentZRow));
        item.setOperationStageCode(mergeCurrentZValue(item.getOperationStageCode(), summary.getOperationStageCode(), currentZRow));
        item.setOperationStageUpdatedAt(mergeCurrentZValue(
                item.getOperationStageUpdatedAt(),
                summary.getOperationStageUpdatedAt(),
                currentZRow
        ));
        item.setOperationStageUpdatedBy(mergeCurrentZValue(
                item.getOperationStageUpdatedBy(),
                summary.getOperationStageUpdatedBy(),
                currentZRow
        ));
        item.setIsActive(mergeCurrentZValue(item.getIsActive(), summary.getIsActive(), currentZRow));
        item.setSyncStatus(mergeCurrentZValue(item.getSyncStatus(), summary.getSyncStatus(), currentZRow));
        item.setLastSyncedAt(mergeCurrentZValue(item.getLastSyncedAt(), summary.getLastSyncedAt(), currentZRow));
        item.setLastDraftSavedAt(mergeCurrentZValue(item.getLastDraftSavedAt(), summary.getLastDraftSavedAt(), currentZRow));
        item.setDetailBaselineStatus(mergeCurrentZValue(item.getDetailBaselineStatus(), summary.getDetailBaselineStatus(), currentZRow));
        item.setDetailBaselineMessage(mergeCurrentZValue(item.getDetailBaselineMessage(), summary.getDetailBaselineMessage(), currentZRow));
        item.setDetailBaselineSyncedAt(mergeCurrentZValue(item.getDetailBaselineSyncedAt(), summary.getDetailBaselineSyncedAt(), currentZRow));
        item.setProductVariantSpecStatus(mergeCurrentZValue(item.getProductVariantSpecStatus(), summary.getProductVariantSpecStatus(), currentZRow));
        item.setProductVariantSpecTotalCount(mergeCurrentZValue(
                item.getProductVariantSpecTotalCount(),
                summary.getProductVariantSpecTotalCount(),
                currentZRow
        ));
        item.setProductVariantSpecReadyCount(mergeCurrentZValue(
                item.getProductVariantSpecReadyCount(),
                summary.getProductVariantSpecReadyCount(),
                currentZRow
        ));
        item.setProductVariantSpecMaintainedCount(mergeCurrentZValue(
                item.getProductVariantSpecMaintainedCount(),
                summary.getProductVariantSpecMaintainedCount(),
                currentZRow
        ));
        item.setVariantCount(mergeCurrentZValue(item.getVariantCount(), summary.getVariantCount(), currentZRow));
        item.setSiteOfferCount(mergeCurrentZValue(item.getSiteOfferCount(), summary.getSiteOfferCount(), currentZRow));
        item.setHistoryMetaReady(mergeCurrentZValue(item.getHistoryMetaReady(), summary.getHistoryMetaReady(), currentZRow));
        item.setPendingKeyContentHistoryCount(
                mergeCurrentZValue(
                        item.getPendingKeyContentHistoryCount(),
                        summary.getPendingKeyContentHistoryCount(),
                        currentZRow
                )
        );
        item.setVisibleKeyContentHistoryCount(
                mergeCurrentZValue(
                        item.getVisibleKeyContentHistoryCount(),
                        summary.getVisibleKeyContentHistoryCount(),
                        currentZRow
                )
        );
        item.setPendingKeyContentHistoryVisibleAfter(
                mergeCurrentZValue(
                        item.getPendingKeyContentHistoryVisibleAfter(),
                        summary.getPendingKeyContentHistoryVisibleAfter(),
                        currentZRow
                )
        );
        if (!summary.getSiteLabels().isEmpty() && (currentZRow || item.getSiteLabels().isEmpty())) {
            item.setSiteLabels(new ArrayList<>(summary.getSiteLabels()));
        } else if (StringUtils.hasText(summary.getStoreCode()) && item.getSiteLabels().isEmpty()) {
            item.setSiteLabels(new ArrayList<>(List.of(summary.getStoreCode())));
        }
        if (!summary.getLiveStatuses().isEmpty() && (currentZRow || item.getLiveStatuses().isEmpty())) {
            item.setLiveStatuses(new ArrayList<>(summary.getLiveStatuses()));
        } else if (StringUtils.hasText(summary.getLiveStatus()) && item.getLiveStatuses().isEmpty()) {
            item.setLiveStatuses(new ArrayList<>(List.of(summary.getLiveStatus())));
        }
        if (!summary.getIssueTags().isEmpty() && (currentZRow || item.getIssueTags().isEmpty())) {
            item.setIssueTags(new ArrayList<>(summary.getIssueTags()));
        }
        item.setTotalFbnStock(mergeCurrentZValue(item.getTotalFbnStock(), summary.getTotalFbnStock(), currentZRow));
        item.setTotalSupermallStock(mergeCurrentZValue(item.getTotalSupermallStock(), summary.getTotalSupermallStock(), currentZRow));
        item.setTotalFbpStock(mergeCurrentZValue(item.getTotalFbpStock(), summary.getTotalFbpStock(), currentZRow));
        item.setViewsCount(mergeCurrentZValue(item.getViewsCount(), summary.getViewsCount(), currentZRow));
        item.setUnitsSold(mergeCurrentZValue(item.getUnitsSold(), summary.getUnitsSold(), currentZRow));
        item.setSalesAmount(mergeCurrentZValue(item.getSalesAmount(), summary.getSalesAmount(), currentZRow));
        item.setSalesCurrency(mergeCurrentZValue(item.getSalesCurrency(), summary.getSalesCurrency(), currentZRow));
        item.setLastPublishTask(mergeCurrentZValue(item.getLastPublishTask(), summary.getLastPublishTask(), currentZRow));
        return item;
    }

    private boolean isCurrentZRow(
            LocalDbStoreInitializationService.StoreInitializationProductListItemView item,
            ProductListSummaryView summary
    ) {
        String itemCurrentZCode = normalize(item.getCurrentZCode());
        String summaryCurrentZCode = firstNonBlank(summary.getCurrentZCode(), summary.getSkuParent());
        return !StringUtils.hasText(itemCurrentZCode)
                || !StringUtils.hasText(summaryCurrentZCode)
                || itemCurrentZCode.equalsIgnoreCase(summaryCurrentZCode);
    }

    private String mergeCurrentZValue(String currentValue, String nextValue, boolean currentZRow) {
        return currentZRow
                ? firstNonBlank(nextValue, currentValue)
                : firstNonBlank(currentValue, nextValue);
    }

    private <T> T mergeCurrentZValue(T currentValue, T nextValue, boolean currentZRow) {
        if (currentZRow) {
            return nextValue != null ? nextValue : currentValue;
        }
        return currentValue != null ? currentValue : nextValue;
    }

    private String resolveLastDatasetSyncedAt(
            List<LocalDbStoreInitializationService.StoreInitializationProductListItemView> items,
            String fallbackTime
    ) {
        String latest = null;
        for (LocalDbStoreInitializationService.StoreInitializationProductListItemView item : items) {
            String candidate = normalize(item.getLastSyncedAt());
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            if (!StringUtils.hasText(latest) || candidate.compareTo(latest) > 0) {
                latest = candidate;
            }
        }
        return StringUtils.hasText(latest) ? latest : fallbackTime;
    }

    private boolean isLiveStatusActive(String status) {
        String normalized = normalize(status);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        String lowerCaseValue = normalized.toLowerCase();
        return "true".equals(lowerCaseValue) || "1".equals(lowerCaseValue) || "live".equals(lowerCaseValue);
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

    private String productIdentityKey(String storeCode, ProductListSummaryView summary) {
        if (summary == null) {
            return null;
        }
        String productKey = firstNonBlank(summary.getPartnerSku(), summary.getSkuParent());
        if (!StringUtils.hasText(productKey)) {
            return null;
        }
        return firstNonBlank(summary.getStoreCode(), storeCode) + "::" + productKey;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
