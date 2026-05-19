package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementAutoInquirySendGateway.SendAttemptResult;
import com.nuono.next.procurement.ProcurementAutoInquirySendGateway.SendPreparationResult;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquirySessionView;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquiryTaskView;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class LocalChromeHostedBrowserSendAdapter {

    private static final String ENDPOINT_PREFIX = "chrome-local://";
    private static final String CHAT_URL_TEMPLATE =
            "https://air.1688.com/app/ocms-fusion-components-1688/def_cbu_web_im/index.html?offerId=%s#/";

    private final ChromeAppleScriptClient chromeClient;
    private final ChromeProbeResultParser probeResultParser;

    public LocalChromeHostedBrowserSendAdapter(ChromeAppleScriptClient chromeClient, ChromeProbeResultParser probeResultParser) {
        this.chromeClient = chromeClient;
        this.probeResultParser = probeResultParser;
    }

    public boolean supports(String endpoint) {
        return StringUtils.hasText(endpoint) && endpoint.trim().startsWith(ENDPOINT_PREFIX);
    }

    public SendPreparationResult prepareInput(AutoInquiryTaskView task, AutoInquirySessionView session) {
        String offerId = normalize(task == null ? null : task.getTargetOfferId());
        String payload = normalize(task == null ? null : task.getInputPayloadText());
        if (!StringUtils.hasText(offerId)) {
            return failurePreparation("MISSING_OFFER_ID", "当前任务还没有有效 offerId，暂时不能命中真实 1688 聊天页。");
        }
        if (!StringUtils.hasText(payload)) {
            return failurePreparation("MISSING_INPUT_PAYLOAD", "当前任务还没有输入内容，暂时不能继续真实发送准备。");
        }

        ChatTabSelection selection = resolveReadyChatTab(task);
        if (!selection.ok) {
            return failurePreparation(
                    firstNonBlank(selection.failureCode, "CHAT_TAB_NOT_FOUND"),
                    firstNonBlank(selection.failureMessage, "还没有找到可用的 1688 聊天页，请先确认本机 Chrome 可正常打开 1688。")
            );
        }
        ChromeTab chatTab = selection.tab;
        chromeClient.sleep(900L);
        ComposerProbe probe = fillComposer(chatTab, payload);
        if (!probe.ok) {
            return failurePreparation(
                    firstNonBlank(probe.failureCode, "INPUT_NOT_FOUND"),
                    firstNonBlank(probe.failureMessage, "真实聊天页暂时没有找到可写输入区。")
            );
        }

        SendPreparationResult result = new SendPreparationResult();
        result.setReady(true);
        result.setInputLocator(buildInputLocator(chatTab, probe.locator));
        result.setContentEcho(compactEcho(probe.editorText));
        result.setEvidence(buildPreparationEvidence(chatTab, selection.contactSelection, probe));
        return result;
    }

    public SendAttemptResult send(AutoInquiryTaskView task, AutoInquirySessionView session) {
        String offerId = normalize(task == null ? null : task.getTargetOfferId());
        String payload = normalize(task == null ? null : task.getInputPayloadText());
        if (!StringUtils.hasText(offerId)) {
            return failureSend("MISSING_OFFER_ID", "当前任务还没有有效 offerId，暂时不能继续真实发送。");
        }
        if (!StringUtils.hasText(payload)) {
            return failureSend("MISSING_INPUT_PAYLOAD", "当前任务还没有输入内容，暂时不能继续真实发送。");
        }

        ChatTabSelection selection = resolveReadyChatTab(task);
        if (!selection.ok) {
            return failureSend(
                    firstNonBlank(selection.failureCode, "CHAT_TAB_NOT_FOUND"),
                    firstNonBlank(selection.failureMessage, "当前没有找到匹配 offerId 的 1688 聊天页，暂时不能继续真实发送。")
            );
        }
        ChromeTab chatTab = selection.tab;
        chromeClient.sleep(900L);
        ComposerProbe beforeProbe = inspectComposer(chatTab);
        if (!beforeProbe.ok || !StringUtils.hasText(beforeProbe.editorText)) {
            beforeProbe = fillComposer(chatTab, payload);
        }
        if (!beforeProbe.ok) {
            return failureSend(
                    firstNonBlank(beforeProbe.failureCode, "INPUT_NOT_READY"),
                    firstNonBlank(beforeProbe.failureMessage, "发送前未能确认真实聊天输入区已就绪。")
            );
        }

        SendTriggerResult triggerResult = triggerSend(chatTab, payload);
        if (!triggerResult.ok) {
            return failureSend(
                    firstNonBlank(triggerResult.failureCode, "SEND_TRIGGER_FAILED"),
                    firstNonBlank(triggerResult.failureMessage, "真实聊天页未能触发发送动作。")
            );
        }

        chromeClient.sleep(1500L);
        ComposerProbe afterProbe = inspectComposer(chatTab);
        String normalizedPayload = normalizeForCompare(payload);
        String normalizedAfter = normalizeForCompare(afterProbe.editorText);
        boolean inputCleared = !StringUtils.hasText(normalizedAfter) || !normalizedAfter.contains(normalizedPayload);
        boolean bodyMatched = containsSnippet(afterProbe.bodyText, payload);
        if (!inputCleared && !bodyMatched) {
            return failureSend(
                    "SEND_CONFIRMATION_MISSING",
                    "已尝试触发真实发送，但还没有观察到输入区清空或线程摘要变化，暂时不能 truthfully 写成已发送。"
            );
        }

        SendAttemptResult result = new SendAttemptResult();
        result.setDelivered(true);
        result.setThreadCheckpoint(buildThreadCheckpoint(chatTab, afterProbe));
        result.setMessageDigest(firstNonBlank(task.getInputPayloadHash(), sha256(payload)));
        result.setEvidence(buildSendEvidence(chatTab, selection.contactSelection, triggerResult, afterProbe, inputCleared, bodyMatched));
        return result;
    }

    private ChatTabSelection resolveReadyChatTab(AutoInquiryTaskView task) {
        String offerId = normalize(task == null ? null : task.getTargetOfferId());
        String supplierIdentity = normalize(task == null ? null : task.getTargetSupplierIdentity());
        if (!StringUtils.hasText(offerId)) {
            return ChatTabSelection.failure("MISSING_OFFER_ID", "当前任务还没有有效 offerId。");
        }
        ChromeTab chatTab = resolveChatTab(offerId);
        if (chatTab == null) {
            return ChatTabSelection.failure("CHAT_TAB_NOT_FOUND", "还没有找到可用的 1688 聊天页。");
        }
        if (chatTab.isLoginPage()) {
            return ChatTabSelection.failure("LOGIN_REQUIRED", "本机 Chrome 的 1688 托管会话当前未登录。");
        }

        chromeClient.focusTab(chatTab);
        ContactSelectionResult selection = ensureContactSelected(chatTab, supplierIdentity);
        if (selection.ok) {
            return ChatTabSelection.success(chatTab, selection);
        }

        ChromeTab bootstrappedTab = bootstrapChatTabFromEntry(task, offerId);
        if (bootstrappedTab != null) {
            chromeClient.focusTab(bootstrappedTab);
            ContactSelectionResult retriedSelection = ensureContactSelected(bootstrappedTab, supplierIdentity);
            if (retriedSelection.ok) {
                return ChatTabSelection.success(bootstrappedTab, retriedSelection);
            }
            selection = retriedSelection;
        }

        return ChatTabSelection.failure(
                firstNonBlank(selection.failureCode, "SUPPLIER_THREAD_NOT_FOUND"),
                firstNonBlank(selection.failureMessage, "当前聊天页还没有命中正确联系人。")
        );
    }

    private ChromeTab resolveChatTab(String offerId) {
        ChromeTab existingChatTab = findMatchingChatTab(offerId);
        if (existingChatTab != null) {
            return existingChatTab;
        }

        chromeClient.openTab(String.format(Locale.ROOT, CHAT_URL_TEMPLATE, offerId));
        chromeClient.sleep(2500L);

        ChromeTab chatTab = findMatchingChatTab(offerId);
        if (chatTab != null) {
            return chatTab;
        }

        ChromeTab offerRelatedTab = findLatestOfferRelatedTab(offerId);
        if (offerRelatedTab != null) {
            return offerRelatedTab;
        }

        return findLatestLoginTab();
    }

    private ChromeTab bootstrapChatTabFromEntry(AutoInquiryTaskView task, String offerId) {
        String entryUrl = normalize(task == null ? null : task.getTargetEntryUrl());
        if (!StringUtils.hasText(entryUrl)) {
            return null;
        }

        ChromeTab entryTab = findDetailTab(offerId);
        if (entryTab == null) {
            chromeClient.openTab(entryUrl);
            chromeClient.sleep(3200L);
            entryTab = findDetailTab(offerId);
        }
        if (entryTab == null || entryTab.isLoginPage()) {
            return null;
        }

        chromeClient.focusTab(entryTab);
        ServiceEntryResult openChatResult = openCustomerService(entryTab);
        if (!openChatResult.ok) {
            return null;
        }
        chromeClient.sleep(2600L);
        return findMatchingChatTab(offerId);
    }

    private ChromeTab findMatchingChatTab(String offerId) {
        return chromeClient.listChromeTabs().stream()
                .filter(tab -> tab.url != null
                        && tab.url.contains(offerId)
                        && tab.url.contains("air.1688.com/app/ocms-fusion-components-1688/def_cbu_web_im/"))
                .max(Comparator
                        .comparingInt((ChromeTab tab) -> chatTabSpecificity(tab, offerId))
                        .thenComparingInt(tab -> tab.windowIndex)
                        .thenComparingInt(tab -> tab.tabIndex))
                .orElse(null);
    }

    private ChromeTab findDetailTab(String offerId) {
        return chromeClient.listChromeTabs().stream()
                .filter(tab -> tab.url != null
                        && tab.url.contains("detail.1688.com/offer/")
                        && tab.url.contains(offerId))
                .max(Comparator.comparingInt((ChromeTab tab) -> tab.windowIndex)
                        .thenComparingInt(tab -> tab.tabIndex))
                .orElse(null);
    }

    private ChromeTab findLatestOfferRelatedTab(String offerId) {
        return chromeClient.listChromeTabs().stream()
                .filter(tab -> tab.url != null && tab.url.contains(offerId))
                .max(Comparator.comparingInt((ChromeTab tab) -> tab.windowIndex)
                        .thenComparingInt(tab -> tab.tabIndex))
                .orElse(null);
    }

    private ChromeTab findLatestLoginTab() {
        return chromeClient.listChromeTabs().stream()
                .filter(ChromeTab::isLoginPage)
                .max(Comparator.comparingInt((ChromeTab tab) -> tab.windowIndex)
                        .thenComparingInt(tab -> tab.tabIndex))
                .orElse(null);
    }

    private ComposerProbe fillComposer(ChromeTab tab, String message) {
        return probeResultParser.parseComposerProbe(chromeClient.executeTabJavascript(tab, buildFillComposerJavascript(message)));
    }

    private ComposerProbe inspectComposer(ChromeTab tab) {
        return probeResultParser.parseComposerProbe(chromeClient.executeTabJavascript(tab, buildInspectComposerJavascript()));
    }

    private ContactSelectionResult ensureContactSelected(ChromeTab tab, String supplierIdentity) {
        if (!StringUtils.hasText(supplierIdentity)) {
            return ContactSelectionResult.success(true, null, null, null);
        }
        return probeResultParser.parseContactSelectionResult(chromeClient.executeTabJavascript(tab, buildEnsureContactJavascript(supplierIdentity)));
    }

    private ServiceEntryResult openCustomerService(ChromeTab tab) {
        return probeResultParser.parseServiceEntryResult(chromeClient.executeTabJavascript(tab, buildOpenCustomerServiceJavascript()));
    }

    private SendTriggerResult triggerSend(ChromeTab tab, String message) {
        return probeResultParser.parseSendTriggerResult(chromeClient.executeTabJavascript(tab, buildSendJavascript(message)));
    }

    private String buildFillComposerJavascript(String message) {
        String messageBase64 = Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8));
        return loadJavascriptResource("fill-composer.js").replace("__PAYLOAD_BASE64__", messageBase64);
    }

    private String buildInspectComposerJavascript() {
        return loadJavascriptResource("inspect-composer.js");
    }

    private String buildEnsureContactJavascript(String supplierIdentity) {
        String supplierBase64 = Base64.getEncoder().encodeToString(supplierIdentity.getBytes(StandardCharsets.UTF_8));
        return loadJavascriptResource("ensure-contact.js").replace("__SUPPLIER_BASE64__", supplierBase64);
    }

    private String buildOpenCustomerServiceJavascript() {
        return loadJavascriptResource("open-customer-service.js");
    }

    private String buildSendJavascript(String message) {
        String messageBase64 = Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8));
        return loadJavascriptResource("send.js").replace("__PAYLOAD_BASE64__", messageBase64);
    }

    private String buildInputLocator(ChromeTab tab, String locator) {
        return "W" + tab.windowIndex + " T" + tab.tabIndex + " :: " + firstNonBlank(locator, "composer");
    }

    private String buildThreadCheckpoint(ChromeTab tab, ComposerProbe probe) {
        return "chrome-local:"
                + tab.windowIndex
                + ":"
                + tab.tabIndex
                + ":"
                + Instant.now().toEpochMilli()
                + ":"
                + firstNonBlank(normalize(probe.locator), normalize(tab.url), "thread");
    }

    private String buildPreparationEvidence(
            ChromeTab tab,
            ContactSelectionResult contactSelection,
            ComposerProbe probe
    ) {
        List<String> parts = new ArrayList<>();
        parts.add("已命中真实聊天输入区");
        parts.add("tab=W" + tab.windowIndex + " T" + tab.tabIndex);
        if (StringUtils.hasText(contactSelection.matchedText)) {
            parts.add("contact=" + contactSelection.matchedText);
        }
        if (StringUtils.hasText(contactSelection.locator)) {
            parts.add("contactLocator=" + contactSelection.locator);
        }
        if (StringUtils.hasText(probe.locator)) {
            parts.add("inputLocator=" + probe.locator);
        }
        return String.join("；", parts);
    }

    private String buildSendEvidence(
            ChromeTab tab,
            ContactSelectionResult contactSelection,
            SendTriggerResult triggerResult,
            ComposerProbe afterProbe,
            boolean inputCleared,
            boolean bodyMatched
    ) {
        List<String> parts = new ArrayList<>();
        parts.add("真实发送已触发");
        parts.add("tab=W" + tab.windowIndex + " T" + tab.tabIndex);
        if (StringUtils.hasText(contactSelection.matchedText)) {
            parts.add("contact=" + contactSelection.matchedText);
        }
        parts.add("trigger=" + firstNonBlank(triggerResult.triggerType, "unknown"));
        if (StringUtils.hasText(triggerResult.sendControlLocator)) {
            parts.add("sendControl=" + triggerResult.sendControlLocator);
        }
        if (StringUtils.hasText(afterProbe.locator)) {
            parts.add("inputLocator=" + afterProbe.locator);
        }
        parts.add("inputCleared=" + (inputCleared ? "yes" : "no"));
        parts.add("bodyMatched=" + (bodyMatched ? "yes" : "no"));
        return String.join("；", parts);
    }

    private boolean containsSnippet(String bodyText, String payload) {
        String normalizedBody = normalizeForCompare(bodyText);
        String normalizedPayload = normalizeForCompare(payload);
        if (!StringUtils.hasText(normalizedBody) || !StringUtils.hasText(normalizedPayload)) {
            return false;
        }
        String snippet = normalizedPayload.length() <= 12 ? normalizedPayload : normalizedPayload.substring(0, 12);
        return normalizedBody.contains(snippet);
    }

    private String compactEcho(String payloadText) {
        String normalized = payloadText == null ? null : payloadText.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
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

    private SendPreparationResult failurePreparation(String code, String message) {
        SendPreparationResult result = new SendPreparationResult();
        result.setReady(false);
        result.setFailureCode(code);
        result.setFailureMessage(message);
        return result;
    }

    private SendAttemptResult failureSend(String code, String message) {
        SendAttemptResult result = new SendAttemptResult();
        result.setDelivered(false);
        result.setFailureCode(code);
        result.setFailureMessage(message);
        return result;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeForCompare(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        return normalized.replaceAll("\\s+", " ");
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

    private int chatTabSpecificity(ChromeTab tab, String offerId) {
        String url = normalize(tab == null ? null : tab.url);
        if (url == null) {
            return 0;
        }
        int score = 0;
        if (url.contains("offerId=" + offerId)) {
            score += 40;
        }
        if (url.contains("touid=")) {
            score += 200;
        }
        if (url.contains("sourceValue=")) {
            score += 200;
        }
        if (url.contains("status=1")) {
            score += 20;
        }
        return score;
    }

    private String loadJavascriptResource(String resourceName) {
        try (InputStream inputStream = LocalChromeHostedBrowserSendAdapter.class.getResourceAsStream(
                "/procurement-browser/" + resourceName
        )) {
            if (inputStream == null) {
                throw new IllegalStateException("缺少浏览器执行脚本资源：" + resourceName);
            }
            return chromeClient.readFully(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("读取浏览器执行脚本资源失败：" + resourceName, exception);
        }
    }

}
