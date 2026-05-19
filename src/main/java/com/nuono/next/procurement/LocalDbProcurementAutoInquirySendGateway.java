package com.nuono.next.procurement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.procurement.ProcurementAutoInquirySendGateway.SendAttemptResult;
import com.nuono.next.procurement.ProcurementAutoInquirySendGateway.SendPreparationResult;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquirySessionView;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquiryTaskView;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class LocalDbProcurementAutoInquirySendGateway implements ProcurementAutoInquirySendGateway {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final LocalChromeHostedBrowserSendAdapter localChromeHostedBrowserSendAdapter;

    public LocalDbProcurementAutoInquirySendGateway(
            ObjectMapper objectMapper,
            LocalChromeHostedBrowserSendAdapter localChromeHostedBrowserSendAdapter
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
        this.localChromeHostedBrowserSendAdapter = localChromeHostedBrowserSendAdapter;
    }

    @Override
    public SendPreparationResult prepareInput(AutoInquiryTaskView task, AutoInquirySessionView session) {
        if (task == null || session == null) {
            return failurePreparation("MISSING_CONTEXT", "缺少任务或托管会话，暂时不能继续发送准备。");
        }
        if (!StringUtils.hasText(task.getInputPayloadText())) {
            return failurePreparation("MISSING_INPUT_PAYLOAD", "当前任务还没有输入内容，暂时不能继续发送准备。");
        }

        String endpoint = normalize(session.getBrowserEndpoint());
        if (!StringUtils.hasText(endpoint)) {
            return failurePreparation("MISSING_BROWSER_ENDPOINT", "当前托管会话还没有浏览器执行入口，暂时不能继续自动发送。");
        }

        if (localChromeHostedBrowserSendAdapter.supports(endpoint)) {
            return localChromeHostedBrowserSendAdapter.prepareInput(task, session);
        }

        if (isMockEndpoint(endpoint)) {
            SendPreparationResult result = new SendPreparationResult();
            result.setReady(true);
            result.setInputLocator("mock://conversation/input#primary");
            result.setContentEcho(compactEcho(task.getInputPayloadText()));
            result.setEvidence("local-db mock gateway 已回读输入内容，准备进入发送确认。");
            return result;
        }

        JsonNode response = postJson(
                endpoint,
                "/prepare-input",
                buildPayload(task, session)
        );
        if (response == null) {
            return failurePreparation("SEND_GATEWAY_EMPTY", "浏览器发送网关没有返回准备结果。");
        }

        SendPreparationResult result = new SendPreparationResult();
        result.setReady(response.path("ready").asBoolean(false));
        result.setInputLocator(text(response, "inputLocator"));
        result.setContentEcho(text(response, "contentEcho"));
        result.setEvidence(text(response, "evidence"));
        result.setFailureCode(text(response, "failureCode"));
        result.setFailureMessage(text(response, "failureMessage"));
        if (!result.isReady()) {
            if (!StringUtils.hasText(result.getFailureCode())) {
                result.setFailureCode("SEND_GATEWAY_NOT_READY");
            }
            if (!StringUtils.hasText(result.getFailureMessage())) {
                result.setFailureMessage("浏览器发送网关没有确认输入区已命中。");
            }
        }
        return result;
    }

    @Override
    public SendAttemptResult send(AutoInquiryTaskView task, AutoInquirySessionView session) {
        if (task == null || session == null) {
            return failureSend("MISSING_CONTEXT", "缺少任务或托管会话，暂时不能继续自动发送。");
        }
        if (!StringUtils.hasText(task.getInputPayloadText())) {
            return failureSend("MISSING_INPUT_PAYLOAD", "当前任务还没有输入内容，暂时不能继续自动发送。");
        }

        String endpoint = normalize(session.getBrowserEndpoint());
        if (!StringUtils.hasText(endpoint)) {
            return failureSend("MISSING_BROWSER_ENDPOINT", "当前托管会话还没有浏览器执行入口，暂时不能继续自动发送。");
        }

        if (localChromeHostedBrowserSendAdapter.supports(endpoint)) {
            return localChromeHostedBrowserSendAdapter.send(task, session);
        }

        if (isMockEndpoint(endpoint)) {
            SendAttemptResult result = new SendAttemptResult();
            result.setDelivered(true);
            result.setThreadCheckpoint(buildMockCheckpoint(task));
            result.setMessageDigest(firstNonBlank(task.getInputPayloadHash(), sha256(task.getInputPayloadText())));
            result.setEvidence("local-db mock gateway 已确认发送动作，线程 checkpoint 和消息摘要已生成。");
            return result;
        }

        JsonNode response = postJson(
                endpoint,
                "/send",
                buildPayload(task, session)
        );
        if (response == null) {
            return failureSend("SEND_GATEWAY_EMPTY", "浏览器发送网关没有返回发送结果。");
        }

        SendAttemptResult result = new SendAttemptResult();
        result.setDelivered(response.path("delivered").asBoolean(false));
        result.setThreadCheckpoint(text(response, "threadCheckpoint"));
        result.setMessageDigest(text(response, "messageDigest"));
        result.setEvidence(text(response, "evidence"));
        result.setFailureCode(text(response, "failureCode"));
        result.setFailureMessage(text(response, "failureMessage"));
        if (!result.isDelivered()) {
            if (!StringUtils.hasText(result.getFailureCode())) {
                result.setFailureCode("SEND_GATEWAY_NOT_DELIVERED");
            }
            if (!StringUtils.hasText(result.getFailureMessage())) {
                result.setFailureMessage("浏览器发送网关没有给出已发送确认。");
            }
        }
        return result;
    }

    private JsonNode postJson(String endpoint, String suffix, Map<String, Object> payload) {
        try {
            URI uri = buildUri(endpoint, suffix);
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("浏览器发送网关返回状态码 " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (URISyntaxException | IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("浏览器发送网关调用失败：" + exception.getMessage(), exception);
        }
    }

    private URI buildUri(String endpoint, String suffix) throws URISyntaxException {
        String normalized = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return new URI(normalized + suffix);
    }

    private Map<String, Object> buildPayload(AutoInquiryTaskView task, AutoInquirySessionView session) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getId());
        payload.put("sessionId", session.getId());
        payload.put("sessionKey", session.getSessionKey());
        payload.put("accountLabel", session.getAccountLabel());
        payload.put("targetOfferId", task.getTargetOfferId());
        payload.put("targetSupplierIdentity", task.getTargetSupplierIdentity());
        payload.put("targetEntryUrl", task.getTargetEntryUrl());
        payload.put("targetLocatorText", task.getTargetLocatorText());
        payload.put("inputPayloadText", task.getInputPayloadText());
        payload.put("inputPayloadHash", task.getInputPayloadHash());
        payload.put("threadCheckpoint", task.getThreadCheckpoint());
        payload.put("lastMessageDigest", task.getLastMessageDigest());
        return payload;
    }

    private boolean isMockEndpoint(String endpoint) {
        return endpoint.startsWith("mock://")
                || endpoint.startsWith("ws://local-placeholder")
                || endpoint.startsWith("http://local-placeholder")
                || endpoint.startsWith("https://local-placeholder");
    }

    private SendPreparationResult failurePreparation(String failureCode, String failureMessage) {
        SendPreparationResult result = new SendPreparationResult();
        result.setReady(false);
        result.setFailureCode(failureCode);
        result.setFailureMessage(failureMessage);
        return result;
    }

    private SendAttemptResult failureSend(String failureCode, String failureMessage) {
        SendAttemptResult result = new SendAttemptResult();
        result.setDelivered(false);
        result.setFailureCode(failureCode);
        result.setFailureMessage(failureMessage);
        return result;
    }

    private String buildMockCheckpoint(AutoInquiryTaskView task) {
        return "mock-thread:"
                + firstNonBlank(task.getTargetOfferId(), "unknown")
                + ":"
                + Instant.now().toEpochMilli();
    }

    private String compactEcho(String payloadText) {
        String normalized = payloadText.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 100) {
            return normalized;
        }
        return normalized.substring(0, 97) + "...";
    }

    private String sha256(String payloadText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(payloadText.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前环境缺少 SHA-256 算法，暂时不能生成发送摘要。", exception);
        }
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        String value = valueNode.asText(null);
        return normalize(value);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }
}
