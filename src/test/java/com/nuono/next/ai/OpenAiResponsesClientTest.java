package com.nuono.next.ai;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OpenAiResponsesClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildStructuredOutputsRequestPayload() {
        AiProperties properties = properties();
        properties.getOpenai().setReasoningEffort("high");
        properties.getOpenai().setIncludeMetadata(true);
        OpenAiResponsesClient client = new OpenAiResponsesClient(properties, objectMapper);
        AiStructuredTextCommand command = new AiStructuredTextCommand();
        command.setModel("gpt-5.4-mini");
        command.setInstructions("Return JSON only.");
        command.setPrompt("score logistics route");
        command.setSchemaName("route_score");
        command.setMetadata(object("feature", "logistics"));
        command.setSchema(object(
                "type", "object",
                "required", Arrays.asList("summary"),
                "properties", object("summary", object("type", "string"))
        ));

        Map<String, Object> payload = client.buildRequestPayload(command);

        Assertions.assertEquals("gpt-5.4-mini", payload.get("model"));
        Assertions.assertEquals("score logistics route", payload.get("input"));
        Assertions.assertEquals(Boolean.FALSE, payload.get("store"));
        Assertions.assertEquals(object("feature", "logistics"), payload.get("metadata"));
        Map<?, ?> reasoning = (Map<?, ?>) payload.get("reasoning");
        Assertions.assertEquals("high", reasoning.get("effort"));
        Map<?, ?> text = (Map<?, ?>) payload.get("text");
        Map<?, ?> format = (Map<?, ?>) text.get("format");
        Assertions.assertEquals("json_schema", format.get("type"));
        Assertions.assertEquals("route_score", format.get("name"));
        Assertions.assertEquals(Boolean.TRUE, format.get("strict"));
    }

    @Test
    void shouldOmitMetadataWhenProviderDoesNotSupportIt() {
        AiProperties properties = properties();
        properties.getOpenai().setIncludeMetadata(false);
        OpenAiResponsesClient client = new OpenAiResponsesClient(properties, objectMapper);
        AiStructuredTextCommand command = new AiStructuredTextCommand();
        command.setPrompt("score logistics route");
        command.setMetadata(object("feature", "logistics"));

        Map<String, Object> payload = client.buildRequestPayload(command);

        Assertions.assertFalse(payload.containsKey("metadata"));
    }

    @Test
    void shouldUseLongerDefaultTimeoutForSlowResponsesProvider() {
        AiProperties properties = new AiProperties();

        Assertions.assertEquals(180, properties.getOpenai().getTimeoutSeconds());
    }

    @Test
    void shouldLetCommandOverrideRuntimeOptions() {
        AiProperties properties = properties();
        properties.getOpenai().setReasoningEffort("high");
        properties.getOpenai().setMaxOutputTokens(1200);
        OpenAiResponsesClient client = new OpenAiResponsesClient(properties, objectMapper);
        AiStructuredTextCommand command = new AiStructuredTextCommand();
        command.setModel("gpt-5.4-mini");
        command.setReasoningEffort("low");
        command.setMaxOutputTokens(400);
        command.setPrompt("extract rows");

        Map<String, Object> payload = client.buildRequestPayload(command);

        Assertions.assertEquals("gpt-5.4-mini", payload.get("model"));
        Assertions.assertEquals(400, payload.get("max_output_tokens"));
        Map<?, ?> reasoning = (Map<?, ?>) payload.get("reasoning");
        Assertions.assertEquals("low", reasoning.get("effort"));
    }

    @Test
    void shouldBuildMultimodalRequestPayloadWithFileAndImageAttachments() {
        OpenAiResponsesClient client = new OpenAiResponsesClient(properties(), objectMapper);
        AiStructuredTextCommand command = new AiStructuredTextCommand();
        command.setModel("gpt-5.4-mini");
        command.setPrompt("extract structured rows");
        command.setInputAttachments(List.of(
                new AiInputAttachment("quote.pdf", "application/pdf", "pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                new AiInputAttachment("scan.png", "image/png", "png".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        ));

        Map<String, Object> payload = client.buildRequestPayload(command);

        Object input = payload.get("input");
        Assertions.assertTrue(input instanceof List);
        Map<?, ?> message = (Map<?, ?>) ((List<?>) input).get(0);
        Assertions.assertEquals("user", message.get("role"));
        List<?> content = (List<?>) message.get("content");
        Assertions.assertEquals("input_text", ((Map<?, ?>) content.get(0)).get("type"));
        Assertions.assertEquals("input_file", ((Map<?, ?>) content.get(1)).get("type"));
        Assertions.assertEquals("quote.pdf", ((Map<?, ?>) content.get(1)).get("filename"));
        Assertions.assertTrue(String.valueOf(((Map<?, ?>) content.get(1)).get("file_data")).startsWith("data:application/pdf;base64,"));
        Assertions.assertEquals("input_image", ((Map<?, ?>) content.get(2)).get("type"));
        Assertions.assertTrue(String.valueOf(((Map<?, ?>) content.get(2)).get("image_url")).startsWith("data:image/png;base64,"));
    }

    @Test
    void shouldParseResponsesApiOutputTextAndUsage() {
        OpenAiResponsesClient client = new OpenAiResponsesClient(properties(), objectMapper);
        String responseBody = "{"
                + "\"id\":\"resp_test\","
                + "\"status\":\"completed\","
                + "\"model\":\"gpt-5.4-mini\","
                + "\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"summary\\\":\\\"ok\\\",\\\"score\\\":8}\"}]}],"
                + "\"usage\":{\"input_tokens\":12,\"output_tokens\":7,\"total_tokens\":19}"
                + "}";

        AiStructuredTextResult result = client.parseResponse(responseBody);

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals("resp_test", result.getResponseId());
        Assertions.assertEquals("ok", result.getParsedJson().get("summary"));
        Assertions.assertEquals(12, result.getUsage().getInputTokens());
        Assertions.assertEquals(7, result.getUsage().getOutputTokens());
        Assertions.assertEquals(19, result.getUsage().getTotalTokens());
    }

    @Test
    void shouldParseRefusalAsControlledStatus() {
        OpenAiResponsesClient client = new OpenAiResponsesClient(properties(), objectMapper);
        String responseBody = "{"
                + "\"id\":\"resp_test\","
                + "\"status\":\"completed\","
                + "\"model\":\"gpt-5.4-mini\","
                + "\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"Cannot help with that.\"}]}]"
                + "}";

        AiStructuredTextResult result = client.parseResponse(responseBody);

        Assertions.assertEquals(AiResultStatus.AI_REFUSED, result.getStatus());
        Assertions.assertEquals("OPENAI_REFUSAL", result.getErrorCode());
        Assertions.assertEquals("Cannot help with that.", result.getRefusal());
    }

    @Test
    void shouldReturnLocalizedRequestFailureMessageWithoutEndpointUrl() {
        AiProperties properties = properties();
        properties.getOpenai().setBaseUrl("file:///tmp/aicodelink");
        OpenAiResponsesClient client = new OpenAiResponsesClient(properties, objectMapper);
        AiStructuredTextCommand command = new AiStructuredTextCommand();
        command.setPrompt("merge competitor title");

        AiStructuredTextResult result = client.createStructuredText(command);

        Assertions.assertEquals(AiResultStatus.AI_PROVIDER_ERROR, result.getStatus());
        Assertions.assertEquals("OPENAI_REQUEST_FAILED", result.getErrorCode());
        Assertions.assertTrue(result.getErrorMessage().contains("AI 服务连接失败"));
        Assertions.assertFalse(result.getErrorMessage().contains("file:///tmp/aicodelink"));
    }

    @Test
    void shouldSendExplicitUserAgentToProvider() throws Exception {
        AtomicReference<String> userAgent = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] response = ("{"
                    + "\"id\":\"resp_user_agent\","
                    + "\"status\":\"completed\","
                    + "\"model\":\"gpt-5.4-mini\","
                    + "\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"summary\\\":\\\"ok\\\"}\"}]}]"
                    + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });
        server.start();
        try {
            AiProperties properties = properties();
            properties.getOpenai().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            OpenAiResponsesClient client = new OpenAiResponsesClient(properties, objectMapper);
            AiStructuredTextCommand command = new AiStructuredTextCommand();
            command.setPrompt("merge competitor title");

            AiStructuredTextResult result = client.createStructuredText(command);

            Assertions.assertTrue(result.isSuccess(), result.getErrorMessage());
            Assertions.assertEquals("NuonoNext-AI/1.0", userAgent.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldFallbackToCurlWhenEnabledAndJavaConnectionIsClosed() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(isCurlAvailable());
        List<String> userAgents = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            if (userAgents.size() == 1) {
                exchange.close();
                return;
            }
            byte[] response = ("{"
                    + "\"id\":\"resp_curl_fallback\","
                    + "\"status\":\"completed\","
                    + "\"model\":\"gpt-5.4-mini\","
                    + "\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"summary\\\":\\\"curl ok\\\"}\"}]}]"
                    + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });
        server.start();
        try {
            AiProperties properties = properties();
            properties.getOpenai().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            properties.getOpenai().setCurlFallbackEnabled(true);
            OpenAiResponsesClient client = new OpenAiResponsesClient(properties, objectMapper);
            AiStructuredTextCommand command = new AiStructuredTextCommand();
            command.setPrompt("merge competitor title");

            AiStructuredTextResult result = client.createStructuredText(command);

            Assertions.assertTrue(result.isSuccess(), result.getErrorMessage());
            Assertions.assertEquals("curl ok", result.getParsedJson().get("summary"));
            Assertions.assertEquals(2, userAgents.size());
            Assertions.assertEquals("NuonoNext-AI/1.0", userAgents.get(0));
            Assertions.assertTrue(userAgents.get(1).startsWith("curl/"), userAgents.toString());
        } finally {
            server.stop(0);
        }
    }

    private AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.getOpenai().setApiKey("test-api-key");
        return properties;
    }

    private Map<String, Object> object(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private boolean isCurlAvailable() {
        try {
            Process process = new ProcessBuilder("curl", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception exception) {
            return false;
        }
    }
}
