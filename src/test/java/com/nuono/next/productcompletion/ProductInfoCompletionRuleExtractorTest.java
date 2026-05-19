package com.nuono.next.productcompletion;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProductInfoCompletionRuleExtractorTest {

    private final ProductInfoCompletionRuleExtractor extractor =
            new ProductInfoCompletionRuleExtractor(new ProductInfoCompletionPatternParser());

    @Test
    void shouldExtractCoreFieldsFrom1688DetailText() {
        ProductInfoCompletionCommand command = new ProductInfoCompletionCommand();
        command.setTitle("陶瓷香薰炉跨境热销摆件");
        command.setAttributeSnapshotText("材质：陶瓷 尺寸：12x8x6cm 毛重：0.5kg");
        command.setPackageSnapshotText("包装：彩盒包装 装箱数：48个");

        ProductInfoCompletionView view = extractor.extract(command);

        Assertions.assertEquals("CAN_QUOTE", view.getCompletionLevel());
        Assertions.assertEquals("陶瓷", fieldValue(view, "material"));
        Assertions.assertEquals("12x8x6cm", fieldValue(view, "dimensions"));
        Assertions.assertEquals("0.5kg", fieldValue(view, "weight"));
        Assertions.assertTrue(view.getMissingFields().isEmpty());
        Assertions.assertTrue(view.getRiskFlags().stream().anyMatch(risk -> "FRAGILE".equals(risk.getRiskCode())));
    }

    @Test
    void shouldReturnMissingFieldsWhenWeightAndDimensionsAreAbsent() {
        ProductInfoCompletionCommand command = new ProductInfoCompletionCommand();
        command.setTitle("树脂收纳摆件");
        command.setDetailText("材质：树脂 包装：OPP袋");

        ProductInfoCompletionView view = extractor.extract(command);

        Assertions.assertEquals("NEEDS_CONFIRMATION", view.getCompletionLevel());
        Assertions.assertTrue(view.getMissingFields().contains("尺寸"));
        Assertions.assertTrue(view.getMissingFields().contains("重量"));
        Assertions.assertTrue(view.getRiskFlags().stream().anyMatch(risk -> "MISSING_WEIGHT".equals(risk.getRiskCode())));
        Assertions.assertTrue(view.getRiskFlags().stream().anyMatch(risk -> "MISSING_DIMENSIONS".equals(risk.getRiskCode())));
    }

    private String fieldValue(ProductInfoCompletionView view, String key) {
        return view.getFields().stream()
                .filter(field -> key.equals(field.getFieldKey()))
                .findFirst()
                .map(ProductInfoCompletionFieldView::getValue)
                .orElse(null);
    }
}
