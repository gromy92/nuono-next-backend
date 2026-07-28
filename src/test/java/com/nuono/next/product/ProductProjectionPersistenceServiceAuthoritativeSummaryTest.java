package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.CoreTableStatusMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.system.BootstrapProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ProductProjectionPersistenceServiceAuthoritativeSummaryTest {

    @Mock
    private ProductManagementMapper productManagementMapper;

    @Mock
    private CoreTableStatusMapper coreTableStatusMapper;

    private ProductProjectionPersistenceService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ProductProjectionPersistenceService(
                productManagementMapper,
                coreTableStatusMapper,
                new BootstrapProperties(),
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
        record.setOperationStageCode("STABLE");
        record.setOperationStageUpdatedAt("2026-07-06 11:30:00");
        record.setOperationStageUpdatedBy(10003L);
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

        when(productManagementMapper.selectProductListProjectionBySkuParent(
                10002L,
                "STR245027-NAE",
                "ZTEST001"
        )).thenReturn(record);

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
        assertEquals("STABLE", summary.getOperationStageCode());
        assertEquals("2026-07-06 11:30:00", summary.getOperationStageUpdatedAt());
        assertEquals(10003L, summary.getOperationStageUpdatedBy());
        assertEquals("星耀琥珀香薰炉", summary.getTitleCn());
        assertEquals(List.of("AE"), summary.getSiteLabels());
        assertEquals(List.of("LIVE"), summary.getLiveStatuses());
    }
}
