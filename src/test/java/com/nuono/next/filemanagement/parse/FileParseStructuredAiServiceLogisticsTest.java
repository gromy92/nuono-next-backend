package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileParseStructuredAiServiceLogisticsTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Mock
    private AiCapabilityService aiCapabilityService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBackfillEtContextAcrossSparseRowsWhenStabilizingLogisticsItems() throws Exception {
        FileParseStructuredAiService service = new FileParseStructuredAiService(aiCapabilityService, objectMapper);
        FileParseStructuredAiResult raw = new FileParseStructuredAiResult();
        raw.setParserModel("test-model");
        raw.setItems(List.of(
                structuredLogisticsItem(
                        FileParseLogisticsQuoteStandard.SERVICE_LINE,
                        mapOf(
                                "forwarderName", "ET",
                                "country", "沙特",
                                "transportMode", "快递",
                                "serviceScope", "中国到沙特仓到仓",
                                "leadTimeText", "7-12天"
                        ),
                        List.of(91001L),
                        1
                ),
                structuredLogisticsItem(
                        FileParseLogisticsQuoteStandard.CARGO_CATEGORY,
                        mapOf(
                                "categoryCode", "D类",
                                "categoryName", "D类 医疗用品",
                                "productExamples", "医疗用品",
                                "manualConfirmRequired", false
                        ),
                        List.of(91002L),
                        2
                ),
                structuredLogisticsItem(
                        FileParseLogisticsQuoteStandard.BASE_PRICE,
                        mapOf(
                                "cargoCategoryKey", "D类",
                                "unitPrice", "92 RMB/KG"
                        ),
                        List.of(91003L),
                        3
                ),
                structuredLogisticsItem(
                        FileParseLogisticsQuoteStandard.BILLING_RULE,
                        mapOf(
                                "ruleName", "最低计费重量",
                                "conditionText", "不足10KG",
                                "actionText", "按10KG计费"
                        ),
                        List.of(91004L),
                        4
                ),
                structuredLogisticsItem(
                        FileParseLogisticsQuoteStandard.RESTRICTION,
                        mapOf(
                                "restrictionType", "限发",
                                "itemText", "医疗用品",
                                "requirementText", "需提供资质",
                                "severity", "warning"
                        ),
                        List.of(91005L),
                        5
                ),
                structuredLogisticsItem(
                        FileParseLogisticsQuoteStandard.WAREHOUSE_SERVICE_FEE,
                        mapOf(
                                "serviceName", "单独报关",
                                "feeType", "报关费",
                                "amount", "450 RMB/票",
                                "originWarehouse", "佛山仓,义乌仓"
                        ),
                        List.of(91006L),
                        6
                )
        ));

        FileParseStructuredAiResult stabilized = service.stabilizeWithSourceContext(
                raw,
                targetPlan(),
                allLogisticsStandards(),
                "[[SOURCE_ROW_ID=91001;TYPE=pdf_text_line;LOC=page=1;line=1]] ET物流报价 中国到沙特仓到仓 快递 FBN 7-12天\n"
                        + "[[SOURCE_ROW_ID=91002;TYPE=pdf_text_line;LOC=page=2;line=6]] D类 医疗用品\n"
                        + "[[SOURCE_ROW_ID=91003;TYPE=pdf_text_line;LOC=page=2;line=7]] D类 92 RMB/KG\n"
                        + "[[SOURCE_ROW_ID=91004;TYPE=pdf_text_line;LOC=page=3;line=1]] 不足10KG按10KG计费\n"
                        + "[[SOURCE_ROW_ID=91005;TYPE=pdf_text_line;LOC=page=4;line=1]] 医疗用品需提供资质\n"
                        + "[[SOURCE_ROW_ID=91006;TYPE=pdf_text_line;LOC=page=5;line=1]] 佛山仓/义乌仓 单独报关 RMB450/票"
        );

        FileParseStructuredItem serviceLine = itemOf(stabilized, FileParseLogisticsQuoteStandard.SERVICE_LINE);
        FileParseStructuredItem category = itemOf(stabilized, FileParseLogisticsQuoteStandard.CARGO_CATEGORY);
        FileParseStructuredItem basePrice = itemOf(stabilized, FileParseLogisticsQuoteStandard.BASE_PRICE);
        FileParseStructuredItem billingRule = itemOf(stabilized, FileParseLogisticsQuoteStandard.BILLING_RULE);
        FileParseStructuredItem restriction = itemOf(stabilized, FileParseLogisticsQuoteStandard.RESTRICTION);
        FileParseStructuredItem warehouseFee = itemOf(stabilized, FileParseLogisticsQuoteStandard.WAREHOUSE_SERVICE_FEE);

        Map<String, Object> servicePayload = payloadOf(serviceLine);
        assertEquals("et", servicePayload.get("forwarderCode"));
        assertEquals("KSA", servicePayload.get("country"));
        assertEquals("FBN", servicePayload.get("fulfillmentMode"));
        assertEquals("express", servicePayload.get("transportMode"));
        assertEquals("warehouse_to_fbn", servicePayload.get("serviceScope"));
        assertEquals("KSA FBN warehouse", servicePayload.get("destinationNode"));
        assertEquals("pass", serviceLine.getValidationStatus());

        Map<String, Object> categoryPayload = payloadOf(category);
        String serviceLineKey = String.valueOf(categoryPayload.get("serviceLineKey"));
        assertEquals("et", categoryPayload.get("forwarderCode"));
        assertEquals("D", categoryPayload.get("categoryCode"));
        assertTrue(serviceLineKey.contains("ET KSA express"));
        assertEquals("pass", category.getValidationStatus());

        Map<String, Object> basePricePayload = payloadOf(basePrice);
        assertEquals("et", basePricePayload.get("forwarderCode"));
        assertEquals(serviceLineKey, basePricePayload.get("serviceLineKey"));
        assertEquals("D", basePricePayload.get("cargoCategoryKey"));
        assertEquals("CNY", basePricePayload.get("currency"));
        assertEquals("kg", basePricePayload.get("billingUnit"));
        assertEquals("per_weight", basePricePayload.get("pricingModel"));
        assertEquals("active", basePricePayload.get("priceStatus"));
        assertEquals("pass", basePrice.getValidationStatus());

        assertEquals("et", payloadOf(billingRule).get("forwarderCode"));
        assertEquals(serviceLineKey, payloadOf(billingRule).get("serviceLineKey"));
        assertEquals("pass", billingRule.getValidationStatus());

        assertEquals("et", payloadOf(restriction).get("forwarderCode"));
        assertEquals(serviceLineKey, payloadOf(restriction).get("serviceLineKey"));
        assertEquals("pass", restriction.getValidationStatus());
        assertEquals("佛山仓,义乌仓", payloadOf(warehouseFee).get("warehouseNode"));
        assertEquals("pass", warehouseFee.getValidationStatus());
        assertFalse(stabilized.getValidationSummaryJson().contains("hard_error"));
    }

    @Test
    void shouldGuideAiToEmitLogisticsLinkageFieldsForEtCategoriesAThroughG() {
        FileParseStructuredAiService service = new FileParseStructuredAiService(aiCapabilityService, objectMapper);
        when(aiCapabilityService.createStructuredText(any())).thenReturn(aiResultWithNoItems());

        service.parse(
                task(),
                targetPlan(),
                standardVersion(),
                allLogisticsStandards(),
                "[[SOURCE_ROW_ID=92001;TYPE=pdf_text_line;LOC=page=1;line=1]] ET物流报价 A-G类 44 RMB/KG",
                10001L
        );

        ArgumentCaptor<AiStructuredTextCommand> commandCaptor = ArgumentCaptor.forClass(AiStructuredTextCommand.class);
        verify(aiCapabilityService).createStructuredText(commandCaptor.capture());
        String instructions = commandCaptor.getValue().getInstructions();

        assertTrue(instructions.contains("forwarderCode"));
        assertTrue(instructions.contains("serviceLineKey"));
        assertTrue(instructions.contains("A-G"));
        assertTrue(instructions.contains("ET"));
        assertTrue(instructions.contains("warehouse_to_fbn"));
    }

    @Test
    void shouldNormalizeServiceLinePayloadBeforeResultItemsArePersisted() throws Exception {
        FileParseStructuredAiService service = new FileParseStructuredAiService(aiCapabilityService, objectMapper);
        when(aiCapabilityService.createStructuredText(any())).thenReturn(aiResultWithEtServiceLine());

        FileParseStructuredAiResult result = service.parse(
                task(),
                targetPlan(),
                standardVersion(),
                List.of(serviceLineStandard()),
                "[[SOURCE_ROW_ID=35001;TYPE=pdf_text_line;LOC=page=1;line=1]] ET Saudi air cargo 5-7 days",
                10001L
        );

        FileParseStructuredItem item = result.getItems().get(0);
        Map<String, Object> payload = objectMapper.readValue(item.getNormalizedPayloadJson(), MAP_TYPE);

        assertEquals("et", payload.get("forwarderCode"));
        assertEquals("ET/易通", payload.get("forwarderName"));
        assertEquals("KSA", payload.get("country"));
        assertEquals("cargo_air", payload.get("transportMode"));
        assertEquals("warehouse_to_fbn", payload.get("serviceScope"));
        assertEquals(5, payload.get("leadTimeMinDays"));
        assertEquals(7, payload.get("leadTimeMaxDays"));
        assertEquals("et|KSA|FBN|cargo_air|warehouse_to_fbn|Riyadh FBN warehouse", item.getNaturalKey());
        assertEquals("pass", item.getValidationStatus());
    }

    @Test
    void shouldNormalizeCargoCategoryAndFlagManualConfirmRequired() throws Exception {
        FileParseStructuredAiService service = new FileParseStructuredAiService(aiCapabilityService, objectMapper);
        when(aiCapabilityService.createStructuredText(any())).thenReturn(aiResultWithCargoCategory());

        FileParseStructuredAiResult result = service.parse(
                task(),
                targetPlan(),
                standardVersion(),
                List.of(cargoCategoryStandard()),
                "[[SOURCE_ROW_ID=35002;TYPE=pdf_text_line;LOC=page=2;line=5]] A类 普货: toys/baby products, no battery, manual confirm",
                10001L
        );

        FileParseStructuredItem item = result.getItems().get(0);
        Map<String, Object> payload = objectMapper.readValue(item.getNormalizedPayloadJson(), MAP_TYPE);

        assertEquals("et", payload.get("forwarderCode"));
        assertEquals("A", payload.get("categoryCode"));
        assertEquals("non_electric", payload.get("electricType"));
        assertEquals(true, payload.get("manualConfirmRequired"));
        assertEquals("et|ET KSA air cargo|A", item.getNaturalKey());
        assertEquals("warning", item.getValidationStatus());
        assertEquals("pending", item.getReviewStatus());
        assertTrue(item.getValidationErrorJson().contains("manualConfirmRequired"));
    }

    @Test
    void shouldNormalizeBasePriceAndRejectIncompatibleUnitCombination() throws Exception {
        FileParseStructuredAiService service = new FileParseStructuredAiService(aiCapabilityService, objectMapper);
        when(aiCapabilityService.createStructuredText(any())).thenReturn(aiResultWithBasePrice());

        FileParseStructuredAiResult result = service.parse(
                task(),
                targetPlan(),
                standardVersion(),
                List.of(basePriceStandard()),
                "[[SOURCE_ROW_ID=35003;TYPE=pdf_text_line;LOC=page=3;line=8]] A class price 44 RMB/KG by volume",
                10001L
        );

        FileParseStructuredItem item = result.getItems().get(0);
        Map<String, Object> payload = objectMapper.readValue(item.getNormalizedPayloadJson(), MAP_TYPE);

        assertEquals("et", payload.get("forwarderCode"));
        assertEquals("A", payload.get("cargoCategoryKey"));
        assertEquals("44", String.valueOf(payload.get("unitPrice")));
        assertEquals("CNY", payload.get("currency"));
        assertEquals("kg", payload.get("billingUnit"));
        assertEquals("per_volume", payload.get("pricingModel"));
        assertEquals("et|ET KSA air cargo|A|per_volume|kg|CNY", item.getNaturalKey());
        assertEquals("hard_error", item.getValidationStatus());
        assertTrue(item.getValidationErrorJson().contains("incompatibleUnit"));
    }

    @Test
    void shouldValidateSurchargeAndRestrictionRows() throws Exception {
        FileParseStructuredAiService service = new FileParseStructuredAiService(aiCapabilityService, objectMapper);
        when(aiCapabilityService.createStructuredText(any())).thenReturn(aiResultWithSurchargeAndRestriction());

        FileParseStructuredAiResult result = service.parse(
                task(),
                targetPlan(),
                standardVersion(),
                List.of(surchargeStandard(), restrictionStandard()),
                "[[SOURCE_ROW_ID=35004;TYPE=pdf_text_line;LOC=page=4;line=2]] Oversize fee and prohibited batteries",
                10001L
        );

        FileParseStructuredItem surcharge = result.getItems().get(0);
        FileParseStructuredItem restriction = result.getItems().get(1);

        assertEquals("et|ET KSA air cargo|超长附加费|", surcharge.getNaturalKey());
        assertEquals("hard_error", surcharge.getValidationStatus());
        assertTrue(surcharge.getValidationErrorJson().contains("triggerCondition"));

        assertEquals("et|ET KSA air cargo|prohibited|纯电池", restriction.getNaturalKey());
        assertEquals("hard_error", restriction.getValidationStatus());
        assertTrue(restriction.getValidationErrorJson().contains("blockingRestriction"));
    }

    @Test
    void shouldWarnButNotBlockWarehouseServiceFeeWithoutAmount() throws Exception {
        FileParseStructuredAiService service = new FileParseStructuredAiService(aiCapabilityService, objectMapper);
        when(aiCapabilityService.createStructuredText(any())).thenReturn(aiResultWithWarehouseServiceWithoutAmount());

        FileParseStructuredAiResult result = service.parse(
                task(),
                targetPlan(),
                standardVersion(),
                List.of(warehouseServiceFeeStandard()),
                "[[SOURCE_ROW_ID=35006;TYPE=pdf_text_line;LOC=page=5;line=1]] 收货点为佛山仓、义乌仓",
                10001L
        );

        FileParseStructuredItem item = result.getItems().get(0);

        assertEquals("warning", item.getValidationStatus());
        assertEquals("pending", item.getReviewStatus());
        assertTrue(item.getValidationErrorJson().contains("amount"));
    }

    private AiStructuredTextResult aiResultWithWarehouseServiceWithoutAmount() {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "ET");
        payload.put("country", "KSA");
        payload.put("warehouseNode", "佛山仓/义乌仓");
        payload.put("serviceName", "收货点服务");
        payload.put("feeType", "receiving");
        payload.put("conditionText", "收货点为佛山仓、义乌仓");

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemType", FileParseLogisticsQuoteStandard.WAREHOUSE_SERVICE_FEE);
        item.put("payload", payload);
        item.put("confidence", "medium");
        item.put("sourceRowIds", List.of(35006L));
        result.setParsedJson(Map.of("summary", Map.of("source", "test"), "items", List.of(item)));
        return result;
    }

    private AiStructuredTextResult aiResultWithEtServiceLine() {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "广州易通天下物流");
        payload.put("country", "Saudi Arabia");
        payload.put("fulfillmentMode", "FBN delivery");
        payload.put("destinationNode", "Riyadh FBN warehouse");
        payload.put("transportMode", "空运大货");
        payload.put("serviceScope", "仓到仓 + FBN送仓");
        payload.put("leadTimeText", "5-7 days");
        payload.put("effectiveDate", "2026/04/14");

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemType", FileParseLogisticsQuoteStandard.SERVICE_LINE);
        item.put("payload", payload);
        item.put("confidence", "high");
        item.put("sourceRowIds", List.of(35001L));
        result.setParsedJson(Map.of("summary", Map.of("source", "test"), "items", List.of(item)));
        return result;
    }

    private AiStructuredTextResult aiResultWithCargoCategory() {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "ET");
        payload.put("serviceLineKey", "ET KSA air cargo");
        payload.put("categoryCode", "A类");
        payload.put("categoryName", "A类 普货");
        payload.put("productExamples", "toys, baby products");
        payload.put("keywords", "baby products; toys");
        payload.put("electricType", "不带电");
        payload.put("sensitiveTags", "非敏感");
        payload.put("packingPolicy", "按普货包装");
        payload.put("manualConfirmRequired", "需要人工确认");

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemType", FileParseLogisticsQuoteStandard.CARGO_CATEGORY);
        item.put("payload", payload);
        item.put("confidence", "medium");
        item.put("sourceRowIds", List.of(35002L));
        result.setParsedJson(Map.of("summary", Map.of("source", "test"), "items", List.of(item)));
        return result;
    }

    private AiStructuredTextResult aiResultWithBasePrice() {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "ET");
        payload.put("serviceLineKey", "ET KSA air cargo");
        payload.put("cargoCategoryKey", "A类");
        payload.put("unitPrice", "44 RMB/KG");
        payload.put("currency", "RMB");
        payload.put("billingUnit", "KG");
        payload.put("pricingModel", "按体积");
        payload.put("minimumBillableUnit", "10KG");
        payload.put("volumeDivisor", "6000");
        payload.put("priceStatus", "正常");

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemType", FileParseLogisticsQuoteStandard.BASE_PRICE);
        item.put("payload", payload);
        item.put("confidence", "medium");
        item.put("sourceRowIds", List.of(35003L));
        result.setParsedJson(Map.of("summary", Map.of("source", "test"), "items", List.of(item)));
        return result;
    }

    private AiStructuredTextResult aiResultWithSurchargeAndRestriction() {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");

        Map<String, Object> surchargePayload = new LinkedHashMap<>();
        surchargePayload.put("forwarderName", "ET");
        surchargePayload.put("serviceLineKey", "ET KSA air cargo");
        surchargePayload.put("surchargeName", "超长附加费");
        surchargePayload.put("amount", "15 RMB");
        surchargePayload.put("billingUnit", "KG");
        surchargePayload.put("includedInBasePrice", "否");

        Map<String, Object> surcharge = new LinkedHashMap<>();
        surcharge.put("itemType", FileParseLogisticsQuoteStandard.SURCHARGE);
        surcharge.put("payload", surchargePayload);
        surcharge.put("confidence", "medium");
        surcharge.put("sourceRowIds", List.of(35004L));

        Map<String, Object> restrictionPayload = new LinkedHashMap<>();
        restrictionPayload.put("forwarderName", "ET");
        restrictionPayload.put("serviceLineKey", "ET KSA air cargo");
        restrictionPayload.put("restrictionType", "禁发");
        restrictionPayload.put("itemText", "纯电池");
        restrictionPayload.put("requirementText", "不可承运");
        restrictionPayload.put("severity", "hard");

        Map<String, Object> restriction = new LinkedHashMap<>();
        restriction.put("itemType", FileParseLogisticsQuoteStandard.RESTRICTION);
        restriction.put("payload", restrictionPayload);
        restriction.put("confidence", "high");
        restriction.put("sourceRowIds", List.of(35005L));

        result.setParsedJson(Map.of("summary", Map.of("source", "test"), "items", List.of(surcharge, restriction)));
        return result;
    }

    private AiStructuredTextResult aiResultWithNoItems() {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");
        result.setParsedJson(Map.of("summary", Map.of("source", "test"), "items", List.of()));
        return result;
    }

    private FileParseStructuredItem structuredLogisticsItem(
            String itemType,
            Map<String, Object> payload,
            List<Long> sourceRowIds,
            int sortNo
    ) throws Exception {
        FileParseStructuredItem item = new FileParseStructuredItem();
        item.setItemType(itemType);
        item.setNaturalKey(itemType + "-" + sortNo);
        item.setNaturalKeyHash(FileParseNaturalKeySupport.naturalKeyHash(itemType, item.getNaturalKey()));
        item.setConfidence("medium");
        item.setNormalizedPayloadJson(objectMapper.writeValueAsString(payload));
        item.setSourceRowIds(sourceRowIds);
        item.setEvidenceJson(objectMapper.writeValueAsString(Map.of(
                "source", "ai_structured_text",
                "sourceRowIds", sourceRowIds
        )));
        item.setValidationStatus("hard_error");
        item.setReviewStatus("needs_fix");
        item.setValidationErrorJson("{\"missingRequiredFields\":[\"forwarderCode\"]}");
        item.setSortNo(sortNo);
        return item;
    }

    private Map<String, Object> payloadOf(FileParseStructuredItem item) throws Exception {
        return objectMapper.readValue(item.getNormalizedPayloadJson(), MAP_TYPE);
    }

    private FileParseStructuredItem itemOf(FileParseStructuredAiResult result, String itemType) {
        return result.getItems().stream()
                .filter(item -> itemType.equals(item.getItemType()))
                .findFirst()
                .orElseThrow();
    }

    private Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            payload.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return payload;
    }

    private FileParseTaskRow task() {
        FileParseTaskRow row = new FileParseTaskRow();
        row.setId(20001L);
        row.setTargetPlanId(4006L);
        row.setStandardVersionId(2003L);
        return row;
    }

    private FileParseTargetPlanRow targetPlan() {
        FileParseTargetPlanRow row = new FileParseTargetPlanRow();
        row.setId(4006L);
        row.setCode("logistics_et");
        row.setLabel("物流-易通");
        row.setDocumentType("logistics_rule");
        return row;
    }

    private FileParseStandardVersionRow standardVersion() {
        FileParseStandardVersionRow row = new FileParseStandardVersionRow();
        row.setId(2003L);
        row.setStandardVersion("STD-2026.05");
        return row;
    }

    private FileParseItemStandardRow serviceLineStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setId(3010L);
        row.setStandardVersionId(2003L);
        row.setItemType(FileParseLogisticsQuoteStandard.SERVICE_LINE);
        row.setItemLabel("物流服务线路");
        row.setNaturalKeyJson("{\"fields\":[\"forwarderCode\",\"country\",\"fulfillmentMode\",\"transportMode\",\"serviceScope\",\"destinationNode\"]}");
        row.setFieldSchemaJson("{\"forwarderCode\":\"string\",\"country\":\"string\",\"fulfillmentMode\":\"string\",\"transportMode\":\"string\",\"serviceScope\":\"string\",\"destinationNode\":\"string\"}");
        row.setDisplayConfigJson("{\"columns\":[\"forwarderName\",\"country\",\"transportMode\",\"serviceScope\",\"destinationNode\",\"leadTimeText\"]}");
        row.setValidationRuleJson("{\"required\":[\"forwarderCode\",\"country\",\"transportMode\",\"serviceScope\",\"destinationNode\"]}");
        row.setDiffRuleJson("{\"compareFields\":[\"leadTimeText\",\"leadTimeMinDays\",\"leadTimeMaxDays\",\"effectiveDate\"]}");
        row.setSortNo(31);
        return row;
    }

    private FileParseItemStandardRow cargoCategoryStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setId(3011L);
        row.setStandardVersionId(2003L);
        row.setItemType(FileParseLogisticsQuoteStandard.CARGO_CATEGORY);
        row.setItemLabel("物流货物分类");
        row.setNaturalKeyJson("{\"fields\":[\"forwarderCode\",\"serviceLineKey\",\"categoryCode\",\"categoryName\"]}");
        row.setFieldSchemaJson("{\"forwarderCode\":\"string\",\"serviceLineKey\":\"string\",\"categoryCode\":\"string\",\"categoryName\":\"string\",\"manualConfirmRequired\":\"boolean\"}");
        row.setDisplayConfigJson("{\"columns\":[\"serviceLineKey\",\"categoryCode\",\"categoryName\",\"productExamples\",\"electricType\",\"manualConfirmRequired\"]}");
        row.setValidationRuleJson("{\"required\":[\"forwarderCode\",\"serviceLineKey\",\"categoryCode\",\"categoryName\"]}");
        row.setDiffRuleJson("{\"compareFields\":[\"categoryName\",\"productExamples\",\"keywords\",\"electricType\",\"sensitiveTags\",\"packingPolicy\",\"manualConfirmRequired\"]}");
        row.setSortNo(32);
        return row;
    }

    private FileParseItemStandardRow basePriceStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setId(3012L);
        row.setStandardVersionId(2003L);
        row.setItemType(FileParseLogisticsQuoteStandard.BASE_PRICE);
        row.setItemLabel("物流基础价格");
        row.setNaturalKeyJson("{\"fields\":[\"forwarderCode\",\"serviceLineKey\",\"cargoCategoryKey\",\"pricingModel\",\"billingUnit\",\"currency\"]}");
        row.setFieldSchemaJson("{\"forwarderCode\":\"string\",\"serviceLineKey\":\"string\",\"cargoCategoryKey\":\"string\",\"unitPrice\":\"decimal\",\"currency\":\"string\",\"billingUnit\":\"string\",\"pricingModel\":\"string\",\"priceStatus\":\"string\"}");
        row.setDisplayConfigJson("{\"columns\":[\"serviceLineKey\",\"cargoCategoryKey\",\"unitPrice\",\"currency\",\"billingUnit\",\"pricingModel\",\"priceStatus\"]}");
        row.setValidationRuleJson("{\"required\":[\"forwarderCode\",\"serviceLineKey\",\"pricingModel\",\"billingUnit\",\"currency\",\"priceStatus\"]}");
        row.setDiffRuleJson("{\"compareFields\":[\"unitPrice\",\"currency\",\"billingUnit\",\"pricingModel\",\"minimumBillableUnit\",\"minimumBillableUnitType\",\"volumeDivisor\",\"seaWeightRatio\",\"roundingRule\",\"priceStatus\",\"effectiveDate\"]}");
        row.setSortNo(33);
        return row;
    }

    private FileParseItemStandardRow surchargeStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setId(3013L);
        row.setStandardVersionId(2003L);
        row.setItemType(FileParseLogisticsQuoteStandard.SURCHARGE);
        row.setItemLabel("物流附加费");
        row.setValidationRuleJson("{\"required\":[\"forwarderCode\",\"serviceLineKey\",\"surchargeName\",\"triggerCondition\"]}");
        row.setDiffRuleJson("{\"compareFields\":[\"surchargeType\",\"triggerCondition\",\"amount\",\"rate\",\"currency\",\"billingUnit\",\"includedInBasePrice\"]}");
        row.setSortNo(34);
        return row;
    }

    private FileParseItemStandardRow restrictionStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setId(3016L);
        row.setStandardVersionId(2003L);
        row.setItemType(FileParseLogisticsQuoteStandard.RESTRICTION);
        row.setItemLabel("物流禁限运与合规");
        row.setValidationRuleJson("{\"required\":[\"forwarderCode\",\"serviceLineKey\",\"restrictionType\",\"itemText\",\"requirementText\"]}");
        row.setDiffRuleJson("{\"compareFields\":[\"requirementText\",\"applicabilityScope\",\"severity\",\"manualConfirmRequired\"]}");
        row.setSortNo(37);
        return row;
    }

    private FileParseItemStandardRow warehouseServiceFeeStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setId(3015L);
        row.setStandardVersionId(2003L);
        row.setItemType(FileParseLogisticsQuoteStandard.WAREHOUSE_SERVICE_FEE);
        row.setItemLabel("海外仓服务费");
        row.setValidationRuleJson("{\"required\":[\"forwarderCode\",\"warehouseNode\",\"serviceName\",\"feeType\"]}");
        row.setDiffRuleJson("{\"compareFields\":[\"serviceType\",\"processingScope\",\"feeType\",\"amount\",\"rate\",\"currency\",\"billingUnit\",\"conditionText\",\"freeCondition\"]}");
        row.setSortNo(36);
        return row;
    }

    private FileParseItemStandardRow billingRuleStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setId(3014L);
        row.setStandardVersionId(2003L);
        row.setItemType(FileParseLogisticsQuoteStandard.BILLING_RULE);
        row.setItemLabel("物流计费规则");
        row.setValidationRuleJson("{\"required\":[\"forwarderCode\",\"serviceLineKey\",\"ruleName\",\"conditionText\",\"actionText\"]}");
        row.setDiffRuleJson("{\"compareFields\":[\"conditionText\",\"actionText\",\"operator\",\"thresholdValue\",\"thresholdUnit\",\"severity\"]}");
        row.setSortNo(35);
        return row;
    }

    private List<FileParseItemStandardRow> allLogisticsStandards() {
        return List.of(
                serviceLineStandard(),
                cargoCategoryStandard(),
                basePriceStandard(),
                surchargeStandard(),
                billingRuleStandard(),
                warehouseServiceFeeStandard(),
                restrictionStandard()
        );
    }
}
