package com.nuono.next.productcompletion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiResultStatus;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductInfoCompletionService {

    private final ProductInfoCompletionRuleExtractor ruleExtractor;
    private final AiCapabilityService aiCapabilityService;
    private final ObjectMapper objectMapper;

    public ProductInfoCompletionService(
            ProductInfoCompletionRuleExtractor ruleExtractor,
            AiCapabilityService aiCapabilityService,
            ObjectMapper objectMapper
    ) {
        this.ruleExtractor = ruleExtractor;
        this.aiCapabilityService = aiCapabilityService;
        this.objectMapper = objectMapper;
    }

    public ProductInfoCompletionView preview1688(ProductInfoCompletionCommand command) {
        ProductInfoCompletionView view = ruleExtractor.extract(command);
        if (Boolean.FALSE.equals(command.getUseAi())) {
            view.setAiStatus("SKIPPED");
            return view;
        }

        AiStructuredTextResult aiResult = aiCapabilityService.createStructuredText(buildAiCommand(command, view));
        view.setAiStatus(aiResult.getStatus());
        view.setAiModel(aiResult.getModel());
        if (aiResult.isSuccess() && aiResult.getParsedJson() != null) {
            mergeAiResult(view, aiResult.getParsedJson());
            ruleExtractor.refreshMissingFields(view);
            ruleExtractor.refreshRisks(view, collectRawText(command));
            mergeAiRisks(view, aiResult.getParsedJson());
            mergeAiMissingFields(view, aiResult.getParsedJson());
            ruleExtractor.refreshCompletionLevel(view);
            ruleExtractor.refreshMessage(view);
            view.setExtractionMode("AI_STRUCTURED");
            view.setMessage(view.getMessage() + " AI 已补充候选字段，所有 AI 字段仍需人工确认。");
        } else if (AiResultStatus.AI_DISABLED.equals(aiResult.getStatus())
                || AiResultStatus.AI_PROVIDER_NOT_CONFIGURED.equals(aiResult.getStatus())) {
            view.setExtractionMode("AI_UNAVAILABLE_RULE_ONLY");
        } else {
            view.setExtractionMode("AI_FAILED_RULE_ONLY");
            view.getSuggestions().add("AI 补全失败，当前结果仅来自规则抽取：" + aiResult.getErrorCode());
        }
        return view;
    }

    private AiStructuredTextCommand buildAiCommand(ProductInfoCompletionCommand command, ProductInfoCompletionView ruleView) {
        AiStructuredTextCommand aiCommand = new AiStructuredTextCommand();
        aiCommand.setFeatureCode("product-info-completion");
        aiCommand.setOperationCode("1688_detail_completion");
        aiCommand.setOperatorUserId(command.getOperatorUserId());
        aiCommand.setSchemaName("nuono_1688_product_info_completion");
        aiCommand.setSchema(outputSchema());
        aiCommand.setInstructions(String.join("\n",
                "你是跨境 ERP 的 1688 商品资料补全助手。",
                "只基于输入的 1688 标题、详情文本、属性、包装、物流描述和已抽取字段输出 JSON。",
                "不要编造重量、尺寸、装箱数、材质；不确定就放入 missingFields 或 suggestions。",
                "AI 输出只能作为候选资料，不能作为正式商品主档或物流报价依据。"
        ));
        aiCommand.setPrompt(buildPrompt(command, ruleView));
        aiCommand.setMetadata(mapOf(
                "sourcePlatform", "1688",
                "sourceUrl", safeText(command.getSourceUrl()),
                "feature", "product-info-completion"
        ));
        return aiCommand;
    }

    private String buildPrompt(ProductInfoCompletionCommand command, ProductInfoCompletionView ruleView) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourcePlatform", "1688");
        payload.put("sourceUrl", safeText(command.getSourceUrl()));
        payload.put("title", safeText(command.getTitle()));
        payload.put("supplierName", safeText(command.getSupplierName()));
        payload.put("detailText", abbreviate(command.getDetailText(), 4000));
        payload.put("attributeSnapshotText", abbreviate(command.getAttributeSnapshotText(), 3000));
        payload.put("packageSnapshotText", abbreviate(command.getPackageSnapshotText(), 2000));
        payload.put("shippingSnapshotText", abbreviate(command.getShippingSnapshotText(), 2000));
        payload.put("rawHtmlText", abbreviate(command.getRawHtml(), 4000));
        payload.put("imageOcrText", abbreviate(command.getImageOcrText(), 3000));
        payload.put("ruleDraftFields", ruleView.getFields());
        payload.put("ruleMissingFields", ruleView.getMissingFields());
        payload.put("ruleRiskFlags", ruleView.getRiskFlags());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI prompt payload serialization failed", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeAiResult(ProductInfoCompletionView view, Map<String, Object> parsedJson) {
        Object fieldsValue = parsedJson.get("fields");
        if (!(fieldsValue instanceof Map)) {
            return;
        }
        Map<String, Object> fields = (Map<String, Object>) fieldsValue;
        addAiField(view, "category", "疑似品类", fields.get("category"));
        addAiField(view, "material", "材质", fields.get("material"));
        addAiField(view, "powerMode", "供电方式", fields.get("powerMode"));
        addAiField(view, "dimensions", "尺寸", fields.get("dimensions"));
        addAiField(view, "weight", "重量", fields.get("weight"));
        addAiField(view, "packageSpec", "包装信息", fields.get("packageSpec"));
        addAiField(view, "quantityPerCarton", "装箱数", fields.get("quantityPerCarton"));
        addAiField(view, "logisticsNote", "物流备注", fields.get("logisticsNote"));
        Object completionLevel = parsedJson.get("completionLevel");
        if (completionLevel instanceof String && StringUtils.hasText((String) completionLevel)) {
            view.setCompletionLevel(((String) completionLevel).trim());
        }
        mergeStringList(view.getSuggestions(), parsedJson.get("suggestions"));
    }

    @SuppressWarnings("unchecked")
    private void mergeAiRisks(ProductInfoCompletionView view, Map<String, Object> parsedJson) {
        Object risksValue = parsedJson.get("riskFlags");
        if (!(risksValue instanceof List)) {
            return;
        }
        for (Object item : (List<?>) risksValue) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> riskMap = (Map<String, Object>) item;
            String code = stringValue(riskMap.get("riskCode"));
            if (!StringUtils.hasText(code) || hasRisk(view, code)) {
                continue;
            }
            ProductInfoCompletionRiskView risk = new ProductInfoCompletionRiskView();
            risk.setRiskCode(code);
            risk.setLabel(firstText(riskMap.get("label"), code));
            risk.setSeverity(firstText(riskMap.get("severity"), "medium"));
            risk.setReason(firstText(riskMap.get("reason"), "AI 根据 1688 文本标记，需人工确认。"));
            risk.setSource("AI_SUGGESTED");
            view.getRiskFlags().add(risk);
        }
    }

    private void mergeAiMissingFields(ProductInfoCompletionView view, Map<String, Object> parsedJson) {
        mergeStringList(view.getMissingFields(), parsedJson.get("missingFields"));
    }

    private void addAiField(ProductInfoCompletionView view, String fieldKey, String label, Object value) {
        String text = stringValue(value);
        if (!StringUtils.hasText(text)) {
            return;
        }
        ruleExtractor.addField(view, fieldKey, label, text, "AI_SUGGESTED", "low", "AI 结构化输出，需人工确认");
    }

    private boolean hasRisk(ProductInfoCompletionView view, String code) {
        return view.getRiskFlags().stream().anyMatch(risk -> code.equals(risk.getRiskCode()));
    }

    private void mergeStringList(List<String> target, Object value) {
        if (!(value instanceof List)) {
            return;
        }
        for (Object item : (List<?>) value) {
            String text = stringValue(item);
            if (StringUtils.hasText(text) && !target.contains(text)) {
                target.add(text);
            }
        }
    }

    private Map<String, Object> outputSchema() {
        return mapOf(
                "type", "object",
                "additionalProperties", false,
                "required", Arrays.asList("fields", "missingFields", "riskFlags", "completionLevel", "suggestions"),
                "properties", mapOf(
                        "fields", mapOf(
                                "type", "object",
                                "additionalProperties", false,
                                "required", Arrays.asList(
                                        "category",
                                        "material",
                                        "powerMode",
                                        "dimensions",
                                        "weight",
                                        "packageSpec",
                                        "quantityPerCarton",
                                        "logisticsNote"
                                ),
                                "properties", mapOf(
                                        "category", stringSchema(),
                                        "material", stringSchema(),
                                        "powerMode", stringSchema(),
                                        "dimensions", stringSchema(),
                                        "weight", stringSchema(),
                                        "packageSpec", stringSchema(),
                                        "quantityPerCarton", stringSchema(),
                                        "logisticsNote", stringSchema()
                                )
                        ),
                        "missingFields", mapOf("type", "array", "items", stringSchema()),
                        "riskFlags", mapOf(
                                "type", "array",
                                "items", mapOf(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "required", Arrays.asList("riskCode", "label", "severity", "reason"),
                                        "properties", mapOf(
                                                "riskCode", stringSchema(),
                                                "label", stringSchema(),
                                                "severity", stringSchema(),
                                                "reason", stringSchema()
                                        )
                                )
                        ),
                        "completionLevel", stringSchema(),
                        "suggestions", mapOf("type", "array", "items", stringSchema())
                )
        );
    }

    private Map<String, Object> stringSchema() {
        return mapOf("type", Arrays.asList("string", "null"));
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    private String collectRawText(ProductInfoCompletionCommand command) {
        List<String> parts = new ArrayList<>();
        addPart(parts, command.getTitle());
        addPart(parts, command.getDetailText());
        addPart(parts, command.getAttributeSnapshotText());
        addPart(parts, command.getPackageSnapshotText());
        addPart(parts, command.getShippingSnapshotText());
        addPart(parts, command.getRawHtml());
        addPart(parts, command.getImageOcrText());
        return String.join(" | ", parts);
    }

    private void addPart(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value.trim());
        }
    }

    private String firstText(Object value, String fallback) {
        String text = stringValue(value);
        return StringUtils.hasText(text) ? text : fallback;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength) + "...";
    }
}
