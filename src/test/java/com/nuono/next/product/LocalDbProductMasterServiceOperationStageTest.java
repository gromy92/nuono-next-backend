package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.StoreSyncStoreRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalDbProductMasterServiceOperationStageTest {

    @Test
    void updateOperationStageWritesStoreSiteOfferAndReloadsCurrentStoreList() {
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        ProductReadModelService readModelService = mock(ProductReadModelService.class);
        LocalDbProductMasterService service = new LocalDbProductMasterService(
                productManagementMapper,
                null,
                null,
                storeSyncMapper,
                null,
                new ObjectMapper(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                readModelService,
                null,
                null,
                null,
                null
        );
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setStoreCode("STR245027-NAE");
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);
        when(productManagementMapper.updateProductSiteOfferOperationStage(
                10002L,
                "STR245027-NAE",
                "PAPERSAYSB132",
                "CLEARANCE",
                10003L
        )).thenReturn(1);
        ProductListDatasetView expected = new ProductListDatasetView();
        when(readModelService.loadListDataset(any(ProductMasterFetchCommand.class))).thenReturn(expected);

        ProductOperationStageUpdateCommand command = new ProductOperationStageUpdateCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        command.setPartnerSku("PAPERSAYSB132");
        command.setOperationStageCode("CLEARANCE");
        command.setOperatorUserId(10003L);

        ProductListDatasetView actual = service.updateOperationStage(command);

        assertSame(expected, actual);
        verify(productManagementMapper).updateProductSiteOfferOperationStage(
                10002L,
                "STR245027-NAE",
                "PAPERSAYSB132",
                "CLEARANCE",
                10003L
        );
        ArgumentCaptor<ProductMasterFetchCommand> reloadCaptor =
                ArgumentCaptor.forClass(ProductMasterFetchCommand.class);
        verify(readModelService).loadListDataset(reloadCaptor.capture());
        assertEquals(10002L, reloadCaptor.getValue().getOwnerUserId());
        assertEquals("STR245027-NAE", reloadCaptor.getValue().getStoreCode());
    }
}
