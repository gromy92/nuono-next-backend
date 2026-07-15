package com.nuono.next.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiResultStatus;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductCompetitorContentMergeService {

    private final ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider;
    private final ObjectMapper objectMapper;

    public ProductCompetitorContentMergeService(
            ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider,
            ObjectMapper objectMapper
    ) {
        this.aiCapabilityServiceProvider = aiCapabilityServiceProvider;
        this.objectMapper = objectMapper;
    }

    public ProductCompetitorContentMergeView merge(ProductCompetitorContentMergeCommand command) {
        if (command == null) {
            command = new ProductCompetitorContentMergeCommand();
        }
        String fieldType = normalizeFieldType(command.getFieldType());
        String targetLang = normalizeTargetLang(command.getTargetLang());
        List<String> competitorTexts = normalizeTexts(command.getCompetitorTexts());
        if (competitorTexts.isEmpty()) {
            throw new IllegalArgumentException("竞品文案不能为空。");
        }

        AiStructuredTextResult aiResult = mergeWithAi(command, fieldType, targetLang, competitorTexts);
        String draftText = aiDraftText(aiResult);
        if (StringUtils.hasText(draftText)) {
            return ProductCompetitorContentMergeView.of(draftText.trim(), "ai", warningsFrom(aiResult));
        }

        List<String> warnings = new ArrayList<>(warningsFrom(aiResult));
        return ProductCompetitorContentMergeView.unavailable(
                "ai",
                "竞品 AI 整合暂时不可用：" + mergeErrorMessage(aiResult),
                warnings
        );
    }

    private AiStructuredTextResult mergeWithAi(
            ProductCompetitorContentMergeCommand command,
            String fieldType,
            String targetLang,
            List<String> competitorTexts
    ) {
        AiCapabilityService aiCapabilityService = aiCapabilityServiceProvider.getIfAvailable();
        if (aiCapabilityService == null) {
            return AiStructuredTextResult.failure(AiResultStatus.AI_DISABLED, "AI_SERVICE_MISSING", "AI service is not available");
        }
        AiStructuredTextCommand aiCommand = new AiStructuredTextCommand();
        aiCommand.setFeatureCode("product-management");
        aiCommand.setOperationCode("competitor_content_merge");
        aiCommand.setOperatorUserId(command.getOperatorUserId());
        aiCommand.setSchemaName("nuono_product_competitor_content_merge");
        aiCommand.setSchema(outputSchema());
        aiCommand.setReasoningEffort("low");
        aiCommand.setMaxOutputTokens(maxOutputTokens(fieldType));
        aiCommand.setTimeoutSeconds(75);
        aiCommand.setInstructions(String.join("\n",
                "Create a concise Noon ecommerce product content draft from competitor materials.",
                "Use only factual product content found in the competitor texts and the current draft.",
                "Do not invent certifications, compatibility, warranty, dimensions, or quantities not present in the input.",
                "Return JSON only with draft.text.",
                "Target field: " + fieldType,
                "Target language: " + languageName(targetLang) + " (" + targetLang + ")"
        ));
        aiCommand.setPrompt(prompt(command, fieldType, targetLang, competitorTexts));
        aiCommand.setMetadata(Map.of(
                "targetLang", targetLang,
                "fieldType", fieldType,
                "feature", "product-management"
        ));
        return aiCapabilityService.createStructuredText(aiCommand);
    }

    private int maxOutputTokens(String fieldType) {
        switch (fieldType) {
            case "title":
                return 220;
            case "highlights":
                return 700;
            case "description":
            default:
                return 900;
        }
    }

    private String prompt(
            ProductCompetitorContentMergeCommand command,
            String fieldType,
            String targetLang,
            List<String> competitorTexts
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fieldType", fieldType);
        payload.put("targetLang", targetLang);
        payload.put("currentText", command.getCurrentText());
        payload.put("competitorTexts", competitorTexts);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("竞品文案整合请求序列化失败。", exception);
        }
    }

    private Map<String, Object> outputSchema() {
        Map<String, Object> text = Map.of("type", "string");
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("type", "object");
        draft.put("additionalProperties", false);
        draft.put("required", List.of("text"));
        draft.put("properties", Map.of("text", text));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("draft"));
        schema.put("properties", Map.of("draft", draft));
        return schema;
    }

    @SuppressWarnings("unchecked")
    private String aiDraftText(AiStructuredTextResult aiResult) {
        if (aiResult == null || !aiResult.isSuccess() || aiResult.getParsedJson() == null) {
            return null;
        }
        Object draft = aiResult.getParsedJson().get("draft");
        if (!(draft instanceof Map<?, ?>)) {
            return null;
        }
        Object text = ((Map<String, Object>) draft).get("text");
        return text == null ? null : String.valueOf(text);
    }

    private List<String> warningsFrom(AiStructuredTextResult aiResult) {
        List<String> warnings = new ArrayList<>();
        if (aiResult == null) {
            return warnings;
        }
        if (aiResult.getWarnings() != null) {
            warnings.addAll(aiResult.getWarnings());
        }
        if (!aiResult.isSuccess() && StringUtils.hasText(aiResult.getErrorCode())) {
            warnings.add(aiResult.getErrorCode());
        }
        return warnings;
    }

    private String mergeErrorMessage(AiStructuredTextResult aiResult) {
        if (aiResult != null && "OPENAI_API_KEY_MISSING".equalsIgnoreCase(aiResult.getErrorCode())) {
            return "后端未配置 OPENAI_API_KEY，请配置后重启服务。";
        }
        if (aiResult != null && StringUtils.hasText(aiResult.getErrorMessage())) {
            return aiResult.getErrorMessage();
        }
        return "AI 未返回可用整合结果。";
    }

    private List<String> normalizeTexts(List<String> texts) {
        if (texts == null) {
            return List.of();
        }
        return texts.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }

    private String normalizeFieldType(String fieldType) {
        String normalized = String.valueOf(fieldType == null ? "" : fieldType).trim().toLowerCase(Locale.ROOT);
        if ("title".equals(normalized) || "description".equals(normalized) || "highlights".equals(normalized)) {
            return normalized;
        }
        return "description";
    }

    private String normalizeTargetLang(String lang) {
        String normalized = String.valueOf(lang == null ? "" : lang).trim().toUpperCase(Locale.ROOT);
        if ("CN".equals(normalized) || "ZH-CN".equals(normalized)) {
            return "ZH";
        }
        if ("EN".equals(normalized) || "AR".equals(normalized) || "ZH".equals(normalized)) {
            return normalized;
        }
        return "EN";
    }

    private String languageName(String lang) {
        switch (lang) {
            case "ZH":
                return "Simplified Chinese";
            case "AR":
                return "Arabic";
            case "EN":
            default:
                return "English";
        }
    }
}
