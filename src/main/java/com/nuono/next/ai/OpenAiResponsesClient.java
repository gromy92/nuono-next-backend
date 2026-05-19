package com.nuono.next.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.URLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenAiResponsesClient implements AiModelClient {

    private static final String PROVIDER = "openai";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiResponsesClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiStructuredTextResult createStructuredText(AiStructuredTextCommand command) {
        AiProperties.OpenAi openAi = properties.getOpenai();
        if (!StringUtils.hasText(openAi.getApiKey())) {
            return providerFailure(AiResultStatus.AI_PROVIDER_NOT_CONFIGURED, "OPENAI_API_KEY_MISSING", "OpenAI API key is not configured");
        }
        URI uri = buildResponsesUri(openAi);
        int timeoutSeconds = resolveTimeoutSeconds(command, openAi);
        try {
            String requestBody = objectMapper.writeValueAsString(buildRequestPayload(command));
            HttpResponseEnvelope response = postJson(uri, requestBody, openAi, timeoutSeconds);
            if (response.statusCode < 200 || response.statusCode >= 300) {
                return providerFailure(
                        AiResultStatus.AI_PROVIDER_ERROR,
                        "OPENAI_HTTP_" + response.statusCode,
                        abbreviate(response.body, 600)
                );
            }
            return parseResponse(response.body);
        } catch (SocketTimeoutException exception) {
            return providerFailure(
                    AiResultStatus.AI_PROVIDER_ERROR,
                    "OPENAI_REQUEST_TIMEOUT",
                    "AI 请求超时（已等待 " + Math.max(1, timeoutSeconds) + " 秒），请稍后重新解析，或减少输入内容后重试。"
            );
        } catch (IOException | RuntimeException exception) {
            return providerFailure(
                    AiResultStatus.AI_PROVIDER_ERROR,
                    "OPENAI_REQUEST_FAILED",
                    "AI request failed for " + uri + ": " + exception.getMessage()
            );
        }
    }

    Map<String, Object> buildRequestPayload(AiStructuredTextCommand command) {
        AiProperties.OpenAi openAi = properties.getOpenai();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", StringUtils.hasText(command.getModel()) ? command.getModel() : openAi.getDefaultTextModel());
        payload.put("input", buildInput(command));
        payload.put("store", openAi.isStoreResponses());
        if (StringUtils.hasText(command.getInstructions())) {
            payload.put("instructions", command.getInstructions());
        }
        Integer maxOutputTokens = command.getMaxOutputTokens() != null
                ? command.getMaxOutputTokens()
                : openAi.getMaxOutputTokens();
        if (maxOutputTokens != null && maxOutputTokens > 0) {
            payload.put("max_output_tokens", maxOutputTokens);
        }
        String reasoningEffort = StringUtils.hasText(command.getReasoningEffort())
                ? command.getReasoningEffort()
                : openAi.getReasoningEffort();
        if (StringUtils.hasText(reasoningEffort)) {
            Map<String, Object> reasoning = new LinkedHashMap<>();
            reasoning.put("effort", reasoningEffort);
            payload.put("reasoning", reasoning);
        }
        if (openAi.isIncludeMetadata() && command.getMetadata() != null && !command.getMetadata().isEmpty()) {
            payload.put("metadata", command.getMetadata());
        }
        if (command.getSchema() != null && !command.getSchema().isEmpty()) {
            Map<String, Object> format = new LinkedHashMap<>();
            format.put("type", "json_schema");
            format.put("name", resolveSchemaName(command));
            format.put("strict", true);
            format.put("schema", command.getSchema());
            Map<String, Object> text = new LinkedHashMap<>();
            text.put("format", format);
            payload.put("text", text);
        }
        return payload;
    }

    private Object buildInput(AiStructuredTextCommand command) {
        List<AiInputAttachment> attachments = command.getInputAttachments();
        if (attachments.isEmpty()) {
            return command.getPrompt();
        }
        List<Object> content = new ArrayList<>();
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("type", "input_text");
        text.put("text", command.getPrompt());
        content.add(text);
        for (AiInputAttachment attachment : attachments) {
            content.add(buildAttachmentInput(attachment));
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", content);
        return List.of(message);
    }

    private Map<String, Object> buildAttachmentInput(AiInputAttachment attachment) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (attachment.isImage()) {
            item.put("type", "input_image");
            item.put("image_url", toDataUrl(attachment));
            item.put("detail", "auto");
        } else {
            item.put("type", "input_file");
            item.put("filename", attachment.getFileName());
            item.put("file_data", toDataUrl(attachment));
        }
        return item;
    }

    private String toDataUrl(AiInputAttachment attachment) {
        String encoded = Base64.getEncoder().encodeToString(attachment.getContent());
        return "data:" + attachment.getContentType() + ";base64," + encoded;
    }

    AiStructuredTextResult parseResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.get("error");
            if (error != null && !error.isNull()) {
                return providerFailure(AiResultStatus.AI_PROVIDER_ERROR, "OPENAI_RESPONSE_ERROR", abbreviate(error.toString(), 600));
            }
            AiStructuredTextResult result = AiStructuredTextResult.success();
            result.setProvider(PROVIDER);
            result.setResponseId(textValue(root.get("id")));
            result.setModel(textValue(root.get("model")));
            result.setUsage(parseUsage(root.get("usage")));
            for (JsonNode outputItem : root.path("output")) {
                for (JsonNode contentItem : outputItem.path("content")) {
                    String type = textValue(contentItem.get("type"));
                    if ("output_text".equals(type) && !StringUtils.hasText(result.getOutputText())) {
                        result.setOutputText(textValue(contentItem.get("text")));
                    } else if ("refusal".equals(type)) {
                        result.setStatus(AiResultStatus.AI_REFUSED);
                        result.setRefusal(textValue(contentItem.get("refusal")));
                        result.setErrorCode("OPENAI_REFUSAL");
                        result.setErrorMessage(result.getRefusal());
                    }
                }
            }
            if (AiResultStatus.AI_REFUSED.equals(result.getStatus())) {
                return result;
            }
            if (!StringUtils.hasText(result.getOutputText())) {
                return providerFailure(AiResultStatus.AI_PROVIDER_EMPTY_RESPONSE, "OPENAI_EMPTY_RESPONSE", "OpenAI response did not contain output_text");
            }
            parseOutputJson(result);
            return result;
        } catch (JsonProcessingException exception) {
            return providerFailure(AiResultStatus.AI_PROVIDER_ERROR, "OPENAI_RESPONSE_PARSE_FAILED", exception.getMessage());
        }
    }

    private void parseOutputJson(AiStructuredTextResult result) {
        String outputText = result.getOutputText();
        if (!StringUtils.hasText(outputText)) {
            return;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(outputText, new TypeReference<Map<String, Object>>() {
            });
            result.setParsedJson(parsed);
        } catch (JsonProcessingException exception) {
            result.getWarnings().add("AI_OUTPUT_NOT_JSON");
        }
    }

    private AiUsage parseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isNull()) {
            return null;
        }
        AiUsage usage = new AiUsage();
        usage.setInputTokens(integerValue(usageNode.get("input_tokens")));
        usage.setOutputTokens(integerValue(usageNode.get("output_tokens")));
        usage.setTotalTokens(integerValue(usageNode.get("total_tokens")));
        return usage;
    }

    private URI buildResponsesUri(AiProperties.OpenAi openAi) {
        String baseUrl = trimRightSlash(openAi.getBaseUrl());
        String path = StringUtils.hasText(openAi.getResponsesPath()) ? openAi.getResponsesPath() : "/responses";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return URI.create(baseUrl + path);
    }

    private HttpResponseEnvelope postJson(
            URI uri,
            String requestBody,
            AiProperties.OpenAi openAi,
            int timeoutSeconds
    ) throws IOException {
        URLConnection rawConnection = uri.toURL().openConnection();
        if (!(rawConnection instanceof HttpURLConnection)) {
            throw new IOException("Unsupported AI endpoint protocol: " + uri.getScheme());
        }
        HttpURLConnection connection = (HttpURLConnection) rawConnection;
        int timeoutMillis = Math.toIntExact(Duration.ofSeconds(Math.max(1, timeoutSeconds)).toMillis());
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + openAi.getApiKey());
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(requestBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        int statusCode = connection.getResponseCode();
        InputStream inputStream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String body = inputStream == null
                ? ""
                : new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        connection.disconnect();
        return new HttpResponseEnvelope(statusCode, body);
    }

    private int resolveTimeoutSeconds(AiStructuredTextCommand command, AiProperties.OpenAi openAi) {
        Integer override = command == null ? null : command.getTimeoutSeconds();
        if (override != null && override > 0) {
            return override;
        }
        return openAi.getTimeoutSeconds();
    }

    private String resolveSchemaName(AiStructuredTextCommand command) {
        if (StringUtils.hasText(command.getSchemaName())) {
            return command.getSchemaName();
        }
        if (StringUtils.hasText(command.getOperationCode())) {
            return command.getOperationCode().replaceAll("[^a-zA-Z0-9_-]", "_");
        }
        return "nuono_ai_output";
    }

    private AiStructuredTextResult providerFailure(String status, String errorCode, String errorMessage) {
        AiStructuredTextResult result = AiStructuredTextResult.failure(status, errorCode, errorMessage);
        result.setProvider(PROVIDER);
        return result;
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private Integer integerValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asInt();
    }

    private String trimRightSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "https://aicodelink.top/v1";
        }
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private static class HttpResponseEnvelope {

        private final int statusCode;
        private final String body;

        private HttpResponseEnvelope(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}
