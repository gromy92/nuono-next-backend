package com.nuono.next.productcompletion;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiJsonSchemaValidator;
import com.nuono.next.ai.AiModelClient;
import com.nuono.next.ai.AiProperties;
import com.nuono.next.ai.AiResultStatus;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProductInfoCompletionServiceTest {

    @Test
    void shouldFallbackToRulesWhenAiIsDisabled() {
        ProductInfoCompletionService service = new ProductInfoCompletionService(
                extractor(),
                aiService(false, command -> AiStructuredTextResult.success()),
                new ObjectMapper()
        );
        ProductInfoCompletionCommand command = commandWithoutWeight();

        ProductInfoCompletionView view = service.preview1688(command);

        Assertions.assertEquals(AiResultStatus.AI_DISABLED, view.getAiStatus());
        Assertions.assertEquals("AI_UNAVAILABLE_RULE_ONLY", view.getExtractionMode());
        Assertions.assertTrue(view.getMissingFields().contains("重量"));
    }

    @Test
    void shouldMergeAiSuggestedFieldsWithoutOverwritingRuleFields() {
        ProductInfoCompletionService service = new ProductInfoCompletionService(
                extractor(),
                aiService(true, command -> aiResult()),
                new ObjectMapper()
        );

        ProductInfoCompletionView view = service.preview1688(commandWithoutWeight());

        Assertions.assertEquals("AI_STRUCTURED", view.getExtractionMode());
        Assertions.assertEquals("0.3kg", fieldValue(view, "weight"));
        Assertions.assertEquals("AI_SUGGESTED", fieldSource(view, "weight"));
        Assertions.assertEquals("树脂", fieldValue(view, "material"));
        Assertions.assertTrue(view.getRiskFlags().stream().anyMatch(risk -> "FRAGILE".equals(risk.getRiskCode())));
    }

    private ProductInfoCompletionCommand commandWithoutWeight() {
        ProductInfoCompletionCommand command = new ProductInfoCompletionCommand();
        command.setTitle("树脂收纳摆件");
        command.setDetailText("材质：树脂 包装：OPP袋");
        return command;
    }

    private ProductInfoCompletionRuleExtractor extractor() {
        return new ProductInfoCompletionRuleExtractor(new ProductInfoCompletionPatternParser());
    }

    private AiCapabilityService aiService(boolean enabled, AiModelClient client) {
        AiProperties properties = new AiProperties();
        properties.setEnabled(enabled);
        properties.getOpenai().setApiKey("test-api-key");
        return new AiCapabilityService(properties, client, new AiJsonSchemaValidator(), entry -> {
        });
    }

    private AiStructuredTextResult aiResult() {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("gpt-5.4-mini");
        result.setParsedJson(object(
                "fields", object(
                        "category", "收纳用品",
                        "material", null,
                        "powerMode", null,
                        "dimensions", "10x6x5cm",
                        "weight", "0.3kg",
                        "packageSpec", null,
                        "quantityPerCarton", null,
                        "logisticsNote", "重量来自 AI 候选，需要人工确认"
                ),
                "missingFields", Arrays.asList("装箱数"),
                "riskFlags", Arrays.asList(object(
                        "riskCode", "FRAGILE",
                        "label", "疑似易碎",
                        "severity", "medium",
                        "reason", "摆件类商品需要确认包装"
                )),
                "completionLevel", "NEEDS_CONFIRMATION",
                "suggestions", Arrays.asList("确认单品重量和装箱数")
        ));
        return result;
    }

    private Map<String, Object> object(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private String fieldValue(ProductInfoCompletionView view, String key) {
        return view.getFields().stream()
                .filter(field -> key.equals(field.getFieldKey()))
                .findFirst()
                .map(ProductInfoCompletionFieldView::getValue)
                .orElse(null);
    }

    private String fieldSource(ProductInfoCompletionView view, String key) {
        return view.getFields().stream()
                .filter(field -> key.equals(field.getFieldKey()))
                .findFirst()
                .map(ProductInfoCompletionFieldView::getSource)
                .orElse(null);
    }
}
