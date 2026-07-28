package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiProductImageGeneratorTest {

    @Test
    void transparentContentLayerPromptShouldRequestTransparentPng() throws Exception {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.getOpenai().setApiKey("test-key");
        OpenAiProductImageGenerator generator =
                new OpenAiProductImageGenerator(properties, new ObjectMapper());

        Map<String, Object> requestPayload = generator.buildPayload(
                "只生成透明商品内容层，不要生成品牌皮肤、Logo、图标、文字、边框或背景。",
                List.of()
        );

        JsonNode payload = new ObjectMapper().valueToTree(requestPayload);
        assertEquals("transparent", payload.path("background").asText());
        assertEquals("png", payload.path("output_format").asText());
        assertTrue(payload.path("prompt").asText().contains("透明商品内容层"));
    }
}
