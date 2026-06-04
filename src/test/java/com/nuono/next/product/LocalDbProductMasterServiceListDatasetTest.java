package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.LocalDbStoreInitializationService;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbProductMasterServiceListDatasetTest {

    @Mock
    private ProductManagementMapper productManagementMapper;

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Mock
    private ProductProjectionPersistenceService productProjectionPersistenceService;

    @Mock
    private LocalDbStoreInitializationService localDbStoreInitializationService;

    private LocalDbProductMasterService service;

    @BeforeEach
    void setUp() {
        service = new LocalDbProductMasterService(
                productManagementMapper,
                storeSyncMapper,
                null,
                new ObjectMapper(),
                null,
                null,
                productProjectionPersistenceService,
                localDbStoreInitializationService,
                null,
                null,
                null
        );
    }

    @Test
    void listDatasetReadsLocalProjectionWithoutInitializationStatus() {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setStoreCode("STR245027-NAE");
        store.setProjectCode("PRJ108065");
        store.setProjectName("canman");
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);
        when(productManagementMapper.selectDeletedProductSkuParentsByStoreCode(10002L, "STR245027-NAE"))
                .thenReturn(List.of());

        ProductListSummaryView summary = new ProductListSummaryView();
        summary.setReady(true);
        summary.setStoreCode("STR245027-NAE");
        summary.setSkuParent("PAPERSAYSB132");
        summary.setTitle("Paper bag");
        summary.setSyncStatus("synced");
        summary.setDetailBaselineStatus("missing");
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
        assertEquals(1, view.getItems().size());
        assertEquals("PAPERSAYSB132", view.getItems().get(0).getSkuParent());

        verify(productProjectionPersistenceService).loadProductListSummaries(
                eq(10002L),
                eq("STR245027-NAE"),
                anyList()
        );
        verifyNoInteractions(localDbStoreInitializationService);
    }
}
