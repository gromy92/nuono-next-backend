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

    private static final String INQUIRY_RESULT_URL_PREFIX =
            "https://air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryResult";

    private final ChromeAppleScriptClient chromeClient;
    private final ObjectMapper objectMapper;

    AliAiBulkInquiryReadAdapter(ChromeAppleScriptClient chromeClient, ObjectMapper objectMapper) {
        this.chromeClient = chromeClient;
        this.objectMapper = objectMapper;
    }

    AliAiBulkInquiryPageSnapshot readResultPage(String resultUrl, boolean openIfMissing) {
        String normalizedUrl = normalize(resultUrl);
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
        String payload = chromeClient.executeTabJavascript(resultTab, buildReadResultJavascript());
        try {
            AliAiBulkInquiryPageSnapshot snapshot = objectMapper.readValue(payload, AliAiBulkInquiryPageSnapshot.class);
            if (snapshot == null) {
                return failure("ALI_AI_RESULT_EMPTY", "1688 智能询盘结果页没有返回可读快照。", resultTab);
            }
            snapshot.setUrl(firstNonBlank(snapshot.getUrl(), resultTab.url));
            snapshot.setTitle(firstNonBlank(snapshot.getTitle(), resultTab.title));
            return snapshot;
        } catch (JsonProcessingException exception) {
            return failure("ALI_AI_RESULT_PARSE_FAILED", "1688 智能询盘结果页快照无法解析。", resultTab);
        }
    }

    private ChromeTab findResultTab(String resultUrl) {
        return chromeClient.listChromeTabs().stream()
                .filter(tab -> isInquiryResultTab(tab, resultUrl))
                .max(Comparator.comparingInt((ChromeTab tab) -> tab.windowIndex)
                        .thenComparingInt(tab -> tab.tabIndex))
                .orElse(null);
    }

    private boolean isInquiryResultTab(ChromeTab tab, String resultUrl) {
        if (tab == null || !StringUtils.hasText(tab.url)) {
            return false;
        }
        if (StringUtils.hasText(resultUrl) && tab.url.startsWith(resultUrl)) {
            return true;
        }
        return tab.url.startsWith(INQUIRY_RESULT_URL_PREFIX);
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
