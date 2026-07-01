package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.LocalDbStoreInitializationService;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        Set<String> deletedSkuParents = new LinkedHashSet<>(
                productManagementMapper.selectDeletedProductSkuParentsByStoreCode(command.getOwnerUserId(), storeCode)
        );

        LinkedHashMap<String, LocalDbStoreInitializationService.StoreInitializationProductListItemView> itemsByIdentity =
                new LinkedHashMap<>();
        for (ProductListSummaryView summary : summaries) {
            if (summary == null) {
                continue;
            }
            String currentZCode = firstNonBlank(summary.getCurrentZCode(), summary.getSkuParent());
            String identityKey = firstNonBlank(summary.getPartnerSku(), currentZCode);
            if (!StringUtils.hasText(identityKey)) {
                continue;
            }
            if (StringUtils.hasText(currentZCode) && deletedSkuParents.contains(currentZCode)) {
                continue;
            }
            LocalDbStoreInitializationService.StoreInitializationProductListItemView current =
                    itemsByIdentity.get(identityKey);
            if (current == null) {
                itemsByIdentity.put(identityKey, createListItemFromSummary(summary));
            }
        }

        List<LocalDbStoreInitializationService.StoreInitializationProductListItemView> items =
                new ArrayList<>(itemsByIdentity.values());

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
        if ("ready".equalsIgnoreCase(summary.getDetailBaselineStatus())) {
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
        item.setSkuParent(firstNonBlank(item.getSkuParent(), summary.getCurrentZCode(), summary.getSkuParent()));
        item.setCurrentZCode(firstNonBlank(item.getCurrentZCode(), summary.getCurrentZCode(), summary.getSkuParent()));
        item.setProductSourceType(firstNonBlank(summary.getProductSourceType(), item.getProductSourceType()));
        item.setPartnerSku(firstNonBlank(summary.getPartnerSku(), item.getPartnerSku()));
        item.setPskuCode(firstNonBlank(summary.getPskuCode(), item.getPskuCode()));
        item.setOfferCode(firstNonBlank(summary.getOfferCode(), item.getOfferCode()));
        item.setReferenceStoreCode(firstNonBlank(summary.getStoreCode(), item.getReferenceStoreCode()));
        item.setTitle(firstNonBlank(summary.getTitle(), item.getTitle()));
        item.setTitleCn(firstNonBlank(summary.getTitleCn(), item.getTitleCn()));
        item.setBrand(firstNonBlank(summary.getBrand(), item.getBrand()));
        item.setImageUrl(firstNonBlank(summary.getImageUrl(), item.getImageUrl()));
        if (!summary.getGalleryImages().isEmpty()) {
            item.setGalleryImages(new ArrayList<>(summary.getGalleryImages()));
        }
        item.setBarcode(firstNonBlank(summary.getBarcode(), item.getBarcode()));
        item.setReferencePrice(firstNonBlank(summary.getReferencePrice(), item.getReferencePrice()));
        item.setOriginalPrice(firstNonBlank(summary.getOriginalPrice(), item.getOriginalPrice()));
        item.setSalePrice(firstNonBlank(summary.getSalePrice(), item.getSalePrice()));
        item.setProductFulltype(firstNonBlank(summary.getProductFulltype(), item.getProductFulltype()));
        item.setSkuGroup(normalize(summary.getSkuGroup()));
        item.setGroupRef(normalize(summary.getGroupRef()));
        item.setGroupRefCanonical(normalize(summary.getGroupRefCanonical()));
        item.setLiveStatus(firstNonBlank(summary.getLiveStatus(), item.getLiveStatus()));
        item.setStatusCode(firstNonBlank(summary.getStatusCode(), item.getStatusCode()));
        item.setIsActive(summary.getIsActive() != null ? summary.getIsActive() : item.getIsActive());
        item.setSyncStatus(firstNonBlank(summary.getSyncStatus(), item.getSyncStatus()));
        item.setLastSyncedAt(firstNonBlank(summary.getLastSyncedAt(), item.getLastSyncedAt()));
        item.setLastDraftSavedAt(firstNonBlank(summary.getLastDraftSavedAt(), item.getLastDraftSavedAt()));
        item.setDetailBaselineStatus(firstNonBlank(summary.getDetailBaselineStatus(), item.getDetailBaselineStatus()));
        item.setDetailBaselineMessage(firstNonBlank(summary.getDetailBaselineMessage(), item.getDetailBaselineMessage()));
        item.setDetailBaselineSyncedAt(firstNonBlank(summary.getDetailBaselineSyncedAt(), item.getDetailBaselineSyncedAt()));
        item.setProductVariantSpecStatus(firstNonBlank(summary.getProductVariantSpecStatus(), item.getProductVariantSpecStatus()));
        item.setProductVariantSpecTotalCount(summary.getProductVariantSpecTotalCount() != null
                ? summary.getProductVariantSpecTotalCount()
                : item.getProductVariantSpecTotalCount());
        item.setProductVariantSpecReadyCount(summary.getProductVariantSpecReadyCount() != null
                ? summary.getProductVariantSpecReadyCount()
                : item.getProductVariantSpecReadyCount());
        item.setProductVariantSpecMaintainedCount(summary.getProductVariantSpecMaintainedCount() != null
                ? summary.getProductVariantSpecMaintainedCount()
                : item.getProductVariantSpecMaintainedCount());
        item.setVariantCount(summary.getVariantCount() != null ? summary.getVariantCount() : item.getVariantCount());
        item.setSiteOfferCount(summary.getSiteOfferCount() != null ? summary.getSiteOfferCount() : item.getSiteOfferCount());
        item.setHistoryMetaReady(summary.getHistoryMetaReady() != null ? summary.getHistoryMetaReady() : item.getHistoryMetaReady());
        item.setPendingKeyContentHistoryCount(
                summary.getPendingKeyContentHistoryCount() != null
                        ? summary.getPendingKeyContentHistoryCount()
                        : item.getPendingKeyContentHistoryCount()
        );
        item.setVisibleKeyContentHistoryCount(
                summary.getVisibleKeyContentHistoryCount() != null
                        ? summary.getVisibleKeyContentHistoryCount()
                        : item.getVisibleKeyContentHistoryCount()
        );
        item.setPendingKeyContentHistoryVisibleAfter(
                firstNonBlank(summary.getPendingKeyContentHistoryVisibleAfter(), item.getPendingKeyContentHistoryVisibleAfter())
        );
        if (!summary.getSiteLabels().isEmpty()) {
            item.setSiteLabels(new ArrayList<>(summary.getSiteLabels()));
        } else if (StringUtils.hasText(summary.getStoreCode()) && item.getSiteLabels().isEmpty()) {
            item.setSiteLabels(new ArrayList<>(List.of(summary.getStoreCode())));
        }
        if (!summary.getLiveStatuses().isEmpty()) {
            item.setLiveStatuses(new ArrayList<>(summary.getLiveStatuses()));
        } else if (StringUtils.hasText(summary.getLiveStatus()) && item.getLiveStatuses().isEmpty()) {
            item.setLiveStatuses(new ArrayList<>(List.of(summary.getLiveStatus())));
        }
        if (!summary.getIssueTags().isEmpty()) {
            item.setIssueTags(new ArrayList<>(summary.getIssueTags()));
        }
        item.setTotalFbnStock(summary.getTotalFbnStock() != null ? summary.getTotalFbnStock() : item.getTotalFbnStock());
        item.setTotalSupermallStock(summary.getTotalSupermallStock() != null ? summary.getTotalSupermallStock() : item.getTotalSupermallStock());
        item.setTotalFbpStock(summary.getTotalFbpStock() != null ? summary.getTotalFbpStock() : item.getTotalFbpStock());
        item.setViewsCount(summary.getViewsCount() != null ? summary.getViewsCount() : item.getViewsCount());
        item.setUnitsSold(summary.getUnitsSold() != null ? summary.getUnitsSold() : item.getUnitsSold());
        item.setSalesAmount(firstNonBlank(summary.getSalesAmount(), item.getSalesAmount()));
        item.setSalesCurrency(firstNonBlank(summary.getSalesCurrency(), item.getSalesCurrency()));
        item.setLastPublishTask(summary.getLastPublishTask() != null ? summary.getLastPublishTask() : item.getLastPublishTask());
        return item;
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

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
