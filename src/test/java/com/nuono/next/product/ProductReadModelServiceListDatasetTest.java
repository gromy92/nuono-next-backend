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
        when(productManagementMapper.selectDeletedProductSkuParentsByStoreCode(10002L, "STR245027-NAE"))
                .thenReturn(List.of());

        ProductListSummaryView summary = new ProductListSummaryView();
        summary.setReady(true);
        summary.setStoreCode("STR245027-NAE");
        summary.setSkuParent("PAPERSAYSB132");
        summary.setCurrentZCode("ZPAPER001");
        summary.setPartnerSku("PAPERSAYSB132");
        summary.setTitle("Paper bag");
        summary.setSyncStatus("synced");
        summary.setDetailBaselineStatus("missing");
        summary.setLastSyncedAt("2026-06-04 10:00:00");
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
        when(productManagementMapper.selectDeletedProductSkuParentsByStoreCode(10002L, "STR245027-NAE"))
                .thenReturn(List.of());

        ProductListSummaryView oldZ = new ProductListSummaryView();
        oldZ.setReady(true);
        oldZ.setStoreCode("STR245027-NAE");
        oldZ.setSkuParent("ZOLD001");
        oldZ.setCurrentZCode("ZOLD001");
        oldZ.setPartnerSku("PAPERSAYSB132");
        oldZ.setTitle("Old Z row");
        oldZ.setSyncStatus("synced");

        ProductListSummaryView currentZ = new ProductListSummaryView();
        currentZ.setReady(true);
        currentZ.setStoreCode("STR245027-NAE");
        currentZ.setSkuParent("ZNEW001");
        currentZ.setCurrentZCode("ZNEW001");
        currentZ.setPartnerSku("PAPERSAYSB132");
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
}
