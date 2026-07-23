package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoonProductListFieldSupportTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReadPskuCodeFromCamelCaseMapField() {
        assertEquals(
                "PSKU-CAMEL",
                NoonProductListFieldSupport.pskuCode(Map.of("pskuCode", "PSKU-CAMEL"))
        );
    }

    @Test
    void shouldReadPskuCodeFromNestedJsonOfferField() {
        JsonNode node = objectMapper.valueToTree(Map.of(
                "offer",
                Map.of("psku_code", "PSKU-NESTED")
        ));

        assertEquals("PSKU-NESTED", NoonProductListFieldSupport.pskuCode(node));
    }

    @Test
    void shouldReadAndDeduplicateScalarAndPartnerBarcodesFromMap() {
        assertEquals(
                List.of("PAPERSAYS440", "PAPERSAYSB440"),
                NoonProductListFieldSupport.barcodes(Map.of(
                        "barcode", "PAPERSAYS440",
                        "partner_barcodes", List.of("PAPERSAYS440", "PAPERSAYSB440", " ")
                ))
        );
    }

    @Test
    void shouldReadEveryPartnerBarcodeFromJson() {
        JsonNode node = objectMapper.valueToTree(Map.of(
                "partner_sku", "PAPERSAYS440",
                "partner_barcodes", List.of("PAPERSAYS440", "PAPERSAYSB440")
        ));

        assertEquals(
                List.of("PAPERSAYS440", "PAPERSAYSB440"),
                NoonProductListFieldSupport.barcodes(node)
        );
    }
}
