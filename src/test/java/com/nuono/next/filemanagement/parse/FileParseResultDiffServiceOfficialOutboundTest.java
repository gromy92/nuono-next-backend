package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileParseResultDiffServiceOfficialOutboundTest {

    @Mock
    private FileManagementParseMapper fileManagementParseMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDiffOfficialOutboundStructuredItemTypesIndependently() {
        FileParseTaskRow task = new FileParseTaskRow();
        task.setBaseVersionId(71002L);
        when(fileManagementParseMapper.selectVersionItems(71002L)).thenReturn(List.of(
                versionItem(FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION,
                        "{\"country\":\"KSA\",\"platform\":\"NOON\",\"fulfillmentType\":\"FBN\",\"classificationName\":\"Small Envelope\",\"longestSideMaxCm\":20,\"maxShippingWeightGrams\":250,\"packagingWeightGrams\":20,\"effectiveDate\":\"2026-05-24\"}"),
                versionItem(FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB,
                        "{\"country\":\"KSA\",\"platform\":\"NOON\",\"fulfillmentType\":\"FBN\",\"classificationName\":\"Small Envelope\",\"weightMinGrams\":0,\"weightMinInclusive\":true,\"weightMaxGrams\":250,\"weightMaxInclusive\":true,\"standardFeeAmount\":5.5,\"highAspFeeAmount\":7,\"currency\":\"SAR\",\"effectiveDate\":\"2026-05-24\"}"),
                versionItem(FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY,
                        "{\"country\":\"KSA\",\"platform\":\"NOON\",\"fulfillmentType\":\"FBN\",\"shippingWeightFormula\":\"physical_weight_plus_packaging_weight\",\"salesPriceThresholdAmount\":25,\"thresholdCurrency\":\"SAR\",\"effectiveDate\":\"2026-05-24\"}")
        ));

        FileParseStructuredAiResult result = new FileParseStructuredAiResult();
        FileParseStructuredItem classification = structuredItem(
                FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION,
                "{\"country\":\"KSA\",\"platform\":\"NOON\",\"fulfillmentType\":\"FBN\",\"classificationName\":\"Small Envelope\",\"longestSideMaxCm\":22,\"maxShippingWeightGrams\":250,\"packagingWeightGrams\":25,\"effectiveDate\":\"2026-05-24\"}"
        );
        FileParseStructuredItem slab = structuredItem(
                FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB,
                "{\"country\":\"KSA\",\"platform\":\"NOON\",\"fulfillmentType\":\"FBN\",\"classificationName\":\"Small Envelope\",\"weightMinGrams\":0,\"weightMinInclusive\":true,\"weightMaxGrams\":250,\"weightMaxInclusive\":true,\"standardFeeAmount\":6.25,\"highAspFeeAmount\":7.75,\"currency\":\"SAR\",\"effectiveDate\":\"2026-05-24\"}"
        );
        FileParseStructuredItem policy = structuredItem(
                FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY,
                "{\"country\":\"KSA\",\"platform\":\"NOON\",\"fulfillmentType\":\"FBN\",\"shippingWeightFormula\":\"physical_weight_plus_packaging_weight\",\"salesPriceThresholdAmount\":30,\"thresholdCurrency\":\"SAR\",\"effectiveDate\":\"2026-05-24\"}"
        );
        result.setItems(List.of(classification, slab, policy));

        new FileParseResultDiffService(fileManagementParseMapper, objectMapper)
                .applyDiff(task, List.of(
                        itemStandard(FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION),
                        itemStandard(FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB),
                        itemStandard(FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY)
                ), result);

        assertEquals("changed", classification.getChangeType());
        assertTrue(classification.getChangedFieldKeysJson().contains("longestSideMaxCm"));
        assertTrue(classification.getChangedFieldKeysJson().contains("packagingWeightGrams"));
        assertEquals("changed", slab.getChangeType());
        assertTrue(slab.getChangedFieldKeysJson().contains("standardFeeAmount"));
        assertTrue(slab.getChangedFieldKeysJson().contains("highAspFeeAmount"));
        assertEquals("changed", policy.getChangeType());
        assertTrue(policy.getChangedFieldKeysJson().contains("salesPriceThresholdAmount"));
    }

    private FileParseStructuredItem structuredItem(String itemType, String payloadJson) {
        FileParseStructuredItem item = new FileParseStructuredItem();
        item.setItemType(itemType);
        item.setValidationStatus("pass");
        item.setNormalizedPayloadJson(payloadJson);
        return item;
    }

    private FileParseVersionItemRow versionItem(String itemType, String payloadJson) {
        FileParseVersionItemRow row = new FileParseVersionItemRow();
        row.setId((long) payloadJson.hashCode());
        row.setVersionId(71002L);
        row.setItemType(itemType);
        row.setNaturalKeyHash("hash-" + Math.abs(payloadJson.hashCode()));
        row.setVersionPayloadJson(payloadJson);
        row.setSortNo(1);
        return row;
    }

    private FileParseItemStandardRow itemStandard(String itemType) {
        FileParseOfficialOutboundFeeStandard.ItemTypeDefinition definition =
                FileParseOfficialOutboundFeeStandard.definition(itemType);
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType(itemType);
        row.setDiffRuleJson("{\"compareFields\":[\"" + String.join("\",\"", definition.getCompareFields()) + "\"]}");
        return row;
    }
}
