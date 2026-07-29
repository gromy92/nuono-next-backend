package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
class ProductReadModelServiceGroupCandidatesTest {

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
    void groupCandidatesReadFromProjectionSummaries() {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setStoreCode("STR245027-NAE");
        store.setProjectCode("PRJ108065");
        store.setProjectName("canman");
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
}
