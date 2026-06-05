package com.nuono.next.product;

class ProductWorkbenchRecordRestorer {

    private final ProductSnapshotHydrator productSnapshotHydrator;
    private final ProductPublishPreparationService productPublishPreparationService;

    ProductWorkbenchRecordRestorer(
            ProductSnapshotHydrator productSnapshotHydrator,
            ProductPublishPreparationService productPublishPreparationService
    ) {
        this.productSnapshotHydrator = productSnapshotHydrator;
        this.productPublishPreparationService = productPublishPreparationService;
    }

    ProductWorkbenchRecord createSyncedRecord(ProductMasterSnapshotView snapshot) {
        ProductWorkbenchRecord record = new ProductWorkbenchRecord();
        record.setBaselineSnapshot(productSnapshotHydrator.copySnapshot(snapshot));
        record.setDraftSnapshot(productSnapshotHydrator.copySnapshot(snapshot));
        record.setSyncStatus("synced");
        record.setLastSyncedAt(extractFetchedAt(snapshot));
        record.setNote(
                snapshot != null && snapshot.isDegraded()
                        ? "已按降级模式打开详情；共享主档可继续查看和编辑，站点经营数据待索引补齐后再完整同步。"
                        : "已读取商品详情基线，可以开始调整共享主档和站点经营面。"
        );
        return record;
    }

    ProductWorkbenchRecord restorePersistedWorkbenchRecord(
            ProductMasterSnapshotView liveSnapshot,
            ProductProjectionPersistenceService.PersistedWorkbenchState persistedState
    ) {
        ProductWorkbenchRecord record = createSyncedRecord(liveSnapshot);
        if (persistedState == null) {
            return record;
        }
        if (persistedState.getRecentActions() != null && !persistedState.getRecentActions().isEmpty()) {
            record.setRecentActions(productSnapshotHydrator.copyRecordList(persistedState.getRecentActions()));
        }
        if (persistedState.getKeyContentHistory() != null && !persistedState.getKeyContentHistory().isEmpty()) {
            record.setKeyContentHistory(productSnapshotHydrator.copyRecordList(persistedState.getKeyContentHistory()));
        }
        record.setPendingKeyContentHistoryCount(persistedState.getPendingKeyContentHistoryCount());
        record.setPendingKeyContentHistoryVisibleAfter(persistedState.getPendingKeyContentHistoryVisibleAfter());
        ProductMasterSnapshotView persistedBaseline = persistedState.getBaselineSnapshot();
        ProductMasterSnapshotView persistedDraft = persistedState.getDraftSnapshot();
        if (persistedBaseline == null || persistedDraft == null) {
            return record;
        }

        if (sameBusinessSnapshot(persistedDraft, persistedBaseline)) {
            if (!sameBusinessSnapshot(persistedBaseline, liveSnapshot)) {
                record.setNote("检测到 Noon 新基线，已按最新快照恢复工作台。");
            }
            return record;
        }

        ProductMasterSnapshotView hydratedDraft = productSnapshotHydrator.copySnapshot(persistedDraft);
        productSnapshotHydrator.hydrateProjectionOnlyFields(hydratedDraft, liveSnapshot);
        if (sameBusinessSnapshot(hydratedDraft, liveSnapshot)) {
            if (!sameBusinessSnapshot(persistedBaseline, liveSnapshot)) {
                record.setNote("已按最新发布结果恢复工作台。");
            }
            return record;
        }
        record.setBaselineSnapshot(productSnapshotHydrator.copySnapshot(liveSnapshot));
        record.setDraftSnapshot(hydratedDraft);
        record.setLastSyncedAt(extractFetchedAt(liveSnapshot));
        if (sameBusinessSnapshot(persistedBaseline, liveSnapshot)) {
            record.setSyncStatus("draft");
            record.setNote("已从本地库恢复未发布草稿。");
            return record;
        }

        record.setSyncStatus("draft");
        record.setNote("已从本地库恢复未发布草稿。");
        return record;
    }

    private boolean sameBusinessSnapshot(ProductMasterSnapshotView left, ProductMasterSnapshotView right) {
        return productPublishPreparationService.sameBusinessSnapshot(left, right);
    }

    private String extractFetchedAt(ProductMasterSnapshotView snapshot) {
        Object fetchedAt = snapshot != null ? snapshot.getStoreContext().get("fetchedAt") : null;
        return fetchedAt == null ? null : String.valueOf(fetchedAt);
    }
}
