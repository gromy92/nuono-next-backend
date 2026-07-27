package com.nuono.next.procurement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
class AliAiBulkInquiryReadAdapter {

    private final ChromeAppleScriptClient chromeClient;
    private final ObjectMapper objectMapper;
    private final Ali1688BrowserUrlPolicy urlPolicy;

    AliAiBulkInquiryReadAdapter(
            ChromeAppleScriptClient chromeClient,
            ObjectMapper objectMapper,
            Ali1688BrowserUrlPolicy urlPolicy
    ) {
        this.chromeClient = chromeClient;
        this.objectMapper = objectMapper;
        this.urlPolicy = urlPolicy;
    }

    AliAiBulkInquiryPageSnapshot readResultPage(String resultUrl, boolean openIfMissing) {
        String normalizedUrl = urlPolicy.validateRequestedUrl(
                resultUrl,
                Ali1688BrowserUrlPolicy.PageKind.INQUIRY_RESULT
        );
        if (!StringUtils.hasText(normalizedUrl)) {
            AliAiBulkInquiryPageSnapshot snapshot = new AliAiBulkInquiryPageSnapshot();
            snapshot.setOk(false);
            snapshot.setFailureCode("ALI_AI_RESULT_URL_REQUIRED");
            snapshot.setFailureMessage("读取 1688 智能询盘结果页时必须提供明确的受信任地址。");
            return snapshot;
        }
        ChromeTab resultTab = findResultTab(normalizedUrl);
        if (resultTab == null && openIfMissing && StringUtils.hasText(normalizedUrl)) {
            chromeClient.openTab(normalizedUrl);
            chromeClient.sleep(2500L);
            resultTab = findResultTab(normalizedUrl);
        }
        if (resultTab == null) {
            AliAiBulkInquiryPageSnapshot snapshot = new AliAiBulkInquiryPageSnapshot();
            snapshot.setOk(false);
            snapshot.setFailureCode("ALI_AI_RESULT_TAB_NOT_FOUND");
            snapshot.setFailureMessage("没有找到已打开的 1688 智能询盘结果页。");
            snapshot.setUrl(normalizedUrl);
            return snapshot;
        }

        chromeClient.focusTab(resultTab);
        String payload = chromeClient.executeTabJavascript(
                resultTab,
                urlPolicy.guardJavascript(
                        Ali1688BrowserUrlPolicy.PageKind.INQUIRY_RESULT,
                        normalizedUrl,
                        buildReadResultJavascript()
                )
        );
        ChromeTab observedTab = ChromeTab.findCurrent(chromeClient.listChromeTabs(), resultTab);
        if (observedTab == null || !urlPolicy.matchesRequestedPage(
                observedTab.url,
                normalizedUrl,
                Ali1688BrowserUrlPolicy.PageKind.INQUIRY_RESULT
        )) {
            return failure(
                    "ALI_AI_RESULT_UNTRUSTED_REDIRECT",
                    "1688 智能询盘结果页已跳转到其他地址，未读取页面内容。",
                    observedTab == null ? resultTab : observedTab
            );
        }
        try {
            AliAiBulkInquiryPageSnapshot snapshot = objectMapper.readValue(payload, AliAiBulkInquiryPageSnapshot.class);
            if (snapshot == null) {
                return failure("ALI_AI_RESULT_EMPTY", "1688 智能询盘结果页没有返回可读快照。", observedTab);
            }
            snapshot.setUrl(observedTab.url);
            snapshot.setTitle(firstNonBlank(snapshot.getTitle(), observedTab.title));
            return snapshot;
        } catch (JsonProcessingException exception) {
            return failure("ALI_AI_RESULT_PARSE_FAILED", "1688 智能询盘结果页快照无法解析。", observedTab);
        }
    }

    private ChromeTab findResultTab(String requestedUrl) {
        return chromeClient.listChromeTabs().stream()
                .filter(tab -> tab != null && urlPolicy.matchesRequestedPage(
                        tab.url,
                        requestedUrl,
                        Ali1688BrowserUrlPolicy.PageKind.INQUIRY_RESULT
                ))
                .max(Comparator.comparingInt((ChromeTab tab) -> tab.windowIndex)
                        .thenComparingInt(tab -> tab.tabIndex))
                .orElse(null);
    }

    private AliAiBulkInquiryPageSnapshot failure(String code, String message, ChromeTab tab) {
        AliAiBulkInquiryPageSnapshot snapshot = new AliAiBulkInquiryPageSnapshot();
        snapshot.setOk(false);
        snapshot.setFailureCode(code);
        snapshot.setFailureMessage(message);
        if (tab != null) {
            snapshot.setUrl(tab.url);
            snapshot.setTitle(tab.title);
        }
        return snapshot;
    }

    private String buildReadResultJavascript() {
        return "(function(){"
                + "function compact(value){return String(value || '').replace(/\\s+/g,' ').trim();}"
                + "try {"
                + "var text = compact(document.body ? document.body.innerText : '');"
                + "return JSON.stringify({ok:true,url:location.href,title:document.title || '',text:text});"
                + "} catch (error) {"
                + "return JSON.stringify({ok:false,failureCode:'ALI_AI_RESULT_READ_FAILED',failureMessage:String(error && error.message || error)});"
                + "}"
                + "})();";
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

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
