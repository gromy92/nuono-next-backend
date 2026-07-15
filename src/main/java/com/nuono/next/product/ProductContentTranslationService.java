package com.nuono.next.product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiResultStatus;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductContentTranslationService {

    private final ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider;
    private final ObjectMapper objectMapper;

    public ProductContentTranslationService(
            ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider,
            ObjectMapper objectMapper
    ) {
        this.aiCapabilityServiceProvider = aiCapabilityServiceProvider;
        this.objectMapper = objectMapper;
    }

    public ProductContentTranslateView translate(ProductContentTranslateCommand command) {
        String text = command == null ? null : command.getText();
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("翻译文本不能为空。");
        }
        String targetLang = normalizeTargetLang(command.getTargetLang());
        AiStructuredTextResult aiResult = translateWithAi(command, targetLang);
        String aiText = aiTranslationText(aiResult);
        if (StringUtils.hasText(aiText)) {
            if (!translatedTextMatchesTargetLang(aiText, targetLang)) {
                List<String> warnings = new ArrayList<>(warningsFrom(aiResult));
                warnings.add("AI_TRANSLATION_TARGET_LANG_MISMATCH");
                return ProductContentTranslateView.unavailable(
                        "ai",
                        targetLanguageMismatchMessage(targetLang),
                        warnings
                );
            }
            return ProductContentTranslateView.of(aiText.trim(), "ai", warningsFrom(aiResult));
        }

        List<String> warnings = new ArrayList<>(warningsFrom(aiResult));
        String errorMessage = translationErrorMessage(aiResult);
        return ProductContentTranslateView.unavailable(
                "ai",
                "AI 翻译暂时不可用：" + errorMessage,
                warnings
        );
    }

    private AiStructuredTextResult translateWithAi(ProductContentTranslateCommand command, String targetLang) {
        AiCapabilityService aiCapabilityService = aiCapabilityServiceProvider.getIfAvailable();
        if (aiCapabilityService == null) {
            return AiStructuredTextResult.failure(AiResultStatus.AI_DISABLED, "AI_SERVICE_MISSING", "AI service is not available");
        }
        AiStructuredTextCommand aiCommand = new AiStructuredTextCommand();
        aiCommand.setFeatureCode("product-management");
        aiCommand.setOperationCode("content_translate");
        aiCommand.setOperatorUserId(command.getOperatorUserId());
        aiCommand.setSchemaName("nuono_product_content_translation");
        aiCommand.setSchema(outputSchema());
        aiCommand.setInstructions(String.join("\n",
                "Translate ecommerce product content for Noon seller operations.",
                "Source language may be AUTO. Detect the source language from the provided text when sourceLang is AUTO.",
                "Translate normal product nouns and marketing terms into the target language; keep only brand names, SKUs, numbers, measurements, HTML tags and punctuation unchanged when possible.",
                "Return JSON only with translation.text.",
                "Target language: " + languageName(targetLang) + " (" + targetLang + ")"
        ));
        aiCommand.setPrompt(prompt(command, targetLang));
        aiCommand.setMetadata(Map.of("targetLang", targetLang, "feature", "product-management"));
        return aiCapabilityService.createStructuredText(aiCommand);
    }

    private String translationErrorMessage(AiStructuredTextResult aiResult) {
        if (aiResult != null && "OPENAI_API_KEY_MISSING".equalsIgnoreCase(aiResult.getErrorCode())) {
            return "后端未配置 OPENAI_API_KEY，请配置后重启服务。";
        }
        if (aiResult != null && StringUtils.hasText(aiResult.getErrorMessage())) {
            return aiResult.getErrorMessage();
        }
        return "AI 未返回可用翻译结果。";
    }

    private String prompt(ProductContentTranslateCommand command, String targetLang) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceLang", normalizeSourceLang(command.getSourceLang()));
        payload.put("targetLang", targetLang);
        payload.put("text", command.getText());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("翻译请求序列化失败。", exception);
        }
    }

    private Map<String, Object> outputSchema() {
        Map<String, Object> text = Map.of("type", "string");
        Map<String, Object> translation = new LinkedHashMap<>();
        translation.put("type", "object");
        translation.put("additionalProperties", false);
        translation.put("required", List.of("text"));
        translation.put("properties", Map.of("text", text));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("translation"));
        schema.put("properties", Map.of("translation", translation));
        return schema;
    }

    @SuppressWarnings("unchecked")
    private String aiTranslationText(AiStructuredTextResult aiResult) {
        if (aiResult == null || !aiResult.isSuccess() || aiResult.getParsedJson() == null) {
            return null;
        }
        Object translation = aiResult.getParsedJson().get("translation");
        if (!(translation instanceof Map<?, ?>)) {
            return null;
        }
        Object text = ((Map<String, Object>) translation).get("text");
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

    private String localDraft(String text, String targetLang) {
        if ("ZH".equals(targetLang)) {
            return replaceWords(text, Map.ofEntries(
                    Map.entry("Astronaut", "宇航员"),
                    Map.entry("Galaxy", "银河"),
                    Map.entry("Projector", "投影灯"),
                    Map.entry("Star", "星空"),
                    Map.entry("Night Light", "夜灯"),
                    Map.entry("Remote", "遥控器"),
                    Map.entry("Bedroom", "卧室"),
                    Map.entry("Kids", "儿童"),
                    Map.entry("Decor", "装饰"),
                    Map.entry("Adjustable", "可调节"),
                    Map.entry("Lamp", "灯"),
                    Map.entry("Gifts", "礼物"),
                    Map.entry("Adults", "成人"),
                    Map.entry("Teens", "青少年"),
                    Map.entry("Birthday", "生日")
            ));
        }
        if ("AR".equals(targetLang)) {
            return replaceWords(text, Map.ofEntries(
                    Map.entry("Astronaut", "رائد فضاء"),
                    Map.entry("Galaxy", "مجرة"),
                    Map.entry("Projector", "جهاز عرض"),
                    Map.entry("Star", "نجوم"),
                    Map.entry("Night Light", "مصباح ليلي"),
                    Map.entry("Remote", "جهاز تحكم"),
                    Map.entry("Bedroom", "غرفة نوم"),
                    Map.entry("Kids", "أطفال"),
                    Map.entry("Decor", "ديكور"),
                    Map.entry("Lamp", "مصباح")
            ));
        }
        return text;
    }

    private boolean translatedTextMatchesTargetLang(String text, String targetLang) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if ("ZH".equals(targetLang)) {
            return containsChinese(text);
        }
        if ("AR".equals(targetLang)) {
            return containsArabic(text);
        }
        if ("EN".equals(targetLang)) {
            return containsLatin(text) && !containsArabic(text) && !containsChinese(text);
        }
        return true;
    }

    private String targetLanguageMismatchMessage(String targetLang) {
        if ("ZH".equals(targetLang)) {
            return "AI 返回的翻译不是中文，请重试。";
        }
        if ("AR".equals(targetLang)) {
            return "AI 返回的翻译不是阿语，请重试。";
        }
        if ("EN".equals(targetLang)) {
            return "AI 返回的翻译不是英文，请重试。";
        }
        return "AI 返回的翻译目标语言不正确，请重试。";
    }

    private boolean containsChinese(String value) {
        return value.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private boolean containsArabic(String value) {
        return value.codePoints().anyMatch(codePoint -> codePoint >= 0x0600 && codePoint <= 0x06FF);
    }

    private boolean containsLatin(String value) {
        return value.codePoints().anyMatch(codePoint ->
                (codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= 'a' && codePoint <= 'z')
        );
    }

    private String replaceWords(String source, Map<String, String> dictionary) {
        String translated = source;
        for (Map.Entry<String, String> entry : dictionary.entrySet()) {
            translated = translated.replace(entry.getKey(), entry.getValue());
        }
        return translated;
    }

    private String normalizeSourceLang(String lang) {
        String normalized = String.valueOf(lang == null ? "" : lang).trim().toUpperCase(Locale.ROOT);
        if ("CN".equals(normalized) || "ZH-CN".equals(normalized)) {
            return "ZH";
        }
        if (!StringUtils.hasText(normalized)) {
            return "AUTO";
        }
        return normalized;
    }

    private String normalizeTargetLang(String lang) {
        String normalized = normalizeSourceLang(lang);
        return "AUTO".equals(normalized) ? "ZH" : normalized;
    }

    private String languageName(String lang) {
        switch (lang) {
            case "ZH":
                return "Simplified Chinese";
            case "EN":
                return "English";
            case "AR":
                return "Arabic";
            default:
                return lang;
        }
    }
}
