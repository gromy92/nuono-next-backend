package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class LocalDbProductMasterServiceListDatasetTest {

    @Test
    void listSummaryAcceptsPartnerSkuOnlyIdentity() {
        ProductProjectionPersistenceService projectionPersistenceService =
                mock(ProductProjectionPersistenceService.class);
        LocalDbProductMasterService service = service(projectionPersistenceService);
        ProductListSummaryView expected = new ProductListSummaryView();
        when(projectionPersistenceService.loadProductListSummary(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("PAPERSAYSB132"),
                eq(null),
                anyList()
        )).thenReturn(expected);

        ProductMasterFetchCommand command = command();
        ProductListSummaryView actual = service.loadListSummary(command);

        assertSame(expected, actual);
    }

    @Test
    void historyAcceptsPartnerSkuOnlyIdentity() {
        ProductProjectionPersistenceService projectionPersistenceService =
                mock(ProductProjectionPersistenceService.class);
        LocalDbProductMasterService service = service(projectionPersistenceService);
        ProductHistoryView expected = new ProductHistoryView();
        when(projectionPersistenceService.loadProductHistoryView(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("PAPERSAYSB132"),
                eq(null),
                anyList()
        )).thenReturn(expected);

        ProductMasterFetchCommand command = command();
        ProductHistoryView actual = service.loadHistory(command);

        assertSame(expected, actual);
    }

    private ProductMasterFetchCommand command() {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        command.setPartnerSku("PAPERSAYSB132");
        return command;
    }

    private LocalDbProductMasterService service(ProductProjectionPersistenceService projectionPersistenceService) {
        return new LocalDbProductMasterService(
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper(),
                null,
                projectionPersistenceService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
