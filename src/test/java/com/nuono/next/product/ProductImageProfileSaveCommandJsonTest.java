package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ProductImageProfileSaveCommandJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void legacyProductVariantIdIsIgnoredDuringRollingUpgrade() throws Exception {
        ProductImageProfileSaveCommand command = objectMapper.readValue(
                "{\"pskuCode\":\"PAPERSAYSB024\",\"productVariantId\":53001}",
                ProductImageProfileSaveCommand.class
        );

        assertThat(command.getPskuCode()).isEqualTo("PAPERSAYSB024");
        assertThat(objectMapper.writeValueAsString(command)).doesNotContain("productVariantId");
    }
}
