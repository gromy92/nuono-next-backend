package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.CoreTableStatusMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.system.BootstrapProperties;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ProductProjectionPersistenceServiceListSummaryTest {

    @Mock
    private ProductManagementMapper productManagementMapper;

    @Mock
    private CoreTableStatusMapper coreTableStatusMapper;

    private ProductProjectionPersistenceService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        BootstrapProperties bootstrapProperties = new BootstrapProperties();
        service = new ProductProjectionPersistenceService(
                productManagementMapper,
                coreTableStatusMapper,
                bootstrapProperties,
                new ObjectMapper(),
                new ProductKeyContentHistoryAssembler(),
                null
        );
        when(coreTableStatusMapper.findExistingTableNames(eq("nuono_new_dev"), anyList()))
                .thenAnswer(invocation -> new ArrayList<>((List<String>) invocation.getArgument(1)));
    }

    @Test
    void shouldLoadAuthoritativeProjectionSummaryWhenRowExists() {
        ProductListProjectionRecord record = new ProductListProjectionRecord();
        record.setSkuParent("ZTEST001");
        record.setCurrentZCode("ZTEST001");
        record.setPartnerSku("PARTNER-001");
        record.setPskuCode("PSKU-001");
        record.setOfferCode("OFFER-001");
        record.setTitle("Amber Burner");
        record.setTitleCn("星耀琥珀香薰炉");
        record.setBrand("xingyao");
        record.setImageUrl("https://img.example.com/a.jpg");
        record.setReferencePrice("139.00");
        record.setOriginalPrice("159.00");
        record.setSalePrice("139.00");
        record.setProductFulltype("Home > Burner");
        record.setGroupRef("XINGYAO");
        record.setCurrentSiteActiveFlag(1);
        record.setCurrentSiteLiveStatus("LIVE");
        record.setCurrentSiteStatusCode("LIVE");
        record.setListingStartedAt("2026-05-10 00:00:00");
        record.setListingStartedSource("pv");
        record.setSyncStatus("draft");
        record.setLastSyncedAt("2026-04-27 12:30:00");
        record.setDetailBaselineStatus("ready");
        record.setDetailBaselineSyncedAt("2026-04-27 12:31:00");
        record.setVariantCount(2);
        record.setProductVariantSpecTotalCount(2);
        record.setProductVariantSpecReadyCount(1);
        record.setProductVariantSpecMaintainedCount(1);
        record.setSiteOfferCount(1);
        record.setSiteLabelsCsv("AE");
        record.setLiveStatusesCsv("LIVE");
        record.setTotalFbnStock(12);
        record.setTotalFbpStock(5);

        when(productManagementMapper.selectProductListProjectionBySkuParent(10002L, "STR245027-NAE", "ZTEST001"))
                .thenReturn(record);

        ProductListSummaryView summary = service.loadProductListSummary(
                10002L,
                "STR245027-NAE",
                "ZTEST001",
                new ArrayList<>()
        );

        assertTrue(summary.isReady());
        assertEquals("projection", summary.getSource());
        assertEquals("ZTEST001", summary.getSkuParent());
        assertEquals("ZTEST001", summary.getCurrentZCode());
        assertEquals("PARTNER-001", summary.getPartnerSku());
        assertEquals("draft", summary.getSyncStatus());
        assertEquals("ready", summary.getDetailBaselineStatus());
        assertEquals("详情基线已准备。", summary.getDetailBaselineMessage());
        assertEquals("2026-04-27 12:31:00", summary.getDetailBaselineSyncedAt());
        assertEquals("incomplete", summary.getProductVariantSpecStatus());
        assertEquals(2, summary.getProductVariantSpecTotalCount());
        assertEquals(1, summary.getProductVariantSpecReadyCount());
        assertEquals(1, summary.getProductVariantSpecMaintainedCount());
        assertEquals("139.00", summary.getReferencePrice());
        assertEquals(Boolean.TRUE, summary.getIsActive());
        assertEquals("LIVE", summary.getLiveStatus());
        assertEquals("2026-05-10 00:00:00", summary.getListingStartedAt());
        assertEquals("pv", summary.getListingStartedSource());
        assertEquals("星耀琥珀香薰炉", summary.getTitleCn());
        assertEquals(List.of("AE"), summary.getSiteLabels());
        assertEquals(List.of("LIVE"), summary.getLiveStatuses());
    }

    @Test
    void shouldPreferPartnerSkuWhenLoadingProjectionSummary() {
        ProductListProjectionRecord record = new ProductListProjectionRecord();
        record.setSkuParent("ZCURRENT001");
        record.setCurrentZCode("ZCURRENT001");
        record.setPartnerSku("PARTNER-PSKU-001");
        record.setPskuCode("NOON-EXTERNAL-PSKU");
        record.setTitle("PSKU anchored product");
        record.setSyncStatus("synced");

        when(productManagementMapper.selectLogicalStoreIdByOwnerStoreCode(10002L, "STR245027-NAE"))
                .thenReturn(50001L);
        when(productManagementMapper.selectProductListProjectionByStorePartnerSku(
                50001L,
                "STR245027-NAE",
                "PARTNER-PSKU-001"
        )).thenReturn(record);

        ProductListSummaryView summary = service.loadProductListSummary(
                10002L,
                "STR245027-NAE",
                "PARTNER-PSKU-001",
                null,
                new ArrayList<>()
        );

        assertTrue(summary.isReady());
        assertEquals("PARTNER-PSKU-001", summary.getPartnerSku());
        assertEquals("ZCURRENT001", summary.getCurrentZCode());
        assertEquals("ZCURRENT001", summary.getSkuParent());
        verify(productManagementMapper, never()).selectProductListProjectionBySkuParent(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("PARTNER-PSKU-001")
        );
    }

    @Test
    void shouldReturnMissingSummaryWhenProjectionRowAbsent() {
        when(productManagementMapper.selectProductListProjectionBySkuParent(10002L, "STR245027-NAE", "ZMISS001"))
                .thenReturn(null);

        ProductListSummaryView summary = service.loadProductListSummary(
                10002L,
                "STR245027-NAE",
                "ZMISS001",
                new ArrayList<>()
        );

        assertFalse(summary.isReady());
        assertEquals("missing", summary.getSource());
        assertEquals("ZMISS001", summary.getSkuParent());
    }

    @Test
    void shouldFallbackToCurrentZCodeWhenPartnerSkuIsAbsent() {
        ProductListProjectionRecord record = new ProductListProjectionRecord();
        record.setSkuParent("ZLEGACY001");
        record.setCurrentZCode("ZLEGACY001");
        record.setTitle("Legacy product");
        record.setSyncStatus("synced");
        when(productManagementMapper.selectProductListProjectionBySkuParent(10002L, "STR245027-NAE", "ZLEGACY001"))
                .thenReturn(record);

        ProductListSummaryView summary = service.loadProductListSummary(
                10002L,
                "STR245027-NAE",
                null,
                "ZLEGACY001",
                new ArrayList<>()
        );

        assertTrue(summary.isReady());
        assertEquals("ZLEGACY001", summary.getSkuParent());
        assertEquals("ZLEGACY001", summary.getCurrentZCode());
    }

    @Test
    void historyShouldResolveProductMasterByPartnerSkuBeforeSkuParent() {
        ProductListProjectionRecord record = new ProductListProjectionRecord();
        record.setSkuParent("ZCURRENT001");
        record.setCurrentZCode("ZCURRENT001");
        record.setPartnerSku("PARTNER-PSKU-001");
        record.setTitle("PSKU history product");
        record.setSyncStatus("synced");

        when(productManagementMapper.selectLogicalStoreIdByOwnerStoreCode(10002L, "STR245027-NAE"))
                .thenReturn(50001L);
        when(productManagementMapper.selectProductListProjectionByStorePartnerSku(
                50001L,
                "STR245027-NAE",
                "PARTNER-PSKU-001"
        )).thenReturn(record);
        when(productManagementMapper.selectProductMasterIdByStorePartnerSku(50001L, "PARTNER-PSKU-001"))
                .thenReturn(52001L);
        when(productManagementMapper.selectRecentProductPublishTasks(52001L)).thenReturn(List.of());
        when(productManagementMapper.selectRecentProductActionLogs(52001L)).thenReturn(List.of());
        when(productManagementMapper.selectPendingProductKeyContentHistories(52001L)).thenReturn(List.of());
        when(productManagementMapper.selectVisibleProductKeyContentHistories(52001L)).thenReturn(List.of());
        when(productManagementMapper.countVisibleProductKeyContentHistories(52001L)).thenReturn(0);
        when(productManagementMapper.countPendingProductKeyContentHistories(52001L)).thenReturn(0);

        ProductHistoryView view = service.loadProductHistoryView(
                10002L,
                "STR245027-NAE",
                "PARTNER-PSKU-001",
                null,
                new ArrayList<>()
        );

        assertTrue(view.isReady());
        assertEquals("PARTNER-PSKU-001", view.getListSummary().getPartnerSku());
        assertEquals("ZCURRENT001", view.getListSummary().getCurrentZCode());
        verify(productManagementMapper, never()).selectProductMasterIdByStoreCode(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("ZCURRENT001")
        );
    }

    @Test
    void listSummariesShouldAttachLastPublishTaskWithoutHydratingHistoryMetadataPerRow() {
        ProductListProjectionRecord record = new ProductListProjectionRecord();
        record.setSkuParent("ZTEST001");
        record.setCurrentZCode("ZTEST001");
        record.setPartnerSku("PARTNER-001");
        record.setTitle("Amber Burner");
        record.setDetailBaselineStatus("missing");
        record.setSyncStatus("synced");
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64001L);
        task.setProductMasterId(52001L);
        task.setTaskType("product-delete");
        task.setStatus("queued");
        task.setPartnerSku("PARTNER-001");
        task.setPskuCode("NOON-001");
        task.setSubmittedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        when(productManagementMapper.selectProductListProjection(10002L, "STR245027-NAE"))
                .thenReturn(List.of(record));
        when(productManagementMapper.selectLogicalStoreIdBySiteStoreCode("STR245027-NAE"))
                .thenReturn(50003L);
        when(productManagementMapper.selectLatestProductPublishTasksByStorePartnerSkus(
                eq(50003L),
                eq(List.of("PARTNER-001"))
        ))
                .thenReturn(List.of(task));

        List<ProductListSummaryView> summaries = service.loadProductListSummaries(
                10002L,
                "STR245027-NAE",
                new ArrayList<>()
        );

        assertEquals(1, summaries.size());
        assertEquals("ZTEST001", summaries.get(0).getSkuParent());
        assertNotNull(summaries.get(0).getLastPublishTask());
        assertEquals("product-delete", summaries.get(0).getLastPublishTask().get("taskType"));
        assertEquals("删除中", summaries.get(0).getLastPublishTask().get("statusLabel"));
        assertEquals("PARTNER-001", summaries.get(0).getLastPublishTask().get("partnerSku"));
        assertNull(summaries.get(0).getHistoryMetaReady());
        verify(productManagementMapper, never()).selectProductMasterIdByStoreCode(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("ZTEST001")
        );
        verify(productManagementMapper, never()).promoteVisibleProductKeyContentHistory(
                anyLong(),
                any(LocalDateTime.class),
                anyLong()
        );
        verify(productManagementMapper, never()).selectProductMasterIdByStorePartnerSku(anyLong(), eq("PARTNER-001"));
        verify(productManagementMapper, never()).selectRecentProductPublishTasks(anyLong());
    }

    @Test
    void listSummariesShouldShowRebuildDeleteTaskAsRebuildInProgress() {
        ProductListProjectionRecord record = new ProductListProjectionRecord();
        record.setSkuParent("ZTEST001");
        record.setCurrentZCode("ZTEST001");
        record.setPartnerSku("PARTNER-001");
        record.setTitle("Amber Burner");
        record.setDetailBaselineStatus("ready");
        record.setSyncStatus("synced");
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64002L);
        task.setProductMasterId(52001L);
        task.setTaskType("product-delete");
        task.setStatus("queued");
        task.setPartnerSku("PARTNER-001");
        task.setPskuCode("NOON-001");
        task.setSubmittedAt(LocalDateTime.of(2026, 7, 4, 15, 30));
        task.setRequestJson("{\"action\":\"product-delete\",\"rebuildAction\":\"product-rebuild\"}");
        when(productManagementMapper.selectProductListProjection(10002L, "STR245027-NAE"))
                .thenReturn(List.of(record));
        when(productManagementMapper.selectLogicalStoreIdBySiteStoreCode("STR245027-NAE"))
                .thenReturn(50003L);
        when(productManagementMapper.selectLatestProductPublishTasksByStorePartnerSkus(
                eq(50003L),
                eq(List.of("PARTNER-001"))
        ))
                .thenReturn(List.of(task));

        List<ProductListSummaryView> summaries = service.loadProductListSummaries(
                10002L,
                "STR245027-NAE",
                new ArrayList<>()
        );

        assertEquals(1, summaries.size());
        assertNotNull(summaries.get(0).getLastPublishTask());
        assertEquals("product-rebuild", summaries.get(0).getLastPublishTask().get("taskType"));
        assertEquals("重建中", summaries.get(0).getLastPublishTask().get("statusLabel"));
        assertEquals("商品重建已排队，系统会先删除旧 PSKU。", summaries.get(0).getLastPublishTask().get("resultText"));
    }

    @Test
    void listSummariesShouldIncludeVisibleRebuildTaskWhenSourceMasterWasDeleted() throws Exception {
        ProductMasterSnapshotView snapshot = historySnapshot("PIECE title", "عنوان", "Note");
        snapshot.getIdentity().put("skuParent", "Z85F0FCAE5F7CF01AEC82Z");
        snapshot.getIdentity().put("partnerSku", "PIECE001");
        snapshot.getIdentity().put("pskuCode", "a65fefec83a3ec3c1440483579d40c1a");
        snapshot.getIdentity().put("brand", "xingyao");
        snapshot.getContent().put("titleEn", "PIECE title");
        snapshot.getContent().put("titleCn", "PIECE 中文");
        snapshot.getTaxonomy().put("productFulltype", "Home > Test");

        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64084L);
        task.setProductMasterId(53263L);
        task.setTaskType("product-delete");
        task.setStatus("synced");
        task.setSkuParent("Z85F0FCAE5F7CF01AEC82Z");
        task.setPartnerSku("PIECE001");
        task.setPskuCode("a65fefec83a3ec3c1440483579d40c1a");
        task.setCurrentSiteCode("AE");
        task.setFinishedAt(LocalDateTime.of(2026, 7, 4, 18, 6, 34));
        task.setBaselineJson(new ObjectMapper().writeValueAsString(snapshot));
        task.setDraftJson(task.getBaselineJson());
        task.setRequestJson("{\"action\":\"product-delete\",\"rebuildAction\":\"product-rebuild\"}");
        task.setResultJson("{\"status\":\"synced\"}");

        when(productManagementMapper.selectProductListProjection(307L, "STR245027-NSA"))
                .thenReturn(List.of());
        when(productManagementMapper.selectVisibleProductRebuildTasksByStore(307L, "STR245027-NSA", 50))
                .thenReturn(List.of(task));

        List<ProductListSummaryView> summaries = service.loadProductListSummaries(
                307L,
                "STR245027-NSA",
                new ArrayList<>()
        );

        assertEquals(1, summaries.size());
        ProductListSummaryView summary = summaries.get(0);
        assertTrue(summary.isReady());
        assertEquals("rebuild-task", summary.getSource());
        assertEquals("STR245027-NSA", summary.getStoreCode());
        assertEquals("Z85F0FCAE5F7CF01AEC82Z", summary.getSkuParent());
        assertEquals("PIECE001", summary.getPartnerSku());
        assertEquals("SELF_BUILT", summary.getProductSourceType());
        assertEquals("PIECE 中文", summary.getTitleCn());
        assertNotNull(summary.getLastPublishTask());
        assertEquals("product-rebuild", summary.getLastPublishTask().get("taskType"));
        assertEquals("重建中", summary.getLastPublishTask().get("statusLabel"));
        assertEquals("旧 PSKU 删除已确认，系统正在提交重建上架。", summary.getLastPublishTask().get("resultText"));
    }

    @Test
    void historyShouldFallbackToVisibleRebuildTaskWhenSourceMasterWasDeleted() throws Exception {
        ProductMasterSnapshotView snapshot = historySnapshot("PIECE title", "عنوان", "Note");
        snapshot.getIdentity().put("skuParent", "Z85F0FCAE5F7CF01AEC82Z");
        snapshot.getIdentity().put("partnerSku", "PIECE001");
        snapshot.getIdentity().put("pskuCode", "a65fefec83a3ec3c1440483579d40c1a");
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64084L);
        task.setProductMasterId(53263L);
        task.setTaskType("product-delete");
        task.setStatus("synced");
        task.setSkuParent("Z85F0FCAE5F7CF01AEC82Z");
        task.setPartnerSku("PIECE001");
        task.setPskuCode("a65fefec83a3ec3c1440483579d40c1a");
        task.setCurrentSiteCode("AE");
        task.setFinishedAt(LocalDateTime.of(2026, 7, 4, 18, 6, 34));
        task.setBaselineJson(new ObjectMapper().writeValueAsString(snapshot));
        task.setDraftJson(task.getBaselineJson());
        task.setRequestJson("{\"action\":\"product-delete\",\"rebuildAction\":\"product-rebuild\"}");
        task.setResultJson("{\"status\":\"synced\"}");

        when(productManagementMapper.selectLogicalStoreIdByOwnerStoreCode(307L, "STR245027-NSA"))
                .thenReturn(50001L);
        when(productManagementMapper.selectProductMasterIdByStorePartnerSku(50001L, "PIECE001"))
                .thenReturn(null);
        when(productManagementMapper.selectProductMasterIdByStoreCode(307L, "STR245027-NSA", "Z85F0FCAE5F7CF01AEC82Z"))
                .thenReturn(null);
        when(productManagementMapper.selectLatestVisibleProductRebuildTaskByStoreIdentity(
                307L,
                "STR245027-NSA",
                "PIECE001",
                "Z85F0FCAE5F7CF01AEC82Z"
        )).thenReturn(task);

        ProductHistoryView view = service.loadProductHistoryView(
                307L,
                "STR245027-NSA",
                "PIECE001",
                "Z85F0FCAE5F7CF01AEC82Z",
                new ArrayList<>()
        );

        assertTrue(view.isReady());
        assertEquals("rebuild-task-history", view.getSource());
        assertEquals("PIECE001", view.getListSummary().getPartnerSku());
        assertEquals("重建中", view.getListSummary().getLastPublishTask().get("statusLabel"));
        assertEquals(1, view.getHistoryItems().size());
        Map<String, Object> item = view.getHistoryItems().get(0);
        assertEquals("product-rebuild", item.get("actionType"));
        assertEquals("重建中", item.get("statusLabel"));
        assertEquals("旧 PSKU 删除已确认，系统正在提交重建上架。", item.get("message"));
    }

    @Test
    void historyShouldIncludeRebuildTaskEvenWhenSnapshotsHaveNoFieldChanges() throws Exception {
        ProductListProjectionRecord record = new ProductListProjectionRecord();
        record.setSkuParent("ZCURRENT001");
        record.setCurrentZCode("ZCURRENT001");
        record.setPartnerSku("PARTNER-PSKU-001");
        record.setTitle("PSKU history product");
        record.setSyncStatus("synced");
        ProductMasterSnapshotView snapshot = historySnapshot("Same title", "عنوان", "Same note");
        String snapshotJson = new ObjectMapper().writeValueAsString(snapshot);
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64002L);
        task.setProductMasterId(52001L);
        task.setTaskType("product-delete");
        task.setStatus("queued");
        task.setPartnerSku("PARTNER-PSKU-001");
        task.setPskuCode("NOON-001");
        task.setSubmittedAt(LocalDateTime.of(2026, 7, 4, 15, 31));
        task.setBaselineJson(snapshotJson);
        task.setDraftJson(snapshotJson);
        task.setRequestJson("{\"action\":\"product-delete\",\"rebuildAction\":\"product-rebuild\"}");
        when(productManagementMapper.selectLogicalStoreIdByOwnerStoreCode(10002L, "STR245027-NAE"))
                .thenReturn(50001L);
        when(productManagementMapper.selectProductListProjectionByStorePartnerSku(
                50001L,
                "STR245027-NAE",
                "PARTNER-PSKU-001"
        )).thenReturn(record);
        when(productManagementMapper.selectProductMasterIdByStorePartnerSku(50001L, "PARTNER-PSKU-001"))
                .thenReturn(52001L);
        when(productManagementMapper.selectRecentProductPublishTasks(52001L)).thenReturn(List.of(task));
        when(productManagementMapper.selectRecentProductActionLogs(52001L)).thenReturn(List.of());
        when(productManagementMapper.selectPendingProductKeyContentHistories(52001L)).thenReturn(List.of());
        when(productManagementMapper.selectVisibleProductKeyContentHistories(52001L)).thenReturn(List.of());
        when(productManagementMapper.countVisibleProductKeyContentHistories(52001L)).thenReturn(0);
        when(productManagementMapper.countPendingProductKeyContentHistories(52001L)).thenReturn(0);

        ProductHistoryView view = service.loadProductHistoryView(
                10002L,
                "STR245027-NAE",
                "PARTNER-PSKU-001",
                null,
                new ArrayList<>()
        );

        assertTrue(view.isReady());
        assertEquals(1, view.getHistoryItems().size());
        Map<String, Object> item = view.getHistoryItems().get(0);
        assertEquals("modification", item.get("historyKind"));
        assertEquals("publish_task", item.get("source"));
        assertEquals("product-rebuild", item.get("actionType"));
        assertEquals("queued", item.get("resultStatus"));
        assertEquals("重建中", item.get("statusLabel"));
        assertEquals("商品重建已排队，系统会先删除旧 PSKU。", item.get("message"));
        assertFalse(item.containsKey("changes"));
    }

    @Test
    void shouldPreserveExistingEditableOfferProjectionWhenIncomingOfferOmitsFields() throws Exception {
        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("storeCode", "STR245027-NAE");
        incoming.put("priceMin", "9.13");

        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("price", "48.00");
        existing.put("salePrice", "39.90");
        existing.put("saleStart", "2026-05-19 00:00:00");
        existing.put("saleEnd", "2036-05-19 23:59:59");
        existing.put("priceMin", "11.00");
        existing.put("priceMax", "55.00");
        existing.put("idWarranty", 5);

        Method method = ProductProjectionPersistenceService.class.getDeclaredMethod(
                "preserveMissingEditableOfferFields",
                Map.class,
                Map.class
        );
        method.setAccessible(true);
        method.invoke(service, incoming, existing);

        assertEquals("48.00", incoming.get("price"));
        assertEquals("39.90", incoming.get("salePrice"));
        assertEquals("2026-05-19 00:00:00", incoming.get("saleStart"));
        assertEquals("2036-05-19 23:59:59", incoming.get("saleEnd"));
        assertEquals("9.13", incoming.get("priceMin"));
        assertEquals("55.00", incoming.get("priceMax"));
        assertEquals(5, incoming.get("idWarranty"));
    }

    @Test
    void shouldPreserveExistingEditableOfferProjectionWhenIncomingOfferReadsEmptyFields() throws Exception {
        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("storeCode", "STR245027-NAE");
        incoming.put("price", "");
        incoming.put("salePrice", null);
        incoming.put("priceMin", "9.13");
        incoming.put("priceMax", "");

        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("price", "48.00");
        existing.put("salePrice", "39.90");
        existing.put("saleStart", "2026-05-19 00:00:00");
        existing.put("saleEnd", "2036-05-19 23:59:59");
        existing.put("priceMin", "11.00");
        existing.put("priceMax", "55.00");
        existing.put("idWarranty", 5);

        Method method = ProductProjectionPersistenceService.class.getDeclaredMethod(
                "preserveMissingEditableOfferFields",
                Map.class,
                Map.class
        );
        method.setAccessible(true);
        method.invoke(service, incoming, existing);

        assertEquals("48.00", incoming.get("price"));
        assertEquals("39.90", incoming.get("salePrice"));
        assertEquals("2026-05-19 00:00:00", incoming.get("saleStart"));
        assertEquals("2036-05-19 23:59:59", incoming.get("saleEnd"));
        assertEquals("9.13", incoming.get("priceMin"));
        assertEquals("55.00", incoming.get("priceMax"));
        assertEquals(5, incoming.get("idWarranty"));
    }

    @Test
    void initializationProjectionReusesPartnerSkuVariantWhenSkuParentChanges() {
        when(productManagementMapper.selectLogicalStoreId(307L, "PRJ69486")).thenReturn(50003L);
        when(productManagementMapper.selectLogicalStoreIdBySiteStoreCode("STR69486-NSA")).thenReturn(50003L);
        when(productManagementMapper.selectLogicalStoreSiteIdInLogicalStore(50003L, "STR69486-NSA")).thenReturn(51003L);
        when(productManagementMapper.selectProductMasterIdByStorePartnerSku(50003L, "SGGRB113"))
                .thenReturn(null, 52001L, 52001L, 52001L);
        when(productManagementMapper.nextProductMasterId()).thenReturn(52001L);
        when(productManagementMapper.selectProductVariantIdByStorePartnerSku(50003L, "SGGRB113"))
                .thenReturn(null, 53001L, 53001L, 53001L);
        when(productManagementMapper.nextProductVariantId()).thenReturn(53001L, 53002L);
        when(productManagementMapper.selectProductSiteOfferIdByStorePartnerSkuSite(50003L, "SGGRB113", "SA"))
                .thenReturn(null, 54001L);
        when(productManagementMapper.nextProductSiteOfferId()).thenReturn(54001L);

        service.persistInitializationProjection(
                307L,
                "PRJ69486",
                "SGGR",
                "STR69486-NSA",
                List.of(new ProductProjectionPersistenceService.SiteSeed("STR69486-NSA", "SA", "ACTIVE", true)),
                List.of(
                        productSeed("ZOLDPSKU001", "SGGRB113", "PSO-OLD"),
                        productSeed("ZNEWPSKU001", "SGGRB113", "PSO-NEW")
                ),
                new ArrayList<>()
        );

        verify(productManagementMapper, times(1)).nextProductMasterId();
        verify(productManagementMapper, times(1)).nextProductVariantId();
        verify(productManagementMapper, times(2)).upsertProductVariant(
                53001L,
                50003L,
                52001L,
                "SGGRB113-CHILD",
                "SGGRB113",
                null,
                null,
                null,
                307L
        );
    }

    @Test
    void shouldCaptureArabicContentAndOfferNoteInModificationChanges() throws Exception {
        ProductMasterSnapshotView baseline = historySnapshot("Old title", "عنوان قديم", "Old note");
        ProductMasterSnapshotView draft = historySnapshot("Old title", "عنوان جديد", "New note");
        draft.getContent().put("descriptionAr", "وصف جديد");
        draft.getContent().put("highlightsAr", List.of("ميزة جديدة"));

        List<Map<String, Object>> changes = invokeBuildProductModificationChanges(
                baseline,
                draft,
                "STR245027-NAE"
        );

        assertTrue(changes.stream().anyMatch((change) -> "title_ar".equals(change.get("field"))));
        assertTrue(changes.stream().anyMatch((change) -> "long_description_ar".equals(change.get("field"))));
        assertTrue(changes.stream().anyMatch((change) -> "feature_bullet_ar".equals(change.get("field"))));
        assertTrue(changes.stream().anyMatch((change) -> "offerNote".equals(change.get("field"))));
    }

    private static ProductProjectionPersistenceService.ProductMasterSeed productSeed(
            String skuParent,
            String partnerSku,
            String pskuCode
    ) {
        ProductProjectionPersistenceService.ProductMasterSeed seed =
                new ProductProjectionPersistenceService.ProductMasterSeed();
        seed.setSkuParent(skuParent);
        seed.setPartnerSku(partnerSku);
        seed.setChildSku(partnerSku + "-CHILD");
        seed.setTitleCache(partnerSku + " title");
        seed.addSiteOffer(siteOffer("STR69486-NSA", pskuCode));
        return seed;
    }

    private static ProductProjectionPersistenceService.SiteOfferSeed siteOffer(String storeCode, String pskuCode) {
        ProductProjectionPersistenceService.SiteOfferSeed offer = new ProductProjectionPersistenceService.SiteOfferSeed();
        offer.setStoreCode(storeCode);
        offer.setPskuCode(pskuCode);
        offer.setOfferCode(pskuCode + "-OFFER");
        return offer;
    }

    @Test
    void shouldIgnoreEquivalentOfferDateFormatsInModificationChanges() throws Exception {
        ProductMasterSnapshotView baseline = historySnapshot("Same title", "عنوان", "Same note");
        ProductMasterSnapshotView draft = historySnapshot("Same title", "عنوان", "Same note");
        baseline.getSiteOffers().get(0).put("saleStart", "2025-10-20T00:00:00+00:00");
        baseline.getSiteOffers().get(0).put("saleEnd", "2025-11-30T23:59:59+00:00");
        draft.getSiteOffers().get(0).put("saleStart", "2025-10-20");
        draft.getSiteOffers().get(0).put("saleEnd", "2025-11-30");

        List<Map<String, Object>> changes = invokeBuildProductModificationChanges(
                baseline,
                draft,
                "STR245027-NAE"
        );

        assertFalse(changes.stream().anyMatch((change) -> "saleStart".equals(change.get("field"))));
        assertFalse(changes.stream().anyMatch((change) -> "saleEnd".equals(change.get("field"))));
    }

    @Test
    void shouldParseNoonOffsetDateTimeForProjectionPersistence() throws Exception {
        assertEquals(
                LocalDateTime.of(2026, 5, 19, 0, 0),
                invokeParseDateTime("2026-05-19T00:00:00+00:00")
        );
        assertEquals(
                LocalDateTime.of(2036, 5, 19, 23, 59, 59),
                invokeParseDateTime("2036-05-19T23:59:59+00:00")
        );
    }

    @Test
    void shouldRestoreEditableOfferPricesFromRecentBaselineWhenCurrentProjectionLostThem() throws Exception {
        ProductMasterSnapshotView historical = historySnapshot("Title", "عنوان", "Note");
        historical.getStoreContext().put("storeCode", "STR245027-NAE");
        Map<String, Object> historicalOffer = historical.getSiteOffers().get(0);
        historicalOffer.put("salePrice", "39.90");
        historicalOffer.put("priceMin", "11");
        historicalOffer.put("priceMax", "55");
        ProductMasterSnapshotRecord historicalRecord = new ProductMasterSnapshotRecord();
        historicalRecord.setId(56489L);
        historicalRecord.setProductMasterId(52006L);
        historicalRecord.setSnapshotType("baseline");
        historicalRecord.setFetchedAt(LocalDateTime.of(2026, 5, 18, 19, 1, 12));
        historicalRecord.setSnapshotJson(new ObjectMapper().writeValueAsString(historical));
        when(productManagementMapper.selectRecentProductMasterSnapshots(52006L, "baseline", 50))
                .thenReturn(List.of(historicalRecord));

        ProductMasterSnapshotView current = new ProductMasterSnapshotView();
        current.getStoreContext().put("storeCode", "STR245027-NAE");
        current.setPricing(new LinkedHashMap<>());
        current.setSiteOffers(new ArrayList<>(List.of(new LinkedHashMap<>(Map.of(
                "storeCode", "STR245027-NAE",
                "site", "AE"
        )))));

        invokeHydrateMissingEditableOfferFieldsFromHistory(52006L, "STR245027-NAE", current);

        Map<String, Object> restoredOffer = current.getSiteOffers().get(0);
        assertEquals("89.50", restoredOffer.get("price"));
        assertEquals("39.90", restoredOffer.get("salePrice"));
        assertEquals("11", restoredOffer.get("priceMin"));
        assertEquals("55", restoredOffer.get("priceMax"));
        assertEquals("89.50", current.getPricing().get("price"));
        assertEquals("39.90", current.getPricing().get("salePrice"));
    }

    @Test
    void shouldPreserveEditableOfferPricesBeforePersistingIncompleteFetch() throws Exception {
        ProductMasterSnapshotView historical = historySnapshot("Title", "عنوان", "Note");
        historical.getSiteOffers().get(0).put("salePrice", "39.90");
        historical.getSiteOffers().get(0).put("priceMin", "11");
        historical.getSiteOffers().get(0).put("priceMax", "55");
        ProductMasterSnapshotRecord historicalRecord = new ProductMasterSnapshotRecord();
        historicalRecord.setId(56489L);
        historicalRecord.setProductMasterId(52006L);
        historicalRecord.setSnapshotType("baseline");
        historicalRecord.setSnapshotJson(new ObjectMapper().writeValueAsString(historical));
        when(productManagementMapper.selectRecentProductMasterSnapshots(52006L, "baseline", 50))
                .thenReturn(List.of(historicalRecord));
        when(productManagementMapper.selectProductSiteOfferProjectionRows(52006L))
                .thenReturn(List.of(new LinkedHashMap<>(Map.of(
                        "storeCode", "STR245027-NAE",
                        "pskuCode", "afa96a913120ef720151b23c4e37a84a"
                ))));

        ProductMasterSnapshotView current = new ProductMasterSnapshotView();
        current.getStoreContext().put("storeCode", "STR245027-NAE");
        current.setPricing(new LinkedHashMap<>());
        current.setStock(new LinkedHashMap<>());
        current.setSiteOffers(new ArrayList<>(List.of(new LinkedHashMap<>(Map.of(
                "storeCode", "STR245027-NAE",
                "site", "AE"
        )))));

        invokePreserveMissingSnapshotOfferProjectionFields(52006L, current);

        Map<String, Object> preservedOffer = current.getSiteOffers().get(0);
        assertEquals("89.50", preservedOffer.get("price"));
        assertEquals("39.90", preservedOffer.get("salePrice"));
        assertEquals("11", preservedOffer.get("priceMin"));
        assertEquals("55", preservedOffer.get("priceMax"));
        assertEquals("89.50", current.getPricing().get("price"));
        assertEquals("39.90", current.getPricing().get("salePrice"));
    }

    @Test
    void shouldHydrateListingStartedMetadataFromProjectionRowsForRebuildInheritance() throws Exception {
        when(productManagementMapper.selectProductSiteOfferProjectionRows(52006L))
                .thenReturn(List.of(new LinkedHashMap<>(Map.of(
                        "storeCode", "STR245027-NSA",
                        "site", "SA",
                        "pskuCode", "a65fefec83a3ec3c1440483579d40c1a",
                        "offerCode", "Z85F0FCAE5F7CF01AEC82Z",
                        "listingStartedAt", "2026-07-03 10:24:03",
                        "listingStartedSource", "product_listing"
                ))));

        ProductMasterSnapshotView current = new ProductMasterSnapshotView();
        current.getStoreContext().put("storeCode", "STR245027-NSA");
        current.setPricing(new LinkedHashMap<>());
        current.setStock(new LinkedHashMap<>());
        current.setSiteOffers(new ArrayList<>(List.of(new LinkedHashMap<>(Map.of(
                "storeCode", "STR245027-NSA",
                "site", "SA"
        )))));

        invokeHydrateSnapshotFromProjection(52006L, "STR245027-NSA", current);

        Map<String, Object> hydratedOffer = current.getSiteOffers().get(0);
        assertEquals("2026-07-03 10:24:03", hydratedOffer.get("listingStartedAt"));
        assertEquals("product_listing", hydratedOffer.get("listingStartedSource"));
    }

    @Test
    void shouldRejectProjectionSiteSeedWhenStoreCodeBelongsToAnotherLogicalStore() {
        ProductProjectionPersistenceService.ProductMasterSeed productSeed =
                new ProductProjectionPersistenceService.ProductMasterSeed();
        productSeed.setSkuParent("ZCROSS001");

        ProductProjectionPersistenceService.SiteSeed siteSeed =
                new ProductProjectionPersistenceService.SiteSeed("STR108065-NAE", "AE", "ACTIVE", true);

        when(productManagementMapper.selectLogicalStoreId(307L, "PRJ69486"))
                .thenReturn(50003L);
        when(productManagementMapper.selectLogicalStoreIdBySiteStoreCode("STR108065-NAE"))
                .thenReturn(50005L);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.persistInitializationProjection(
                        307L,
                        "PRJ69486",
                        "songguoguo",
                        "STR108065-NAE",
                        List.of(siteSeed),
                        List.of(productSeed),
                        new ArrayList<>()
                )
        );

        assertTrue(exception.getMessage().contains("STR108065-NAE"));
        assertTrue(exception.getMessage().contains("其他逻辑店铺"));
        verify(productManagementMapper, never()).upsertLogicalStoreSite(
                eq(51004L),
                eq(50003L),
                eq("STR108065-NAE"),
                eq("AE"),
                eq(true),
                eq(true),
                eq("ACTIVE"),
                eq(307L)
        );
    }

    @Test
    void shouldNormalizeNoonCoverImageUrlWhenPersistingProductMasterSeed() {
        ProductProjectionPersistenceService.ProductMasterSeed productSeed =
                new ProductProjectionPersistenceService.ProductMasterSeed();
        productSeed.setSkuParent("ZIMAGE001");
        productSeed.setBrandCache("Papersays");
        productSeed.setTitleCache("Noon bad image");
        productSeed.setCoverImageUrl("https://f.nooncdn.com/pzsku/Z92550AC9ECB3A39E5B7AZ/45/1768272072/75cf5a38-3af3-4055-ae03-df18f1d3912b");
        productSeed.setSyncStatus("synced");

        ProductProjectionPersistenceService.SiteSeed siteSeed =
                new ProductProjectionPersistenceService.SiteSeed("STR108065-NSA", "SA", "ACTIVE", true);

        when(productManagementMapper.selectLogicalStoreId(307L, "PRJ69486"))
                .thenReturn(50003L);
        when(productManagementMapper.selectLogicalStoreIdBySiteStoreCode("STR108065-NSA"))
                .thenReturn(50003L);
        when(productManagementMapper.selectLogicalStoreSiteIdInLogicalStore(50003L, "STR108065-NSA"))
                .thenReturn(51004L);
        when(productManagementMapper.selectProductMasterId(50003L, "ZIMAGE001"))
                .thenReturn(null, 52006L);
        when(productManagementMapper.nextProductMasterId()).thenReturn(52006L);

        service.persistInitializationProjection(
                307L,
                "PRJ69486",
                "canman",
                "STR108065-NSA",
                List.of(siteSeed),
                List.of(productSeed),
                new ArrayList<>()
        );

        verify(productManagementMapper).upsertProductMaster(
                eq(52006L),
                eq(50003L),
                isNull(),
                eq("ZIMAGE001"),
                eq("ZIMAGE001"),
                eq("SELF_BUILT"),
                eq("Papersays"),
                eq("Noon bad image"),
                isNull(),
                isNull(),
                eq("https://f.nooncdn.com/p/pzsku/Z92550AC9ECB3A39E5B7AZ/45/1768272072/75cf5a38-3af3-4055-ae03-df18f1d3912b.jpg"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(0),
                isNull(),
                isNull(),
                eq("synced"),
                isNull(),
                eq(307L)
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> invokeBuildProductModificationChanges(
            ProductMasterSnapshotView before,
            ProductMasterSnapshotView after,
            String currentSiteCode
    ) throws Exception {
        Method method = ProductProjectionPersistenceService.class.getDeclaredMethod(
                "buildProductModificationChanges",
                ProductMasterSnapshotView.class,
                ProductMasterSnapshotView.class,
                String.class
        );
        method.setAccessible(true);
        return (List<Map<String, Object>>) method.invoke(service, before, after, currentSiteCode);
    }

    private void invokeHydrateMissingEditableOfferFieldsFromHistory(
            Long productMasterId,
            String currentSiteCode,
            ProductMasterSnapshotView snapshot
    ) throws Exception {
        Method method = ProductProjectionPersistenceService.class.getDeclaredMethod(
                "hydrateMissingEditableOfferFieldsFromHistory",
                Long.class,
                String.class,
                ProductMasterSnapshotView.class
        );
        method.setAccessible(true);
        method.invoke(service, productMasterId, currentSiteCode, snapshot);
    }

    private void invokePreserveMissingSnapshotOfferProjectionFields(
            Long productMasterId,
            ProductMasterSnapshotView snapshot
    ) throws Exception {
        Method method = ProductProjectionPersistenceService.class.getDeclaredMethod(
                "preserveMissingSnapshotOfferProjectionFields",
                Long.class,
                ProductMasterSnapshotView.class
        );
        method.setAccessible(true);
        method.invoke(service, productMasterId, snapshot);
    }

    private void invokeHydrateSnapshotFromProjection(
            Long productMasterId,
            String referenceStoreCode,
            ProductMasterSnapshotView snapshot
    ) throws Exception {
        Method method = ProductProjectionPersistenceService.class.getDeclaredMethod(
                "hydrateSnapshotFromProjection",
                Long.class,
                String.class,
                ProductMasterSnapshotView.class
        );
        method.setAccessible(true);
        method.invoke(service, productMasterId, referenceStoreCode, snapshot);
    }

    private LocalDateTime invokeParseDateTime(String value) throws Exception {
        Method method = ProductProjectionPersistenceService.class.getDeclaredMethod("parseDateTime", String.class);
        method.setAccessible(true);
        return (LocalDateTime) method.invoke(service, value);
    }

    private ProductMasterSnapshotView historySnapshot(String titleEn, String titleAr, String offerNote) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getStoreContext().put("storeCode", "STR245027-NAE");
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("brand", "milkyway");
        snapshot.setIdentity(identity);

        Map<String, Object> taxonomy = new LinkedHashMap<>();
        taxonomy.put("productFulltype", "home_decor-lighting-table_lamps");
        snapshot.setTaxonomy(taxonomy);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("titleEn", titleEn);
        content.put("titleAr", titleAr);
        content.put("descriptionEn", "Same description");
        content.put("descriptionAr", "وصف قديم");
        content.put("highlightsEn", List.of("Same feature"));
        content.put("highlightsAr", List.of("ميزة قديمة"));
        content.put("images", List.of("https://img.example.com/1.jpg"));
        snapshot.setContent(content);

        Map<String, Object> siteOffer = new LinkedHashMap<>();
        siteOffer.put("storeCode", "STR245027-NAE");
        siteOffer.put("site", "AE");
        siteOffer.put("price", "89.50");
        siteOffer.put("offerNote", offerNote);
        snapshot.setSiteOffers(new ArrayList<>(List.of(siteOffer)));
        return snapshot;
    }
}
