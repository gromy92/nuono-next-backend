package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalDbProductMasterServiceAccessBoundaryTest {

    private final ProductProjectionPersistenceService productProjectionPersistenceService =
            mock(ProductProjectionPersistenceService.class);

    private final LocalDbProductMasterService service = new LocalDbProductMasterService(
            null,
            null,
            null,
            new ObjectMapper(),
            null,
            null,
            productProjectionPersistenceService,
            null,
            null,
            null,
            null
    );

    @Test
    void shouldUseTrustedOwnerWhenSyncingProductMasterStatus() throws Exception {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setStoreContext(Map.of(
                "ownerUserId", 99999L,
                "projectCode", "STR245027-NAE",
                "projectName", "Noon UAE"
        ));
        snapshot.setIdentity(Map.of("skuParent", "MILKYWAYA17"));

        invokeSyncProductMasterStatus(10002L, snapshot);

        verify(productProjectionPersistenceService).updateProductMasterStatus(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("Noon UAE"),
                eq("MILKYWAYA17"),
                eq("draft"),
                eq("2026-05-19 12:00:00"),
                anyList()
        );
    }

    @Test
    void shouldEnforceTrustedSnapshotBusinessIdentityBeforeDraftPersistence() throws Exception {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setStoreContext(Map.of(
                "ownerUserId", 99999L,
                "projectCode", "PROJECT-B",
                "storeCode", "STR-B"
        ));
        snapshot.setIdentity(Map.of("skuParent", "SKU-B"));

        ProductMasterSnapshotView baseline = new ProductMasterSnapshotView();
        baseline.setStoreContext(Map.of(
                "ownerUserId", 10002L,
                "projectCode", "PROJECT-A",
                "projectName", "Noon UAE",
                "storeCode", "STR245027-NAE"
        ));
        baseline.setIdentity(Map.of("skuParent", "BASELINE-SKU"));

        ProductMasterActionCommand command = new ProductMasterActionCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("MILKYWAYA17");

        invokeEnforceSnapshotBusinessIdentity(snapshot, baseline, command);

        assertEquals(10002L, snapshot.getStoreContext().get("ownerUserId"));
        assertEquals("PROJECT-A", snapshot.getStoreContext().get("projectCode"));
        assertEquals("STR245027-NAE", snapshot.getStoreContext().get("storeCode"));
        assertEquals("MILKYWAYA17", snapshot.getIdentity().get("skuParent"));
    }

    @Test
    void shouldLimitPublishSiteOffersToTrustedCurrentSite() throws Exception {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setSiteOffers(List.of(
                Map.of("storeCode", "STR245027-NAE", "price", "48"),
                Map.of("storeCode", "STR-OTHER", "price", "1")
        ));

        List<Map<String, Object>> comparable = invokeSiteOfferComparableList(snapshot, "STR245027-NAE");

        assertEquals(1, comparable.size());
        assertEquals("STR245027-NAE", comparable.get(0).get("storeCode"));
        assertEquals(0, invokeSiteOfferComparableList(snapshot, "STR-MISSING").size());
    }

    private void invokeSyncProductMasterStatus(
            Long ownerUserId,
            ProductMasterSnapshotView snapshot
    ) throws Exception {
        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "syncProductMasterStatus",
                Long.class,
                ProductMasterSnapshotView.class,
                String.class,
                String.class,
                List.class
        );
        method.setAccessible(true);
        method.invoke(service, ownerUserId, snapshot, "draft", "2026-05-19 12:00:00", List.of());
    }

    private void invokeEnforceSnapshotBusinessIdentity(
            ProductMasterSnapshotView snapshot,
            ProductMasterSnapshotView baseline,
            ProductMasterActionCommand command
    ) throws Exception {
        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "enforceSnapshotBusinessIdentity",
                ProductMasterSnapshotView.class,
                ProductMasterSnapshotView.class,
                ProductMasterActionCommand.class
        );
        method.setAccessible(true);
        method.invoke(service, snapshot, baseline, command);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> invokeSiteOfferComparableList(
            ProductMasterSnapshotView snapshot,
            String siteCode
    ) throws Exception {
        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "siteOfferComparableList",
                ProductMasterSnapshotView.class,
                String.class,
                boolean.class
        );
        method.setAccessible(true);
        return (List<Map<String, Object>>) method.invoke(service, snapshot, siteCode, false);
    }
}
