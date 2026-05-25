package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FileParseResultItemViewAssemblerTest {

    private final FileParseResultItemViewAssembler assembler = new FileParseResultItemViewAssembler(new ObjectMapper());

    @Test
    void shouldBuildStructuredLogisticsColumnsAcrossItemTypesInsteadOfLegacyOnly() {
        List<FileParseProcessingColumnView> columns = assembler.buildColumns(List.of(
                itemStandard(
                        FileParseLogisticsQuoteStandard.LEGACY_CHANNEL_RULE,
                        "{\"channelKey\":\"string\",\"country\":\"string\",\"city\":\"string\",\"shippingMethod\":\"string\",\"feeItem\":\"string\",\"billingRule\":\"string\",\"leadTime\":\"string\"}",
                        "{\"columns\":[\"channelKey\",\"country\",\"city\",\"shippingMethod\",\"feeItem\",\"billingRule\",\"leadTime\"]}",
                        30
                ),
                itemStandard(
                        FileParseLogisticsQuoteStandard.SERVICE_LINE,
                        "{\"forwarderName\":\"string\",\"country\":\"string\",\"transportMode\":\"string\",\"leadTimeText\":\"string\"}",
                        "{\"columns\":[\"forwarderName\",\"country\",\"transportMode\",\"leadTimeText\"]}",
                        31
                ),
                itemStandard(
                        FileParseLogisticsQuoteStandard.BASE_PRICE,
                        "{\"serviceLineKey\":\"string\",\"unitPrice\":\"decimal\",\"currency\":\"string\",\"billingUnit\":\"string\"}",
                        "{\"columns\":[\"serviceLineKey\",\"unitPrice\",\"currency\",\"billingUnit\"]}",
                        33
                )
        ));

        List<String> keys = columns.stream()
                .map(FileParseProcessingColumnView::getKey)
                .collect(Collectors.toList());

        assertFalse(keys.contains("channelKey"));
        assertTrue(keys.contains("forwarderName"));
        assertTrue(keys.contains("leadTimeText"));
        assertTrue(keys.contains("unitPrice"));
        assertEquals(
                List.of("forwarderName", "country", "transportMode", "leadTimeText",
                        "serviceLineKey", "unitPrice", "currency", "billingUnit"),
                keys
        );
        assertEquals("货代", columns.get(0).getLabel());
        assertEquals("单价", columns.stream()
                .filter(column -> "unitPrice".equals(column.getKey()))
                .findFirst()
                .orElseThrow()
                .getLabel());
    }

    @Test
    void shouldBuildStructuredOfficialOutboundColumnsAcrossItemTypesInsteadOfLegacyOnly() {
        List<FileParseProcessingColumnView> columns = assembler.buildColumns(List.of(
                itemStandard(
                        FileParseOfficialOutboundFeeStandard.LEGACY_OUTBOUND_FEE_RULE,
                        "{\"country\":\"string\",\"feeItem\":\"string\",\"sizeTier\":\"string\",\"feeAmount\":\"decimal\",\"currency\":\"string\"}",
                        "{\"columns\":[\"country\",\"feeItem\",\"sizeTier\",\"feeAmount\",\"currency\"]}",
                        20
                ),
                itemStandard(
                        FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION,
                        "{\"classificationName\":\"string\",\"longestSideMaxCm\":\"decimal\",\"maxShippingWeightGrams\":\"decimal\",\"packagingWeightGrams\":\"decimal\"}",
                        "{\"columns\":[\"classificationName\",\"longestSideMaxCm\",\"maxShippingWeightGrams\",\"packagingWeightGrams\"]}",
                        21
                ),
                itemStandard(
                        FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB,
                        "{\"classificationName\":\"string\",\"weightMaxGrams\":\"decimal\",\"standardFeeAmount\":\"decimal\",\"currency\":\"string\"}",
                        "{\"columns\":[\"classificationName\",\"weightMaxGrams\",\"standardFeeAmount\",\"currency\"]}",
                        22
                ),
                itemStandard(
                        FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY,
                        "{\"shippingWeightFormula\":\"string\",\"salesPriceThresholdAmount\":\"decimal\",\"thresholdCurrency\":\"string\"}",
                        "{\"columns\":[\"shippingWeightFormula\",\"salesPriceThresholdAmount\",\"thresholdCurrency\"]}",
                        23
                )
        ));

        List<String> keys = columns.stream()
                .map(FileParseProcessingColumnView::getKey)
                .collect(Collectors.toList());

        assertFalse(keys.contains("feeItem"));
        assertFalse(keys.contains("feeAmount"));
        assertEquals(
                List.of(
                        "classificationName", "longestSideMaxCm", "maxShippingWeightGrams", "packagingWeightGrams",
                        "weightMaxGrams", "standardFeeAmount", "currency",
                        "shippingWeightFormula", "salesPriceThresholdAmount", "thresholdCurrency"
                ),
                keys
        );
        assertEquals("规格分类", columns.get(0).getLabel());
        assertEquals("标准费用", columns.stream()
                .filter(column -> "standardFeeAmount".equals(column.getKey()))
                .findFirst()
                .orElseThrow()
                .getLabel());
    }

    private FileParseItemStandardRow itemStandard(
            String itemType,
            String fieldSchemaJson,
            String displayConfigJson,
            int sortNo
    ) {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType(itemType);
        row.setItemLabel(itemType);
        row.setFieldSchemaJson(fieldSchemaJson);
        row.setDisplayConfigJson(displayConfigJson);
        row.setSortNo(sortNo);
        return row;
    }
}
