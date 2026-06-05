package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductWorkbenchOpenService {

    private final ProductProjectionPersistenceService productProjectionPersistenceService;
    private final ProductWorkbenchRecordStore productWorkbenchRecordStore;

    public ProductWorkbenchOpenService(
            ProductProjectionPersistenceService productProjectionPersistenceService,
            ProductWorkbenchRecordStore productWorkbenchRecordStore
    ) {
        this.productProjectionPersistenceService = productProjectionPersistenceService;
        this.productWorkbenchRecordStore = productWorkbenchRecordStore == null
                ? new ProductWorkbenchRecordStore()
                : productWorkbenchRecordStore;
    }

    ProductMasterWorkbenchView openFromLocalBaseline(
            ProductMasterFetchCommand command,
            OpenSupport support
    ) {
        if (command == null || command.getOwnerUserId() == null || support == null) {
            return null;
        }
        String storeCode = normalize(command.getStoreCode());
        String skuParent = normalize(command.getSkuParent());
        if (!StringUtils.hasText(storeCode) || !StringUtils.hasText(skuParent)) {
            return null;
        }

        String key = workbenchKey(command.getOwnerUserId(), storeCode, skuParent);
        List<String> warnings = new ArrayList<>();
        ProductMasterSnapshotView baselineSnapshot =
                productProjectionPersistenceService.loadLatestBaselineSnapshot(
                        command.getOwnerUserId(),
                        storeCode,
                        skuParent,
                        warnings
                );
        if (baselineSnapshot == null || !baselineSnapshot.isReady()) {
            return null;
        }
        support.hydrateBaselineSnapshot(command.getOwnerUserId(), storeCode, skuParent, baselineSnapshot, warnings);

        ProductProjectionPersistenceService.PersistedWorkbenchState persistedState =
                productProjectionPersistenceService.loadPersistedWorkbenchState(
                        command.getOwnerUserId(),
                        storeCode,
                        skuParent,
                        warnings
                );
        if (!support.hasActivePersistedDraft(persistedState)) {
            productProjectionPersistenceService.clearInactivePersistedDraft(
                    command.getOwnerUserId(),
                    storeCode,
                    skuParent,
                    support.extractFetchedAt(baselineSnapshot),
                    warnings
            );
        }
        ProductWorkbenchRecord existingRecord = productWorkbenchRecordStore.get(key);
        if (
                existingRecord != null
                        && !"synced".equalsIgnoreCase(existingRecord.getSyncStatus())
                        && support.hasActivePersistedDraft(persistedState)
        ) {
            support.hydrateWorkbenchRecord(command.getOwnerUserId(), storeCode, existingRecord, warnings);
            return support.finalizeWorkbenchView(
                    command.getOwnerUserId(),
                    existingRecord,
                    localBaselineOpenNote(existingRecord.getLastSyncedAt()),
                    existingRecord.getDraftSnapshot() != null ? existingRecord.getDraftSnapshot().getWarnings() : new ArrayList<>()
            );
        }

        ProductWorkbenchRecord record = support.restorePersistedWorkbenchRecord(baselineSnapshot, persistedState);
        support.hydrateWorkbenchRecord(command.getOwnerUserId(), storeCode, record, warnings);
        if ("synced".equals(record.getSyncStatus())) {
            record.setNote(localBaselineOpenNote(support.extractFetchedAt(baselineSnapshot)));
        }
        productWorkbenchRecordStore.put(key, record);
        return support.finalizeWorkbenchView(
                command.getOwnerUserId(),
                record,
                localBaselineOpenNote(support.extractFetchedAt(baselineSnapshot)),
                support.mergeWarnings(baselineSnapshot.getWarnings(), warnings)
        );
    }

    private String workbenchKey(Long ownerUserId, String storeCode, String skuParent) {
        return ownerUserId + "::" + normalize(storeCode) + "::" + normalize(skuParent);
    }

    private String localBaselineOpenNote(String lastSyncedAt) {
        if (StringUtils.hasText(lastSyncedAt)) {
            return "当前使用本地商品基线，最后同步时间：" + lastSyncedAt + "。需要核对 Noon 当前版本时可手动同步。";
        }
        return "当前使用本地商品基线。需要核对 Noon 当前版本时可手动同步。";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    interface OpenSupport {
        void hydrateBaselineSnapshot(
                Long ownerUserId,
                String storeCode,
                String skuParent,
                ProductMasterSnapshotView baselineSnapshot,
                List<String> warnings
        );

        boolean hasActivePersistedDraft(ProductProjectionPersistenceService.PersistedWorkbenchState persistedState);

        void hydrateWorkbenchRecord(
                Long ownerUserId,
                String storeCode,
                ProductWorkbenchRecord record,
                List<String> warnings
        );

        ProductWorkbenchRecord restorePersistedWorkbenchRecord(
                ProductMasterSnapshotView baselineSnapshot,
                ProductProjectionPersistenceService.PersistedWorkbenchState persistedState
        );

        String extractFetchedAt(ProductMasterSnapshotView snapshot);

        ProductMasterWorkbenchView finalizeWorkbenchView(
                Long ownerUserId,
                ProductWorkbenchRecord record,
                String message,
                List<String> warnings
        );

        List<String> mergeWarnings(List<String> baseWarnings, List<String> extraWarnings);
    }
}
