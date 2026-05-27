package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.CoreTableStatusMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.system.BootstrapProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ProductProjectionReaderServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProductManagementMapper productManagementMapper;

    @Mock
    private CoreTableStatusMapper coreTableStatusMapper;

    private ProductProjectionReaderService reader;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(coreTableStatusMapper.findExistingTableNames(eq("nuono_new_dev"), anyList()))
                .thenAnswer(invocation -> new ArrayList<>((List<String>) invocation.getArgument(1)));
        ProductProjectionPersistenceService persistenceService = new ProductProjectionPersistenceService(
                productManagementMapper,
                coreTableStatusMapper,
                new BootstrapProperties(),
                objectMapper,
                new ProductKeyContentHistoryAssembler(),
                null
        );
        reader = new ProductProjectionReaderService(persistenceService);
    }

    @Test
    void listSummaryComesFromProjectionReaderAndKeepsPublishTaskSummary() throws Exception {
        ProductListProjectionRecord record = new ProductListProjectionRecord();
        record.setSkuParent("ZTEST001");
        record.setPartnerSku("PARTNER-001");
        record.setPskuCode("PSKU-001");
        record.setTitle("Projection title");
        record.setBrand("xingyao");
        record.setReferencePrice("48.00");
        record.setDetailBaselineStatus("ready");
        record.setCurrentSiteLiveStatus("LIVE");
        record.setCurrentSiteActiveFlag(1);
        record.setSyncStatus("draft");

        ProductPublishTaskRecord task = publishTask("pending_manual_check");

        when(productManagementMapper.selectProductListProjectionBySkuParent(10002L, "STR245027-NAE", "ZTEST001"))
                .thenReturn(record);
        when(productManagementMapper.selectProductMasterIdByStoreCode(10002L, "STR245027-NAE", "ZTEST001"))
                .thenReturn(52001L);
        when(productManagementMapper.selectRecentProductPublishTasks(52001L))
                .thenReturn(List.of(task));

        ProductListSummaryView summary = reader.loadProductListSummary(
                10002L,
                "STR245027-NAE",
                "ZTEST001",
                new ArrayList<>()
        );

        assertTrue(summary.isReady());
        assertEquals("projection", summary.getSource());
        assertEquals("Projection title", summary.getTitle());
        assertEquals("draft", summary.getSyncStatus());
        assertEquals("待人工核对", summary.getLastPublishTask().get("statusLabel"));
        verify(productManagementMapper).selectProductListProjectionBySkuParent(10002L, "STR245027-NAE", "ZTEST001");
        verify(productManagementMapper).selectRecentProductPublishTasks(52001L);
    }

    @Test
    void detailBaselineUsesProjectionOverlayRatherThanRawSnapshotValuesOnly() throws Exception {
        ProductMasterSnapshotView rawBaseline = new ProductMasterSnapshotView();
        rawBaseline.setReady(true);
        rawBaseline.getStoreContext().put("storeCode", "STR245027-NAE");
        rawBaseline.getIdentity().put("skuParent", "ZTEST001");
        rawBaseline.setSiteOffers(new ArrayList<>(List.of(new LinkedHashMap<>(Map.of(
                "storeCode",
                "STR245027-NAE"
        )))));

        ProductMasterSnapshotRecord baselineRecord = new ProductMasterSnapshotRecord();
        baselineRecord.setSnapshotJson(objectMapper.writeValueAsString(rawBaseline));

        Map<String, Object> projectionOffer = new LinkedHashMap<>();
        projectionOffer.put("storeCode", "STR245027-NAE");
        projectionOffer.put("site", "AE");
        projectionOffer.put("partnerSku", "PARTNER-001");
        projectionOffer.put("pskuCode", "PSKU-001");
        projectionOffer.put("price", new BigDecimal("48.00"));
        projectionOffer.put("salePrice", new BigDecimal("39.90"));
        projectionOffer.put("priceMin", new BigDecimal("9.13"));
        projectionOffer.put("priceMax", new BigDecimal("55.00"));
        projectionOffer.put("fbnStock", 8);
        projectionOffer.put("supermallStock", 0);
        projectionOffer.put("fbpStock", 0);

        when(productManagementMapper.selectProductMasterIdByStoreCode(10002L, "STR245027-NAE", "ZTEST001"))
                .thenReturn(52001L);
        when(productManagementMapper.selectLatestProductMasterSnapshot(52001L, "baseline"))
                .thenReturn(baselineRecord);
        when(productManagementMapper.selectProductSiteOfferProjectionRows(52001L))
                .thenReturn(List.of(projectionOffer));
        when(productManagementMapper.selectRecentProductMasterSnapshots(52001L, "baseline", 50))
                .thenReturn(List.of());

        ProductMasterSnapshotView baseline = reader.loadLatestBaselineSnapshot(
                10002L,
                "STR245027-NAE",
                "ZTEST001",
                new ArrayList<>()
        );

        assertTrue(baseline.isReady());
        assertEquals(new BigDecimal("48.00"), baseline.getSiteOffers().get(0).get("price"));
        assertEquals(new BigDecimal("39.90"), baseline.getPricing().get("salePrice"));
        assertEquals(8, baseline.getStock().get("fbnStock"));
        verify(productManagementMapper).selectLatestProductMasterSnapshot(52001L, "baseline");
        verify(productManagementMapper).selectProductSiteOfferProjectionRows(52001L);
    }

    private ProductPublishTaskRecord publishTask(String status) throws Exception {
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64001L);
        task.setStatus(status);
        task.setCurrentSiteCode("STR245027-NAE");
        task.setPartnerSku("PARTNER-001");
        task.setPskuCode("PSKU-001");
        task.setBaselineJson(objectMapper.writeValueAsString(historySnapshot("Old title")));
        task.setDraftJson(objectMapper.writeValueAsString(historySnapshot("New title")));
        task.setChangedDomainsJson("[\"content\"]");
        task.setSubmittedAt(LocalDateTime.of(2026, 5, 20, 10, 0));
        task.setFinishedAt(LocalDateTime.of(2026, 5, 20, 10, 5));
        return task;
    }

    private ProductMasterSnapshotView historySnapshot(String title) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getContent().put("titleEn", title);
        snapshot.setSiteOffers(new ArrayList<>(List.of(new LinkedHashMap<>(Map.of(
                "storeCode",
                "STR245027-NAE"
        )))));
        return snapshot;
    }
}
