package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
class ProductWorkbenchStatusHydratorTest {

    @Mock
    private ProductProjectionPersistenceService productProjectionPersistenceService;

    private ProductWorkbenchStatusHydrator hydrator;

    @BeforeEach
    void setUp() {
        hydrator = new ProductWorkbenchStatusHydrator(productProjectionPersistenceService);
    }

    @Test
    void syncsProductMasterStatusFromSnapshotIdentity() {
        ProductMasterSnapshotView snapshot = snapshot();
        List<String> warnings = new ArrayList<>();

        hydrator.syncProductMasterStatus(snapshot, "draft", "2026-06-04 10:00:00", warnings);

        verify(productProjectionPersistenceService).updateProductMasterStatus(
                eq(10002L),
                eq("PRJ108065"),
                eq("canman"),
                eq("PAPERSAYSB132"),
                eq("PARTNER-132"),
                eq("draft"),
                eq("2026-06-04 10:00:00"),
                eq(warnings)
        );
    }

    @Test
    void hydratesListSummaryOnlyForWorkbenchViews() {
        ProductMasterWorkbenchView workbench = new ProductMasterWorkbenchView();
        workbench.getStoreContext().put("storeCode", "STR245027-NAE");
        workbench.getIdentity().put("skuParent", "PAPERSAYSB132");
        List<String> warnings = new ArrayList<>();
        ProductListSummaryView summary = new ProductListSummaryView();
        when(productProjectionPersistenceService.loadProductListSummary(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("PAPERSAYSB132"),
                anyList()
        )).thenReturn(summary);

        hydrator.hydrateListSummaryState(10002L, workbench, warnings);
        hydrator.hydrateListSummaryState(10002L, snapshot(), warnings);

        assertSame(summary, workbench.getListSummary());
        verify(productProjectionPersistenceService).loadProductListSummary(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("PAPERSAYSB132"),
                eq(warnings)
        );
    }

    private ProductMasterSnapshotView snapshot() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getStoreContext().put("ownerUserId", 10002L);
        snapshot.getStoreContext().put("projectCode", "PRJ108065");
        snapshot.getStoreContext().put("projectName", "canman");
        snapshot.getStoreContext().put("storeCode", "STR245027-NAE");
        snapshot.getIdentity().put("skuParent", "PAPERSAYSB132");
        snapshot.getIdentity().put("partnerSku", "PARTNER-132");
        return snapshot;
    }
}
