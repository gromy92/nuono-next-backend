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
class ProductListingAiListingGuardTest extends ProductListingAiListingTestSupport {

    @Test
    void shouldFailClosedBeforeGenerationWhenFactLedgerCannotTraceItsSource() {
        ProductListingAiListingService service = new ProductListingAiListingService(
                aiCapabilityServiceProvider,
                new ObjectMapper()
        );
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        AiStructuredTextResult factResult = AiStructuredTextResult.success();
        factResult.setParsedJson(Map.of(
                "facts", List.of(Map.of(
                        "factId", "F001",
                        "factType", "PRODUCT_IDENTITY",
                        "sourceField", "titleCn",
                        "sourceText", "防水旅行箱",
                        "englishCanonical", "waterproof suitcase",
                        "arabicCanonical", "حقيبة سفر مقاومة للماء",
                        "titleRequired", true
                )),
                "warnings", List.of()
        ));
        when(aiCapabilityService.createStructuredText(org.mockito.ArgumentMatchers.any())).thenReturn(factResult);

        ProductListingAiListingView view = service.generate(context(), commandWithDraft());

        assertFalse(view.isReady());
        assertTrue(view.getData().isEmpty());
        assertTrue(view.getWarnings().isEmpty());
        assertTrue(view.getMessage().contains("可靠事实依据"));
        verify(aiCapabilityService, times(2)).createStructuredText(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldNotBlockOptionalMissingFactsOrReviewNotes() {
        ProductListingAiListingService service = new ProductListingAiListingService(
                aiCapabilityServiceProvider,
                new ObjectMapper()
        );
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        Map<String, Object> payload = new LinkedHashMap<>(successPayload());
        payload.put("needsHumanConfirmation", List.of("尺寸", "材质"));
        aiResult.setParsedJson(payload);
        stubAi(defaultFactPayload(), aiResult);

        ProductListingAiListingView view = service.generate(context(), commandWithDraft());

        assertTrue(view.isReady());
        assertFalse(view.getData().isEmpty());
        assertTrue(view.getWarnings().isEmpty());
    }

    @Test
    void shouldBlockMutuallyExclusiveCriticalFacts() {
        ProductListingAiListingService service = new ProductListingAiListingService(
                aiCapabilityServiceProvider,
                new ObjectMapper()
        );
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        Map<String, Object> payload = new LinkedHashMap<>(successPayload());
        payload.put("inputCompleteness", Map.of(
                "summary", "physical facts conflict",
                "missingCritical", List.of("包装数量同时出现 30 件和 50 件"),
                "missingOptional", List.of()
        ));
        aiResult.setParsedJson(payload);
        stubAi(defaultFactPayload(), aiResult);

        ProductListingAiListingView view = service.generate(context(), commandWithDraft());

        assertFalse(view.isReady());
        assertTrue(view.getData().isEmpty());
        assertTrue(view.getWarnings().isEmpty());
        assertTrue(view.getMessage().contains("互相冲突的核心事实"));
        verify(aiCapabilityService, times(3)).createStructuredText(org.mockito.ArgumentMatchers.any());
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

    @Test
    void shouldNotTreatCategoryOnlyDraftAsProductFactEvidence() {
        ProductListingAiListingService service = new ProductListingAiListingService(
                aiCapabilityServiceProvider,
                new ObjectMapper()
        );
        ProductListingAiListingCommand command = new ProductListingAiListingCommand();
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setProductFullType("Greeting Card Envelopes");
        command.setDraft(draft);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.generate(context(), command)
        );

        assertTrue(error.getMessage().contains("商品标题"));
    }

}
