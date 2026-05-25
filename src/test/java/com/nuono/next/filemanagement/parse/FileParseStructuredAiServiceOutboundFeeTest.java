package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileParseStructuredAiServiceOutboundFeeTest {

    @Mock
    private AiCapabilityService aiCapabilityService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRejectReferralFeesAndPercentAmountsForOutboundFeeResults() {
        FileParseStructuredAiService service = new FileParseStructuredAiService(aiCapabilityService, objectMapper);
        when(aiCapabilityService.createStructuredText(any())).thenReturn(aiResultWithReferralFeeAsOutbound());

        FileParseStructuredAiResult result = service.parse(
                task(),
                targetPlan("outbound_fee_ksa"),
                standardVersion(),
                List.of(outboundFeeStandard()),
                "[[SOURCE_ROW_ID=91001;TYPE=pdf_text_line;LOC=page=1;line=5]] Referral Fees Fashion All 27% SAR",
                10001L
        );

        FileParseStructuredItem item = result.getItems().get(0);

        assertEquals("hard_error", item.getValidationStatus());
        assertTrue(item.getValidationErrorJson().contains("outboundFeeSection"));
        assertTrue(item.getValidationErrorJson().contains("feeAmount"));
    }

    @Test
    void shouldTellAiToParseOnlyOutboundFeesForOutboundTargetPlan() {
        FileParseStructuredAiService service = new FileParseStructuredAiService(aiCapabilityService, objectMapper);
        when(aiCapabilityService.createStructuredText(any())).thenReturn(aiResultWithValidOutboundFee());

        service.parse(
                task(),
                targetPlan("outbound_fee_ksa"),
                standardVersion(),
                List.of(outboundFeeStandard()),
                "[[SOURCE_ROW_ID=91002;TYPE=pdf_text_line;LOC=page=2;line=8]] Small Envelope 7 SAR",
                10001L
        );

        ArgumentCaptor<AiStructuredTextCommand> command = ArgumentCaptor.forClass(AiStructuredTextCommand.class);
        verify(aiCapabilityService).createStructuredText(command.capture());

        assertTrue(command.getValue().getInstructions().contains("只解析 FBN Outbound fees"));
        assertTrue(command.getValue().getInstructions().contains("不要解析 Referral Fees"));
    }

    @Test
    void shouldTellAiToUseStructuredOutboundFactTypesForOfficialOutboundPlan() {
        FileParseStructuredAiService service = new FileParseStructuredAiService(aiCapabilityService, objectMapper);
        when(aiCapabilityService.createStructuredText(any())).thenReturn(aiResult(structuredClassificationPayload(), 92001L,
                FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION));

        service.parse(
                task(),
                targetPlan("outbound_fee_ksa"),
                standardVersion(),
                structuredOutboundStandards(),
                "[[SOURCE_ROW_ID=92001;TYPE=pdf_text_line;LOC=page=2;line=8]] Small Envelope 7 SAR",
                10001L
        );

        ArgumentCaptor<AiStructuredTextCommand> command = ArgumentCaptor.forClass(AiStructuredTextCommand.class);
        verify(aiCapabilityService).createStructuredText(command.capture());

        assertTrue(command.getValue().getInstructions().contains("outbound_size_classification_rule"));
        assertTrue(command.getValue().getInstructions().contains("outbound_fee_weight_slab_rule"));
        assertTrue(command.getValue().getInstructions().contains("outbound_fee_calculation_policy"));
        assertTrue(command.getValue().getInstructions().contains("不要解析 Referral Fees"));
    }

    @Test
    void shouldNormalizeStructuredOutboundFieldsAndKeepManualSupplementLineage() throws Exception {
        FileParseStructuredAiService service = new FileParseStructuredAiService(aiCapabilityService, objectMapper);
        Map<String, Object> payload = structuredClassificationPayload();
        payload.put("country", "Saudi Arabia");
        payload.put("platform", "Noon");
        payload.put("fulfillmentType", "Fulfilled by Noon");
        payload.put("longestSideMaxCm", "20 cm");
        payload.put("medianSideMaxCm", "15cm");
        payload.put("shortestSideMaxCm", "2 centimeters");
        payload.put("maxShippingWeightGrams", "250 g");
        payload.put("packagingWeightGrams", "20 grams");
        payload.put("dimensionUnit", "centimeters");
        payload.put("weightUnit", "g");
        payload.put("effectiveDate", "Applicable from 24 May 2026");
        when(aiCapabilityService.createStructuredText(any())).thenReturn(
                aiResult(payload, List.of(92001L, 92002L), FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION)
        );

        FileParseStructuredAiResult result = service.parse(
                task(),
                targetPlan("outbound_fee_ksa"),
                standardVersion(),
                structuredOutboundStandards(),
                "[[SOURCE_ROW_ID=92001;TYPE=pdf_text_line;LOC=page=2;line=8]] Small Envelope max shipping weight 250 g\n"
                        + "[[SOURCE_ROW_ID=92002;TYPE=manual_text_block;LOC=manual]] KSA Small Envelope packaging weight should be 20 grams.",
                10001L
        );

        FileParseStructuredItem item = result.getItems().get(0);
        Map<String, Object> normalized = objectMapper.readValue(item.getNormalizedPayloadJson(), Map.class);

        assertEquals("KSA", normalized.get("country"));
        assertEquals("NOON", normalized.get("platform"));
        assertEquals("FBN", normalized.get("fulfillmentType"));
        assertEquals("20", String.valueOf(normalized.get("longestSideMaxCm")));
        assertEquals("250", String.valueOf(normalized.get("maxShippingWeightGrams")));
        assertEquals("20", String.valueOf(normalized.get("packagingWeightGrams")));
        assertEquals("cm", normalized.get("dimensionUnit"));
        assertEquals("grams", normalized.get("weightUnit"));
        assertEquals("2026-05-24", normalized.get("effectiveDate"));
        assertEquals(List.of(92001L, 92002L), item.getSourceRowIds());
        assertTrue(item.getEvidenceJson().contains("92002"));
    }

    @Test
    void shouldRaiseHardErrorsForIncompleteStructuredOutboundFacts() {
        FileParseStructuredAiService service = new FileParseStructuredAiService(aiCapabilityService, objectMapper);
        when(aiCapabilityService.createStructuredText(any())).thenReturn(aiResultWithIncompleteStructuredOutboundFacts());

        FileParseStructuredAiResult result = service.parse(
                task(),
                targetPlan("outbound_fee_ksa"),
                standardVersion(),
                structuredOutboundStandards(),
                "[[SOURCE_ROW_ID=93001;TYPE=pdf_text_line;LOC=page=2;line=8]] incomplete outbound table",
                10001L
        );

        FileParseStructuredItem classification = result.getItems().get(0);
        FileParseStructuredItem slab = result.getItems().get(1);
        FileParseStructuredItem policy = result.getItems().get(2);

        assertEquals("hard_error", classification.getValidationStatus());
        assertTrue(classification.getValidationErrorJson().contains("classificationBoundary"));
        assertTrue(classification.getValidationErrorJson().contains("maxShippingWeightGrams"));
        assertEquals("hard_error", slab.getValidationStatus());
        assertTrue(slab.getValidationErrorJson().contains("weightRange"));
        assertTrue(slab.getValidationErrorJson().contains("standardFeeAmount"));
        assertTrue(slab.getValidationErrorJson().contains("currency"));
        assertEquals("hard_error", policy.getValidationStatus());
        assertTrue(policy.getValidationErrorJson().contains("shippingWeightFormula"));
    }

    @Test
    void shouldStabilizeLiveKsaPdfOutboundRowsFromSourceContext() throws Exception {
        FileParseStructuredAiService service = new FileParseStructuredAiService(aiCapabilityService, objectMapper);
        when(aiCapabilityService.createStructuredText(any())).thenReturn(aiResultWithLiveKsaPdfPartialOutput());

        FileParseStructuredAiResult result = service.parse(
                task(),
                targetPlan("outbound_fee_ksa"),
                standardVersion(),
                liveOutboundStandards(),
                liveKsaOutboundPdfExcerpt(),
                10001L
        );

        assertFalse(result.getItems().stream().anyMatch(item -> "outbound_fee_rule".equals(item.getItemType())));
        assertFalse(result.getItems().stream().anyMatch(item -> "hard_error".equals(item.getValidationStatus())));
        assertFalse(result.getItems().stream().anyMatch(item ->
                FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB.equals(item.getItemType())
                        && item.getNaturalKey() != null
                        && item.getNaturalKey().contains("MIN:*")
        ));

        FileParseStructuredItem smallEnvelope = findItem(
                result.getItems(),
                FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION,
                "Small envelope"
        );
        Map<String, Object> smallEnvelopePayload = objectMapper.readValue(smallEnvelope.getNormalizedPayloadJson(), Map.class);
        assertEquals("NOON", smallEnvelopePayload.get("platform"));
        assertEquals("20", String.valueOf(smallEnvelopePayload.get("longestSideMaxCm")));
        assertEquals("15", String.valueOf(smallEnvelopePayload.get("medianSideMaxCm")));
        assertEquals("1", String.valueOf(smallEnvelopePayload.get("shortestSideMaxCm")));
        assertEquals("100", String.valueOf(smallEnvelopePayload.get("maxShippingWeightGrams")));
        assertEquals("20", String.valueOf(smallEnvelopePayload.get("packagingWeightGrams")));

        FileParseStructuredItem bulkySlab = findItem(
                result.getItems(),
                FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB,
                "Bulky|MIN:35000:0|MAX:40000:1|CUR:SAR|2025-09-01"
        );
        Map<String, Object> bulkyPayload = objectMapper.readValue(bulkySlab.getNormalizedPayloadJson(), Map.class);
        assertEquals("NOON", bulkyPayload.get("platform"));
        assertEquals("70", String.valueOf(bulkyPayload.get("standardFeeAmount")));
        assertEquals("75", String.valueOf(bulkyPayload.get("highAspFeeAmount")));

        FileParseStructuredItem policy = findItem(
                result.getItems(),
                FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY,
                "KSA|NOON|FBN|2025-09-01"
        );
        Map<String, Object> policyPayload = objectMapper.readValue(policy.getNormalizedPayloadJson(), Map.class);
        assertEquals("physical_weight_plus_packaging_weight", policyPayload.get("shippingWeightFormula"));
        assertEquals("25", String.valueOf(policyPayload.get("salesPriceThresholdAmount")));
        assertEquals("SAR", policyPayload.get("thresholdCurrency"));
    }

    @Test
    void shouldDefaultLiveKsaOutboundEffectiveDateWhenChunkContextStartsAtOutboundSection() throws Exception {
        FileParseStructuredAiService service = new FileParseStructuredAiService(aiCapabilityService, objectMapper);
        when(aiCapabilityService.createStructuredText(any())).thenReturn(aiResultWithLiveKsaPdfPartialOutput());

        FileParseStructuredAiResult result = service.parse(
                task(),
                targetPlan("outbound_fee_ksa"),
                standardVersion(),
                liveOutboundStandards(),
                liveKsaOutboundPdfExcerptWithoutDateHeader(),
                10001L
        );

        FileParseStructuredItem smallEnvelope = findItem(
                result.getItems(),
                FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION,
                "Small envelope"
        );
        Map<String, Object> payload = objectMapper.readValue(smallEnvelope.getNormalizedPayloadJson(), Map.class);

        assertEquals("2025-09-01", payload.get("effectiveDate"));
        assertTrue(smallEnvelope.getNaturalKey().contains("2025-09-01"));
    }


    private AiStructuredTextResult aiResultWithReferralFeeAsOutbound() {
        return aiResult(outboundPayload("KSA", "Referral Fees", "Fashion", "27%", "SAR"), 91001L);
    }

    private AiStructuredTextResult aiResultWithValidOutboundFee() {
        return aiResult(outboundPayload("KSA", "FBN Outbound Fee", "Small Envelope", "7", "SAR"), 91002L);
    }

    private AiStructuredTextResult aiResultWithIncompleteStructuredOutboundFacts() {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");
        result.setParsedJson(Map.of("summary", Map.of("source", "test"), "items", List.of(
                item(FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION, Map.of(
                        "country", "KSA",
                        "platform", "NOON",
                        "fulfillmentType", "FBN",
                        "classificationName", "Small Envelope"
                ), List.of(93001L)),
                item(FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB, Map.of(
                        "country", "KSA",
                        "platform", "NOON",
                        "fulfillmentType", "FBN",
                        "classificationName", "Small Envelope",
                        "weightMinGrams", 0
                ), List.of(93001L)),
                item(FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY, Map.of(
                        "country", "KSA",
                        "platform", "NOON",
                        "fulfillmentType", "FBN"
                ), List.of(93001L))
        )));
        return result;
    }

    private AiStructuredTextResult aiResultWithLiveKsaPdfPartialOutput() {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");
        result.setParsedJson(Map.of("summary", Map.of("source", "live-ksa-pdf"), "items", List.of(
                item("outbound_fee_rule", Map.of(
                        "country", "KSA",
                        "feeItem", "FBN Outbound fee per unit",
                        "sizeTier", "Small envelope",
                        "currency", "SAR",
                        "fulfillmentType", "FBN"
                ), List.of(94001L)),
                item(FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION, Map.of(
                        "country", "KSA",
                        "platform", "AMAZON",
                        "fulfillmentType", "FBN",
                        "classificationName", "Small envelope",
                        "dimensionUnit", "cm",
                        "weightUnit", "kg"
                ), List.of(94003L)),
                item(FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB, livePartialStandardParcelSlab(), List.of(94013L)),
                item(FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB, liveAiResidualSmallEnvelopeSlab(), List.of(94003L))
        )));
        return result;
    }

    private Map<String, Object> livePartialStandardParcelSlab() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("country", "KSA");
        payload.put("platform", "AMAZON");
        payload.put("fulfillmentType", "FBN");
        payload.put("classificationName", "Standard parcel");
        payload.put("weightMinGrams", 2000);
        payload.put("weightMinInclusive", false);
        payload.put("weightMaxGrams", 3000);
        payload.put("weightMaxInclusive", true);
        payload.put("standardFeeAmount", 10);
        payload.put("highAspFeeAmount", 13);
        payload.put("salesPriceThresholdAmount", 25);
        payload.put("thresholdCurrency", "SAR");
        payload.put("extraWeightStepGrams", 1000);
        payload.put("extraFeeAmount", 1);
        return payload;
    }

    private Map<String, Object> liveAiResidualSmallEnvelopeSlab() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("country", "KSA");
        payload.put("platform", "NOON");
        payload.put("fulfillmentType", "FBN");
        payload.put("classificationName", "Small envelope");
        payload.put("weightMinInclusive", true);
        payload.put("weightMaxInclusive", true);
        payload.put("standardFeeAmount", 5.5);
        payload.put("highAspFeeAmount", 7.5);
        payload.put("currency", "SAR");
        return payload;
    }

    private AiStructuredTextResult aiResult(Map<String, Object> payload, Long sourceRowId) {
        return aiResult(payload, sourceRowId, "outbound_fee_rule");
    }

    private AiStructuredTextResult aiResult(Map<String, Object> payload, Long sourceRowId, String itemType) {
        return aiResult(payload, List.of(sourceRowId), itemType);
    }

    private AiStructuredTextResult aiResult(Map<String, Object> payload, List<Long> sourceRowIds, String itemType) {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");
        result.setParsedJson(Map.of("summary", Map.of("source", "test"), "items", List.of(item(itemType, payload, sourceRowIds))));
        return result;
    }

    private Map<String, Object> item(String itemType, Map<String, Object> payload, List<Long> sourceRowIds) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemType", itemType);
        item.put("payload", payload);
        item.put("confidence", "high");
        item.put("sourceRowIds", sourceRowIds);
        return item;
    }

    private Map<String, Object> outboundPayload(
            String country,
            String feeItem,
            String sizeTier,
            String feeAmount,
            String currency
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("country", country);
        payload.put("feeItem", feeItem);
        payload.put("sizeTier", sizeTier);
        payload.put("feeAmount", feeAmount);
        payload.put("currency", currency);
        payload.put("effectiveDate", "2025-08");
        return payload;
    }

    private Map<String, Object> structuredClassificationPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("country", "KSA");
        payload.put("platform", "NOON");
        payload.put("fulfillmentType", "FBN");
        payload.put("classificationName", "Small Envelope");
        payload.put("longestSideMaxCm", 20);
        payload.put("medianSideMaxCm", 15);
        payload.put("shortestSideMaxCm", 2);
        payload.put("maxShippingWeightGrams", 250);
        payload.put("packagingWeightGrams", 20);
        payload.put("effectiveDate", "2026-05-24");
        return payload;
    }

    private FileParseTaskRow task() {
        FileParseTaskRow row = new FileParseTaskRow();
        row.setId(20114L);
        row.setDocumentTitle("FBN outbound test");
        return row;
    }

    private FileParseTargetPlanRow targetPlan(String code) {
        FileParseTargetPlanRow row = new FileParseTargetPlanRow();
        row.setId(4003L);
        row.setCode(code);
        row.setLabel("出仓费-KSA");
        row.setDocumentType("official_outbound_fee");
        return row;
    }

    private FileParseStandardVersionRow standardVersion() {
        FileParseStandardVersionRow row = new FileParseStandardVersionRow();
        row.setId(2002L);
        row.setStandardVersion("STD-2026.05");
        return row;
    }

    private FileParseItemStandardRow outboundFeeStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setId(3002L);
        row.setItemType("outbound_fee_rule");
        row.setItemLabel("出仓费规则");
        row.setNaturalKeyJson("{\"fields\":[\"country\",\"feeItem\",\"sizeTier\"]}");
        row.setFieldSchemaJson("{\"country\":\"string\",\"feeItem\":\"string\",\"sizeTier\":\"string\",\"feeAmount\":\"decimal\",\"currency\":\"string\",\"minFee\":\"decimal\",\"effectiveDate\":\"date\"}");
        row.setValidationRuleJson("{\"required\":[\"country\",\"feeItem\",\"feeAmount\",\"currency\"]}");
        return row;
    }

    private List<FileParseItemStandardRow> structuredOutboundStandards() {
        return FileParseOfficialOutboundFeeStandard.structuredItemTypes()
                .stream()
                .map(this::structuredOutboundStandard)
                .collect(Collectors.toList());
    }

    private List<FileParseItemStandardRow> liveOutboundStandards() {
        List<FileParseItemStandardRow> standards = new java.util.ArrayList<>();
        standards.add(outboundFeeStandard());
        standards.addAll(structuredOutboundStandards());
        return standards;
    }

    private FileParseItemStandardRow structuredOutboundStandard(FileParseOfficialOutboundFeeStandard.ItemTypeDefinition definition) {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType(definition.getItemType());
        row.setItemLabel(definition.getLabel());
        row.setNaturalKeyJson(writeJson(Map.of("fields", definition.getNaturalKeyFields())));
        row.setFieldSchemaJson(writeJson(definition.getFieldTypes()));
        row.setValidationRuleJson(writeJson(Map.of("required", definition.getRequiredFields())));
        row.setDiffRuleJson(writeJson(Map.of("compareFields", definition.getCompareFields())));
        return row;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private FileParseStructuredItem findItem(List<FileParseStructuredItem> items, String itemType, String naturalKeyPart) {
        return items.stream()
                .filter(item -> itemType.equals(item.getItemType()))
                .filter(item -> item.getNaturalKey() != null && item.getNaturalKey().contains(naturalKeyPart))
                .findFirst()
                .orElseThrow();
    }

    private String liveKsaOutboundPdfExcerpt() {
        return String.join("\n",
                "[[SOURCE_ROW_ID=94000;TYPE=pdf_text_line;LOC=page=1;line=7]]",
                "Starting  1st September 2025 the following fees will apply to all your Fulfilled by noon (FBN) items.",
                "[[SOURCE_ROW_ID=94001;TYPE=pdf_text_line;LOC=page=6;line=1]]",
                "2. FBN Outbound fees",
                "[[SOURCE_ROW_ID=94002;TYPE=pdf_text_line;LOC=page=6;line=2]]",
                "Size Tier Weight Slab",
                "[[SOURCE_ROW_ID=94003;TYPE=pdf_text_line;LOC=page=6;line=6]]",
                "Small envelope One rate 5.5 7.5",
                "[[SOURCE_ROW_ID=94004;TYPE=pdf_text_line;LOC=page=6;line=7]]",
                "Standard",
                "[[SOURCE_ROW_ID=94005;TYPE=pdf_text_line;LOC=page=6;line=8]]",
                "envelope",
                "[[SOURCE_ROW_ID=94006;TYPE=pdf_text_line;LOC=page=6;line=9]]",
                "<=0.1Kg 6.0 8.0",
                "[[SOURCE_ROW_ID=94007;TYPE=pdf_text_line;LOC=page=6;line=10]]",
                "<=0.25 Kg 6.0 8.0",
                "[[SOURCE_ROW_ID=94008;TYPE=pdf_text_line;LOC=page=6;line=11]]",
                "<=0.50 Kg 6.5 8.5",
                "[[SOURCE_ROW_ID=94009;TYPE=pdf_text_line;LOC=page=6;line=12]]",
                "Large envelope One rate 7.0 9.0",
                "[[SOURCE_ROW_ID=94010;TYPE=pdf_text_line;LOC=page=6;line=13]]",
                "Standard",
                "[[SOURCE_ROW_ID=94011;TYPE=pdf_text_line;LOC=page=6;line=14]]",
                "parcel",
                "[[SOURCE_ROW_ID=94012;TYPE=pdf_text_line;LOC=page=6;line=15]]",
                "<=0.25 Kg 7.0 9.0",
                "[[SOURCE_ROW_ID=94013;TYPE=pdf_text_line;LOC=page=6;line=16]]",
                ">2.0 Kg & <=3.0Kg 10.0 13.0",
                "[[SOURCE_ROW_ID=94014;TYPE=pdf_text_line;LOC=page=6;line=17]]",
                "Each additional 1Kg till",
                "[[SOURCE_ROW_ID=94015;TYPE=pdf_text_line;LOC=page=6;line=18]]",
                "12 Kg Additional 1 SAR per Kg Additional 1 SAR per Kg",
                "[[SOURCE_ROW_ID=94016;TYPE=pdf_text_line;LOC=page=6;line=19]]",
                "Oversize parcel",
                "[[SOURCE_ROW_ID=94017;TYPE=pdf_text_line;LOC=page=6;line=20]]",
                "<=1.0 Kg 10.0 14.0",
                "[[SOURCE_ROW_ID=94018;TYPE=pdf_text_line;LOC=page=6;line=21]]",
                "Extra oversize <=4.0 Kg 15.5 18.5",
                "[[SOURCE_ROW_ID=94019;TYPE=pdf_text_line;LOC=page=7;line=1]]",
                "Bulky",
                "[[SOURCE_ROW_ID=94020;TYPE=pdf_text_line;LOC=page=7;line=2]]",
                "<=20 Kg 33 37",
                "[[SOURCE_ROW_ID=94021;TYPE=pdf_text_line;LOC=page=7;line=3]]",
                ">35Kg & <=40 Kg 70 75",
                "[[SOURCE_ROW_ID=94022;TYPE=pdf_text_line;LOC=page=7;line=4]]",
                "40kg> Additional 10 SAR per 5 Kg Additional 10 SAR per 5 Kg",
                "[[SOURCE_ROW_ID=94023;TYPE=pdf_text_line;LOC=page=7;line=5]]",
                "3. Monthly Storage Fees"
        );
    }

    private String liveKsaOutboundPdfExcerptWithoutDateHeader() {
        return String.join("\n",
                "[[SOURCE_ROW_ID=94001;TYPE=pdf_text_line;LOC=page=6;line=1]]",
                "2. FBN Outbound fees",
                "[[SOURCE_ROW_ID=94002;TYPE=pdf_text_line;LOC=page=6;line=2]]",
                "Size Tier Weight Slab",
                "[[SOURCE_ROW_ID=94003;TYPE=pdf_text_line;LOC=page=6;line=6]]",
                "Small envelope One rate 5.5 7.5",
                "[[SOURCE_ROW_ID=94019;TYPE=pdf_text_line;LOC=page=7;line=1]]",
                "Bulky",
                "[[SOURCE_ROW_ID=94020;TYPE=pdf_text_line;LOC=page=7;line=2]]",
                "<=20 Kg 33 37",
                "[[SOURCE_ROW_ID=94021;TYPE=pdf_text_line;LOC=page=7;line=3]]",
                ">35Kg & <=40 Kg 70 75",
                "[[SOURCE_ROW_ID=94022;TYPE=pdf_text_line;LOC=page=7;line=4]]",
                "40kg> Additional 10 SAR per 5 Kg Additional 10 SAR per 5 Kg",
                "[[SOURCE_ROW_ID=94023;TYPE=pdf_text_line;LOC=page=7;line=5]]",
                "3. Monthly Storage Fees"
        );
    }
}
