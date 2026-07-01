package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
        OpenIdentity identity = resolveIdentity(command);
        if (command == null || command.getOwnerUserId() == null || support == null || identity == null) {
            return null;
        }
        String storeCode = identity.storeCode();
        String partnerSku = identity.partnerSku();
        String skuParent = identity.currentZCode();
        String fallbackSkuParent = identity.currentZCode();
        String key = workbenchKey(command.getOwnerUserId(), storeCode, identity.productKey());
        List<String> warnings = new ArrayList<>();
        ProductMasterSnapshotView baselineSnapshot =
                productProjectionPersistenceService.loadLatestBaselineSnapshot(
                        command.getOwnerUserId(),
                        storeCode,
                        partnerSku,
                        fallbackSkuParent,
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
                        partnerSku,
                        fallbackSkuParent,
                        warnings
                );
        if (!support.hasActivePersistedDraft(persistedState)) {
            productProjectionPersistenceService.clearInactivePersistedDraft(
                    command.getOwnerUserId(),
                    storeCode,
                    partnerSku,
                    fallbackSkuParent,
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

    OpenIdentity resolveIdentity(ProductMasterFetchCommand command) {
        if (command == null) {
            return null;
        }
        String storeCode = normalize(command.getStoreCode());
        String partnerSku = normalize(command.getPartnerSku());
        String currentZCode = firstNonBlank(command.getCurrentZCode(), command.getSkuParent());
        if (StringUtils.hasText(currentZCode)) {
            command.setCurrentZCode(currentZCode);
        }
        if (!StringUtils.hasText(storeCode)) {
            return null;
        }
        if (StringUtils.hasText(partnerSku)) {
            return new OpenIdentity(storeCode, partnerSku, currentZCode, null);
        }
        if (!StringUtils.hasText(currentZCode)) {
            return null;
        }
        return new OpenIdentity(storeCode, null, currentZCode, currentZCode);
    }

    private String firstNonBlank(String first, String second) {
        String normalized = normalize(first);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return normalize(second);
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

    static class OpenIdentity {
        private final String storeCode;
        private final String partnerSku;
        private final String currentZCode;
        private final String legacySkuParent;

        OpenIdentity(String storeCode, String partnerSku, String currentZCode, String legacySkuParent) {
            this.storeCode = storeCode;
            this.partnerSku = partnerSku;
            this.currentZCode = currentZCode;
            this.legacySkuParent = legacySkuParent;
        }

        String storeCode() {
            return storeCode;
        }

        String partnerSku() {
            return partnerSku;
        }

        String currentZCode() {
            return currentZCode;
        }

        String legacySkuParent() {
            return legacySkuParent;
        }

        String productKey() {
            return StringUtils.hasText(partnerSku) ? partnerSku : legacySkuParent;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof OpenIdentity)) {
                return false;
            }
            OpenIdentity that = (OpenIdentity) other;
            return Objects.equals(storeCode, that.storeCode)
                    && Objects.equals(partnerSku, that.partnerSku)
                    && Objects.equals(legacySkuParent, that.legacySkuParent);
        }

        @Override
        public int hashCode() {
            return Objects.hash(storeCode, partnerSku, legacySkuParent);
        }
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
