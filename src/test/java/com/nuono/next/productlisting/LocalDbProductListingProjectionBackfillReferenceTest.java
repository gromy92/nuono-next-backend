package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductListingProjectionMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductImageProfileService;
import com.nuono.next.product.ProductProjectionPersistenceService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class LocalDbProductListingProjectionBackfillReferenceTest {

    @Mock
    private ProductProjectionPersistenceService projectionPersistenceService;

    @Mock
    private ProductListingProjectionMapper projectionMapper;

    @Mock
    private ProductImageProfileService productImageProfileService;

    @Mock
    private ProductManagementMapper productManagementMapper;

    private LocalDbProductListingProjectionBackfill backfill;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        backfill = new LocalDbProductListingProjectionBackfill(
                projectionPersistenceService,
                projectionMapper,
                productImageProfileService,
                productManagementMapper
        );
    }

    @Test
    void shouldFitProductRebuildInheritedListingSourceIntoColumnLimit() {
        ProductListingStoreProjectionContext storeContext = new ProductListingStoreProjectionContext();
        storeContext.setProjectCode("PRJ69486");
        storeContext.setProjectName("xingyao");
        storeContext.setStoreCode("STR245027-NAE");
        storeContext.setSite("SA");
        when(projectionMapper.selectStoreContext(10002L, "STR245027-NAE"))
                .thenReturn(storeContext);
        when(projectionMapper.selectProjectStoreContexts(10002L, "PRJ69486"))
                .thenReturn(List.of(storeContext));

        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");
        ProductListingDraftCommand draft = ProductListingTestFixtures.validCommand();
        draft.setInheritedListingStartedAt("2026-07-03 10:24:03");
        draft.setInheritedListingStartedSource("product_listing");
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("create_product");
        step.setStatus("succeeded");
        step.setExternalReference("skuParent=ZREBUILD001;pskuCode=PSKU-REBUILD-1");

        backfill.backfillSuccessfulListing(
                task,
                draft,
                ProductListingNoonWriteResult.succeeded(List.of(step))
        );

        ArgumentCaptor<List> seedCaptor = ArgumentCaptor.forClass(List.class);
        verify(projectionPersistenceService).persistInitializationProjection(
                eq(10002L),
                eq("PRJ69486"),
                eq("xingyao"),
                eq("STR245027-NAE"),
                anyList(),
                seedCaptor.capture(),
                anyList(),
                eq(true)
        );
        ProductProjectionPersistenceService.ProductMasterSeed seed =
                (ProductProjectionPersistenceService.ProductMasterSeed) seedCaptor.getValue().get(0);
        ProductProjectionPersistenceService.SiteOfferSeed offer = seed.getSiteOffers().get(0);
        assertEquals("product_rebuild_inherited:listing", offer.getListingStartedSource());
        assertTrue(offer.getListingStartedSource().length() <= 40);
    }

    @Test
    void shouldRejectSuccessfulProjectionWhenCreateReferencesAreSplitAcrossSteps() {
        ProductListingStoreProjectionContext storeContext =
                new ProductListingStoreProjectionContext();
        storeContext.setProjectCode("PRJ69486");
        storeContext.setProjectName("xingyao");
        storeContext.setStoreCode("STR245027-NAE");
        storeContext.setSite("SA");
        when(projectionMapper.selectStoreContext(
                10002L,
                "STR245027-NAE"
        )).thenReturn(storeContext);
        when(projectionMapper.selectProjectStoreContexts(
                10002L,
                "PRJ69486"
        )).thenReturn(List.of(storeContext));
        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");
        ProductListingNoonWriteStepResult create =
                new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("succeeded");
        create.setExternalReference("skuParent=Z-SPLIT");
        ProductListingNoonWriteStepResult resolve =
                new ProductListingNoonWriteStepResult();
        resolve.setStepKey("resolve_create_reference");
        resolve.setStatus("succeeded");
        resolve.setExternalReference("pskuCode=PSKU-SPLIT");

        boolean projected = backfill.backfillSuccessfulListing(
                task,
                ProductListingTestFixtures.validCommand(),
                ProductListingNoonWriteResult.succeeded(
                        List.of(create, resolve)
                )
        );

        assertFalse(projected);
        verify(projectionPersistenceService, never())
                .persistInitializationProjection(
                        eq(10002L),
                        anyString(),
                        anyString(),
                        eq("STR245027-NAE"),
                        anyList(),
                        anyList(),
                        anyList(),
                        eq(true)
                );
    }
}
