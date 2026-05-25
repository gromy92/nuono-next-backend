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

class FileParseLogisticsQuoteStandardTest {

    @Test
    void shouldDeclareStructuredLogisticsQuotePackageItemTypes() {
        List<String> itemTypes = FileParseLogisticsQuoteStandard.structuredItemTypeNames();

        assertEquals(
                List.of(
                        "logistics_service_line",
                        "logistics_cargo_category",
                        "logistics_base_price",
                        "logistics_surcharge",
                        "logistics_billing_rule",
                        "logistics_warehouse_service_fee",
                        "logistics_restriction"
                ),
                itemTypes
        );
        assertTrue(FileParseLogisticsQuoteStandard.supportedItemTypeNames().contains("logistics_channel_rule"));
    }

    @Test
    void shouldProvideRequiredDisplayAndDiffMetadataForEachStructuredItemType() {
        for (FileParseLogisticsQuoteStandard.ItemTypeDefinition definition
                : FileParseLogisticsQuoteStandard.structuredItemTypes()) {
            assertNotNull(definition.getLabel(), definition.getItemType() + " label is required");
            assertFalse(definition.getNaturalKeyFields().isEmpty(), definition.getItemType() + " needs natural keys");
            assertFalse(definition.getFieldTypes().isEmpty(), definition.getItemType() + " needs field schema");
            assertFalse(definition.getDisplayColumns().isEmpty(), definition.getItemType() + " needs display columns");
            assertFalse(definition.getRequiredFields().isEmpty(), definition.getItemType() + " needs required fields");
            assertFalse(definition.getCompareFields().isEmpty(), definition.getItemType() + " needs diff fields");
        }

        FileParseLogisticsQuoteStandard.ItemTypeDefinition serviceLine =
                FileParseLogisticsQuoteStandard.definition("logistics_service_line");
        assertEquals(
                List.of("forwarderCode", "country", "fulfillmentMode", "transportMode", "serviceScope", "destinationNode"),
                serviceLine.getRequiredFields()
        );
        assertTrue(serviceLine.getDisplayColumns().contains("leadTimeText"));

        FileParseLogisticsQuoteStandard.ItemTypeDefinition basePrice =
                FileParseLogisticsQuoteStandard.definition("logistics_base_price");
        assertEquals("decimal", basePrice.getFieldTypes().get("unitPrice"));
        assertTrue(basePrice.getCompareFields().contains("unitPrice"));
        assertTrue(basePrice.getCompareFields().contains("currency"));
    }

    @Test
    void logisticsUpgradeSeedShouldExposeYiteAndEtUnderOneStructuredStandard() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "055_file_management_logistics_output_structure.sql"
        ));

        for (String itemType : FileParseLogisticsQuoteStandard.structuredItemTypeNames()) {
            assertTrue(sql.contains("'" + itemType + "'"), "seed must include " + itemType);
        }

        assertTrue(sql.contains("'logistics_channel_rule'"), "seed must keep legacy logistics item compatibility");
        assertTrue(sql.contains("'logistics_yite'"), "seed must keep Yite target plan");
        assertTrue(sql.contains("'logistics_et'"), "seed must add ET target plan");
        assertTrue(sql.contains("\"yite\""), "seed must preserve Yite forwarder identity");
        assertTrue(sql.contains("\"et\""), "seed must preserve ET forwarder identity");
    }

    @Test
    void itemTypeDefinitionsShouldHaveDistinctLabels() {
        Map<String, Long> labels = FileParseLogisticsQuoteStandard.structuredItemTypes()
                .stream()
                .collect(Collectors.groupingBy(
                        FileParseLogisticsQuoteStandard.ItemTypeDefinition::getLabel,
                        Collectors.counting()
                ));

        assertTrue(
                labels.values().stream().allMatch(count -> count == 1),
                "structured logistics item labels should not be reused across item types"
        );
    }
}
