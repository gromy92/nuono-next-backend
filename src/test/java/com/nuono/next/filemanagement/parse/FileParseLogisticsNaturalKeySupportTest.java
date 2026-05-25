package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FileParseLogisticsNaturalKeySupportTest {

    @Test
    void serviceLineNaturalKeyShouldUseNormalizedRouteIdentityNotLeadTime() {
        Map<String, Object> first = serviceLine();
        first.put("leadTimeText", "5-7 days");

        Map<String, Object> changedLeadTime = new LinkedHashMap<>(first);
        changedLeadTime.put("leadTimeText", "6-8 days");

        String firstKey = FileParseNaturalKeySupport.buildNaturalKey("logistics_service_line", first);
        String changedLeadTimeKey = FileParseNaturalKeySupport.buildNaturalKey("logistics_service_line", changedLeadTime);

        assertEquals("et|KSA|FBN|cargo_air|warehouse_to_fbn|Riyadh FBN warehouse", firstKey);
        assertEquals(firstKey, changedLeadTimeKey);
    }

    @Test
    void serviceLineNaturalKeyShouldChangeWhenTransportModeChanges() {
        Map<String, Object> airCargo = serviceLine();
        Map<String, Object> sea = new LinkedHashMap<>(airCargo);
        sea.put("transportMode", "海运");

        assertNotEquals(
                FileParseNaturalKeySupport.buildNaturalKey("logistics_service_line", airCargo),
                FileParseNaturalKeySupport.buildNaturalKey("logistics_service_line", sea)
        );
    }

    @Test
    void cargoCategoryNaturalKeyShouldUseCategoryCodeInsteadOfDisplayName() {
        Map<String, Object> original = cargoCategory();
        Map<String, Object> renamed = new LinkedHashMap<>(original);
        renamed.put("categoryName", "General cargo class A");
        renamed.put("keywords", "toys, baby products, household goods");

        String originalKey = FileParseNaturalKeySupport.buildNaturalKey("logistics_cargo_category", original);
        String renamedKey = FileParseNaturalKeySupport.buildNaturalKey("logistics_cargo_category", renamed);

        assertEquals("et|ET KSA air cargo|A", originalKey);
        assertEquals(originalKey, renamedKey);
    }

    @Test
    void basePriceNaturalKeyShouldIgnoreUnitPriceChanges() {
        Map<String, Object> original = basePrice();
        Map<String, Object> priceChanged = new LinkedHashMap<>(original);
        priceChanged.put("unitPrice", "46 CNY/KG");

        String originalKey = FileParseNaturalKeySupport.buildNaturalKey("logistics_base_price", original);
        String changedPriceKey = FileParseNaturalKeySupport.buildNaturalKey("logistics_base_price", priceChanged);

        assertEquals("et|ET KSA air cargo|A|per_weight|kg|CNY", originalKey);
        assertEquals(originalKey, changedPriceKey);
    }

    @Test
    void remainingLogisticsItemTypesShouldHaveStableBusinessKeys() {
        assertEquals(
                "et|ET KSA air cargo|超长附加费|任一边超过120cm",
                FileParseNaturalKeySupport.buildNaturalKey("logistics_surcharge", surcharge())
        );
        assertEquals(
                "et|ET KSA air cargo|单件重量限制|单件重量 > 30KG|需提前确认",
                FileParseNaturalKeySupport.buildNaturalKey("logistics_billing_rule", billingRule())
        );
        assertEquals(
                "et|Dubai warehouse|贴标费|per_piece",
                FileParseNaturalKeySupport.buildNaturalKey("logistics_warehouse_service_fee", warehouseFee())
        );
        assertEquals(
                "et|ET KSA air cargo|prohibited|纯电池",
                FileParseNaturalKeySupport.buildNaturalKey("logistics_restriction", restriction())
        );
    }

    private Map<String, Object> serviceLine() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "ET");
        payload.put("country", "Saudi");
        payload.put("fulfillmentMode", "FBN");
        payload.put("destinationNode", "Riyadh FBN warehouse");
        payload.put("transportMode", "air cargo");
        payload.put("serviceScope", "warehouse to FBN");
        return payload;
    }

    private Map<String, Object> cargoCategory() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "ET");
        payload.put("serviceLineKey", "ET KSA air cargo");
        payload.put("categoryCode", "A类");
        payload.put("categoryName", "A类 普货");
        payload.put("keywords", "baby products; toys");
        payload.put("manualConfirmRequired", "否");
        return payload;
    }

    private Map<String, Object> basePrice() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "ET");
        payload.put("serviceLineKey", "ET KSA air cargo");
        payload.put("cargoCategoryKey", "A类");
        payload.put("unitPrice", "44 RMB/KG");
        payload.put("currency", "RMB");
        payload.put("billingUnit", "KG");
        payload.put("pricingModel", "按重量");
        payload.put("priceStatus", "正常");
        return payload;
    }

    private Map<String, Object> surcharge() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "ET");
        payload.put("serviceLineKey", "ET KSA air cargo");
        payload.put("surchargeName", "超长附加费");
        payload.put("triggerCondition", "任一边超过120cm");
        payload.put("amount", "15 RMB");
        payload.put("billingUnit", "KG");
        payload.put("includedInBasePrice", "否");
        return payload;
    }

    private Map<String, Object> billingRule() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "ET");
        payload.put("serviceLineKey", "ET KSA air cargo");
        payload.put("ruleName", "单件重量限制");
        payload.put("conditionText", "单件重量 > 30KG");
        payload.put("actionText", "需提前确认");
        payload.put("severity", "warning");
        return payload;
    }

    private Map<String, Object> warehouseFee() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "ET");
        payload.put("warehouseNode", "Dubai warehouse");
        payload.put("serviceName", "贴标费");
        payload.put("feeType", "per piece");
        payload.put("amount", "0.5 AED");
        payload.put("billingUnit", "件");
        return payload;
    }

    private Map<String, Object> restriction() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "ET");
        payload.put("serviceLineKey", "ET KSA air cargo");
        payload.put("restrictionType", "禁发");
        payload.put("itemText", "纯电池");
        payload.put("requirementText", "不可承运");
        payload.put("severity", "hard");
        return payload;
    }
}
