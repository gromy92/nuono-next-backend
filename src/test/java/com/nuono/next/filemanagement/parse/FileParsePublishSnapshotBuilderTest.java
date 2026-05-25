package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FileParsePublishSnapshotBuilderTest {

    private final FileParsePublishSnapshotBuilder builder = new FileParsePublishSnapshotBuilder(
            new FileParseResultItemViewAssembler(new ObjectMapper())
    );

    @Test
    void shouldCarryForwardKeepOldRemoveDeleteSuspectedAndAppendConfirmedRows() {
        FileParseVersionItemRow deleteBase = baseItem(
                80001L,
                "义特-UAE-Dubai-SEA-card",
                "{\"channelKey\":\"yite-sea-card\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\"海运\",\"feeItem\":\"卡牌\",\"billingRule\":\"24 CNY/KG\"}",
                1
        );
        FileParseVersionItemRow keepBase = baseItem(
                80002L,
                "义特-UAE-Dubai-AIR-battery",
                "{\"channelKey\":\"yite-air-battery\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\"空运\",\"feeItem\":\"带电产品\",\"billingRule\":\"40 CNY/KG\"}",
                2
        );
        FileParseVersionItemRow carriedBase = baseItem(
                80003L,
                "义特-UAE-Dubai-SEA-general",
                "{\"channelKey\":\"yite-sea-general\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\"海运\",\"feeItem\":\"普货\",\"billingRule\":\"18 CNY/KG\"}",
                3
        );
        FileParseResultItemRow deleteItem = resultItem(
                50001L,
                "confirmed",
                "delete_suspected",
                deleteBase.getNaturalKey(),
                deleteBase.getVersionPayloadJson(),
                deleteBase.getVersionPayloadJson(),
                1
        );
        FileParseResultItemRow keepOldItem = resultItem(
                50002L,
                "keep_old",
                "changed",
                keepBase.getNaturalKey(),
                keepBase.getVersionPayloadJson(),
                "{\"channelKey\":\"yite-air-battery\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\"空运\",\"feeItem\":\"带电产品\",\"billingRule\":\"45 CNY/KG\"}",
                2
        );
        FileParseResultItemRow addedItem = resultItem(
                50003L,
                "confirmed",
                "added",
                "义特-UAE-Dubai-AIR-small",
                null,
                "{\"channelKey\":\"yite-air-small\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\"空运\",\"feeItem\":\"小件\",\"billingRule\":\"30 CNY/KG\"}",
                8
        );

        List<FileParsePublishSnapshotItem> snapshot = builder.buildSnapshot(
                List.of(deleteBase, keepBase, carriedBase),
                List.of(deleteItem, keepOldItem, addedItem),
                List.of(logisticsStandard())
        );

        assertEquals(List.of(
                "义特-UAE-Dubai-AIR-battery",
                "义特-UAE-Dubai-SEA-general",
                "义特-UAE-Dubai-AIR-small"
        ), snapshot.stream().map(FileParsePublishSnapshotItem::getNaturalKey).collect(Collectors.toList()));
        assertNull(snapshot.get(0).getSourceResultItemId());
        assertTrue(snapshot.get(0).getPayloadJson().contains("40 CNY/KG"));
        assertFalse(snapshot.get(0).getPayloadJson().contains("45 CNY/KG"));
        assertNull(snapshot.get(1).getSourceResultItemId());
        assertEquals(50003L, snapshot.get(2).getSourceResultItemId());
        assertEquals(8, snapshot.get(2).getSortNo());
    }

    @Test
    void shouldRejectPublishWhenActiveVersionChanged() {
        FileParseTaskRow task = new FileParseTaskRow();
        task.setBaseVersionId(70001L);
        FileParseActiveVersionRow activeVersion = new FileParseActiveVersionRow();
        activeVersion.setVersionId(70002L);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> builder.validateBaseVersion(task, activeVersion)
        );

        assertEquals("当前生效版本已变化，请重新解析或重新对比后再发布。", error.getMessage());
    }

    @Test
    void shouldNormalizeCargoCategoryRowsWhenBuildingSnapshot() {
        FileParseVersionItemRow base = baseItem(
                81001L,
                "et|ET KSA air cargo|A",
                "logistics_cargo_category",
                "{\"forwarderName\":\"ET\",\"serviceLineKey\":\"ET KSA air cargo\",\"categoryCode\":\"A类\",\"categoryName\":\"A类 普货\",\"manualConfirmRequired\":\"否\"}",
                1
        );

        List<FileParsePublishSnapshotItem> snapshot = builder.buildSnapshot(
                List.of(base),
                List.of(),
                List.of(cargoCategoryStandard())
        );

        assertEquals(1, snapshot.size());
        assertTrue(snapshot.get(0).getPayloadJson().contains("\"categoryCode\":\"A\""));
        assertTrue(snapshot.get(0).getPayloadJson().contains("\"manualConfirmRequired\":false"));
        assertFalse(snapshot.get(0).getPayloadJson().contains("A类\",\"categoryName"));
    }

    @Test
    void shouldPublishConfirmedBasePriceRowsAsStructuredSnapshotItems() {
        FileParseResultItemRow resultItem = resultItem(
                52001L,
                "confirmed",
                "added",
                "et|ET KSA air cargo|A|per_weight|kg|CNY",
                null,
                "logistics_base_price",
                "{\"forwarderName\":\"ET\",\"serviceLineKey\":\"ET KSA air cargo\",\"cargoCategoryKey\":\"A类\",\"unitPrice\":\"44 RMB/KG\",\"currency\":\"RMB\",\"billingUnit\":\"KG\",\"pricingModel\":\"按重量\",\"priceStatus\":\"正常\"}",
                4
        );

        List<FileParsePublishSnapshotItem> snapshot = builder.buildSnapshot(
                List.of(),
                List.of(resultItem),
                List.of(basePriceStandard())
        );

        assertEquals(1, snapshot.size());
        assertEquals("logistics_base_price", snapshot.get(0).getItemType());
        assertTrue(snapshot.get(0).getPayloadJson().contains("\"unitPrice\":44"));
        assertTrue(snapshot.get(0).getPayloadJson().contains("\"currency\":\"CNY\""));
        assertEquals(52001L, snapshot.get(0).getSourceResultItemId());
    }

    @Test
    void shouldPublishRemainingStructuredLogisticsRows() {
        List<FileParseResultItemRow> resultItems = List.of(
                resultItem(53001L, "confirmed", "added", "et|ET KSA air cargo|超长附加费|任一边超过120cm", null, "logistics_surcharge", "{\"forwarderName\":\"ET\",\"serviceLineKey\":\"ET KSA air cargo\",\"surchargeName\":\"超长附加费\",\"triggerCondition\":\"任一边超过120cm\",\"amount\":\"15 RMB\",\"billingUnit\":\"KG\",\"includedInBasePrice\":\"否\"}", 5),
                resultItem(53002L, "confirmed", "added", "et|ET KSA air cargo|单件重量限制|单件重量 > 30KG|需提前确认", null, "logistics_billing_rule", "{\"forwarderName\":\"ET\",\"serviceLineKey\":\"ET KSA air cargo\",\"ruleName\":\"单件重量限制\",\"conditionText\":\"单件重量 > 30KG\",\"actionText\":\"需提前确认\",\"severity\":\"warning\"}", 6),
                resultItem(53003L, "confirmed", "added", "et|Dubai warehouse|贴标费|per_piece", null, "logistics_warehouse_service_fee", "{\"forwarderName\":\"ET\",\"country\":\"UAE\",\"warehouseNode\":\"Dubai warehouse\",\"serviceName\":\"贴标费\",\"serviceType\":\"labeling\",\"feeType\":\"per piece\",\"amount\":\"0.5 AED\",\"billingUnit\":\"件\"}", 7),
                resultItem(53004L, "confirmed", "added", "et|ET KSA air cargo|prohibited|纯电池", null, "logistics_restriction", "{\"forwarderName\":\"ET\",\"serviceLineKey\":\"ET KSA air cargo\",\"restrictionType\":\"禁发\",\"itemText\":\"纯电池\",\"requirementText\":\"不可承运\",\"severity\":\"warning\"}", 8)
        );

        List<FileParsePublishSnapshotItem> snapshot = builder.buildSnapshot(
                List.of(),
                resultItems,
                List.of(surchargeStandard(), billingRuleStandard(), warehouseFeeStandard(), restrictionStandard())
        );

        assertEquals(List.of(
                "logistics_surcharge",
                "logistics_billing_rule",
                "logistics_warehouse_service_fee",
                "logistics_restriction"
        ), snapshot.stream().map(FileParsePublishSnapshotItem::getItemType).collect(Collectors.toList()));
        assertTrue(snapshot.get(0).getPayloadJson().contains("\"amount\":15"));
        assertTrue(snapshot.get(2).getPayloadJson().contains("\"feeType\":\"per_piece\""));
        assertTrue(snapshot.get(3).getPayloadJson().contains("\"restrictionType\":\"prohibited\""));
    }

    private FileParseVersionItemRow baseItem(Long id, String naturalKey, String payloadJson, Integer sortNo) {
        return baseItem(id, naturalKey, "logistics_channel_rule", payloadJson, sortNo);
    }

    private FileParseVersionItemRow baseItem(Long id, String naturalKey, String itemType, String payloadJson, Integer sortNo) {
        FileParseVersionItemRow row = new FileParseVersionItemRow();
        row.setId(id);
        row.setVersionId(70001L);
        row.setTargetPlanId(4005L);
        row.setItemType(itemType);
        row.setNaturalKey(naturalKey);
        row.setNaturalKeyHash("hash-" + id);
        row.setVersionPayloadJson(payloadJson);
        row.setSortNo(sortNo);
        return row;
    }

    private FileParseResultItemRow resultItem(
            Long id,
            String reviewStatus,
            String changeType,
            String naturalKey,
            String oldPayloadJson,
            String normalizedPayloadJson,
            Integer sortNo
    ) {
        return resultItem(id, reviewStatus, changeType, naturalKey, oldPayloadJson, "logistics_channel_rule", normalizedPayloadJson, sortNo);
    }

    private FileParseResultItemRow resultItem(
            Long id,
            String reviewStatus,
            String changeType,
            String naturalKey,
            String oldPayloadJson,
            String itemType,
            String normalizedPayloadJson,
            Integer sortNo
    ) {
        FileParseResultItemRow row = new FileParseResultItemRow();
        row.setId(id);
        row.setItemType(itemType);
        row.setNaturalKey(naturalKey);
        row.setNaturalKeyHash("hash-" + id);
        row.setReviewStatus(reviewStatus);
        row.setChangeType(changeType);
        row.setValidationStatus("pass");
        row.setOldPayloadJson(oldPayloadJson);
        row.setNormalizedPayloadJson(normalizedPayloadJson);
        row.setSortNo(sortNo);
        return row;
    }

    private FileParseItemStandardRow logisticsStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType("logistics_channel_rule");
        row.setValidationRuleJson("{\"required\":[\"channelKey\",\"country\",\"city\",\"shippingMethod\",\"feeItem\"]}");
        return row;
    }

    private FileParseItemStandardRow cargoCategoryStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType("logistics_cargo_category");
        row.setValidationRuleJson("{\"required\":[\"forwarderCode\",\"serviceLineKey\",\"categoryCode\",\"categoryName\"]}");
        return row;
    }

    private FileParseItemStandardRow basePriceStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType("logistics_base_price");
        row.setValidationRuleJson("{\"required\":[\"forwarderCode\",\"serviceLineKey\",\"pricingModel\",\"billingUnit\",\"currency\",\"priceStatus\"]}");
        return row;
    }

    private FileParseItemStandardRow surchargeStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType("logistics_surcharge");
        row.setValidationRuleJson("{\"required\":[\"forwarderCode\",\"serviceLineKey\",\"surchargeName\",\"triggerCondition\"]}");
        return row;
    }

    private FileParseItemStandardRow billingRuleStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType("logistics_billing_rule");
        row.setValidationRuleJson("{\"required\":[\"forwarderCode\",\"serviceLineKey\",\"ruleName\",\"conditionText\",\"actionText\"]}");
        return row;
    }

    private FileParseItemStandardRow warehouseFeeStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType("logistics_warehouse_service_fee");
        row.setValidationRuleJson("{\"required\":[\"forwarderCode\",\"warehouseNode\",\"serviceName\",\"feeType\"]}");
        return row;
    }

    private FileParseItemStandardRow restrictionStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType("logistics_restriction");
        row.setValidationRuleJson("{\"required\":[\"forwarderCode\",\"serviceLineKey\",\"restrictionType\",\"itemText\",\"requirementText\"]}");
        return row;
    }
}
