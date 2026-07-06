package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductReadModelServiceListDatasetTest {

    @Mock
    private ProductManagementMapper productManagementMapper;

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Mock
    private ProductProjectionPersistenceService productProjectionPersistenceService;

    @Mock
    private ProductDetailBaselineBackfillService productDetailBaselineBackfillService;

    private ProductReadModelService service;

    @BeforeEach
    void setUp() {
        service = new ProductReadModelService(
                productManagementMapper,
                storeSyncMapper,
                productProjectionPersistenceService,
                productDetailBaselineBackfillService
        );
    }

    @Test
    void listDatasetReadsLocalProjectionWithoutInitializationStatusOrBackfillSideEffects() {
        StoreSyncStoreRecord store = ownerStore();
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);

        ProductListSummaryView summary = new ProductListSummaryView();
        summary.setReady(true);
        summary.setStoreCode("STR245027-NAE");
        summary.setSkuParent("PAPERSAYSB132");
        summary.setCurrentZCode("ZPAPER001");
        summary.setPartnerSku("PAPERSAYSB132");
        summary.setTitle("Paper bag");
        summary.setSyncStatus("synced");
        summary.setDetailBaselineStatus("missing");
        summary.setOperationStageCode("TESTING");
        summary.setOperationStageUpdatedAt("2026-07-06 11:35:00");
        summary.setOperationStageUpdatedBy(10003L);
        summary.setLastSyncedAt("2026-06-04 10:00:00");
        summary.setLastPublishTask(Map.of(
                "taskType", "product-delete",
                "statusLabel", "删除中",
                "partnerSku", "PAPERSAYSB132"
        ));
        when(productProjectionPersistenceService.loadProductListSummaries(
                eq(10002L),
                eq("STR245027-NAE"),
                anyList()
        )).thenReturn(List.of(summary));
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");

        ProductListDatasetView view = service.loadListDataset(command);

        assertTrue(view.isReady());
        assertEquals("projection-primary", view.getSource());
        assertEquals(10002L, view.getOwnerUserId());
        assertEquals("STR245027-NAE", view.getStoreCode());
        assertEquals("canman", view.getProjectName());
        assertEquals("PRJ108065", view.getProjectCode());
        assertNull(view.getInitializationStatus());
        assertNull(view.getInitializationMessage());
        assertEquals("2026-06-04 10:00:00", view.getLastDatasetSyncedAt());
        assertEquals(1, view.getItems().size());
        assertEquals("ZPAPER001", view.getItems().get(0).getSkuParent());
        assertEquals("PAPERSAYSB132", view.getItems().get(0).getPartnerSku());
        assertEquals("ZPAPER001", view.getItems().get(0).getCurrentZCode());
        assertEquals("missing", view.getItems().get(0).getDetailBaselineStatus());
        assertEquals("TESTING", view.getItems().get(0).getOperationStageCode());
        assertEquals("2026-07-06 11:35:00", view.getItems().get(0).getOperationStageUpdatedAt());
        assertEquals(10003L, view.getItems().get(0).getOperationStageUpdatedBy());
        assertEquals("删除中", view.getItems().get(0).getLastPublishTask().get("statusLabel"));

        verify(productProjectionPersistenceService).loadProductListSummaries(
                eq(10002L),
                eq("STR245027-NAE"),
                anyList()
        );
        verify(productDetailBaselineBackfillService, never()).state(10002L, "STR245027-NAE", "PAPERSAYSB132");
        verify(productDetailBaselineBackfillService, never()).enqueue(any(), anyString(), any());
    }

    @Test
    void listDatasetMergesRowsByPartnerSkuBeforeCurrentZCode() {
        StoreSyncStoreRecord store = ownerStore();
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);

        ProductListSummaryView oldZ = productSummary("STR245027-NAE", "ZOLD001", "PAPERSAYSB132", "OLD-NOON-CODE");
        oldZ.setCurrentZCode("ZOLD001");
        oldZ.setTitle("Old Z row");
        oldZ.setSyncStatus("synced");
        ProductListSummaryView currentZ = productSummary("STR245027-NAE", "ZNEW001", "PAPERSAYSB132", "NEW-NOON-CODE");
        currentZ.setCurrentZCode("ZNEW001");
        currentZ.setTitle("Current Z row");
        currentZ.setSyncStatus("draft");
        when(productProjectionPersistenceService.loadProductListSummaries(
                eq(10002L),
                eq("STR245027-NAE"),
                anyList()
        )).thenReturn(List.of(currentZ, oldZ));

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");

        ProductListDatasetView view = service.loadListDataset(command);

        assertEquals(1, view.getItems().size());
        assertEquals("PAPERSAYSB132", view.getItems().get(0).getPartnerSku());
        assertEquals("ZNEW001", view.getItems().get(0).getCurrentZCode());
        assertEquals("ZNEW001", view.getItems().get(0).getSkuParent());
        assertEquals("draft", view.getItems().get(0).getSyncStatus());
    }

    @Test
    void listDatasetKeepsStoreScopeWhenPartnerSkuIsSameAcrossStores() {
        StoreSyncStoreRecord store = ownerStore();
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);

        ProductListSummaryView rebuiltSummary = productSummary("STR245027-NAE", "ZNEWPSKU001", "SGGRB113", "NEW-NOON-CODE");
        ProductListSummaryView otherStoreSummary = productSummary("STR-OTHER", "ZOTHERPSKU001", "SGGRB113", "OTHER-NOON-CODE");
        when(productProjectionPersistenceService.loadProductListSummaries(
                eq(10002L),
                eq("STR245027-NAE"),
                anyList()
        )).thenReturn(List.of(rebuiltSummary, otherStoreSummary));

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");

        ProductListDatasetView view = service.loadListDataset(command);

        assertEquals(2, view.getItems().size());
        assertEquals("ZNEWPSKU001", view.getItems().get(0).getSkuParent());
        assertEquals("SGGRB113", view.getItems().get(0).getPartnerSku());
        assertEquals("NEW-NOON-CODE", view.getItems().get(0).getPskuCode());
        assertEquals("ZOTHERPSKU001", view.getItems().get(1).getSkuParent());
    }

    @Test
    void listDatasetDoesNotHideActiveProjectionRowsByDeletedHistoricalSkuParent() {
        StoreSyncStoreRecord store = ownerStore();
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);

        ProductListSummaryView summary = productSummary("STR245027-NAE", "ZREUSED", "PARTNER-SKU-001", "NOON-CODE-001");
        summary.setCurrentZCode("ZREUSED");
        when(productProjectionPersistenceService.loadProductListSummaries(
                eq(10002L),
                eq("STR245027-NAE"),
                anyList()
        )).thenReturn(List.of(summary));

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");

        ProductListDatasetView view = service.loadListDataset(command);

        assertEquals(1, view.getItems().size());
        assertEquals("ZREUSED", view.getItems().get(0).getSkuParent());
        assertEquals("PARTNER-SKU-001", view.getItems().get(0).getPartnerSku());
        verify(productManagementMapper, never()).selectDeletedProductSkuParentsByStoreCode(10002L, "STR245027-NAE");
    }

    @Test
    void groupCandidatesReadFromProjectionSummaries() {
        StoreSyncStoreRecord store = ownerStore();
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);

        ProductListSummaryView candidate = new ProductListSummaryView();
        candidate.setStoreCode("STR245027-NAE");
        candidate.setSkuParent("PAPERSAYSB133");
        candidate.setBrand("Paper");
        when(productProjectionPersistenceService.loadProductGroupCandidateSummaries(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("PAPERSAYSB132"),
                eq(null),
                anyList()
        )).thenReturn(List.of(candidate));

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("PAPERSAYSB132");

        ProductGroupCandidatesView view = service.loadGroupCandidates(command);

        assertTrue(view.isReady());
        assertEquals("projection-primary", view.getSource());
        assertEquals("STR245027-NAE", view.getStoreCode());
        assertEquals("PAPERSAYSB132", view.getSkuParent());
        assertEquals(1, view.getItems().size());
        assertEquals("PAPERSAYSB133", view.getItems().get(0).getSkuParent());
    }

    private StoreSyncStoreRecord ownerStore() {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setStoreCode("STR245027-NAE");
        store.setProjectCode("PRJ108065");
        store.setProjectName("canman");
        return store;
    }

    private ProductListSummaryView productSummary(
            String storeCode,
            String skuParent,
            String partnerSku,
            String pskuCode
    ) {
        ProductListSummaryView summary = new ProductListSummaryView();
        summary.setReady(true);
        summary.setStoreCode(storeCode);
        summary.setSkuParent(skuParent);
        summary.setPartnerSku(partnerSku);
        summary.setPskuCode(pskuCode);
        summary.setTitle(partnerSku + " title");
        summary.setSyncStatus("synced");
        return summary;
    }
}
