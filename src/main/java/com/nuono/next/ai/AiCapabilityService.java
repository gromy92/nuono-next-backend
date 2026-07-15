package com.nuono.next.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiCapabilityService {

    private final AiProperties properties;
    private final AiModelClient modelClient;
    private final AiJsonSchemaValidator schemaValidator;
    private final AiInvocationLogSink logSink;

    public AiCapabilityService(
            AiProperties properties,
            AiModelClient modelClient,
            AiJsonSchemaValidator schemaValidator,
            AiInvocationLogSink logSink
    ) {
        this.properties = properties;
        this.modelClient = modelClient;
        this.schemaValidator = schemaValidator;
        this.logSink = logSink;
    }

    public AiStructuredTextResult createStructuredText(AiStructuredTextCommand command) {
        Instant startedAt = Instant.now();
        AiStructuredTextCommand normalized = normalize(command);
        AiStructuredTextResult result;
        if (!StringUtils.hasText(normalized.getPrompt())) {
            result = AiStructuredTextResult.failure(AiResultStatus.AI_INVALID_INPUT, "AI_PROMPT_REQUIRED", "prompt is required");
            return finish(normalized, startedAt, result);
        }
        if (!properties.isEnabled()) {
            result = AiStructuredTextResult.failure(AiResultStatus.AI_DISABLED, "AI_DISABLED", "AI capability is disabled");
            return finish(normalized, startedAt, result);
        }
        if (!properties.isOpenAiConfigured()) {
            result = AiStructuredTextResult.failure(AiResultStatus.AI_PROVIDER_NOT_CONFIGURED, "OPENAI_API_KEY_MISSING", "OpenAI API key is not configured");
            return finish(normalized, startedAt, result);
        }
        result = modelClient.createStructuredText(normalized);
        if (shouldRetryTransientProviderRequestFailure(result)) {
            result = modelClient.createStructuredText(normalized);
        }
        result = validateSchemaIfNeeded(normalized, result);
        return finish(normalized, startedAt, result);
    }

    private boolean shouldRetryTransientProviderRequestFailure(AiStructuredTextResult result) {
        if (result == null
                || !AiResultStatus.AI_PROVIDER_ERROR.equals(result.getStatus())
                || !"OPENAI_REQUEST_FAILED".equals(result.getErrorCode())) {
            return false;
        }
        String message = result.getErrorMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("remote host terminated the handshake")
                || normalized.contains("handshake")
                || normalized.contains("connection reset")
                || normalized.contains("unexpected end of file")
                || normalized.contains("header parser received no bytes")
                || normalized.contains("eof");
    }

    private AiStructuredTextResult validateSchemaIfNeeded(AiStructuredTextCommand command, AiStructuredTextResult result) {
        if (!result.isSuccess() || command.getSchema() == null || command.getSchema().isEmpty()) {
            return result;
        }
        if (result.getParsedJson() == null) {
            AiStructuredTextResult failure = copyFailureEnvelope(result, "AI_OUTPUT_NOT_JSON", "AI output is not a JSON object");
            failure.getWarnings().add("AI_OUTPUT_NOT_JSON");
            return failure;
        }
        List<String> errors = schemaValidator.validate(command.getSchema(), result.getParsedJson());
        if (errors.isEmpty()) {
            return result;
        }
        AiStructuredTextResult failure = copyFailureEnvelope(result, "AI_OUTPUT_SCHEMA_INVALID", String.join("; ", errors));
        failure.setWarnings(mergeWarnings(result.getWarnings(), errors));
        return failure;
    }

    private AiStructuredTextResult copyFailureEnvelope(AiStructuredTextResult source, String errorCode, String errorMessage) {
        AiStructuredTextResult failure = AiStructuredTextResult.failure(AiResultStatus.AI_OUTPUT_SCHEMA_INVALID, errorCode, errorMessage);
        failure.setProvider(source.getProvider());
        failure.setModel(source.getModel());
        failure.setOutputText(source.getOutputText());
        failure.setParsedJson(source.getParsedJson());
        failure.setRequestId(source.getRequestId());
        failure.setResponseId(source.getResponseId());
        failure.setUsage(source.getUsage());
        failure.setDurationMillis(source.getDurationMillis());
        failure.setWarnings(source.getWarnings());
        return failure;
    }

    private AiStructuredTextResult finish(AiStructuredTextCommand command, Instant startedAt, AiStructuredTextResult result) {
        result.setDurationMillis(Duration.between(startedAt, Instant.now()).toMillis());
        if (!StringUtils.hasText(result.getModel())) {
            result.setModel(command.getModel());
        }
        logSink.log(toLogEntry(command, result));
        return result;
    }

    private AiInvocationLogEntry toLogEntry(AiStructuredTextCommand command, AiStructuredTextResult result) {
        AiInvocationLogEntry entry = new AiInvocationLogEntry();
        entry.setCreatedAt(Instant.now());
        entry.setFeatureCode(command.getFeatureCode());
        entry.setOperationCode(command.getOperationCode());
        entry.setOperatorUserId(command.getOperatorUserId());
        entry.setProvider(result.getProvider());
        entry.setModel(result.getModel());
        entry.setStatus(result.getStatus());
        entry.setResponseId(result.getResponseId());
        entry.setDurationMillis(result.getDurationMillis());
        entry.setUsage(result.getUsage());
        entry.setPromptDigest(sha256(command.getPrompt()));
        entry.setErrorCode(result.getErrorCode());
        return entry;
    }

    private AiStructuredTextCommand normalize(AiStructuredTextCommand command) {
        AiStructuredTextCommand normalized = new AiStructuredTextCommand();
        if (command == null) {
            normalized.setModel(properties.getOpenai().getDefaultTextModel());
            return normalized;
        }
        normalized.setFeatureCode(command.getFeatureCode());
        normalized.setOperationCode(command.getOperationCode());
        normalized.setOperatorUserId(command.getOperatorUserId());
        normalized.setModel(StringUtils.hasText(command.getModel()) ? command.getModel() : properties.getOpenai().getDefaultTextModel());
        normalized.setReasoningEffort(command.getReasoningEffort());
        normalized.setMaxOutputTokens(command.getMaxOutputTokens());
        normalized.setTimeoutSeconds(command.getTimeoutSeconds());
        normalized.setInstructions(command.getInstructions());
        normalized.setPrompt(command.getPrompt());
        normalized.setSchemaName(command.getSchemaName());
        normalized.setSchema(copyMap(command.getSchema()));
        normalized.setMetadata(copyMap(command.getMetadata()));
        normalized.setInputAttachments(command.getInputAttachments());
        return normalized;
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private List<String> mergeWarnings(List<String> existing, List<String> validationErrors) {
        List<String> warnings = new ArrayList<>();
        if (existing != null) {
            warnings.addAll(existing);
        }
        warnings.addAll(validationErrors);
        return warnings;
    }

    private String sha256(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
