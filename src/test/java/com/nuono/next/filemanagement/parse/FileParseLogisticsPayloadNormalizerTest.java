package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FileParseLogisticsPayloadNormalizerTest {

    @Test
    void shouldNormalizeEtSaudiCargoAirServiceLine() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "广州易通天下物流");
        payload.put("country", "Saudi Arabia");
        payload.put("fulfillmentMode", "FBN delivery");
        payload.put("destinationNode", "Riyadh FBN warehouse");
        payload.put("transportMode", "空运大货");
        payload.put("serviceScope", "仓到仓 + FBN送仓");
        payload.put("originWarehouse", "佛山仓");
        payload.put("destinationWarehouse", "利雅得仓");
        payload.put("departureFrequency", "每周二/五");
        payload.put("leadTimeText", "5-7 days");
        payload.put("effectiveDate", "2026/04/14");

        Map<String, Object> normalized = FileParseLogisticsPayloadNormalizer.normalize(
                "logistics_service_line",
                payload
        );

        assertEquals("et", normalized.get("forwarderCode"));
        assertEquals("ET/易通", normalized.get("forwarderName"));
        assertEquals("KSA", normalized.get("country"));
        assertEquals("FBN", normalized.get("fulfillmentMode"));
        assertEquals("Riyadh FBN warehouse", normalized.get("destinationNode"));
        assertEquals("cargo_air", normalized.get("transportMode"));
        assertEquals("warehouse_to_fbn", normalized.get("serviceScope"));
        assertEquals("佛山仓", normalized.get("originWarehouse"));
        assertEquals("利雅得仓", normalized.get("destinationWarehouse"));
        assertEquals("5-7 days", normalized.get("leadTimeText"));
        assertEquals(5, normalized.get("leadTimeMinDays"));
        assertEquals(7, normalized.get("leadTimeMaxDays"));
        assertEquals("2026-04-14", normalized.get("effectiveDate"));
    }

    @Test
    void shouldNormalizeYiteUaeSeaServiceLine() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "义特物流");
        payload.put("country", "AE");
        payload.put("fulfillmentMode", "FBN");
        payload.put("destinationNode", "Dubai");
        payload.put("transportMode", "海运");
        payload.put("serviceScope", "FBN运输");
        payload.put("leadTimeText", "18-25 天");

        Map<String, Object> normalized = FileParseLogisticsPayloadNormalizer.normalize(
                "logistics_service_line",
                payload
        );

        assertEquals("yite", normalized.get("forwarderCode"));
        assertEquals("义特物流", normalized.get("forwarderName"));
        assertEquals("UAE", normalized.get("country"));
        assertEquals("sea", normalized.get("transportMode"));
        assertEquals("fbn_delivery", normalized.get("serviceScope"));
        assertEquals(18, normalized.get("leadTimeMinDays"));
        assertEquals(25, normalized.get("leadTimeMaxDays"));
    }

    @Test
    void shouldNormalizeCargoCategoryMetadata() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "广州易通天下物流");
        payload.put("serviceLineKey", "ET KSA air cargo");
        payload.put("categoryCode", "A类");
        payload.put("categoryName", "A类 普货");
        payload.put("productExamples", "母婴用品、玩具");
        payload.put("keywords", "baby products; toys");
        payload.put("electricType", "不带电");
        payload.put("sensitiveTags", "非敏感");
        payload.put("packingPolicy", "按普货包装");
        payload.put("manualConfirmRequired", "需要人工确认");

        Map<String, Object> normalized = FileParseLogisticsPayloadNormalizer.normalize(
                "logistics_cargo_category",
                payload
        );

        assertEquals("et", normalized.get("forwarderCode"));
        assertEquals("ET/易通", normalized.get("forwarderName"));
        assertEquals("ET KSA air cargo", normalized.get("serviceLineKey"));
        assertEquals("A", normalized.get("categoryCode"));
        assertEquals("A类 普货", normalized.get("categoryName"));
        assertEquals("non_electric", normalized.get("electricType"));
        assertEquals("非敏感", normalized.get("sensitiveTags"));
        assertEquals("按普货包装", normalized.get("packingPolicy"));
        assertEquals(true, normalized.get("manualConfirmRequired"));
    }

    @Test
    void shouldNormalizeBasePriceNumbersAndUnits() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "广州易通天下物流");
        payload.put("serviceLineKey", "ET KSA air cargo");
        payload.put("cargoCategoryKey", "A类");
        payload.put("unitPrice", "44 RMB/KG");
        payload.put("currency", "人民币");
        payload.put("billingUnit", "KG");
        payload.put("pricingModel", "按重量");
        payload.put("minimumBillableUnit", "10KG");
        payload.put("volumeDivisor", "6000");
        payload.put("seaWeightRatio", "1 : 300");
        payload.put("roundingRule", "不足1KG按1KG");
        payload.put("priceStatus", "起");
        payload.put("effectiveDate", "2026/04/14");

        Map<String, Object> normalized = FileParseLogisticsPayloadNormalizer.normalize(
                "logistics_base_price",
                payload
        );

        assertEquals("et", normalized.get("forwarderCode"));
        assertEquals("A", normalized.get("cargoCategoryKey"));
        assertEquals(new BigDecimal("44"), normalized.get("unitPrice"));
        assertEquals("CNY", normalized.get("currency"));
        assertEquals("kg", normalized.get("billingUnit"));
        assertEquals("per_weight", normalized.get("pricingModel"));
        assertEquals(new BigDecimal("10"), normalized.get("minimumBillableUnit"));
        assertEquals("kg", normalized.get("minimumBillableUnitType"));
        assertEquals(6000, normalized.get("volumeDivisor"));
        assertEquals("1:300", normalized.get("seaWeightRatio"));
        assertEquals("starting_price", normalized.get("priceStatus"));
        assertEquals("2026-04-14", normalized.get("effectiveDate"));
    }

    @Test
    void shouldNormalizeFeesRulesWarehouseFeesAndRestrictions() {
        Map<String, Object> surcharge = new LinkedHashMap<>();
        surcharge.put("forwarderName", "ET");
        surcharge.put("serviceLineKey", "ET KSA air cargo");
        surcharge.put("surchargeName", "超长附加费");
        surcharge.put("surchargeType", "oversize");
        surcharge.put("triggerCondition", "任一边超过120cm");
        surcharge.put("amount", "15 RMB");
        surcharge.put("currency", "RMB");
        surcharge.put("billingUnit", "KG");
        surcharge.put("includedInBasePrice", "否");

        Map<String, Object> billingRule = new LinkedHashMap<>();
        billingRule.put("forwarderName", "ET");
        billingRule.put("serviceLineKey", "ET KSA air cargo");
        billingRule.put("ruleName", "单件重量限制");
        billingRule.put("conditionText", "单件重量 > 30KG");
        billingRule.put("actionText", "需提前确认");
        billingRule.put("operator", "greater than");
        billingRule.put("thresholdValue", "30KG");
        billingRule.put("thresholdUnit", "KG");
        billingRule.put("severity", "warning");

        Map<String, Object> warehouseFee = new LinkedHashMap<>();
        warehouseFee.put("forwarderName", "ET");
        warehouseFee.put("country", "UAE");
        warehouseFee.put("warehouseNode", "Dubai warehouse");
        warehouseFee.put("serviceName", "贴标费");
        warehouseFee.put("serviceType", "labeling");
        warehouseFee.put("feeType", "per piece");
        warehouseFee.put("amount", "0.5 AED");
        warehouseFee.put("currency", "AED");
        warehouseFee.put("billingUnit", "件");

        Map<String, Object> restriction = new LinkedHashMap<>();
        restriction.put("forwarderName", "ET");
        restriction.put("serviceLineKey", "ET KSA air cargo");
        restriction.put("restrictionType", "禁发");
        restriction.put("itemText", "纯电池");
        restriction.put("requirementText", "不可承运");
        restriction.put("applicabilityScope", "Saudi air cargo");
        restriction.put("severity", "hard");
        restriction.put("manualConfirmRequired", "是");

        Map<String, Object> normalizedSurcharge = FileParseLogisticsPayloadNormalizer.normalize("logistics_surcharge", surcharge);
        Map<String, Object> normalizedBillingRule = FileParseLogisticsPayloadNormalizer.normalize("logistics_billing_rule", billingRule);
        Map<String, Object> normalizedWarehouseFee = FileParseLogisticsPayloadNormalizer.normalize("logistics_warehouse_service_fee", warehouseFee);
        Map<String, Object> normalizedRestriction = FileParseLogisticsPayloadNormalizer.normalize("logistics_restriction", restriction);

        assertEquals("oversize", normalizedSurcharge.get("surchargeType"));
        assertEquals(new BigDecimal("15"), normalizedSurcharge.get("amount"));
        assertEquals("CNY", normalizedSurcharge.get("currency"));
        assertEquals("kg", normalizedSurcharge.get("billingUnit"));
        assertEquals(false, normalizedSurcharge.get("includedInBasePrice"));

        assertEquals(">", normalizedBillingRule.get("operator"));
        assertEquals(new BigDecimal("30"), normalizedBillingRule.get("thresholdValue"));
        assertEquals("kg", normalizedBillingRule.get("thresholdUnit"));
        assertEquals("warning", normalizedBillingRule.get("severity"));

        assertEquals("UAE", normalizedWarehouseFee.get("country"));
        assertEquals("labeling", normalizedWarehouseFee.get("serviceType"));
        assertEquals("per_piece", normalizedWarehouseFee.get("feeType"));
        assertEquals(new BigDecimal("0.5"), normalizedWarehouseFee.get("amount"));
        assertEquals("piece", normalizedWarehouseFee.get("billingUnit"));

        assertEquals("prohibited", normalizedRestriction.get("restrictionType"));
        assertEquals("blocking", normalizedRestriction.get("severity"));
        assertEquals(true, normalizedRestriction.get("manualConfirmRequired"));
    }
}
