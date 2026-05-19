package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileParseAiOcrServiceTest {

    @Mock
    private AiCapabilityService aiCapabilityService;

    @Test
    void shouldConvertAiOcrLinesToSourceRows() {
        FileParseAiOcrService service = new FileParseAiOcrService(
                aiCapabilityService,
                "test-model",
                "low",
                8000,
                120
        );
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        aiResult.setModel("test-model");
        aiResult.setParsedJson(Map.of(
                "lines",
                List.of(
                        Map.of("pageNo", 1, "lineNo", 1, "text", "Category Commission"),
                        Map.of("pageNo", 1, "lineNo", 2, "text", "Fashion 15%")
                )
        ));
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiResult);

        FileParseTaskInputRow input = new FileParseTaskInputRow();
        input.setId(30001L);
        input.setFileAssetId(10001L);
        FileParseInputAttachment attachment = new FileParseInputAttachment(
                "commission.png",
                "image/png",
                new byte[]{1, 2, 3},
                30001L,
                10001L
        );

        Optional<FileParseInputExtraction> extraction = service.extractOcrRows(
                input,
                attachment,
                "image_ocr_block",
                "ai-image-ocr"
        );

        assertTrue(extraction.isPresent());
        assertEquals("extracted", extraction.get().getStatus());
        assertEquals(2, extraction.get().getSourceRows().size());
        assertEquals("image_ocr_block", extraction.get().getSourceRows().get(0).getSourceType());
        assertEquals("page=1;line=1", extraction.get().getSourceRows().get(0).getSourceLocator());
        assertTrue(extraction.get().getExtractedText().contains("Fashion 15%"));

        ArgumentCaptor<AiStructuredTextCommand> commandCaptor = ArgumentCaptor.forClass(AiStructuredTextCommand.class);
        org.mockito.Mockito.verify(aiCapabilityService).createStructuredText(commandCaptor.capture());
        assertEquals("source-ocr", commandCaptor.getValue().getOperationCode());
        assertEquals(1, commandCaptor.getValue().getInputAttachments().size());
    }

    @Test
    void shouldReturnEmptyWhenAiOcrFails() {
        FileParseAiOcrService service = new FileParseAiOcrService(
                aiCapabilityService,
                "test-model",
                "low",
                8000,
                120
        );
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class)))
                .thenReturn(AiStructuredTextResult.failure("AI_PROVIDER_ERROR", "TIMEOUT", "timeout"));

        FileParseTaskInputRow input = new FileParseTaskInputRow();
        input.setId(30001L);
        FileParseInputAttachment attachment = new FileParseInputAttachment(
                "scan.pdf",
                "application/pdf",
                new byte[]{1, 2, 3}
        );

        Optional<FileParseInputExtraction> extraction = service.extractOcrRows(
                input,
                attachment,
                "pdf_ocr_line",
                "ai-pdf-ocr"
        );

        assertTrue(extraction.isEmpty());
    }
}
