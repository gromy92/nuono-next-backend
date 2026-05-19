package com.nuono.next.procurement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class ChromeProbeResultParser {

    private final ObjectMapper objectMapper;

    ChromeProbeResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ComposerProbe parseComposerProbe(String payload) {
        if (!StringUtils.hasText(payload)) {
            return ComposerProbe.failure("EMPTY_PROBE", "浏览器侧没有返回输入区探针结果。");
        }
        try {
            ComposerProbe probe = objectMapper.readValue(payload, ComposerProbe.class);
            if (probe == null) {
                return ComposerProbe.failure("EMPTY_PROBE", "浏览器侧没有返回输入区探针结果。");
            }
            probe.editorText = normalize(probe.editorText);
            probe.bodyText = normalize(probe.bodyText);
            probe.locator = normalize(probe.locator);
            probe.evidence = normalize(probe.evidence);
            probe.failureCode = normalize(probe.failureCode);
            probe.failureMessage = normalize(probe.failureMessage);
            return probe;
        } catch (JsonProcessingException exception) {
            return ComposerProbe.failure("PROBE_PARSE_FAILED", "浏览器侧返回的输入区探针结果无法解析。");
        }
    }

    SendTriggerResult parseSendTriggerResult(String payload) {
        if (!StringUtils.hasText(payload)) {
            return SendTriggerResult.failure("EMPTY_SEND_TRIGGER", "浏览器侧没有返回发送触发结果。");
        }
        try {
            SendTriggerResult result = objectMapper.readValue(payload, SendTriggerResult.class);
            if (result == null) {
                return SendTriggerResult.failure("EMPTY_SEND_TRIGGER", "浏览器侧没有返回发送触发结果。");
            }
            result.triggerType = normalize(result.triggerType);
            result.locator = normalize(result.locator);
            result.sendControlLocator = normalize(result.sendControlLocator);
            result.beforeText = normalize(result.beforeText);
            result.failureCode = normalize(result.failureCode);
            result.failureMessage = normalize(result.failureMessage);
            return result;
        } catch (JsonProcessingException exception) {
            return SendTriggerResult.failure("SEND_TRIGGER_PARSE_FAILED", "浏览器侧返回的发送触发结果无法解析。");
        }
    }

    ContactSelectionResult parseContactSelectionResult(String payload) {
        if (!StringUtils.hasText(payload)) {
            return ContactSelectionResult.failure("EMPTY_THREAD_SELECTION", "浏览器侧没有返回联系人命中结果。");
        }
        try {
            ContactSelectionResult result = objectMapper.readValue(payload, ContactSelectionResult.class);
            if (result == null) {
                return ContactSelectionResult.failure("EMPTY_THREAD_SELECTION", "浏览器侧没有返回联系人命中结果。");
            }
            result.matchedText = normalize(result.matchedText);
            result.matchedToken = normalize(result.matchedToken);
            result.locator = normalize(result.locator);
            result.failureCode = normalize(result.failureCode);
            result.failureMessage = normalize(result.failureMessage);
            return result;
        } catch (JsonProcessingException exception) {
            return ContactSelectionResult.failure("THREAD_SELECTION_PARSE_FAILED", "浏览器侧返回的联系人命中结果无法解析。");
        }
    }

    ServiceEntryResult parseServiceEntryResult(String payload) {
        if (!StringUtils.hasText(payload)) {
            return ServiceEntryResult.failure("EMPTY_SERVICE_ENTRY", "浏览器侧没有返回客服入口点击结果。");
        }
        try {
            ServiceEntryResult result = objectMapper.readValue(payload, ServiceEntryResult.class);
            if (result == null) {
                return ServiceEntryResult.failure("EMPTY_SERVICE_ENTRY", "浏览器侧没有返回客服入口点击结果。");
            }
            result.locator = normalize(result.locator);
            result.matchedText = normalize(result.matchedText);
            result.failureCode = normalize(result.failureCode);
            result.failureMessage = normalize(result.failureMessage);
            return result;
        } catch (JsonProcessingException exception) {
            return ServiceEntryResult.failure("SERVICE_ENTRY_PARSE_FAILED", "浏览器侧返回的客服入口点击结果无法解析。");
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
