package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiResultStatus;
import com.nuono.next.ai.AiStructuredTextResult;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductListingAiListingService {

    static final String RULE_VERSION = "v3.3";
    static final String RULE_RESOURCE = "ai/product-listing/noon-listing-v3_3.md";

    private final ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider;
    private final ProductListingAiRequestFactory requestFactory;
    private final ProductListingAiFactExtractionFactory factExtractionFactory;

    public ProductListingAiListingService(
            ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider,
            ObjectMapper objectMapper
    ) {
        this.aiCapabilityServiceProvider = aiCapabilityServiceProvider;
        this.requestFactory = new ProductListingAiRequestFactory(objectMapper);
        this.factExtractionFactory = new ProductListingAiFactExtractionFactory(objectMapper);
    }

    public ProductListingAiListingView generate(BusinessAccessContext context, ProductListingAiListingCommand command) {
        ProductListingAiListingCommand normalizedCommand = command == null ? new ProductListingAiListingCommand() : command;
        ProductListingDraftCommand draft = normalizedCommand.getDraft();
        if (draft == null) {
            throw new IllegalArgumentException("商品上架 AI 整合需要先提供当前上架草稿。");
        }
        List<ProductListingAiCompetitorMaterial> competitorMaterials = usefulCompetitorMaterials(
                normalizedCommand.getCompetitorMaterials()
        );
        if (!hasDraftFacts(draft) && competitorMaterials.isEmpty()) {
            throw new IllegalArgumentException("商品上架 AI 整合需要先填写商品标题、描述、卖点、已验证属性或竞品材料。");
        }

        AiCapabilityService aiCapabilityService = aiCapabilityServiceProvider.getIfAvailable();
        if (aiCapabilityService == null) {
            AiStructuredTextResult missing = AiStructuredTextResult.failure(
                    AiResultStatus.AI_DISABLED,
                    "AI_SERVICE_MISSING",
                    "AI service is not available"
            );
            return unavailable("商品上架 AI 整合暂时不可用：", missing, warningsFrom(missing));
        }

        AiStructuredTextResult factResult = aiCapabilityService.createStructuredText(
                factExtractionFactory.create(context, draft)
        );
        if (!usable(factResult)) {
            factResult = aiCapabilityService.createStructuredText(
                    factExtractionFactory.create(context, draft)
            );
        }
        if (!usable(factResult)) {
            return ProductListingAiListingView.unavailable(
                    RULE_VERSION,
                    "ai",
                    "AI 暂时未能完成商品事实提取，请稍后重新生成。",
                    warningsFrom(factResult)
            );
        }
        ProductListingAiFactLedger factLedger = ProductListingAiFactLedger.from(factResult.getParsedJson())
                .withoutUntraceableOptionalFacts(draft);
        List<String> factIssues = factLedger.validateSource(draft);
        if (!factIssues.isEmpty()) {
            AiStructuredTextResult repairedFactResult = aiCapabilityService.createStructuredText(
                    factExtractionFactory.createRepair(
                            context,
                            draft,
                            factResult.getParsedJson(),
                            factIssues
                    )
            );
            if (repairedFactResult == null
                    || !repairedFactResult.isSuccess()
                    || repairedFactResult.getParsedJson() == null) {
                return unavailable("商品事实自动校正暂时不可用：", repairedFactResult, warningsFrom(repairedFactResult));
            }
            factResult = repairedFactResult;
            factLedger = ProductListingAiFactLedger.from(repairedFactResult.getParsedJson())
                    .withoutUntraceableOptionalFacts(draft);
            factIssues = factLedger.validateSource(draft);
            if (!factIssues.isEmpty()) {
                return ProductListingAiListingView.unavailable(
                        RULE_VERSION,
                        "ai",
                        "AI 未能从现有商品资料建立可靠事实依据，请稍后重新生成。",
                        List.of()
                );
            }
        }

        AiStructuredTextResult aiResult = aiCapabilityService.createStructuredText(
                requestFactory.create(context, normalizedCommand, draft, competitorMaterials, factLedger)
        );
        if (!usable(aiResult)) {
            aiResult = aiCapabilityService.createStructuredText(
                    requestFactory.create(context, normalizedCommand, draft, competitorMaterials, factLedger)
            );
        }
        if (usable(aiResult)) {
            ProductListingAiValidationResult validationResult = ProductListingAiDraftValidator.inspect(
                    draft,
                    factLedger,
                    aiResult.getParsedJson()
            );
            if (validationResult.isReady()) {
                return ProductListingAiListingView.of(
                        RULE_VERSION,
                        aiResult.getParsedJson(),
                        "ai",
                        warningsFrom(aiResult)
                );
            }

            AiStructuredTextResult repairResult = aiCapabilityService.createStructuredText(
                    requestFactory.createRepair(
                            context,
                            normalizedCommand,
                            draft,
                            competitorMaterials,
                            factLedger,
                            aiResult.getParsedJson(),
                            validationResult
                    )
            );
            if (repairResult == null || !repairResult.isSuccess() || repairResult.getParsedJson() == null) {
                return unavailable("AI Listing 自动修复暂时不可用：", repairResult, warningsFrom(repairResult));
            }
            ProductListingAiValidationResult repairedValidation = ProductListingAiDraftValidator.inspect(
                    draft,
                    factLedger,
                    repairResult.getParsedJson()
            );
            if (repairedValidation.isReady()) {
                return ProductListingAiListingView.of(
                        RULE_VERSION,
                        repairResult.getParsedJson(),
                        "ai",
                        warningsFrom(repairResult)
                );
            }
            if (repairedValidation.hasHardConflicts()) {
                return hardConflict();
            }
            return ProductListingAiListingView.unavailable(
                    RULE_VERSION,
                    "ai",
                    "AI 未能根据现有商品资料生成可用 Listing，请稍后重新生成。",
                    List.of()
            );
        }

        return unavailable("商品上架 AI 整合暂时不可用：", aiResult, warningsFrom(aiResult));
    }

    private boolean usable(AiStructuredTextResult result) {
        return result != null && result.isSuccess() && result.getParsedJson() != null;
    }

    private ProductListingAiListingView hardConflict() {
        return ProductListingAiListingView.unavailable(
                RULE_VERSION,
                "ai",
                "现有商品资料存在互相冲突的核心事实，AI 无法可靠生成 Listing。",
                List.of()
        );
    }

    private ProductListingAiListingView unavailable(
            String prefix,
            AiStructuredTextResult aiResult,
            List<String> warnings
    ) {
        return ProductListingAiListingView.unavailable(
                RULE_VERSION,
                "ai",
                prefix + aiErrorMessage(aiResult),
                warnings
        );
    }

    private boolean hasDraftFacts(ProductListingDraftCommand draft) {
        return StringUtils.hasText(draft.getProductTitleCn())
                || StringUtils.hasText(draft.getProductTitleEn())
                || StringUtils.hasText(draft.getProductTitleAr())
                || StringUtils.hasText(draft.getProductDescriptionCn())
                || StringUtils.hasText(draft.getProductDescriptionEn())
                || StringUtils.hasText(draft.getProductDescriptionAr())
                || !normalizeTexts(draft.getProductHighlightsCn()).isEmpty()
                || !normalizeTexts(draft.getProductHighlightsEn()).isEmpty()
                || !normalizeTexts(draft.getProductHighlightsAr()).isEmpty()
                || hasVerifiedAttributeFacts(draft.getKeyAttributes());
    }

    private boolean hasVerifiedAttributeFacts(List<Map<String, Object>> attributes) {
        if (attributes == null) {
            return false;
        }
        return attributes.stream().anyMatch(attribute -> attribute != null && List.of(
                "commonValue", "enValue", "arValue"
        ).stream().anyMatch(field -> {
            Object value = attribute.get(field);
            return value != null && StringUtils.hasText(String.valueOf(value));
        }));
    }

    private List<ProductListingAiCompetitorMaterial> usefulCompetitorMaterials(List<ProductListingAiCompetitorMaterial> materials) {
        if (materials == null) {
            return List.of();
        }
        return materials.stream()
                .filter(this::hasCompetitorContent)
                .collect(Collectors.toList());
    }

    private boolean hasCompetitorContent(ProductListingAiCompetitorMaterial material) {
        if (material == null) {
            return false;
        }
        return StringUtils.hasText(material.getTitleEn())
                || StringUtils.hasText(material.getTitleAr())
                || StringUtils.hasText(material.getDescriptionEn())
                || StringUtils.hasText(material.getDescriptionAr())
                || !normalizeTexts(material.getSellingPointsEn()).isEmpty()
                || !normalizeTexts(material.getSellingPointsAr()).isEmpty();
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

    private String aiErrorMessage(AiStructuredTextResult aiResult) {
        if (aiResult != null && "OPENAI_API_KEY_MISSING".equalsIgnoreCase(aiResult.getErrorCode())) {
            return "后端未配置 OPENAI_API_KEY，请配置后重启服务。";
        }
        if (aiResult != null && StringUtils.hasText(aiResult.getErrorMessage())) {
            return aiResult.getErrorMessage();
        }
        return "AI 未返回可用 Listing 结果。";
    }

    private List<String> normalizeTexts(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }

}
