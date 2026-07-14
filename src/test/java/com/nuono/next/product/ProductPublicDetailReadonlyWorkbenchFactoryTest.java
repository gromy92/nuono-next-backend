package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.productpublicdetail.ProductPublicDetailSnapshot;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductPublicDetailReadonlyWorkbenchFactoryTest {

    private final ProductPublicDetailReadonlyWorkbenchFactory factory =
            new ProductPublicDetailReadonlyWorkbenchFactory();

    @Test
    void buildBaselineUsesPublicDetailAndMarksSnapshotReadOnly() {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(308L);
        command.setStoreCode("STR353172-NSA");
        command.setSkuParent("Z203B08BE8C1E820A4CA6Z");
        ProductListProjectionRecord projection = projection();
        StoreSyncStoreRecord store = store();
        ProductPublicDetailSnapshot detail = publicDetail();

        ProductMasterSnapshotView baseline = factory.buildBaseline(command, store, projection, detail);

        assertEquals(ProductPublicDetailReadonlyWorkbenchFactory.MODE, baseline.getMode());
        assertTrue(baseline.isReady());
        assertTrue(baseline.isDegraded());
        assertEquals("Z203B08BE8C1E820A4CA6Z", baseline.getIdentity().get("skuParent"));
        assertEquals(ProductSourceTypeSupport.FOLLOW_SELL, baseline.getIdentity().get("productSourceType"));
        assertEquals("Public title", baseline.getContent().get("titleEn"));
        assertEquals(List.of("https://f.nooncdn.com/p/pzsku/Z203/main.jpg"), baseline.getContent().get("images"));
        assertEquals("https://www.noon.com/saudi-en/Z203B08BE8C1E820A4CA6Z/p/", baseline.getContent().get("detailUrl"));
        assertEquals("88.50", baseline.getPricing().get("price"));
        assertEquals("SAR", baseline.getPricing().get("currency"));
        assertEquals("STR353172-NSA", baseline.getSiteOffers().get(0).get("storeCode"));
        assertEquals("SA", baseline.getSiteOffers().get(0).get("site"));
    }

    @Test
    void siblingSitePublicDetailUsesOnlySharedCatalogFields() {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(308L);
        command.setStoreCode("STR353172-NAE");
        command.setSkuParent("Z203B08BE8C1E820A4CA6Z");

        ProductListProjectionRecord projection = projection();
        StoreSyncStoreRecord store = store();
        store.setStoreCode("STR353172-NAE");
        store.setSite(null);

        ProductPublicDetailSnapshot detail = publicDetail();
        detail.setStoreCode("STR353172-NSA");
        detail.setSiteCode("SA");
        detail.setPriceAmount(new BigDecimal("88.50"));
        detail.setCurrencyCode("SAR");
        detail.setDetailUrl("https://www.noon.com/saudi-en/Z203B08BE8C1E820A4CA6Z/p/");

        ProductMasterSnapshotView baseline = factory.buildBaseline(command, store, projection, detail);

        assertEquals("Public title", baseline.getContent().get("titleEn"));
        assertEquals(List.of("https://f.nooncdn.com/p/pzsku/Z203/main.jpg"), baseline.getContent().get("images"));
        assertFalse(baseline.getContent().containsKey("detailUrl"));
        assertEquals("STR353172-NAE", baseline.getStoreContext().get("storeCode"));
        assertEquals("AE", baseline.getStoreContext().get("site"));
        assertEquals("99.00", baseline.getPricing().get("price"));
        assertFalse(baseline.getPricing().containsKey("currency"));
        assertEquals("STR353172-NAE", baseline.getSiteOffers().get(0).get("storeCode"));
        assertEquals("AE", baseline.getSiteOffers().get(0).get("site"));
        assertEquals("99.00", baseline.getSiteOffers().get(0).get("price"));
        assertFalse(baseline.getSiteOffers().get(0).containsKey("currency"));
    }

    private ProductListProjectionRecord projection() {
        ProductListProjectionRecord projection = new ProductListProjectionRecord();
        projection.setSkuParent("Z203B08BE8C1E820A4CA6Z");
        projection.setProductSourceType(ProductSourceTypeSupport.FOLLOW_SELL);
        projection.setPartnerSku("PARTNER-001");
        projection.setPskuCode("PSKU-001");
        projection.setTitle("List title");
        projection.setTitleCn("列表标题");
        projection.setBrand("List brand");
        projection.setImageUrl("https://cdn.example/list.jpg");
        projection.setProductFulltype("home_decor-lighting");
        projection.setReferencePrice("99.00");
        projection.setSalePrice("79.00");
        projection.setCurrentSiteLiveStatus("LIVE");
        return projection;
    }

    private StoreSyncStoreRecord store() {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setProjectName("353172");
        store.setProjectCode("PRJ353172");
        store.setStoreCode("STR353172-NSA");
        store.setSite("SA");
        return store;
    }

    private ProductPublicDetailSnapshot publicDetail() {
        ProductPublicDetailSnapshot detail = new ProductPublicDetailSnapshot();
        detail.setSyncStatus(ProductPublicDetailSyncStatus.PARTIAL);
        detail.setNoonProductCode("Z203B08BE8C1E820A4CA6Z");
        detail.setCodeType("Z_CODE");
        detail.setTitleEn("Public title");
        detail.setTitleAr("عنوان");
        detail.setBrand("Public brand");
        detail.setCategoryPath("Home / Lighting");
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
