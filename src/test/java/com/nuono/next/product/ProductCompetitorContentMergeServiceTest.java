package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ProductCompetitorContentMergeServiceTest {

    @Mock
    private ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider;

    @Mock
    private AiCapabilityService aiCapabilityService;

    @Test
    void shouldReturnDraftTextFromAi() {
        ProductCompetitorContentMergeService service = new ProductCompetitorContentMergeService(
                aiCapabilityServiceProvider,
                new ObjectMapper()
        );
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        aiResult.setParsedJson(Map.of("draft", Map.of("text", "Merged English title")));
        aiResult.setWarnings(List.of("minor-warning"));
        when(aiCapabilityService.createStructuredText(org.mockito.ArgumentMatchers.any())).thenReturn(aiResult);

        ProductCompetitorContentMergeCommand command = new ProductCompetitorContentMergeCommand();
        command.setFieldType("title");
        command.setTargetLang("EN");
        command.setCurrentText("Current title");
        command.setCompetitorTexts(List.of("Competitor title A", "Competitor title B"));
        command.setOperatorUserId(30001L);

        ProductCompetitorContentMergeView view = service.merge(command);

        assertTrue(view.isReady());
        assertEquals("ai", view.getSource());
        assertEquals("Merged English title", view.getData().get("draft").get("text"));
        assertEquals(List.of("minor-warning"), view.getWarnings());

        ArgumentCaptor<AiStructuredTextCommand> captor = ArgumentCaptor.forClass(AiStructuredTextCommand.class);
        verify(aiCapabilityService).createStructuredText(captor.capture());
        assertEquals("product-management", captor.getValue().getFeatureCode());
        assertEquals("competitor_content_merge", captor.getValue().getOperationCode());
        assertEquals(30001L, captor.getValue().getOperatorUserId());
        assertEquals("low", captor.getValue().getReasoningEffort());
        assertEquals(220, captor.getValue().getMaxOutputTokens());
        assertEquals(75, captor.getValue().getTimeoutSeconds());
        assertTrue(captor.getValue().getPrompt().contains("Competitor title A"));
    }

    @Test
    void shouldReturnUnavailableWhenAiServiceMissing() {
        ProductCompetitorContentMergeService service = new ProductCompetitorContentMergeService(
                aiCapabilityServiceProvider,
                new ObjectMapper()
        );
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(null);

        ProductCompetitorContentMergeCommand command = new ProductCompetitorContentMergeCommand();
        command.setFieldType("description");
        command.setTargetLang("AR");
        command.setCompetitorTexts(List.of("Competitor description"));

        ProductCompetitorContentMergeView view = service.merge(command);

        assertFalse(view.isReady());
        assertEquals("ai", view.getSource());
        assertTrue(view.getMessage().contains("AI"));
        assertTrue(view.getWarnings().contains("AI_SERVICE_MISSING"));
    }
}
