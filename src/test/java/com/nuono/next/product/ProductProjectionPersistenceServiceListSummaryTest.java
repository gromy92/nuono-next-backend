package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
        record.setPartnerSku("PARTNER-001");
        record.setPskuCode("PSKU-001");
        record.setOfferCode("OFFER-001");
        record.setTitle("Amber Burner");
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
        record.setSyncStatus("draft");
        record.setLastSyncedAt("2026-04-27 12:30:00");
        record.setDetailBaselineStatus("ready");
        record.setDetailBaselineSyncedAt("2026-04-27 12:31:00");
        record.setVariantCount(2);
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
        assertEquals("draft", summary.getSyncStatus());
        assertEquals("ready", summary.getDetailBaselineStatus());
        assertEquals("详情基线已准备。", summary.getDetailBaselineMessage());
        assertEquals("2026-04-27 12:31:00", summary.getDetailBaselineSyncedAt());
        assertEquals("139.00", summary.getReferencePrice());
        assertEquals(Boolean.TRUE, summary.getIsActive());
        assertEquals("LIVE", summary.getLiveStatus());
        assertEquals(List.of("AE"), summary.getSiteLabels());
        assertEquals(List.of("LIVE"), summary.getLiveStatuses());
    }

    @Test
    void shouldExposeLifecycleStateInProductListSummaryWhenCalculated() {
        ProductListProjectionRecord record = new ProductListProjectionRecord();
        record.setSkuParent("ZTEST001");
        record.setPartnerSku("PARTNER-001");
        record.setPskuCode("PSKU-001");
        record.setTitle("Amber Burner");
        record.setSyncStatus("synced");

        when(productManagementMapper.selectProductListProjectionBySkuParent(10002L, "STR245027-NAE", "ZTEST001"))
                .thenReturn(record);
        when(productManagementMapper.selectProductLifecycleSummary(10002L, "STR245027-NAE", "PARTNER-001", "PSKU-001"))
                .thenReturn(new LinkedHashMap<>(Map.of(
                        "code", "stable",
                        "label", "稳定",
                        "ruleVersion", "DEFAULT_V1",
                        "analysisDate", "2026-05-19",
                        "listingDate", "2026-04-20",
                        "listingDateSource", "sales",
                        "qualityState", "ready",
                        "explanation", "销量/PV 窗口充足，订正后趋势未达到增长、衰退或长尾阈值。",
                        "evidenceJson", "{\"reason\":\"stable_default_v1\"}"
                )));

        ProductListSummaryView summary = service.loadProductListSummary(
                10002L,
                "STR245027-NAE",
                "ZTEST001",
                new ArrayList<>()
        );

        assertEquals("stable", summary.getLifecycleState().get("code"));
        assertEquals("稳定", summary.getLifecycleState().get("label"));
        assertEquals("2026-05-19", summary.getLifecycleState().get("analysisDate"));
        assertEquals("sales", summary.getLifecycleState().get("listingDateSource"));
        assertTrue(String.valueOf(summary.getLifecycleState().get("evidenceJson")).contains("stable_default_v1"));
    }

    @Test
    void shouldFallbackLifecycleLookupBySkuWhenProjectionPartnerSkuComesFromAnotherVariant() {
        ProductListProjectionRecord record = new ProductListProjectionRecord();
        record.setSkuParent("ZTEST001");
        record.setPartnerSku("PARTNER-ALT");
        record.setPskuCode("PSKU-ALT");
        record.setOfferCode("OFFER-001");
        record.setTitle("Amber Burner");
        record.setSyncStatus("synced");

        when(productManagementMapper.selectProductListProjectionBySkuParent(10002L, "STR245027-NAE", "ZTEST001"))
                .thenReturn(record);
        when(productManagementMapper.selectProductLifecycleSummary(10002L, "STR245027-NAE", "PARTNER-ALT", "OFFER-001"))
                .thenReturn(null);
        when(productManagementMapper.selectProductLifecycleSummaryBySku(10002L, "STR245027-NAE", "OFFER-001"))
                .thenReturn(new LinkedHashMap<>(Map.of(
                        "code", "stable",
                        "label", "稳定",
                        "ruleVersion", "DEFAULT_V1",
                        "qualityState", "ready",
                        "evidenceJson", "{\"reason\":\"stable_default_v1\"}"
                )));

        ProductListSummaryView summary = service.loadProductListSummary(
                10002L,
                "STR245027-NAE",
                "ZTEST001",
                new ArrayList<>()
        );

        assertEquals("stable", summary.getLifecycleState().get("code"));
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
    void shouldKeepListReadableWhenNoopDraftCleanupDeadlocks() {
        ProductListProjectionRecord record = new ProductListProjectionRecord();
        record.setSkuParent("ZTEST001");
        record.setTitle("Projection title");
        record.setSyncStatus("synced");

        doThrow(new RuntimeException("Deadlock found when trying to get lock; try restarting transaction"))
                .when(productManagementMapper)
                .deleteNoopProductMasterDraftsByStoreCode(10002L, "STR245027-NAE");
        when(productManagementMapper.selectProductListProjection(10002L, "STR245027-NAE"))
                .thenReturn(List.of(record));

        List<ProductListSummaryView> summaries = service.loadProductListSummaries(
                10002L,
                "STR245027-NAE",
                new ArrayList<>()
        );

        assertEquals(1, summaries.size());
        assertEquals("ZTEST001", summaries.get(0).getSkuParent());
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
    void shouldExposeCancelledPublishTaskInListStatusSummary() throws Exception {
        assertEquals("已取消", invokePublishTaskListStatusLabel("cancelled"));
    }

    @Test
    void shouldExplainPendingManualCheckWithActualChangedDomain() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ProductMasterSnapshotView baseline = historySnapshot("Same title", "عنوان", "Same note");
        baseline.setVariants(new ArrayList<>(List.of(variant("CHILD-SKU-001", "M"))));
        ProductMasterSnapshotView draft = historySnapshot("Same title", "عنوان", "Same note");
        draft.setVariants(new ArrayList<>(List.of(variant("CHILD-SKU-001", "L"))));

        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setStatus("pending_manual_check");
        task.setChangedDomainsJson(mapper.writeValueAsString(List.of("unknown")));
        task.setBaselineJson(mapper.writeValueAsString(baseline));
        task.setDraftJson(mapper.writeValueAsString(draft));
        task.setCurrentSiteCode("STR245027-NAE");

        String message = invokePublishTaskHistoryMessage(task);

        assertTrue(message.contains("【尺码】"));
        assertFalse(message.equals("发布结果需要人工核对。"));
    }

    @Test
    void shouldNotShowSuccessfulPublishActionWithoutFieldChangesAsModificationHistory() throws Exception {
        ProductActionLogRecord record = new ProductActionLogRecord();
        record.setActionType("publish-current");
        record.setResultStatus("synced");
        record.setOccurredAt(LocalDateTime.of(2026, 5, 14, 14, 36, 23));
        record.setSummaryJson("{\"message\":\"发布已完成。\"}");
        when(productManagementMapper.selectRecentProductActionLogs(52016L)).thenReturn(List.of(record));

        List<Map<String, Object>> history = invokeLoadActionHistoryItems(52016L, new ArrayList<>());

        assertEquals(List.of(), history);
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

    private LocalDateTime invokeParseDateTime(String value) throws Exception {
        Method method = ProductProjectionPersistenceService.class.getDeclaredMethod("parseDateTime", String.class);
        method.setAccessible(true);
        return (LocalDateTime) method.invoke(service, value);
    }

    private String invokePublishTaskListStatusLabel(String value) throws Exception {
        Method method = ProductProjectionPersistenceService.class.getDeclaredMethod(
                "publishTaskListStatusLabel",
                String.class
        );
        method.setAccessible(true);
        return (String) method.invoke(service, value);
    }

    private String invokePublishTaskHistoryMessage(ProductPublishTaskRecord task) throws Exception {
        Method method = ProductProjectionPersistenceService.class.getDeclaredMethod(
                "publishTaskHistoryMessage",
                ProductPublishTaskRecord.class
        );
        method.setAccessible(true);
        return (String) method.invoke(service, task);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> invokeLoadActionHistoryItems(Long productMasterId, List<String> warnings) throws Exception {
        Method method = ProductProjectionPersistenceService.class.getDeclaredMethod(
                "loadActionHistoryItems",
                Long.class,
                List.class
        );
        method.setAccessible(true);
        return (List<Map<String, Object>>) method.invoke(service, productMasterId, warnings);
    }

    private Map<String, Object> variant(String childSku, String sizeEn) {
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("childSku", childSku);
        variant.put("sizeEn", sizeEn);
        return variant;
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
