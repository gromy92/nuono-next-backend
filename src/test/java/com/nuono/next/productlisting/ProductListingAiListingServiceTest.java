package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ProductListingAiListingServiceTest {

    @Mock
    private ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider;

    @Mock
    private AiCapabilityService aiCapabilityService;

    @Test
    void shouldGenerateStructuredNoonListingWithVersionedRules() {
        ProductListingAiListingService service = new ProductListingAiListingService(
                aiCapabilityServiceProvider,
                new ObjectMapper()
        );
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        aiResult.setParsedJson(successPayload());
        aiResult.setWarnings(List.of("review-missing-dimension"));
        when(aiCapabilityService.createStructuredText(org.mockito.ArgumentMatchers.any())).thenReturn(aiResult);

        ProductListingAiListingCommand command = new ProductListingAiListingCommand();
        command.setOperatorRequirement("偏中东家庭收纳场景，避免夸大材质。");
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setStoreCode("STR245027-NSA");
        draft.setProductTitleCn("桌面收纳盒");
        draft.setProductFullType("Home Organization");
        draft.setProductHighlightsCn(List.of("可放遥控器和小物件"));
        command.setDraft(draft);
        ProductListingAiCompetitorMaterial competitor = new ProductListingAiCompetitorMaterial();
        competitor.setTitleEn("Desk organizer for remote controls");
        competitor.setSellingPointsEn(List.of("Keeps tables tidy"));
        command.setCompetitorMaterials(List.of(competitor));

        ProductListingAiListingView view = service.generate(context(), command);

        assertTrue(view.isReady());
        assertEquals(ProductListingAiListingService.RULE_VERSION, view.getRuleVersion());
        assertEquals("ai", view.getSource());
        assertEquals("Generated English title", ((Map<?, ?>) view.getData().get("noonUploadDraft")).get("productTitleEn"));
        assertEquals(List.of("review-missing-dimension"), view.getWarnings());

        ArgumentCaptor<AiStructuredTextCommand> captor = ArgumentCaptor.forClass(AiStructuredTextCommand.class);
        verify(aiCapabilityService).createStructuredText(captor.capture());
        AiStructuredTextCommand aiCommand = captor.getValue();
        assertEquals("product-listing", aiCommand.getFeatureCode());
        assertEquals("noon_listing_bilingual_generate", aiCommand.getOperationCode());
        assertEquals("nuono_product_listing_noon_bilingual_v3_2", aiCommand.getSchemaName());
        assertEquals("medium", aiCommand.getReasoningEffort());
        assertEquals(7000, aiCommand.getMaxOutputTokens());
        assertEquals(120, aiCommand.getTimeoutSeconds());
        assertEquals(90002L, aiCommand.getOperatorUserId());
        assertTrue(aiCommand.getInstructions().contains("No-Fabrication Guardrails"));
        assertTrue(aiCommand.getInstructions().contains("Competitor listings are references"));
        assertTrue(aiCommand.getInstructions().contains("Arabic must be natural ecommerce Arabic"));
        assertTrue(aiCommand.getInstructions().contains("Markdown bold"));
        assertTrue(aiCommand.getPrompt().contains("桌面收纳盒"));
        assertTrue(aiCommand.getPrompt().contains("Desk organizer for remote controls"));
        assertTrue(aiCommand.getPrompt().contains("\"site\":\"SA\""));
        assertTrue(aiCommand.getSchema().containsKey("properties"));
    }

    @Test
    void shouldReturnUnavailableWhenAiServiceMissing() {
        ProductListingAiListingService service = new ProductListingAiListingService(
                aiCapabilityServiceProvider,
                new ObjectMapper()
        );
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(null);
        ProductListingAiListingCommand command = new ProductListingAiListingCommand();
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setProductTitleCn("桌面收纳盒");
        command.setDraft(draft);

        ProductListingAiListingView view = service.generate(context(), command);

        assertFalse(view.isReady());
        assertEquals(ProductListingAiListingService.RULE_VERSION, view.getRuleVersion());
        assertTrue(view.getMessage().contains("AI"));
        assertTrue(view.getWarnings().contains("AI_SERVICE_MISSING"));
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(90002L)
                .build();
    }

    private Map<String, Object> successPayload() {
        return Map.ofEntries(
                Map.entry("inputCompleteness", Map.of(
                        "summary", "facts usable",
                        "missingCritical", List.of(),
                        "missingOptional", List.of("dimensions")
                )),
                Map.entry("productUnderstanding", Map.of(
                        "productType", "desk organizer",
                        "buyerUseCases", List.of("home desk"),
                        "confirmedFacts", List.of("organizer")
                )),
                Map.entry("styleDecision", Map.of("style", "practical", "rationale", "utility item")),
                Map.entry("keywords", Map.of("english", List.of("desk organizer"), "arabic", List.of("منظم مكتب"))),
                Map.entry("attributeGuardrails", Map.of(
                        "confirmedAttributes", List.of("organizer"),
                        "usableSellingPoints", List.of("tidy desk"),
                        "forbiddenClaims", List.of("premium material")
                )),
                Map.entry("listingStrategy", Map.of("english", "lead with organizer use", "arabic", "localized home context")),
                Map.entry("englishListing", Map.of(
                        "title", "Generated English title",
                        "bullets", List.of("【TIDY STORAGE】 - Keeps daily items organized."),
                        "longDescription", "Generated English long description"
                )),
                Map.entry("arabicListing", Map.of(
                        "title", "عنوان عربي",
                        "bullets", List.of("【تنظيم عملي】 - يساعد على ترتيب الأغراض اليومية."),
                        "longDescription", "وصف عربي"
                )),
                Map.entry("qualityCheck", Map.of(
                        "score", 86,
                        "findings", List.of("需确认尺寸"),
                        "uploadNotes", List.of("去除 review-only 标记"),
                        "removeMarkdownBeforeUpload", true
                )),
                Map.entry("warnings", List.of("missing dimensions")),
                Map.entry("needsHumanConfirmation", List.of("尺寸")),
                Map.entry("noonUploadDraft", Map.of(
                        "productTitleEn", "Generated English title",
                        "productTitleAr", "عنوان عربي",
                        "productHighlightsEn", List.of("【TIDY STORAGE】 - Keeps daily items organized."),
                        "productHighlightsAr", List.of("【تنظيم عملي】 - يساعد على ترتيب الأغراض اليومية."),
                        "productDescriptionEn", "Generated English long description",
                        "productDescriptionAr", "وصف عربي"
                ))
        );
    }
}
