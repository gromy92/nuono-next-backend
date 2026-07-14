package com.nuono.next.productlisting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiResultStatus;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

@Service
public class ProductListingAiListingService {

    static final String RULE_VERSION = "v3.2";
    static final String RULE_RESOURCE = "ai/product-listing/noon-listing-v3_2.md";

    private final ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider;
    private final ObjectMapper objectMapper;
    private final String rulebook;

    public ProductListingAiListingService(
            ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider,
            ObjectMapper objectMapper
    ) {
        this.aiCapabilityServiceProvider = aiCapabilityServiceProvider;
        this.objectMapper = objectMapper;
        this.rulebook = loadRulebook();
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
            throw new IllegalArgumentException("商品上架 AI 整合需要先填写商品标题、类目、描述、卖点或竞品材料。");
        }

        AiStructuredTextResult aiResult = generateWithAi(context, normalizedCommand, draft, competitorMaterials);
        if (aiResult != null && aiResult.isSuccess() && aiResult.getParsedJson() != null) {
            return ProductListingAiListingView.of(RULE_VERSION, aiResult.getParsedJson(), "ai", warningsFrom(aiResult));
        }

        return ProductListingAiListingView.unavailable(
                RULE_VERSION,
                "ai",
                "商品上架 AI 整合暂时不可用：" + aiErrorMessage(aiResult),
                warningsFrom(aiResult)
        );
    }

    private AiStructuredTextResult generateWithAi(
            BusinessAccessContext context,
            ProductListingAiListingCommand command,
            ProductListingDraftCommand draft,
            List<ProductListingAiCompetitorMaterial> competitorMaterials
    ) {
        AiCapabilityService aiCapabilityService = aiCapabilityServiceProvider.getIfAvailable();
        if (aiCapabilityService == null) {
            return AiStructuredTextResult.failure(AiResultStatus.AI_DISABLED, "AI_SERVICE_MISSING", "AI service is not available");
        }

        AiStructuredTextCommand aiCommand = new AiStructuredTextCommand();
        aiCommand.setFeatureCode("product-listing");
        aiCommand.setOperationCode("noon_listing_bilingual_generate");
        aiCommand.setOperatorUserId(context == null ? null : context.getSessionUserId());
        aiCommand.setSchemaName("nuono_product_listing_noon_bilingual_v3_2");
        aiCommand.setSchema(outputSchema());
        aiCommand.setReasoningEffort("medium");
        aiCommand.setMaxOutputTokens(7000);
        aiCommand.setTimeoutSeconds(120);
        aiCommand.setInstructions(String.join("\n\n",
                rulebook,
                "Return JSON only. The `noonUploadDraft` object is the only text that may be copied into Noon upload fields.",
                "Do not submit, publish, call tools, or claim that a Noon write has happened."
        ));
        aiCommand.setPrompt(prompt(command, draft, competitorMaterials));
        aiCommand.setMetadata(Map.of(
                "feature", "product-listing",
                "operation", "noon_listing_bilingual_generate",
                "ruleVersion", RULE_VERSION
        ));
        return aiCapabilityService.createStructuredText(aiCommand);
    }

    private String prompt(
            ProductListingAiListingCommand command,
            ProductListingDraftCommand draft,
            List<ProductListingAiCompetitorMaterial> competitorMaterials
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleVersion", RULE_VERSION);
        payload.put("operatorRequirement", text(command.getOperatorRequirement()));
        payload.put("storeCode", text(draft.getStoreCode()));
        payload.put("site", siteFromStoreCode(draft.getStoreCode()));
        payload.put("draftFacts", draftFacts(draft));
        payload.put("competitorReferenceMaterials", competitorMaterials.stream()
                .map(this::competitorMaterial)
                .collect(Collectors.toList()));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("商品上架 AI 整合请求序列化失败。", exception);
        }
    }

    private Map<String, Object> draftFacts(ProductListingDraftCommand draft) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("psku", text(draft.getPsku()));
        facts.put("productFullType", text(draft.getProductFullType()));
        facts.put("family", text(draft.getFamily()));
        facts.put("productType", text(draft.getProductType()));
        facts.put("productSubType", text(draft.getProductSubType()));
        facts.put("brand", text(draft.getProductBrand()));
        facts.put("brandCode", text(draft.getProductBrandCode()));
        facts.put("titleCn", text(draft.getProductTitleCn()));
        facts.put("titleEn", text(draft.getProductTitleEn()));
        facts.put("titleAr", text(draft.getProductTitleAr()));
        facts.put("descriptionCn", text(draft.getProductDescriptionCn()));
        facts.put("descriptionEn", text(draft.getProductDescriptionEn()));
        facts.put("descriptionAr", text(draft.getProductDescriptionAr()));
        facts.put("highlightsCn", normalizeTexts(draft.getProductHighlightsCn()));
        facts.put("highlightsEn", normalizeTexts(draft.getProductHighlightsEn()));
        facts.put("highlightsAr", normalizeTexts(draft.getProductHighlightsAr()));
        facts.put("keyAttributes", draft.getKeyAttributes() == null ? List.of() : draft.getKeyAttributes());
        facts.put("imageCount", draft.getImageUrls() == null ? 0 : normalizeTexts(draft.getImageUrls()).size());
        facts.put("price", decimalText(draft.getPrice()));
        facts.put("priceMin", decimalText(draft.getPriceMin()));
        facts.put("priceMax", decimalText(draft.getPriceMax()));
        facts.put("salePrice", decimalText(draft.getSalePrice()));
        facts.put("barcode", text(draft.getBarcode()));
        return facts;
    }

    private Map<String, Object> competitorMaterial(ProductListingAiCompetitorMaterial material) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", text(material.getId()));
        item.put("url", text(material.getUrl()));
        item.put("note", text(material.getNote()));
        item.put("sourceHost", text(material.getSourceHost()));
        item.put("fetchedAt", text(material.getFetchedAt()));
        item.put("titleEn", text(material.getTitleEn()));
        item.put("titleAr", text(material.getTitleAr()));
        item.put("descriptionEn", text(material.getDescriptionEn()));
        item.put("descriptionAr", text(material.getDescriptionAr()));
        item.put("sellingPointsEn", normalizeTexts(material.getSellingPointsEn()));
        item.put("sellingPointsAr", normalizeTexts(material.getSellingPointsAr()));
        return item;
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
                || StringUtils.hasText(draft.getProductFullType())
                || StringUtils.hasText(draft.getFamily())
                || StringUtils.hasText(draft.getProductType())
                || StringUtils.hasText(draft.getProductSubType())
                || StringUtils.hasText(draft.getProductBrand());
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

    private Map<String, Object> outputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("inputCompleteness", objectSchema(
                List.of("summary", "missingCritical", "missingOptional"),
                Map.of(
                        "summary", stringSchema(),
                        "missingCritical", stringArraySchema(),
                        "missingOptional", stringArraySchema()
                )
        ));
        properties.put("productUnderstanding", objectSchema(
                List.of("productType", "buyerUseCases", "confirmedFacts"),
                Map.of(
                        "productType", stringSchema(),
                        "buyerUseCases", stringArraySchema(),
                        "confirmedFacts", stringArraySchema()
                )
        ));
        properties.put("styleDecision", objectSchema(
                List.of("style", "rationale"),
                Map.of(
                        "style", stringSchema(),
                        "rationale", stringSchema()
                )
        ));
        properties.put("keywords", objectSchema(
                List.of("english", "arabic"),
                Map.of(
                        "english", stringArraySchema(),
                        "arabic", stringArraySchema()
                )
        ));
        properties.put("attributeGuardrails", objectSchema(
                List.of("confirmedAttributes", "usableSellingPoints", "forbiddenClaims"),
                Map.of(
                        "confirmedAttributes", stringArraySchema(),
                        "usableSellingPoints", stringArraySchema(),
                        "forbiddenClaims", stringArraySchema()
                )
        ));
        properties.put("listingStrategy", objectSchema(
                List.of("english", "arabic"),
                Map.of(
                        "english", stringSchema(),
                        "arabic", stringSchema()
                )
        ));
        properties.put("englishListing", listingSchema());
        properties.put("arabicListing", listingSchema());
        properties.put("qualityCheck", objectSchema(
                List.of("score", "findings", "uploadNotes", "removeMarkdownBeforeUpload"),
                Map.of(
                        "score", integerSchema(),
                        "findings", stringArraySchema(),
                        "uploadNotes", stringArraySchema(),
                        "removeMarkdownBeforeUpload", booleanSchema()
                )
        ));
        properties.put("warnings", stringArraySchema());
        properties.put("needsHumanConfirmation", stringArraySchema());
        properties.put("noonUploadDraft", noonUploadDraftSchema());
        return objectSchema(
                List.of(
                        "inputCompleteness",
                        "productUnderstanding",
                        "styleDecision",
                        "keywords",
                        "attributeGuardrails",
                        "listingStrategy",
                        "englishListing",
                        "arabicListing",
                        "qualityCheck",
                        "warnings",
                        "needsHumanConfirmation",
                        "noonUploadDraft"
                ),
                properties
        );
    }

    private Map<String, Object> listingSchema() {
        return objectSchema(
                List.of("title", "bullets", "longDescription"),
                Map.of(
                        "title", stringSchema(),
                        "bullets", stringArraySchema(),
                        "longDescription", stringSchema()
                )
        );
    }

    private Map<String, Object> noonUploadDraftSchema() {
        return objectSchema(
                List.of("productTitleEn", "productTitleAr", "productHighlightsEn", "productHighlightsAr", "productDescriptionEn", "productDescriptionAr"),
                Map.of(
                        "productTitleEn", stringSchema(),
                        "productTitleAr", stringSchema(),
                        "productHighlightsEn", stringArraySchema(),
                        "productHighlightsAr", stringArraySchema(),
                        "productDescriptionEn", stringSchema(),
                        "productDescriptionAr", stringSchema()
                )
        );
    }

    private Map<String, Object> objectSchema(List<String> required, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", required);
        schema.put("properties", properties);
        return schema;
    }

    private Map<String, Object> stringArraySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", stringSchema());
        return schema;
    }

    private Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    private Map<String, Object> integerSchema() {
        return Map.of("type", "integer");
    }

    private Map<String, Object> booleanSchema() {
        return Map.of("type", "boolean");
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

    private String loadRulebook() {
        ClassPathResource resource = new ClassPathResource(RULE_RESOURCE);
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("商品上架 AI 规则材料缺失：" + RULE_RESOURCE, exception);
        }
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

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private String decimalText(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String siteFromStoreCode(String storeCode) {
        String normalized = text(storeCode).toUpperCase(Locale.ROOT);
        int marker = normalized.lastIndexOf("-N");
        if (marker >= 0 && normalized.length() >= marker + 4) {
            return normalized.substring(marker + 2, marker + 4);
        }
        int dash = normalized.lastIndexOf('-');
        if (dash >= 0 && normalized.length() >= dash + 3) {
            return normalized.substring(dash + 1, dash + 3);
        }
        return "";
    }
}
