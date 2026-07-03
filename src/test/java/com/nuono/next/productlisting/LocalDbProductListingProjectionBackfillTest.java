package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductListingProjectionMapper;
import com.nuono.next.product.ProductMasterSnapshotView;
import com.nuono.next.product.ProductProjectionPersistenceService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class LocalDbProductListingProjectionBackfillTest {

    @Mock
    private ProductProjectionPersistenceService projectionPersistenceService;

    @Mock
    private ProductListingProjectionMapper projectionMapper;

    private LocalDbProductListingProjectionBackfill backfill;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        backfill = new LocalDbProductListingProjectionBackfill(
                projectionPersistenceService,
                projectionMapper
        );
    }

    @Test
    void shouldPersistDraftSnapshotProjectionWhenDraftHasPsku() {
        ProductListingStoreProjectionContext storeContext = new ProductListingStoreProjectionContext();
        storeContext.setProjectCode("PRJ69486");
        storeContext.setProjectName("xingyao");
        storeContext.setStoreCode("STR245027-NAE");
        storeContext.setSite("SA");
        when(projectionMapper.selectStoreContext(10002L, "STR245027-NAE"))
                .thenReturn(storeContext);

        ProductListingDraftRecord record = new ProductListingDraftRecord();
        record.setId(10001L);
        record.setOwnerUserId(10002L);
        record.setStoreCode("STR245027-NAE");
        record.setDraftNo("PLD-10001");
        ProductListingDraftCommand draft = ProductListingTestFixtures.validCommand();
        draft.setProductTitleCn("本地草稿中文标题");

        backfill.backfillDraftListing(record, draft);

        ArgumentCaptor<ProductMasterSnapshotView> snapshotCaptor =
                ArgumentCaptor.forClass(ProductMasterSnapshotView.class);
        verify(projectionPersistenceService).persistSnapshotProjection(
                eq(10002L),
                snapshotCaptor.capture(),
                eq("draft"),
                anyString(),
                anyList()
        );
        ProductMasterSnapshotView snapshot = snapshotCaptor.getValue();
        assertEquals("listing-draft", snapshot.getMode());
        assertTrue(snapshot.isReady());
        assertEquals("STR245027-NAE", snapshot.getStoreContext().get("storeCode"));
        assertEquals("PRJ69486", snapshot.getStoreContext().get("projectCode"));
        assertEquals("NN-TEST-PSKU", snapshot.getIdentity().get("partnerSku"));
        assertTrue(String.valueOf(snapshot.getIdentity().get("skuParent")).startsWith("LOCAL-NN-TEST-PSKU"));
        assertEquals("本地草稿中文标题", snapshot.getContent().get("titleCn"));
        assertEquals(List.of("https://example.test/images/sku-main.jpg"), snapshot.getContent().get("images"));
        assertEquals(1, snapshot.getSiteOffers().size());
        Map<String, Object> siteOffer = snapshot.getSiteOffers().get(0);
        assertEquals("STR245027-NAE", siteOffer.get("storeCode"));
        assertEquals("NN-TEST-PSKU", siteOffer.get("partnerSku"));
        assertEquals("49.9", siteOffer.get("price"));
        assertEquals(24, siteOffer.get("idWarranty"));
        assertNotNull(siteOffer.get("fbpStock"));
    }

    @Test
    void shouldPersistSuccessfulListingWithListingStartedMetadata() {
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
        task.setCompletedAt(LocalDateTime.of(2026, 7, 3, 10, 24, 3));
        ProductListingDraftCommand draft = ProductListingTestFixtures.validCommand();
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("verify_noon_readback");
        step.setStatus("succeeded");
        step.setExternalReference("skuParent=ZTEST001;pskuCode=PSKU-CODE-1");

        backfill.backfillSuccessfulListing(task, draft, ProductListingNoonWriteResult.succeeded(List.of(step)));

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
        assertEquals("2026-07-03 10:24:03", offer.getListingStartedAt());
        assertEquals("product_listing", offer.getListingStartedSource());
    }
}
