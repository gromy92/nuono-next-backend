package com.nuono.next.imagematch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiInvocationLogSink;
import com.nuono.next.ai.AiJsonSchemaValidator;
import com.nuono.next.ai.AiModelClient;
import com.nuono.next.ai.AiProperties;
import com.nuono.next.ai.AiResultStatus;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ImageMatchServiceTest {

    @Test
    void compareUploadedImagesReturnsOnlySimilarityScore() {
        CapturingAiService ai = new CapturingAiService(success(87));
        ImageMatchService service = new ImageMatchService(ai);
        ImageMatchCommand command = uploadedCommand();

        ImageMatchView view = service.compare(command);

        assertEquals(87, view.getSimilarityScore());
        assertEquals("PRODUCT_IMAGE_MATCH", ai.command.getFeatureCode());
        assertEquals("COMPARE_SAME_PRODUCT_SCORE", ai.command.getOperationCode());
        assertEquals(2, ai.command.getInputAttachments().size());
        assertFalse(ai.command.getPrompt().toLowerCase().contains("reason"));
    }

    @Test
    void missingImageFailsWithoutCallingAi() {
        CapturingAiService ai = new CapturingAiService(success(87));
        ImageMatchService service = new ImageMatchService(ai);
        ImageMatchCommand command = new ImageMatchCommand();
        command.setCandidateUpload("candidate.png", "image/png", new byte[] {1});

        ImageMatchException error = assertThrows(ImageMatchException.class, () -> service.compare(command));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertNull(ai.command);
    }

    @Test
    void unsupportedMimeTypeFailsWithoutCallingAi() {
        CapturingAiService ai = new CapturingAiService(success(87));
        ImageMatchService service = new ImageMatchService(ai);
        ImageMatchCommand command = new ImageMatchCommand();
        command.setOriginalUpload("original.gif", "image/gif", new byte[] {1});
        command.setCandidateUpload("candidate.png", "image/png", new byte[] {2});

        ImageMatchException error = assertThrows(ImageMatchException.class, () -> service.compare(command));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertNull(ai.command);
    }

    @Test
    void aiFailureReturnsErrorInsteadOfScore() {
        CapturingAiService ai = new CapturingAiService(
                AiStructuredTextResult.failure(AiResultStatus.AI_DISABLED, "AI_DISABLED", "AI disabled")
        );
        ImageMatchService service = new ImageMatchService(ai);

        ImageMatchException error = assertThrows(ImageMatchException.class, () -> service.compare(uploadedCommand()));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
    }

    @Test
    void schemaInvalidAiOutputReturnsErrorInsteadOfScore() {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setParsedJson(Map.of("score", 87));
        CapturingAiService ai = new CapturingAiService(result);
        ImageMatchService service = new ImageMatchService(ai);

        ImageMatchException error = assertThrows(ImageMatchException.class, () -> service.compare(uploadedCommand()));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
    }

    @Test
    void privateImageUrlIsRejectedBeforeNetworkAccess() {
        CapturingAiService ai = new CapturingAiService(success(87));
        ImageMatchService service = new ImageMatchService(ai);
        ImageMatchCommand command = new ImageMatchCommand();
        command.setOriginalImageUrl("http://127.0.0.1/private.png");
        command.setCandidateUpload("candidate.png", "image/png", new byte[] {2});

        ImageMatchException error = assertThrows(ImageMatchException.class, () -> service.compare(command));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertNull(ai.command);
    }

    private static ImageMatchCommand uploadedCommand() {
        ImageMatchCommand command = new ImageMatchCommand();
        command.setOriginalUpload("original.jpg", "image/jpeg", new byte[] {1, 2, 3});
        command.setCandidateUpload("candidate.jpg", "image/jpeg", new byte[] {4, 5, 6});
        return command;
    }

    private static AiStructuredTextResult success(int score) {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setModel("gpt-5.5");
        result.setParsedJson(object("similarityScore", score));
        return result;
    }

    private static Map<String, Object> object(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static class CapturingAiService extends AiCapabilityService {

        private AiStructuredTextCommand command;
        private final AiStructuredTextResult result;

        CapturingAiService(AiStructuredTextResult result) {
            super(enabledProperties(), noopClient(), new AiJsonSchemaValidator(), noopLogSink());
            this.result = result;
        }

        @Override
        public AiStructuredTextResult createStructuredText(AiStructuredTextCommand command) {
            this.command = command;
            return result;
        }
    }

    private static AiProperties enabledProperties() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.getOpenai().setApiKey("test");
        return properties;
    }

    private static AiModelClient noopClient() {
        return command -> AiStructuredTextResult.failure("AI_PROVIDER_ERROR", "NOOP", "noop");
    }

    private static AiInvocationLogSink noopLogSink() {
        return entry -> {
        };
    }
}
