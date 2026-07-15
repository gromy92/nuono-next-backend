package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiStructuredTextResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ProductContentTranslationServiceTest {

    @Mock
    private ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider;

    @Mock
    private AiCapabilityService aiCapabilityService;

    @Test
    void shouldRejectAiTranslationThatDoesNotMatchTargetLanguage() {
        ProductContentTranslationService service = new ProductContentTranslationService(
                aiCapabilityServiceProvider,
                new ObjectMapper()
        );
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        aiResult.setParsedJson(Map.of("translation", Map.of("text", "جراب مغناطيسي لهاتف iPhone 17")));
        when(aiCapabilityService.createStructuredText(org.mockito.ArgumentMatchers.any())).thenReturn(aiResult);

        ProductContentTranslateCommand command = new ProductContentTranslateCommand();
        command.setText("جراب مغناطيسي لهاتف iPhone 17");
        command.setSourceLang("AR");
        command.setTargetLang("ZH");

        ProductContentTranslateView view = service.translate(command);

        assertFalse(view.isReady());
        assertEquals("ai", view.getSource());
        assertTrue(view.getMessage().contains("AI 返回的翻译不是中文"));
        assertTrue(view.getWarnings().contains("AI_TRANSLATION_TARGET_LANG_MISMATCH"));
    }
}
