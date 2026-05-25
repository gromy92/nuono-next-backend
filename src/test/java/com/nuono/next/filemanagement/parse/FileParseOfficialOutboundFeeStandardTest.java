package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FileParseOfficialOutboundFeeStandardTest {

    @Test
    void shouldDeclareOfficialOutboundFeeCalculationItemTypes() {
        List<String> itemTypes = FileParseOfficialOutboundFeeStandard.structuredItemTypeNames();

        assertEquals(
                List.of(
                        "outbound_size_classification_rule",
                        "outbound_fee_weight_slab_rule",
                        "outbound_fee_calculation_policy"
                ),
                itemTypes
        );
        assertTrue(FileParseOfficialOutboundFeeStandard.supportedItemTypeNames().contains("outbound_fee_rule"));
    }

    @Test
    void shouldProvideRequiredDisplayAndDiffMetadataForEachItemType() {
        for (FileParseOfficialOutboundFeeStandard.ItemTypeDefinition definition
                : FileParseOfficialOutboundFeeStandard.structuredItemTypes()) {
            assertNotNull(definition.getLabel(), definition.getItemType() + " label is required");
            assertFalse(definition.getNaturalKeyFields().isEmpty(), definition.getItemType() + " needs natural keys");
            assertFalse(definition.getFieldTypes().isEmpty(), definition.getItemType() + " needs field schema");
            assertFalse(definition.getDisplayColumns().isEmpty(), definition.getItemType() + " needs display columns");
            assertFalse(definition.getRequiredFields().isEmpty(), definition.getItemType() + " needs required fields");
            assertFalse(definition.getCompareFields().isEmpty(), definition.getItemType() + " needs diff fields");
        }

        FileParseOfficialOutboundFeeStandard.ItemTypeDefinition classification =
                FileParseOfficialOutboundFeeStandard.definition(FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION);
        assertEquals(
                List.of("country", "platform", "fulfillmentType", "classificationName"),
                classification.getRequiredFields()
        );
        assertTrue(classification.getDisplayColumns().contains("longestSideMaxCm"));
        assertTrue(classification.getCompareFields().contains("packagingWeightGrams"));

        FileParseOfficialOutboundFeeStandard.ItemTypeDefinition feeSlab =
                FileParseOfficialOutboundFeeStandard.definition(FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB);
        assertEquals("decimal", feeSlab.getFieldTypes().get("standardFeeAmount"));
        assertTrue(feeSlab.getRequiredFields().contains("weightMaxGrams"));
        assertTrue(feeSlab.getCompareFields().contains("highAspFeeAmount"));

        FileParseOfficialOutboundFeeStandard.ItemTypeDefinition policy =
                FileParseOfficialOutboundFeeStandard.definition(FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY);
        assertTrue(policy.getRequiredFields().contains("shippingWeightFormula"));
        assertTrue(policy.getCompareFields().contains("salesPriceThresholdAmount"));
    }

    @Test
    void officialOutboundSeedShouldExposeStructuredItemTypesAndKeepLegacyCompatibility() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "067_official_fbn_outbound_fee_facts.sql"
        ));

        for (String itemType : FileParseOfficialOutboundFeeStandard.structuredItemTypeNames()) {
            assertTrue(sql.contains("'" + itemType + "'"), "seed must include " + itemType);
        }

        assertTrue(sql.contains("'outbound_fee_rule'"), "seed must keep legacy outbound item compatibility");
        assertTrue(sql.contains("\"outbound_size_classification_rule\""), "standard result schema must include classification rules");
        assertTrue(sql.contains("\"outbound_fee_weight_slab_rule\""), "standard result schema must include fee slab rules");
        assertTrue(sql.contains("\"outbound_fee_calculation_policy\""), "standard result schema must include calculation policy");
    }

    @Test
    void itemTypeDefinitionsShouldHaveDistinctLabels() {
        Map<String, Long> labels = FileParseOfficialOutboundFeeStandard.structuredItemTypes()
                .stream()
                .collect(Collectors.groupingBy(
                        FileParseOfficialOutboundFeeStandard.ItemTypeDefinition::getLabel,
                        Collectors.counting()
                ));

        assertTrue(
                labels.values().stream().allMatch(count -> count == 1),
                "official outbound item labels should not be reused across item types"
        );
    }
}
