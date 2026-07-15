package com.nuono.next.ai;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AiCapabilityServiceTest {

    @Test
    void shouldReturnDisabledWhenFeatureSwitchIsOff() {
        AiProperties properties = properties(false);
        StubModelClient client = new StubModelClient(AiStructuredTextResult.success());
        AiCapabilityService service = new AiCapabilityService(properties, client, new AiJsonSchemaValidator(), entry -> {
        });

        AiStructuredTextResult result = service.createStructuredText(command());

        Assertions.assertEquals(AiResultStatus.AI_DISABLED, result.getStatus());
        Assertions.assertEquals(0, client.getCallCount());
    }

    @Test
    void shouldReturnParsedJsonWhenModelOutputMatchesSchema() {
        AiStructuredTextResult modelResult = AiStructuredTextResult.success();
        modelResult.setProvider("openai");
        modelResult.setModel("gpt-5.4-mini");
        modelResult.setOutputText("{\"summary\":\"ok\",\"score\":9}");
        modelResult.setParsedJson(object("summary", "ok", "score", 9));
        StubModelClient client = new StubModelClient(modelResult);
        AiCapabilityService service = new AiCapabilityService(properties(true), client, new AiJsonSchemaValidator(), entry -> {
        });

        AiStructuredTextResult result = service.createStructuredText(command());

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals("ok", result.getParsedJson().get("summary"));
        Assertions.assertEquals(1, client.getCallCount());
    }

    @Test
    void shouldRetryTransientProviderRequestFailureOnce() {
        AiStructuredTextResult transientFailure = AiStructuredTextResult.failure(
                AiResultStatus.AI_PROVIDER_ERROR,
                "OPENAI_REQUEST_FAILED",
                "AI request failed for https://aicodelink.top/v1/responses: Remote host terminated the handshake"
        );
        transientFailure.setProvider("openai");
        transientFailure.setModel("gpt-5.5");
        AiStructuredTextResult modelResult = AiStructuredTextResult.success();
        modelResult.setProvider("openai");
        modelResult.setModel("gpt-5.5");
        modelResult.setOutputText("{\"summary\":\"ok\"}");
        modelResult.setParsedJson(object("summary", "ok", "score", 9));
        StubModelClient client = new StubModelClient(transientFailure, modelResult);
        AiCapabilityService service = new AiCapabilityService(properties(true), client, new AiJsonSchemaValidator(), entry -> {
        });

        AiStructuredTextResult result = service.createStructuredText(command());

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals("ok", result.getParsedJson().get("summary"));
        Assertions.assertEquals(2, client.getCallCount());
    }

    @Test
    void shouldRejectModelOutputThatMissesRequiredSchemaField() {
        AiStructuredTextResult modelResult = AiStructuredTextResult.success();
        modelResult.setProvider("openai");
        modelResult.setModel("gpt-5.4-mini");
        modelResult.setOutputText("{\"summary\":\"ok\"}");
        modelResult.setParsedJson(object("summary", "ok"));
        StubModelClient client = new StubModelClient(modelResult);
        AiCapabilityService service = new AiCapabilityService(properties(true), client, new AiJsonSchemaValidator(), entry -> {
        });

        AiStructuredTextResult result = service.createStructuredText(command());

        Assertions.assertEquals(AiResultStatus.AI_OUTPUT_SCHEMA_INVALID, result.getStatus());
        Assertions.assertEquals("AI_OUTPUT_SCHEMA_INVALID", result.getErrorCode());
        Assertions.assertTrue(result.getErrorMessage().contains("$.score is required"));
    }

    private AiProperties properties(boolean enabled) {
        AiProperties properties = new AiProperties();
        properties.setEnabled(enabled);
        properties.getOpenai().setApiKey("test-api-key");
        return properties;
    }

    private AiStructuredTextCommand command() {
        AiStructuredTextCommand command = new AiStructuredTextCommand();
        command.setFeatureCode("logistics");
        command.setOperationCode("route_score");
        command.setPrompt("summarize route");
        command.setSchemaName("route_score");
        command.setSchema(object(
                "type", "object",
                "required", Arrays.asList("summary", "score"),
                "properties", object(
                        "summary", object("type", "string"),
                        "score", object("type", "integer")
                )
        ));
        return command;
    }

    private Map<String, Object> object(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static class StubModelClient implements AiModelClient {

        private final java.util.List<AiStructuredTextResult> results;
        private int callCount;

        StubModelClient(AiStructuredTextResult... results) {
            this.results = Arrays.asList(results);
        }

        @Override
        public AiStructuredTextResult createStructuredText(AiStructuredTextCommand command) {
            callCount += 1;
            return results.get(Math.min(callCount - 1, results.size() - 1));
        }

        int getCallCount() {
            return callCount;
        }
    }
}
