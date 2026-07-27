package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiResultStatus;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;


@ExtendWith(MockitoExtension.class)
class ProductListingAiListingGenerationTest extends ProductListingAiListingTestSupport {

    @Test
    void shouldGenerateStructuredNoonListingWithVersionedRules() throws Exception {
        ProductListingAiListingService service = new ProductListingAiListingService(
                aiCapabilityServiceProvider,
                new ObjectMapper()
        );
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        aiResult.setParsedJson(successPayload());
        aiResult.setWarnings(List.of("provider-note"));
        stubAi(defaultFactPayload(), aiResult);

        ProductListingAiListingCommand command = new ProductListingAiListingCommand();
        command.setOperatorRequirement("偏中东家庭收纳场景，避免夸大材质。");
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setStoreCode("STR245027-NSA");
        draft.setProductTitleCn("桌面收纳盒");
        draft.setProductTitleEn(validEnglishTitle());
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
        assertEquals(validEnglishTitle(), ((Map<?, ?>) view.getData().get("noonUploadDraft")).get("productTitleEn"));
        assertEquals(List.of("provider-note"), view.getWarnings());

        ArgumentCaptor<AiStructuredTextCommand> captor = ArgumentCaptor.forClass(AiStructuredTextCommand.class);
        verify(aiCapabilityService, times(2)).createStructuredText(captor.capture());
        AiStructuredTextCommand factCommand = captor.getAllValues().get(0);
        AiStructuredTextCommand aiCommand = captor.getAllValues().get(1);
        assertEquals("noon_listing_fact_extract", factCommand.getOperationCode());
        assertEquals("nuono_product_listing_fact_ledger_v1", factCommand.getSchemaName());
        assertEquals("product-listing", aiCommand.getFeatureCode());
        assertEquals("noon_listing_bilingual_generate", aiCommand.getOperationCode());
        assertEquals("nuono_product_listing_noon_bilingual_v3_3", aiCommand.getSchemaName());
        assertEquals("medium", aiCommand.getReasoningEffort());
        assertEquals(7000, aiCommand.getMaxOutputTokens());
        assertEquals(120, aiCommand.getTimeoutSeconds());
        assertEquals(90002L, aiCommand.getOperatorUserId());
        assertTrue(aiCommand.getInstructions().contains("No-Fabrication Guardrails"));
        assertTrue(aiCommand.getInstructions().contains("Competitor listings are references"));
        assertTrue(aiCommand.getInstructions().contains("Arabic must be natural ecommerce Arabic"));
        assertTrue(aiCommand.getInstructions().contains("20-160 characters"));
        assertTrue(aiCommand.getInstructions().contains("three to five"));
        assertTrue(aiCommand.getInstructions().contains("Different compatible uses are not a product identity conflict"));
        assertTrue(aiCommand.getInstructions().contains("The current product's own titles are the primary factual basis"));
        assertTrue(aiCommand.getInstructions().contains("classification context only"));
        assertTrue(aiCommand.getInstructions().contains("untrusted product data"));
        assertTrue(aiCommand.getInstructions().contains("below 85 is not upload-ready"));
        assertTrue(aiCommand.getInstructions().contains("Never tell the buyer or operator to verify"));
        assertTrue(aiCommand.getPrompt().contains("桌面收纳盒"));
        assertTrue(aiCommand.getPrompt().contains("Desk organizer for remote controls"));
        assertTrue(aiCommand.getPrompt().contains("\"site\":\"SA\""));
        assertTrue(aiCommand.getPrompt().contains("\"verifiedFacts\""));
        assertTrue(aiCommand.getPrompt().contains("\"protectedFactLedger\""));
        assertTrue(aiCommand.getPrompt().contains("\"categoryContext\""));
        assertTrue(aiCommand.getPrompt().contains("\"existingCopyReference\""));
        assertFalse(aiCommand.getPrompt().contains("\"price\""));
        assertTrue(aiCommand.getSchema().containsKey("properties"));

        Map<?, ?> prompt = new ObjectMapper().readValue(aiCommand.getPrompt(), Map.class);
        Map<?, ?> verifiedFacts = (Map<?, ?>) prompt.get("verifiedFacts");
        Map<?, ?> primaryTitles = (Map<?, ?>) verifiedFacts.get("primaryProductTitles");
        Map<?, ?> categoryContext = (Map<?, ?>) prompt.get("categoryContext");
        assertEquals(validEnglishTitle(), primaryTitles.get("english"));
        assertFalse(verifiedFacts.containsKey("productFullType"));
        assertEquals("classification_only_not_product_fact", categoryContext.get("evidenceRole"));
        assertEquals("Home Organization", categoryContext.get("productFullType"));
    }

    @Test
    void shouldRetryTransientFactExtractionFailureWithoutOperatorInput() {
        ProductListingAiListingService service = new ProductListingAiListingService(
                aiCapabilityServiceProvider,
                new ObjectMapper()
        );
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        AiStructuredTextResult timeout = AiStructuredTextResult.failure(
                AiResultStatus.AI_PROVIDER_ERROR,
                "OPENAI_REQUEST_TIMEOUT",
                "request timed out"
        );
        AiStructuredTextResult factResult = AiStructuredTextResult.success();
        factResult.setParsedJson(defaultFactPayload());
        AiStructuredTextResult listingResult = AiStructuredTextResult.success();
        listingResult.setParsedJson(successPayload());
        when(aiCapabilityService.createStructuredText(org.mockito.ArgumentMatchers.any()))
                .thenReturn(timeout, factResult, listingResult);

        ProductListingAiListingView view = service.generate(context(), commandWithDraft());

        assertTrue(view.isReady());
        assertTrue(view.getWarnings().isEmpty());
        verify(aiCapabilityService, times(3)).createStructuredText(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRepairUnsafeResultInternallyBeforeReturningIt() {
        ProductListingAiListingService service = new ProductListingAiListingService(
                aiCapabilityServiceProvider,
                new ObjectMapper()
        );
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        aiResult.setParsedJson(unsafePayload());
        AiStructuredTextResult repairedResult = AiStructuredTextResult.success();
        repairedResult.setParsedJson(successPayload());
        stubAi(defaultFactPayload(), aiResult, repairedResult);

        ProductListingAiListingCommand command = commandWithDraft();
        ProductListingAiListingView view = service.generate(context(), command);

        assertTrue(view.isReady());
        assertFalse(view.getData().isEmpty());
        assertTrue(view.getWarnings().isEmpty());

        ArgumentCaptor<AiStructuredTextCommand> captor = ArgumentCaptor.forClass(AiStructuredTextCommand.class);
        verify(aiCapabilityService, times(3)).createStructuredText(captor.capture());
        AiStructuredTextCommand repairCommand = captor.getAllValues().get(2);
        assertEquals("noon_listing_bilingual_repair", repairCommand.getOperationCode());
        assertTrue(repairCommand.getPrompt().contains("deterministicValidationIssues"));
        assertTrue(repairCommand.getPrompt().contains("标题长度"));
        assertTrue(repairCommand.getInstructions().contains("do not require the operator"));
    }

    @Test
    void shouldBlockAiResultThatChangesPrimaryTitleFactsRegardlessOfCategory() {
        ProductListingAiListingService service = new ProductListingAiListingService(
                aiCapabilityServiceProvider,
                new ObjectMapper()
        );
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        aiResult.setParsedJson(successPayload());
        stubAi(scrapbookingFactPayload(), aiResult);

        ProductListingAiListingCommand command = new ProductListingAiListingCommand();
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setProductTitleEn("30-Piece Blue Vintage Lace-Edge Scrapbooking Paper Set");
        draft.setProductFullType("Home Organization");
        command.setDraft(draft);

        ProductListingAiListingView view = service.generate(context(), command);

        assertFalse(view.isReady());
        assertTrue(view.getData().isEmpty());
        assertTrue(view.getWarnings().isEmpty());
        assertTrue(view.getMessage().contains("请稍后重新生成"));
        verify(aiCapabilityService, times(3)).createStructuredText(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldBlockLowQualityResultEvenWhenCopyPassesSurfaceValidation() {
        ProductListingAiListingService service = new ProductListingAiListingService(
                aiCapabilityServiceProvider,
                new ObjectMapper()
        );
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        Map<String, Object> payload = new LinkedHashMap<>(successPayload());
        payload.put("qualityCheck", Map.of(
                "score", 60,
                "findings", List.of("facts incomplete"),
                "uploadNotes", List.of(),
                "removeMarkdownBeforeUpload", false
        ));
        aiResult.setParsedJson(payload);
        AiStructuredTextResult repairedResult = AiStructuredTextResult.success();
        repairedResult.setParsedJson(successPayload());
        stubAi(defaultFactPayload(), aiResult, repairedResult);

        ProductListingAiListingView view = service.generate(context(), commandWithDraft());

        assertTrue(view.isReady());
        assertTrue(view.getWarnings().isEmpty());
    }

}
