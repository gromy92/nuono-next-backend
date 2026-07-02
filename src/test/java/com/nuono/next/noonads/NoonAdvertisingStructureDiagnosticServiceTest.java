package com.nuono.next.noonads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonAdvertisingStructureDiagnosticServiceTest {
    private final NoonAdvertisingStructureDiagnosticService service = new NoonAdvertisingStructureDiagnosticService();

    @Test
    void classifiesCampaignPlanTypesFromNames() {
        assertEquals(NoonAdvertisingPlanType.EXPLORATION, service.classifyPlanType(campaign("C_AUTO", "Auto discovery test", "SKU-1")));
        assertEquals(NoonAdvertisingPlanType.CORE, service.classifyPlanType(campaign("C_CORE", "Core exact main", "SKU-1")));
        assertEquals(NoonAdvertisingPlanType.UNCLASSIFIED, service.classifyPlanType(campaign("C_LONG", "Longtail phrase category", "SKU-1")));
        assertEquals(NoonAdvertisingPlanType.UNCLASSIFIED, service.classifyPlanType(campaign("C_PRODUCT", "Competitor product targeting", "SKU-1")));
        assertEquals(NoonAdvertisingPlanType.UNCLASSIFIED, service.classifyPlanType(campaign("C_PROMO", "Eid promo clearance", "SKU-1")));
        assertEquals(NoonAdvertisingPlanType.UNCLASSIFIED, service.classifyPlanType(campaign("C_UNKNOWN", "Black spotlight", "SKU-1")));
    }

    @Test
    void flagsExplorationHighZeroOrderProductAsStopLoss() {
        NoonAdvertisingProductRow product = product("SKU-1", 1, 120, "80.00", "0", 0, "0", "70.00", "0.8750");
        NoonAdvertisingCampaignRow campaign = campaign("C_AUTO", "Auto discovery broad", "SKU-1");
        campaign.setSpendAmount(new BigDecimal("80.00"));
        campaign.setOrdersCount(0);
        campaign.setAdRevenue(BigDecimal.ZERO);
        campaign.setRoas(BigDecimal.ZERO);
        campaign.setZeroOrderSpendAmount(new BigDecimal("70.00"));
        campaign.setZeroOrderSpendShare(new BigDecimal("0.8750"));

        NoonAdvertisingStructureDiagnosticResult result = service.diagnose(
                List.of(product),
                List.of(campaign),
                List.of(),
                List.of()
        );

        NoonAdvertisingProductDiagnostic diagnostic = result.getProductDiagnostics().get(0);
        assertEquals("SKU-1", diagnostic.getPartnerSku());
        assertEquals(NoonAdvertisingStructureStatus.RISK, diagnostic.getStructureStatus());
        assertEquals(NoonAdvertisingProductDiagnosisType.STOP_LOSS, diagnostic.getDiagnosisType());
        assertEquals("优先止损", diagnostic.getDiagnosisLabel());
        assertTrue(diagnostic.getPriorityScore() > 0);
        assertEquals(0, diagnostic.getCoreCampaignCount());
        assertEquals(1, diagnostic.getExplorationCampaignCount());
        assertEquals(0, diagnostic.getUnclassifiedCampaignCount());
        assertTrue(diagnostic.getLabels().contains("探索计划零订单消耗偏高"));
        assertTrue(diagnostic.getLabels().contains("搜索排名未接入"));
        assertTrue(diagnostic.getRecommendedActions().contains("探索计划零订单消耗偏高，建议人工确认后收缩范围、降 bid 或加否定。"));
        assertFalse(diagnostic.isRankDataAvailable());

        NoonAdvertisingCampaignDiagnostic campaignDiagnostic = result.getCampaignDiagnostics().get(0);
        assertEquals("C_AUTO", campaignDiagnostic.getCampaignCode());
        assertEquals("SKU-1", campaignDiagnostic.getPartnerSku());
        assertEquals(NoonAdvertisingPlanType.EXPLORATION, campaignDiagnostic.getPlanType());
        assertEquals(NoonAdvertisingPlanTypeConfidence.RULE, campaignDiagnostic.getPlanTypeConfidence());
        assertEquals("探索计划", campaignDiagnostic.getPlanTypeLabel());
        assertTrue(campaignDiagnostic.getLabels().contains("探索消耗偏高"));
    }

    @Test
    void promotesExplorationWinnersToCoreWhenNoCorePlanExists() {
        NoonAdvertisingProductRow product = product("SKU-3", 1, 90, "60.00", "480.00", 6, "8.00", "12.00", "0.2000");
        NoonAdvertisingCampaignRow campaign = campaign("C_AUTO_WIN", "Auto discovery", "SKU-3");
        campaign.setSpendAmount(new BigDecimal("60.00"));
        campaign.setOrdersCount(6);
        campaign.setAdRevenue(new BigDecimal("480.00"));
        campaign.setRoas(new BigDecimal("8.00"));
        campaign.setZeroOrderSpendAmount(new BigDecimal("12.00"));
        campaign.setZeroOrderSpendShare(new BigDecimal("0.2000"));
        NoonAdvertisingQueryRow winning = query("C_AUTO_WIN", "SKU-3", "winner keyword", "10.00", 3, "160.00", "16.00");

        NoonAdvertisingStructureDiagnosticResult result = service.diagnose(
                List.of(product),
                List.of(campaign),
                List.of(),
                List.of(winning)
        );

        NoonAdvertisingProductDiagnostic diagnostic = result.getProductDiagnostics().get(0);
        assertEquals(NoonAdvertisingProductDiagnosisType.PROMOTE_TO_CORE, diagnostic.getDiagnosisType());
        assertEquals("可沉淀核心", diagnostic.getDiagnosisLabel());
        assertEquals(0, diagnostic.getCoreCampaignCount());
        assertEquals(1, diagnostic.getExplorationCampaignCount());
        assertEquals(0, diagnostic.getUnclassifiedCampaignCount());
        assertTrue(diagnostic.getLabels().contains("探索计划有高转化词"));
        assertTrue(diagnostic.getRecommendedActions().contains("把探索计划里的高转化关键词/搜索词沉淀到核心计划。"));
    }

    @Test
    void marksStableCorePlanAsObservable() {
        NoonAdvertisingProductRow product = product("SKU-4", 1, 24, "100.00", "1500.00", 10, "15.00", "10.00", "0.1000");
        NoonAdvertisingCampaignRow campaign = campaign("C_CORE_STABLE", "Core exact main", "SKU-4");
        campaign.setSpendAmount(new BigDecimal("100.00"));
        campaign.setOrdersCount(10);
        campaign.setAdRevenue(new BigDecimal("1500.00"));
        campaign.setRoas(new BigDecimal("15.00"));
        campaign.setZeroOrderSpendAmount(new BigDecimal("10.00"));
        campaign.setZeroOrderSpendShare(new BigDecimal("0.1000"));

        NoonAdvertisingProductDiagnostic diagnostic = service
                .diagnose(List.of(product), List.of(campaign), List.of(), List.of())
                .getProductDiagnostics()
                .get(0);

        assertEquals(NoonAdvertisingProductDiagnosisType.CORE_OBSERVE, diagnostic.getDiagnosisType());
        assertEquals("核心可观察", diagnostic.getDiagnosisLabel());
        assertEquals(1, diagnostic.getCoreCampaignCount());
        assertEquals(0, diagnostic.getExplorationCampaignCount());
        assertTrue(diagnostic.getLabels().contains("核心表现稳定"));
        assertTrue(diagnostic.getRecommendedActions().contains("核心计划表现稳定，建议保护预算和稳定性，避免频繁大幅调整。"));
    }

    @Test
    void keepsUnclearCampaignPurposeAsStructureReview() {
        NoonAdvertisingProductRow product = product("SKU-5", 2, 64, "90.00", "450.00", 4, "5.00", "24.00", "0.2667");
        NoonAdvertisingCampaignRow first = campaign("C_BLACK", "Black spotlight", "SKU-5");
        first.setSpendAmount(new BigDecimal("50.00"));
        first.setOrdersCount(2);
        first.setAdRevenue(new BigDecimal("250.00"));
        first.setRoas(new BigDecimal("5.00"));
        first.setZeroOrderSpendAmount(new BigDecimal("12.00"));
        first.setZeroOrderSpendShare(new BigDecimal("0.2400"));
        NoonAdvertisingCampaignRow second = campaign("C_GRAY", "Gray package", "SKU-5");
        second.setSpendAmount(new BigDecimal("40.00"));
        second.setOrdersCount(2);
        second.setAdRevenue(new BigDecimal("200.00"));
        second.setRoas(new BigDecimal("5.00"));
        second.setZeroOrderSpendAmount(new BigDecimal("12.00"));
        second.setZeroOrderSpendShare(new BigDecimal("0.3000"));

        NoonAdvertisingProductDiagnostic diagnostic = service
                .diagnose(List.of(product), List.of(first, second), List.of(), List.of())
                .getProductDiagnostics()
                .get(0);

        assertEquals(NoonAdvertisingProductDiagnosisType.STRUCTURE_REVIEW, diagnostic.getDiagnosisType());
        assertEquals("结构待整理", diagnostic.getDiagnosisLabel());
        assertEquals(0, diagnostic.getCoreCampaignCount());
        assertEquals(0, diagnostic.getExplorationCampaignCount());
        assertEquals(2, diagnostic.getUnclassifiedCampaignCount());
        assertTrue(diagnostic.getLabels().contains("计划用途待确认"));
        assertTrue(diagnostic.getRecommendedActions().contains("当前计划用途不清，建议先归类为核心或探索后再判断动作。"));
    }

    @Test
    void marksProductWithNoSpendAsInsufficientData() {
        NoonAdvertisingProductRow product = product("SKU-2", 0, 0, "0", "0", 0, "0", "0", "0");

        NoonAdvertisingStructureDiagnosticResult result = service.diagnose(List.of(product), List.of(), List.of(), List.of());

        NoonAdvertisingProductDiagnostic diagnostic = result.getProductDiagnostics().get(0);
        assertEquals(NoonAdvertisingStructureStatus.INSUFFICIENT_DATA, diagnostic.getStructureStatus());
        assertEquals(NoonAdvertisingProductDiagnosisType.INSUFFICIENT_DATA, diagnostic.getDiagnosisType());
        assertEquals("样本不足", diagnostic.getDiagnosisLabel());
        assertTrue(diagnostic.getLabels().contains("样本不足"));
        assertTrue(diagnostic.getRecommendedActions().contains("样本不足，暂不判断广告结构。"));
    }

    @Test
    void productDiagnosticsUseStoreSiteAndPartnerSkuIdentity() {
        NoonAdvertisingProductRow saProduct = product("SHARED-PSKU", 1, 3, "80", "200", 2, "2.50", "10", "0.125");
        saProduct.setStoreCode("STR-A");
        saProduct.setSiteCode("SA");
        NoonAdvertisingProductRow aeProduct = product("SHARED-PSKU", 1, 3, "80", "200", 2, "2.50", "10", "0.125");
        aeProduct.setStoreCode("STR-B");
        aeProduct.setSiteCode("AE");

        NoonAdvertisingCampaignRow saCoreCampaign = campaign("C_SA_CORE", "core exact main", "SHARED-PSKU");
        saCoreCampaign.setStoreCode("STR-A");
        saCoreCampaign.setSiteCode("SA");
        NoonAdvertisingCampaignRow aeCampaign = campaign("C_AE_AUTO", "auto discovery", "SHARED-PSKU");
        aeCampaign.setStoreCode("STR-B");
        aeCampaign.setSiteCode("AE");
        NoonAdvertisingQueryRow saWinning = query("C_SA_CORE", "SHARED-PSKU", "shared keyword", "25.00", 4, "320.00", "12.80");
        saWinning.setStoreCode("STR-A");
        saWinning.setSiteCode("SA");

        NoonAdvertisingStructureDiagnosticResult result = service.diagnose(
                List.of(saProduct, aeProduct),
                List.of(saCoreCampaign, aeCampaign),
                List.of(),
                List.of(saWinning)
        );

        NoonAdvertisingProductDiagnostic saDiagnostic = result.getProductDiagnostics().get(0);
        NoonAdvertisingProductDiagnostic aeDiagnostic = result.getProductDiagnostics().get(1);

        assertEquals("STR-A|SA|SHARED-PSKU", saDiagnostic.getProductIdentityKey());
        assertEquals("STR-B|AE|SHARED-PSKU", aeDiagnostic.getProductIdentityKey());
        assertEquals(1, saDiagnostic.getCoreCampaignCount());
        assertEquals(0, saDiagnostic.getExplorationCampaignCount());
        assertEquals(0, aeDiagnostic.getCoreCampaignCount());
        assertEquals(1, aeDiagnostic.getExplorationCampaignCount());
    }

    @Test
    void unresolvedAdSkuCodeIsNotStableProductIdentity() {
        NoonAdvertisingProductRow product = product("", 1, 3, "80", "200", 2, "2.50", "10", "0.125");
        product.setStoreCode("STR-A");
        product.setSiteCode("SA");
        product.setAdSkuCode("ZDD2976030B3514036193Z-1");
        NoonAdvertisingCampaignRow campaign = campaign("C_AD", "auto discovery", "");
        campaign.setStoreCode("STR-A");
        campaign.setSiteCode("SA");
        campaign.setPrimaryAdSkuCode("ZDD2976030B3514036193Z-1");

        NoonAdvertisingStructureDiagnosticResult result = service.diagnose(List.of(product), List.of(campaign), List.of(), List.of());

        NoonAdvertisingProductDiagnostic diagnostic = result.getProductDiagnostics().get(0);
        assertEquals("", diagnostic.getProductIdentityKey());
        assertEquals("STR-A|SA|ADSKU|ZDD2976030B3514036193Z-1", diagnostic.getAdvertisingIdentityKey());
        assertEquals("ZDD2976030B3514036193Z-1", diagnostic.getAdSkuCode());
        assertFalse(diagnostic.isProductIdentityResolved());
        assertFalse(diagnostic.getLabels().contains("系统PSKU未匹配"));
    }

    private NoonAdvertisingCampaignRow campaign(String campaignCode, String campaignName, String partnerSku) {
        NoonAdvertisingCampaignRow row = new NoonAdvertisingCampaignRow();
        row.setCampaignCode(campaignCode);
        row.setCampaignName(campaignName);
        row.setPrimaryPartnerSku(partnerSku);
        row.setSpendAmount(BigDecimal.ZERO);
        row.setAdRevenue(BigDecimal.ZERO);
        row.setRoas(BigDecimal.ZERO);
        row.setZeroOrderSpendAmount(BigDecimal.ZERO);
        row.setZeroOrderSpendShare(BigDecimal.ZERO);
        return row;
    }

    private NoonAdvertisingProductRow product(
            String partnerSku,
            long campaignCount,
            long queryCount,
            String spend,
            String revenue,
            long orders,
            String roas,
            String zeroSpend,
            String zeroShare
    ) {
        NoonAdvertisingProductRow row = new NoonAdvertisingProductRow();
        row.setPartnerSku(partnerSku);
        row.setCampaignCount(campaignCount);
        row.setQueryCount(queryCount);
        row.setSpendAmount(new BigDecimal(spend));
        row.setAdRevenue(new BigDecimal(revenue));
        row.setOrdersCount(orders);
        row.setRoas(new BigDecimal(roas));
        row.setZeroOrderSpendAmount(new BigDecimal(zeroSpend));
        row.setZeroOrderSpendShare(new BigDecimal(zeroShare));
        return row;
    }

    private NoonAdvertisingQueryRow query(
            String campaignCode,
            String partnerSku,
            String queryText,
            String spend,
            long orders,
            String revenue,
            String roas
    ) {
        NoonAdvertisingQueryRow row = new NoonAdvertisingQueryRow();
        row.setCampaignCode(campaignCode);
        row.setPartnerSku(partnerSku);
        row.setQueryText(queryText);
        row.setQueryKind("search_term");
        row.setSpendAmount(new BigDecimal(spend));
        row.setOrdersCount(orders);
        row.setAdRevenue(new BigDecimal(revenue));
        row.setRoas(new BigDecimal(roas));
        return row;
    }
}
