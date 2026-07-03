package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
}
