package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductWorkbenchOpenServiceTest {

    @Mock
    private ProductProjectionPersistenceService productProjectionPersistenceService;

    private ProductWorkbenchRecordStore productWorkbenchRecordStore;
    private ProductWorkbenchOpenService service;

    @BeforeEach
    void setUp() {
        productWorkbenchRecordStore = new ProductWorkbenchRecordStore();
        service = new ProductWorkbenchOpenService(
                productProjectionPersistenceService,
                productWorkbenchRecordStore
        );
    }

    @Test
    void returnsNullWhenLocalBaselineIsMissing() {
        ProductMasterFetchCommand command = command();
        FakeOpenSupport support = new FakeOpenSupport();
        when(productProjectionPersistenceService.loadLatestBaselineSnapshot(
                eq(10002L),
                eq("STR245027-NAE"),
                eq(null),
                eq("PAPERSAYSB132"),
                anyList()
        )).thenReturn(null);

        ProductMasterWorkbenchView view = service.openFromLocalBaseline(command, support);

        assertNull(view);
        verify(productProjectionPersistenceService, never()).loadPersistedWorkbenchState(
                eq(10002L),
                eq("STR245027-NAE"),
                eq(null),
                eq("PAPERSAYSB132"),
                anyList()
        );
    }

    @Test
    void opensLocalBaselineAndClearsInactivePersistedDraftWithoutExternalFetch() {
        ProductMasterFetchCommand command = command();
        ProductMasterSnapshotView baseline = baselineSnapshot();
        FakeOpenSupport support = new FakeOpenSupport();
        when(productProjectionPersistenceService.loadLatestBaselineSnapshot(
                eq(10002L),
                eq("STR245027-NAE"),
                eq(null),
                eq("PAPERSAYSB132"),
                anyList()
        )).thenReturn(baseline);
        when(productProjectionPersistenceService.loadPersistedWorkbenchState(
                eq(10002L),
                eq("STR245027-NAE"),
                eq(null),
                eq("PAPERSAYSB132"),
                anyList()
        )).thenReturn(null);

        ProductMasterWorkbenchView view = service.openFromLocalBaseline(command, support);

        assertEquals("synced", view.getSyncStatus());
        assertEquals(
                "当前使用本地商品基线，最后同步时间：2026-06-04 10:00:00。需要核对 Noon 当前版本时可手动同步。",
                view.getMessage()
        );
        assertEquals(List.of("hydrated-baseline"), view.getWarnings());
        assertSame(
                support.restoredRecord,
                productWorkbenchRecordStore.get("10002::STR245027-NAE::PAPERSAYSB132")
        );
        verify(productProjectionPersistenceService).clearInactivePersistedDraft(
                eq(10002L),
                eq("STR245027-NAE"),
                eq(null),
                eq("PAPERSAYSB132"),
                eq("2026-06-04 10:00:00"),
                anyList()
        );
    }

    private ProductMasterFetchCommand command() {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("PAPERSAYSB132");
        return command;
    }

    private ProductMasterSnapshotView baselineSnapshot() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setReady(true);
        snapshot.getStoreContext().put("storeCode", "STR245027-NAE");
        snapshot.getStoreContext().put("fetchedAt", "2026-06-04 10:00:00");
        snapshot.getIdentity().put("skuParent", "PAPERSAYSB132");
        return snapshot;
    }

    private static class FakeOpenSupport implements ProductWorkbenchOpenService.OpenSupport {
        private ProductWorkbenchRecord restoredRecord;

        @Override
        public void hydrateBaselineSnapshot(
                Long ownerUserId,
                String storeCode,
                String skuParent,
                ProductMasterSnapshotView baselineSnapshot,
                List<String> warnings
        ) {
            warnings.add("hydrated-baseline");
        }

        @Override
        public boolean hasActivePersistedDraft(
                ProductProjectionPersistenceService.PersistedWorkbenchState persistedState
        ) {
            return false;
        }

        @Override
        public void hydrateWorkbenchRecord(
                Long ownerUserId,
                String storeCode,
                ProductWorkbenchRecord record,
                List<String> warnings
        ) {
        }

        @Override
        public ProductWorkbenchRecord restorePersistedWorkbenchRecord(
                ProductMasterSnapshotView baselineSnapshot,
                ProductProjectionPersistenceService.PersistedWorkbenchState persistedState
        ) {
            restoredRecord = new ProductWorkbenchRecord();
            restoredRecord.setBaselineSnapshot(baselineSnapshot);
            restoredRecord.setDraftSnapshot(baselineSnapshot);
            restoredRecord.setSyncStatus("synced");
            restoredRecord.setLastSyncedAt(extractFetchedAt(baselineSnapshot));
            return restoredRecord;
        }

        @Override
        public String extractFetchedAt(ProductMasterSnapshotView snapshot) {
            Object value = snapshot == null ? null : snapshot.getStoreContext().get("fetchedAt");
            return value == null ? null : String.valueOf(value);
        }

        @Override
        public ProductMasterWorkbenchView finalizeWorkbenchView(
                Long ownerUserId,
                ProductWorkbenchRecord record,
                String message,
                List<String> warnings
        ) {
            ProductMasterWorkbenchView view = new ProductMasterWorkbenchView();
            view.setSyncStatus(record.getSyncStatus());
            view.setMessage(message);
            view.setWarnings(warnings);
            return view;
        }

        @Override
        public List<String> mergeWarnings(List<String> baseWarnings, List<String> extraWarnings) {
            List<String> merged = new ArrayList<>();
            if (baseWarnings != null) {
                merged.addAll(baseWarnings);
            }
            if (extraWarnings != null) {
                merged.addAll(extraWarnings);
            }
            return merged;
        }
    }
}
