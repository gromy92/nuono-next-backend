package com.nuono.next.productcompletion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductInfoCompletionRuleExtractor {

    private final ProductInfoCompletionPatternParser patternParser;

    public ProductInfoCompletionRuleExtractor(ProductInfoCompletionPatternParser patternParser) {
        this.patternParser = patternParser;
    }

    public ProductInfoCompletionView extract(ProductInfoCompletionCommand command) {
        if (command == null || !hasAnyInput(command)) {
            throw new IllegalArgumentException("请至少提供 1688 标题、详情文本或详情页 HTML。");
        }

        String allText = collectSourceText(command);
        ProductInfoCompletionView view = new ProductInfoCompletionView();
        view.setReady(true);
        view.setSourceUrl(normalize(command.getSourceUrl()));
        view.setExtractionMode("RULE_ONLY");

        addField(view, "title", "商品标题", firstNonBlank(command.getTitle(), patternParser.parseHtmlTitle(command.getRawHtml())), "SOURCE", "high", allText);
        addField(view, "category", "疑似品类", patternParser.extractCategory(allText), "RULE", "medium", allText);
        addField(view, "material", "材质", patternParser.extractMaterial(allText), "RULE", "medium", allText);
        addField(view, "powerMode", "供电方式", patternParser.extractPowerMode(allText), "RULE", "medium", allText);
        addField(view, "dimensions", "尺寸", patternParser.extractDimensions(allText), "RULE", "medium", allText);
        addField(view, "weight", "重量", patternParser.extractWeight(allText), "RULE", "medium", allText);
        addField(view, "packageSpec", "包装信息", patternParser.extractPackageSpec(allText), "RULE", "medium", allText);
        addField(view, "quantityPerCarton", "装箱数", patternParser.extractQuantityPerCarton(allText), "RULE", "medium", allText);

        refreshMissingFields(view);
        refreshRisks(view, allText);
        refreshCompletionLevel(view);
        refreshMessage(view);
        return view;
    }

    void refreshMissingFields(ProductInfoCompletionView view) {
        Map<String, String> requiredFields = new LinkedHashMap<>();
        requiredFields.put("material", "材质");
        requiredFields.put("dimensions", "尺寸");
        requiredFields.put("weight", "重量");
        requiredFields.put("packageSpec", "包装信息");
        requiredFields.put("quantityPerCarton", "装箱数");

        List<String> missingFields = new ArrayList<>();
        for (Map.Entry<String, String> entry : requiredFields.entrySet()) {
            if (!hasField(view, entry.getKey())) {
                missingFields.add(entry.getValue());
            }
        }
        view.setMissingFields(missingFields);
    }

    void refreshRisks(ProductInfoCompletionView view, String rawText) {
        List<ProductInfoCompletionRiskView> risks = new ArrayList<>();
        if (!hasField(view, "weight")) {
            risks.add(risk("MISSING_WEIGHT", "缺重量", "high", "缺少重量，不能进入正式物流报价。", "RULE"));
        }
        if (!hasField(view, "dimensions")) {
            risks.add(risk("MISSING_DIMENSIONS", "缺尺寸", "high", "缺少尺寸，不能计算体积重。", "RULE"));
        }
        if (patternParser.hasBatterySignal(rawText)) {
            risks.add(risk("BATTERY", "疑似带电", "medium", "1688 文本包含电池或充电相关描述，需要确认物流属性。", "RULE"));
        }
        if (patternParser.hasLiquidSignal(rawText)) {
            risks.add(risk("LIQUID", "疑似液体", "medium", "1688 文本包含液体相关描述，需要确认是否可走当前渠道。", "RULE"));
        }
        if (patternParser.hasFragileSignal(rawText)) {
            risks.add(risk("FRAGILE", "疑似易碎", "medium", "材质或描述包含易碎品特征，需要确认包装和破损风险。", "RULE"));
        }
        if (patternParser.hasMagneticSignal(rawText)) {
            risks.add(risk("MAGNETIC", "疑似磁性", "medium", "1688 文本包含磁性相关描述，需要确认运输限制。", "RULE"));
        }
        if (patternParser.isOversize(findFieldValue(view, "dimensions"))) {
            risks.add(risk("OVERSIZE", "疑似超长", "medium", "识别到单边尺寸可能超过 100cm，需要人工确认报价规则。", "RULE"));
        }
        view.setRiskFlags(risks);
    }

    void refreshCompletionLevel(ProductInfoCompletionView view) {
        boolean hasQuoteCore = hasField(view, "dimensions") && hasField(view, "weight") && hasField(view, "packageSpec");
        if (hasQuoteCore && view.getMissingFields().isEmpty()) {
            view.setCompletionLevel("CAN_QUOTE");
        } else if (view.getFields().size() >= 3) {
            view.setCompletionLevel("NEEDS_CONFIRMATION");
        } else {
            view.setCompletionLevel("INSUFFICIENT");
        }
    }

    void refreshMessage(ProductInfoCompletionView view) {
        if ("CAN_QUOTE".equals(view.getCompletionLevel())) {
            view.setMessage("已识别到基础报价字段，可以进入人工确认后试算物流方案。");
        } else if ("NEEDS_CONFIRMATION".equals(view.getCompletionLevel())) {
            view.setMessage("已识别部分商品资料，缺失字段补齐后再进入正式报价。");
        } else {
            view.setMessage("当前 1688 信息不足，只能生成待补资料清单。");
        }
        if (!view.getSuggestions().contains("AI 或规则抽取字段均需人工确认后，才能写入商品主档或用于正式物流报价。")) {
            view.getSuggestions().add("AI 或规则抽取字段均需人工确认后，才能写入商品主档或用于正式物流报价。");
        }
    }

    ProductInfoCompletionFieldView addField(
            ProductInfoCompletionView view,
            String fieldKey,
            String label,
            String value,
            String source,
            String confidence,
            String evidence
    ) {
        if (!StringUtils.hasText(value) || hasField(view, fieldKey)) {
            return null;
        }
        ProductInfoCompletionFieldView field = new ProductInfoCompletionFieldView();
        field.setFieldKey(fieldKey);
        field.setLabel(label);
        field.setValue(value.trim());
        field.setSource(source);
        field.setConfidence(confidence);
        field.setEvidence(compact(evidence));
        view.getFields().add(field);
        return field;
    }

    boolean hasField(ProductInfoCompletionView view, String fieldKey) {
        return StringUtils.hasText(findFieldValue(view, fieldKey));
    }

    String findFieldValue(ProductInfoCompletionView view, String fieldKey) {
        for (ProductInfoCompletionFieldView field : view.getFields()) {
            if (fieldKey.equals(field.getFieldKey())) {
                return field.getValue();
            }
        }
        return null;
    }

    private boolean hasAnyInput(ProductInfoCompletionCommand command) {
        return StringUtils.hasText(command.getTitle())
                || StringUtils.hasText(command.getDetailText())
                || StringUtils.hasText(command.getAttributeSnapshotText())
                || StringUtils.hasText(command.getPackageSnapshotText())
                || StringUtils.hasText(command.getShippingSnapshotText())
                || StringUtils.hasText(command.getRawHtml())
                || StringUtils.hasText(command.getImageOcrText());
    }

    private String collectSourceText(ProductInfoCompletionCommand command) {
        return joinText(
                command.getTitle(),
                command.getSupplierName(),
                command.getAttributeSnapshotText(),
                command.getDetailText(),
                command.getPackageSnapshotText(),
                command.getShippingSnapshotText(),
                patternParser.stripHtml(command.getRawHtml()),
                command.getImageOcrText()
        );
    }

    private ProductInfoCompletionRiskView risk(String code, String label, String severity, String reason, String source) {
        ProductInfoCompletionRiskView risk = new ProductInfoCompletionRiskView();
        risk.setRiskCode(code);
        risk.setLabel(label);
        risk.setSeverity(severity);
        risk.setReason(reason);
        risk.setSource(source);
        return risk;
    }

    private String joinText(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                parts.add(value.trim());
            }
        }
        return String.join(" | ", parts);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : normalize(second);
    }

    private String compact(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160) + "...";
    }
}
