package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.LocalDbStoreInitializationService;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductListStatusResolverTest {

    @Mock
    private ProductManagementMapper productManagementMapper;
    @Mock
    private StoreSyncMapper storeSyncMapper;
    @Mock
    private ProductProjectionPersistenceService projectionPersistenceService;
    @Mock
    private ProductDetailBaselineBackfillService productDetailBaselineBackfillService;

    private ProductReadModelService service;

    @BeforeEach
    void setUp() {
        service = new ProductReadModelService(
                productManagementMapper,
                storeSyncMapper,
                projectionPersistenceService,
                productDetailBaselineBackfillService
        );
    }

    @Test
    void explicitListingFailuresOverrideDraftButUncertainWritesDoNot() {
        assertEquals("failed", ProductListStatusResolver.resolve(item("draft", "failed")));
        assertEquals("failed", ProductListStatusResolver.resolve(item("draft", "rejected")));
        assertEquals("draft", ProductListStatusResolver.resolve(item("draft", "written_verify_failed")));
        assertEquals("draft", ProductListStatusResolver.resolve(item("draft", null)));
        assertEquals("failed", ProductListStatusResolver.resolve(item("failed", null)));
    }

    @Test
    void datasetCountsLatestExplicitListingFailuresWithoutChangingSyncStatus() {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setStoreCode("STR245027-NAE");
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);
        when(projectionPersistenceService.loadProductListSummaries(
                eq(10002L),
                eq("STR245027-NAE"),
                anyList()
        )).thenReturn(List.of(
                summary("FAIL-001", "draft", "failed"),
                summary("REJECT-001", "draft", "rejected"),
                summary("VERIFY-001", "draft", "written_verify_failed"),
                summary("SYNC-FAIL-001", "failed", null),
                summary("SYNCED-001", "synced", "succeeded")
        ));

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        ProductListDatasetView view = service.loadListDataset(command);

        assertEquals(3, view.getFailedCount());
        assertEquals(1, view.getDraftCount());
        assertEquals(1, view.getSyncedCount());
        assertEquals("draft", view.getItems().get(0).getSyncStatus());
        assertEquals("failed", view.getItems().get(0).getListingPublishTask().get("status"));
    }

    private LocalDbStoreInitializationService.StoreInitializationProductListItemView item(
            String syncStatus,
            String listingStatus
    ) {
        LocalDbStoreInitializationService.StoreInitializationProductListItemView item =
                new LocalDbStoreInitializationService.StoreInitializationProductListItemView();
        item.setSyncStatus(syncStatus);
        if (listingStatus != null) {
            item.setListingPublishTask(Map.of("status", listingStatus));
        }
        return item;
    }

    private ProductListSummaryView summary(
            String partnerSku,
            String syncStatus,
            String listingStatus
    ) {
        ProductListSummaryView summary = new ProductListSummaryView();
        summary.setReady(true);
        summary.setStoreCode("STR245027-NAE");
        summary.setSkuParent("LOCAL-" + partnerSku);
        summary.setCurrentZCode("LOCAL-" + partnerSku);
        summary.setPartnerSku(partnerSku);
        summary.setTitle(partnerSku);
        summary.setSyncStatus(syncStatus);
        summary.setDetailBaselineStatus("ready");
        if (listingStatus != null) {
            summary.setListingPublishTask(Map.of("status", listingStatus));
        }
        return summary;
    }
}
