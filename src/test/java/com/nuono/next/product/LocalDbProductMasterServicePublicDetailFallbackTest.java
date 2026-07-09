package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.productpublicdetail.ProductPublicDetailSnapshot;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class LocalDbProductMasterServicePublicDetailFallbackTest {

    @Test
    void openWorkbenchFallsBackToSiblingSitePublicDetailReadonlyViewWhenBaselineIsMissing() {
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        ProductPublicDetailMapper productPublicDetailMapper = mock(ProductPublicDetailMapper.class);
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        ProductProjectionPersistenceService persistenceService = mock(ProductProjectionPersistenceService.class);
        ProductWorkbenchOpenService openService = mock(ProductWorkbenchOpenService.class);
        when(openService.openFromLocalBaseline(any(), any())).thenReturn(null);

        StoreSyncStoreRecord store = store();
        when(storeSyncMapper.selectOwnerStore(308L, "STR353172-NAE")).thenReturn(store);
        when(productManagementMapper.selectProductListProjectionBySkuParent(
                308L,
                "STR353172-NAE",
                "Z203B08BE8C1E820A4CA6Z"
        )).thenReturn(projection());
        when(productPublicDetailMapper.selectLatestUsableSnapshotBySkuParent(
                308L,
                "STR353172-NAE",
                "Z203B08BE8C1E820A4CA6Z"
        )).thenReturn(publicDetail());

        LocalDbProductMasterService service = new LocalDbProductMasterService(
                productManagementMapper,
                null,
                productPublicDetailMapper,
                storeSyncMapper,
                null,
                new ObjectMapper(),
                null,
                persistenceService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ProductWorkbenchRecordStore(),
                openService,
                null,
                null
        );

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(308L);
        command.setStoreCode("STR353172-NAE");
        command.setSkuParent("Z203B08BE8C1E820A4CA6Z");

        ProductMasterWorkbenchView view = service.openWorkbench(command);

        assertEquals(ProductPublicDetailReadonlyWorkbenchFactory.MODE, view.getMode());
        assertTrue(view.isReady());
        assertTrue(view.isDegraded());
        assertEquals("failed", view.getSyncStatus());
        assertEquals(ProductPublicDetailReadonlyWorkbenchFactory.MODE, view.getBaselineSnapshot().getMode());
        assertEquals("Public title", view.getDraftSnapshot().getContent().get("titleEn"));
        assertFalse(view.getDraftSnapshot().getContent().containsKey("detailUrl"));
        assertEquals("STR353172-NAE", view.getDraftSnapshot().getStoreContext().get("storeCode"));
        assertEquals("AE", view.getDraftSnapshot().getStoreContext().get("site"));
        assertEquals("99.00", view.getDraftSnapshot().getPricing().get("price"));
        assertFalse(view.getDraftSnapshot().getPricing().containsKey("currency"));
        assertEquals("STR353172-NAE", view.getDraftSnapshot().getSiteOffers().get(0).get("storeCode"));
        assertEquals("AE", view.getDraftSnapshot().getSiteOffers().get(0).get("site"));
        assertFalse(view.getDraftSnapshot().getSiteOffers().get(0).containsKey("currency"));
        assertTrue(view.getWarnings().stream().anyMatch((warning) -> warning.contains("前台公开详情")));
    }

    @Test
    void openWorkbenchUsesPartnerSkuInsteadOfRequestZCodeWhenPartnerSkuIsPresent() {
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        ProductPublicDetailMapper productPublicDetailMapper = mock(ProductPublicDetailMapper.class);
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        ProductProjectionPersistenceService persistenceService = mock(ProductProjectionPersistenceService.class);
        ProductWorkbenchOpenService openService = mock(ProductWorkbenchOpenService.class);
        when(openService.openFromLocalBaseline(any(), any())).thenReturn(null);

        StoreSyncStoreRecord store = store();
        when(storeSyncMapper.selectOwnerStore(308L, "STR353172-NAE")).thenReturn(store);
        when(productManagementMapper.selectLogicalStoreIdByOwnerStoreCode(308L, "STR353172-NAE"))
                .thenReturn(501L);
        when(productManagementMapper.selectProductListProjectionByStorePartnerSku(
                501L,
                "STR353172-NAE",
                "PARTNER-001"
        )).thenReturn(projection());
        when(productPublicDetailMapper.selectLatestUsableSnapshotBySkuParent(
                308L,
                "STR353172-NAE",
                "PARTNER-001"
        )).thenReturn(publicDetail());

        LocalDbProductMasterService service = new LocalDbProductMasterService(
                productManagementMapper,
                null,
                productPublicDetailMapper,
                storeSyncMapper,
                null,
                new ObjectMapper(),
                null,
                persistenceService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ProductWorkbenchRecordStore(),
                openService,
                null,
                null
        );

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(308L);
        command.setStoreCode("STR353172-NAE");
        command.setPartnerSku("PARTNER-001");
        command.setSkuParent("ZSTALE-BELONGS-OTHER-PSKU");

        ProductMasterWorkbenchView view = service.openWorkbench(command);

        verify(productManagementMapper, never()).selectProductListProjectionBySkuParent(
                308L,
                "STR353172-NAE",
                "ZSTALE-BELONGS-OTHER-PSKU"
        );
        verify(productPublicDetailMapper, never()).selectLatestUsableSnapshotBySkuParent(
                308L,
                "STR353172-NAE",
                "ZSTALE-BELONGS-OTHER-PSKU"
        );
        verify(productManagementMapper).selectProductListProjectionByStorePartnerSku(
                501L,
                "STR353172-NAE",
                "PARTNER-001"
        );
        verify(productPublicDetailMapper).selectLatestUsableSnapshotBySkuParent(
                308L,
                "STR353172-NAE",
                "PARTNER-001"
        );
        assertEquals(ProductPublicDetailReadonlyWorkbenchFactory.MODE, view.getMode());
        assertTrue(view.isReady());
    }

    private StoreSyncStoreRecord store() {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setProjectName("353172");
        store.setProjectCode("PRJ353172");
        store.setStoreCode("STR353172-NAE");
        store.setSite("AE");
        return store;
    }

    private ProductListProjectionRecord projection() {
        ProductListProjectionRecord projection = new ProductListProjectionRecord();
        projection.setSkuParent("Z203B08BE8C1E820A4CA6Z");
        projection.setProductSourceType(ProductSourceTypeSupport.FOLLOW_SELL);
        projection.setPartnerSku("PARTNER-001");
        projection.setPskuCode("PSKU-001");
        projection.setTitle("List title");
        projection.setBrand("List brand");
        projection.setImageUrl("https://cdn.example/list.jpg");
        projection.setProductFulltype("home_decor-lighting");
        projection.setReferencePrice("99.00");
        projection.setCurrentSiteLiveStatus("LIVE");
        return projection;
    }

    private ProductPublicDetailSnapshot publicDetail() {
        ProductPublicDetailSnapshot detail = new ProductPublicDetailSnapshot();
        detail.setSyncStatus(ProductPublicDetailSyncStatus.PARTIAL);
        detail.setNoonProductCode("Z203B08BE8C1E820A4CA6Z");
        detail.setSkuParent("Z203B08BE8C1E820A4CA6Z");
        detail.setCodeType("Z_CODE");
        detail.setTitleEn("Public title");
        detail.setBrand("Public brand");
        detail.setPriceAmount(new BigDecimal("88.50"));
        detail.setCurrencyCode("SAR");
        detail.setMainImageUrl("https://f.nooncdn.com/p/pzsku/Z203/main.jpg");
        detail.setDetailUrl("https://www.noon.com/saudi-en/Z203B08BE8C1E820A4CA6Z/p/");
        detail.setFetchedAt(LocalDateTime.of(2026, 6, 22, 10, 15, 30));
        detail.setStoreCode("STR353172-NSA");
        detail.setSiteCode("SA");
        return detail;
    }
}
