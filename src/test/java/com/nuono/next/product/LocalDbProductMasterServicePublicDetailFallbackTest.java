package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
    void openWorkbenchFallsBackToPublicDetailReadonlyViewWhenBaselineIsMissing() {
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        ProductPublicDetailMapper productPublicDetailMapper = mock(ProductPublicDetailMapper.class);
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        ProductProjectionPersistenceService persistenceService = mock(ProductProjectionPersistenceService.class);
        ProductWorkbenchOpenService openService = mock(ProductWorkbenchOpenService.class);
        when(openService.openFromLocalBaseline(any(), any())).thenReturn(null);

        StoreSyncStoreRecord store = store();
        when(storeSyncMapper.selectOwnerStore(308L, "STR353172-NSA")).thenReturn(store);
        when(productManagementMapper.selectProductListProjectionBySkuParent(
                308L,
                "STR353172-NSA",
                "Z203B08BE8C1E820A4CA6Z"
        )).thenReturn(projection());
        when(productPublicDetailMapper.selectLatestUsableSnapshotBySkuParent(
                308L,
                "STR353172-NSA",
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
        command.setStoreCode("STR353172-NSA");
        command.setSkuParent("Z203B08BE8C1E820A4CA6Z");

        ProductMasterWorkbenchView view = service.openWorkbench(command);

        assertEquals(ProductPublicDetailReadonlyWorkbenchFactory.MODE, view.getMode());
        assertTrue(view.isReady());
        assertTrue(view.isDegraded());
        assertEquals("failed", view.getSyncStatus());
        assertEquals(ProductPublicDetailReadonlyWorkbenchFactory.MODE, view.getBaselineSnapshot().getMode());
        assertEquals("Public title", view.getDraftSnapshot().getContent().get("titleEn"));
        assertTrue(view.getWarnings().stream().anyMatch((warning) -> warning.contains("前台公开详情")));
    }

    private StoreSyncStoreRecord store() {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setProjectName("353172");
        store.setProjectCode("PRJ353172");
        store.setStoreCode("STR353172-NSA");
        store.setSite("SA");
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
        detail.setFetchedAt(LocalDateTime.of(2026, 6, 22, 10, 15, 30));
        detail.setStoreCode("STR353172-NSA");
        detail.setSiteCode("SA");
        return detail;
    }
}
