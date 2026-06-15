package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductWorkbenchViewFinalizerTest {

    @Mock
    private ProductProjectionPersistenceService productProjectionPersistenceService;

    @Mock
    private ProductWorkbenchStatusHydrator productWorkbenchStatusHydrator;

    @Mock
    private ProductWorkbenchPublishTaskAttacher productWorkbenchPublishTaskAttacher;

    private ProductWorkbenchViewFinalizer finalizer;

    @BeforeEach
    void setUp() {
        finalizer = new ProductWorkbenchViewFinalizer(
                productProjectionPersistenceService,
                new ProductWorkbenchDirtySiteResolver(new ObjectMapper()),
                productWorkbenchStatusHydrator,
                productWorkbenchPublishTaskAttacher
        );
    }

    @Test
    void finalizesViewWithoutPersistingWhenActionTypeIsBlank() {
        ProductWorkbenchRecord record = record("Baseline title", "Draft title");
        CallTracker callTracker = new CallTracker();
        stubPublishTaskAttacher(callTracker);
        stubStatusHydrator(callTracker);

        ProductMasterWorkbenchView view = finalizer.finalizeView(
                10002L,
                null,
                null,
                record,
                "本地工作台已就绪。",
                List.of("用户可见提示"),
                null,
                null
        );

        assertEquals("draft", view.getSyncStatus());
        assertEquals("Draft title", view.getContent().get("title"));
        assertSame(view, callTracker.statusSnapshot);
        assertSame(view, callTracker.summarySnapshot);
        assertEquals(List.of("attach", "sync", "hydrate"), callTracker.calls);
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
        CallTracker callTracker = new CallTracker();
        stubPublishTaskAttacher(callTracker);
        stubStatusHydrator(callTracker);
        List<List<String>> persistedWarnings = new ArrayList<>();
        doAnswer((invocation) -> {
            callTracker.calls.add("persist");
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
                actionDraft
        );

        assertEquals(List.of("attach", "sync", "persist", "hydrate"), callTracker.calls);
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

    private void stubPublishTaskAttacher(CallTracker callTracker) {
        doAnswer((invocation) -> {
            callTracker.calls.add("attach");
            assertEquals(Long.valueOf(10002L), invocation.getArgument(0));
            return null;
        }).when(productWorkbenchPublishTaskAttacher).attachActivePublishTask(
                eq(10002L),
                any(ProductWorkbenchRecord.class)
        );
    }

    private void stubStatusHydrator(CallTracker callTracker) {
        doAnswer((invocation) -> {
            callTracker.calls.add("sync");
            callTracker.statusSnapshot = invocation.getArgument(0);
            assertEquals("draft", invocation.getArgument(1));
            assertEquals("2026-06-04 10:00:00", invocation.getArgument(2));
            List<String> warnings = invocation.getArgument(3);
            warnings.add("status-synced");
            return null;
        }).when(productWorkbenchStatusHydrator).syncProductMasterStatus(
                any(ProductMasterSnapshotView.class),
                any(),
                any(),
                any()
        );
        doAnswer((invocation) -> {
            callTracker.calls.add("hydrate");
            assertEquals(Long.valueOf(10002L), invocation.getArgument(0));
            callTracker.summarySnapshot = invocation.getArgument(1);
            List<String> warnings = invocation.getArgument(2);
            warnings.add("summary-hydrated");
            return null;
        }).when(productWorkbenchStatusHydrator).hydrateListSummaryState(
                eq(10002L),
                any(ProductMasterSnapshotView.class),
                any()
        );
    }

    private ProductWorkbenchRecord record(String baselineTitle, String draftTitle) {
        ProductWorkbenchRecord record = new ProductWorkbenchRecord();
        ProductMasterSnapshotView baseline = snapshot(baselineTitle);
        baseline.setSiteOffers(List.of(siteOffer("188.00")));
        ProductMasterSnapshotView draft = snapshot(draftTitle);
        draft.setSiteOffers(List.of(siteOffer("199.00")));
        record.setBaselineSnapshot(baseline);
        record.setDraftSnapshot(draft);
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

    private Map<String, Object> siteOffer(String price) {
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("storeCode", "STR245027-NAE");
        offer.put("site", "ae");
        offer.put("price", price);
        return offer;
    }

    private static class CallTracker {
        private final List<String> calls = new ArrayList<>();
        private ProductMasterSnapshotView statusSnapshot;
        private ProductMasterSnapshotView summarySnapshot;
    }
}
