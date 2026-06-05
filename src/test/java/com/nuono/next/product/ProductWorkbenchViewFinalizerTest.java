package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductWorkbenchViewFinalizerTest {

    @Mock
    private ProductProjectionPersistenceService productProjectionPersistenceService;

    private ProductWorkbenchViewFinalizer finalizer;

    @BeforeEach
    void setUp() {
        finalizer = new ProductWorkbenchViewFinalizer(productProjectionPersistenceService);
    }

    @Test
    void finalizesViewWithoutPersistingWhenActionTypeIsBlank() {
        ProductWorkbenchRecord record = record("Baseline title", "Draft title");
        FakeFinalizeSupport support = new FakeFinalizeSupport();

        ProductMasterWorkbenchView view = finalizer.finalizeView(
                10002L,
                null,
                null,
                record,
                "本地工作台已就绪。",
                List.of("用户可见提示"),
                null,
                null,
                support
        );

        assertEquals("draft", view.getSyncStatus());
        assertEquals("Draft title", view.getContent().get("title"));
        assertSame(view, support.statusSnapshot);
        assertSame(view, support.summarySnapshot);
        assertEquals(List.of("attach", "sync", "hydrate"), support.calls);
        assertEquals(List.of("用户可见提示", "status-synced", "summary-hydrated"), view.getWarnings());
        verify(productProjectionPersistenceService, never()).persistWorkbenchState(
                eq(10002L),
                any(ProductMasterWorkbenchView.class),
                eq(List.of("STR245027-NAE")),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void persistsWorkbenchStateBeforeHydratingSummaryWhenActionTypeIsPresent() {
        ProductWorkbenchRecord record = record("Baseline title", "Edited title");
        ProductMasterSnapshotView actionBaseline = snapshot("Baseline title");
        ProductMasterSnapshotView actionDraft = snapshot("Edited title");
        FakeFinalizeSupport support = new FakeFinalizeSupport();
        List<List<String>> persistedWarnings = new ArrayList<>();
        doAnswer((invocation) -> {
            persistedWarnings.add(List.copyOf(invocation.getArgument(5)));
            return null;
        }).when(productProjectionPersistenceService).persistWorkbenchState(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );

        ProductMasterWorkbenchView view = finalizer.finalizeView(
                10002L,
                "edit-content",
                "STR245027-NAE",
                record,
                "已保存草稿。",
                new ArrayList<>(List.of("初始提示")),
                actionBaseline,
                actionDraft,
                support
        );

        assertEquals(List.of("attach", "sync", "dirty", "hydrate"), support.calls);
        verify(productProjectionPersistenceService).persistWorkbenchState(
                eq(10002L),
                eq(view),
                eq(List.of("STR245027-NAE")),
                eq("edit-content"),
                eq("STR245027-NAE"),
                any(),
                eq(actionBaseline),
                eq(actionDraft)
        );
        assertEquals(List.of(List.of("初始提示", "status-synced")), persistedWarnings);
        assertEquals(List.of("初始提示", "status-synced", "summary-hydrated"), view.getWarnings());
    }

    private ProductWorkbenchRecord record(String baselineTitle, String draftTitle) {
        ProductWorkbenchRecord record = new ProductWorkbenchRecord();
        record.setBaselineSnapshot(snapshot(baselineTitle));
        record.setDraftSnapshot(snapshot(draftTitle));
        record.setSyncStatus("draft");
        record.setLastSyncedAt("2026-06-04 10:00:00");
        return record;
    }

    private ProductMasterSnapshotView snapshot(String title) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setMode("local-db");
        snapshot.setReady(true);
        snapshot.getStoreContext().put("storeCode", "STR245027-NAE");
        snapshot.getIdentity().put("skuParent", "PAPERSAYSB132");
        snapshot.getContent().put("title", title);
        return snapshot;
    }

    private static class FakeFinalizeSupport implements ProductWorkbenchViewFinalizer.FinalizeSupport {
        private final List<String> calls = new ArrayList<>();
        private ProductMasterSnapshotView statusSnapshot;
        private ProductMasterSnapshotView summarySnapshot;

        @Override
        public void attachActivePublishTask(Long ownerUserId, ProductWorkbenchRecord record) {
            calls.add("attach");
            assertEquals(10002L, ownerUserId);
        }

        @Override
        public void syncProductMasterStatus(
                ProductMasterSnapshotView snapshot,
                String syncStatus,
                String lastSyncedAt,
                List<String> warnings
        ) {
            calls.add("sync");
            statusSnapshot = snapshot;
            assertEquals("draft", syncStatus);
            assertEquals("2026-06-04 10:00:00", lastSyncedAt);
            warnings.add("status-synced");
        }

        @Override
        public List<String> resolveDirtySiteCodes(ProductMasterSnapshotView draft, ProductMasterSnapshotView baseline) {
            calls.add("dirty");
            assertTrue(draft.getContent().containsKey("title"));
            assertTrue(baseline.getContent().containsKey("title"));
            return List.of("STR245027-NAE");
        }

        @Override
        public void hydrateListSummaryState(
                Long ownerUserId,
                ProductMasterSnapshotView snapshot,
                List<String> warnings
        ) {
            calls.add("hydrate");
            summarySnapshot = snapshot;
            assertEquals(10002L, ownerUserId);
            warnings.add("summary-hydrated");
        }
    }
}
