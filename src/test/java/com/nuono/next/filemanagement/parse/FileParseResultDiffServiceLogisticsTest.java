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
class FileParseResultDiffServiceLogisticsTest {

    @Mock
    private FileManagementParseMapper fileManagementParseMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldNormalizeCargoCategoryPayloadsBeforeFieldDiff() {
        FileParseTaskRow task = new FileParseTaskRow();
        task.setBaseVersionId(70001L);
        when(fileManagementParseMapper.selectVersionItems(70001L)).thenReturn(List.of(baseCargoCategory()));

        FileParseStructuredAiResult result = new FileParseStructuredAiResult();
        FileParseStructuredItem item = new FileParseStructuredItem();
        item.setItemType(FileParseLogisticsQuoteStandard.CARGO_CATEGORY);
        item.setNaturalKey("et|ET KSA air cargo|A");
        item.setNaturalKeyHash("new-hash");
        item.setValidationStatus("pass");
        item.setNormalizedPayloadJson("{\"forwarderName\":\"ET\",\"serviceLineKey\":\"ET KSA air cargo\",\"categoryCode\":\"A\",\"categoryName\":\"General cargo class A\",\"manualConfirmRequired\":false}");
        result.setItems(List.of(item));

        new FileParseResultDiffService(fileManagementParseMapper, objectMapper)
                .applyDiff(task, List.of(cargoCategoryStandard()), result);

        assertEquals("changed", item.getChangeType());
        assertTrue(item.getChangedFieldKeysJson().contains("categoryName"));
        assertTrue(item.getOldPayloadJson().contains("\"categoryCode\":\"A\""));
        assertTrue(item.getOldPayloadJson().contains("\"manualConfirmRequired\":false"));
    }

    @Test
    void shouldShowBasePriceNumericChangeAsFieldLevelDiff() {
        FileParseTaskRow task = new FileParseTaskRow();
        task.setBaseVersionId(70002L);
        when(fileManagementParseMapper.selectVersionItems(70002L)).thenReturn(List.of(basePrice()));

        FileParseStructuredAiResult result = new FileParseStructuredAiResult();
        FileParseStructuredItem item = new FileParseStructuredItem();
        item.setItemType(FileParseLogisticsQuoteStandard.BASE_PRICE);
        item.setNaturalKey("et|ET KSA air cargo|A|per_weight|kg|CNY");
        item.setNaturalKeyHash("new-price-hash");
        item.setValidationStatus("pass");
        item.setNormalizedPayloadJson("{\"forwarderName\":\"ET\",\"serviceLineKey\":\"ET KSA air cargo\",\"cargoCategoryKey\":\"A\",\"unitPrice\":\"46 CNY/KG\",\"currency\":\"CNY\",\"billingUnit\":\"KG\",\"pricingModel\":\"按重量\",\"priceStatus\":\"正常\"}");
        result.setItems(List.of(item));

        new FileParseResultDiffService(fileManagementParseMapper, objectMapper)
                .applyDiff(task, List.of(basePriceStandard()), result);

        assertEquals("changed", item.getChangeType());
        assertTrue(item.getChangedFieldKeysJson().contains("unitPrice"));
        assertTrue(item.getEffectivePayloadJson().contains("\"unitPrice\":46"));
    }

    @Test
    void shouldCoverIssue10LogisticsMutationFixtureMatrix() {
        FileParseTaskRow task = new FileParseTaskRow();
        task.setBaseVersionId(70010L);
        when(fileManagementParseMapper.selectVersionItems(70010L)).thenReturn(List.of(
                versionItem(FileParseLogisticsQuoteStandard.SERVICE_LINE,
                        "{\"forwarderCode\":\"et\",\"country\":\"KSA\",\"fulfillmentMode\":\"FBN\",\"transportMode\":\"cargo_air\",\"serviceScope\":\"warehouse_to_fbn\",\"destinationNode\":\"Riyadh FBN warehouse\",\"serviceLineKey\":\"ET KSA air cargo\",\"leadTimeText\":\"5-7 days\",\"leadTimeMinDays\":5,\"leadTimeMaxDays\":7}"),
                versionItem(FileParseLogisticsQuoteStandard.BASE_PRICE,
                        "{\"forwarderCode\":\"et\",\"serviceLineKey\":\"ET KSA air cargo\",\"cargoCategoryKey\":\"A\",\"unitPrice\":\"44 RMB/KG\",\"currency\":\"RMB\",\"billingUnit\":\"KG\",\"pricingModel\":\"按重量\",\"minimumBillableUnit\":\"10KG\",\"priceStatus\":\"正常\"}"),
                versionItem(FileParseLogisticsQuoteStandard.SURCHARGE,
                        "{\"forwarderCode\":\"et\",\"serviceLineKey\":\"ET KSA air cargo\",\"surchargeName\":\"超长附加费\",\"triggerCondition\":\"任一边超过120cm\",\"amount\":\"15 RMB\",\"billingUnit\":\"KG\",\"includedInBasePrice\":\"否\"}"),
                versionItem(FileParseLogisticsQuoteStandard.RESTRICTION,
                        "{\"forwarderCode\":\"et\",\"serviceLineKey\":\"ET KSA air cargo\",\"restrictionType\":\"禁发\",\"itemText\":\"纯电池\",\"requirementText\":\"不可承运\",\"severity\":\"warning\"}")
        ));

        FileParseStructuredAiResult result = new FileParseStructuredAiResult();
        FileParseStructuredItem leadTimeChanged = structuredItem(FileParseLogisticsQuoteStandard.SERVICE_LINE,
                "{\"forwarderCode\":\"et\",\"country\":\"KSA\",\"fulfillmentMode\":\"FBN\",\"transportMode\":\"cargo_air\",\"serviceScope\":\"warehouse_to_fbn\",\"destinationNode\":\"Riyadh FBN warehouse\",\"serviceLineKey\":\"ET KSA air cargo\",\"leadTimeText\":\"6-8 days\",\"leadTimeMinDays\":6,\"leadTimeMaxDays\":8}");
        FileParseStructuredItem minimumBillingChanged = structuredItem(FileParseLogisticsQuoteStandard.BASE_PRICE,
                "{\"forwarderCode\":\"et\",\"serviceLineKey\":\"ET KSA air cargo\",\"cargoCategoryKey\":\"A\",\"unitPrice\":\"46 RMB/KG\",\"currency\":\"RMB\",\"billingUnit\":\"KG\",\"pricingModel\":\"按重量\",\"minimumBillableUnit\":\"12KG\",\"priceStatus\":\"正常\"}");
        FileParseStructuredItem addedCategory = structuredItem(FileParseLogisticsQuoteStandard.CARGO_CATEGORY,
                "{\"forwarderCode\":\"et\",\"serviceLineKey\":\"ET KSA air cargo\",\"categoryCode\":\"B\",\"categoryName\":\"B类 敏感货\",\"keywords\":\"cosmetics, liquid\",\"manualConfirmRequired\":true}");
        FileParseStructuredItem surchargeChanged = structuredItem(FileParseLogisticsQuoteStandard.SURCHARGE,
                "{\"forwarderCode\":\"et\",\"serviceLineKey\":\"ET KSA air cargo\",\"surchargeName\":\"超长附加费\",\"triggerCondition\":\"任一边超过120cm\",\"amount\":\"20 RMB\",\"billingUnit\":\"KG\",\"includedInBasePrice\":\"否\"}");
        FileParseStructuredItem restrictionChanged = structuredItem(FileParseLogisticsQuoteStandard.RESTRICTION,
                "{\"forwarderCode\":\"et\",\"serviceLineKey\":\"ET KSA air cargo\",\"restrictionType\":\"禁发\",\"itemText\":\"纯电池\",\"requirementText\":\"不可承运，需改走专线\",\"severity\":\"hard\"}");
        result.setItems(List.of(leadTimeChanged, minimumBillingChanged, addedCategory, surchargeChanged, restrictionChanged));

        new FileParseResultDiffService(fileManagementParseMapper, objectMapper)
                .applyDiff(task, List.of(
                        serviceLineStandard(),
                        cargoCategoryStandard(),
                        basePriceStandard(),
                        surchargeStandard(),
                        restrictionStandard()
                ), result);

        assertEquals("changed", leadTimeChanged.getChangeType());
        assertTrue(leadTimeChanged.getChangedFieldKeysJson().contains("leadTimeText"));
        assertTrue(leadTimeChanged.getChangedFieldKeysJson().contains("leadTimeMinDays"));
        assertEquals("changed", minimumBillingChanged.getChangeType());
        assertTrue(minimumBillingChanged.getChangedFieldKeysJson().contains("unitPrice"));
        assertTrue(minimumBillingChanged.getChangedFieldKeysJson().contains("minimumBillableUnit"));
        assertEquals("added", addedCategory.getChangeType());
        assertEquals("changed", surchargeChanged.getChangeType());
        assertTrue(surchargeChanged.getChangedFieldKeysJson().contains("amount"));
        assertEquals("changed", restrictionChanged.getChangeType());
        assertTrue(restrictionChanged.getChangedFieldKeysJson().contains("requirementText"));
        assertTrue(restrictionChanged.getChangedFieldKeysJson().contains("severity"));
    }

    private FileParseVersionItemRow baseCargoCategory() {
        FileParseVersionItemRow row = new FileParseVersionItemRow();
        row.setId(81001L);
        row.setVersionId(70001L);
        row.setItemType(FileParseLogisticsQuoteStandard.CARGO_CATEGORY);
        row.setNaturalKey("et|ET KSA air cargo|A");
        row.setNaturalKeyHash("base-hash");
        row.setVersionPayloadJson("{\"forwarderName\":\"ET\",\"serviceLineKey\":\"ET KSA air cargo\",\"categoryCode\":\"A类\",\"categoryName\":\"A类 普货\",\"manualConfirmRequired\":\"否\"}");
        row.setSortNo(1);
        return row;
    }

    private FileParseVersionItemRow basePrice() {
        FileParseVersionItemRow row = new FileParseVersionItemRow();
        row.setId(82001L);
        row.setVersionId(70002L);
        row.setItemType(FileParseLogisticsQuoteStandard.BASE_PRICE);
        row.setNaturalKey("et|ET KSA air cargo|A|per_weight|kg|CNY");
        row.setNaturalKeyHash("base-price-hash");
        row.setVersionPayloadJson("{\"forwarderName\":\"ET\",\"serviceLineKey\":\"ET KSA air cargo\",\"cargoCategoryKey\":\"A类\",\"unitPrice\":\"44 RMB/KG\",\"currency\":\"RMB\",\"billingUnit\":\"KG\",\"pricingModel\":\"按重量\",\"priceStatus\":\"正常\"}");
        row.setSortNo(1);
        return row;
    }

    private FileParseItemStandardRow cargoCategoryStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType(FileParseLogisticsQuoteStandard.CARGO_CATEGORY);
        row.setDiffRuleJson("{\"compareFields\":[\"categoryName\",\"productExamples\",\"keywords\",\"electricType\",\"sensitiveTags\",\"packingPolicy\",\"manualConfirmRequired\"]}");
        return row;
    }

    private FileParseItemStandardRow basePriceStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType(FileParseLogisticsQuoteStandard.BASE_PRICE);
        row.setDiffRuleJson("{\"compareFields\":[\"unitPrice\",\"currency\",\"billingUnit\",\"pricingModel\",\"minimumBillableUnit\",\"minimumBillableUnitType\",\"volumeDivisor\",\"seaWeightRatio\",\"roundingRule\",\"priceStatus\",\"effectiveDate\"]}");
        return row;
    }

    private FileParseItemStandardRow serviceLineStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType(FileParseLogisticsQuoteStandard.SERVICE_LINE);
        row.setDiffRuleJson("{\"compareFields\":[\"destinationNode\",\"transportMode\",\"serviceScope\",\"departureFrequency\",\"leadTimeText\",\"leadTimeMinDays\",\"leadTimeMaxDays\",\"effectiveDate\"]}");
        return row;
    }

    private FileParseItemStandardRow surchargeStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType(FileParseLogisticsQuoteStandard.SURCHARGE);
        row.setDiffRuleJson("{\"compareFields\":[\"triggerCondition\",\"amount\",\"rate\",\"billingUnit\",\"includedInBasePrice\"]}");
        return row;
    }

    private FileParseItemStandardRow restrictionStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType(FileParseLogisticsQuoteStandard.RESTRICTION);
        row.setDiffRuleJson("{\"compareFields\":[\"restrictionType\",\"itemText\",\"requirementText\",\"severity\"]}");
        return row;
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
        row.setVersionId(70010L);
        row.setItemType(itemType);
        row.setNaturalKeyHash("hash-" + Math.abs(payloadJson.hashCode()));
        row.setVersionPayloadJson(payloadJson);
        row.setSortNo(1);
        return row;
    }
}
