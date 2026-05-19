package com.nuono.next.procurement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
class AliAiBulkInquiryCreatePageReadAdapter {

    private static final String AIR_1688_HOST = "air.1688.com";

    private final ChromeAppleScriptClient chromeClient;
    private final ObjectMapper objectMapper;

    AliAiBulkInquiryCreatePageReadAdapter(ChromeAppleScriptClient chromeClient, ObjectMapper objectMapper) {
        this.chromeClient = chromeClient;
        this.objectMapper = objectMapper;
    }

    AliAiBulkInquiryCreatePageSnapshot readCreatePage(String pageUrl, boolean openIfMissing) {
        String normalizedUrl = normalize(pageUrl);
        ChromeTab createTab = findCreatePageTab(normalizedUrl);
        if (createTab == null && openIfMissing && StringUtils.hasText(normalizedUrl)) {
            chromeClient.openTab(normalizedUrl);
            chromeClient.sleep(2500L);
            createTab = findCreatePageTab(normalizedUrl);
        }
        if (createTab == null) {
            AliAiBulkInquiryCreatePageSnapshot snapshot = new AliAiBulkInquiryCreatePageSnapshot();
            snapshot.setOk(false);
            snapshot.setFailureCode("ALI_AI_CREATE_PAGE_TAB_NOT_FOUND");
            snapshot.setFailureMessage("没有找到已打开的 1688 智能询盘创建页。");
            snapshot.setUrl(normalizedUrl);
            return snapshot;
        }

        chromeClient.focusTab(createTab);
        String payload = chromeClient.executeTabJavascript(createTab, buildReadCreatePageJavascript());
        try {
            AliAiBulkInquiryCreatePageSnapshot snapshot =
                    objectMapper.readValue(payload, AliAiBulkInquiryCreatePageSnapshot.class);
            if (snapshot == null) {
                return failure("ALI_AI_CREATE_PAGE_EMPTY", "1688 智能询盘创建页没有返回可读快照。", createTab);
            }
            snapshot.setUrl(firstNonBlank(snapshot.getUrl(), createTab.url));
            snapshot.setTitle(firstNonBlank(snapshot.getTitle(), createTab.title));
            return snapshot;
        } catch (JsonProcessingException exception) {
            return failure("ALI_AI_CREATE_PAGE_PARSE_FAILED", "1688 智能询盘创建页快照无法解析。", createTab);
        }
    }

    private ChromeTab findCreatePageTab(String pageUrl) {
        return chromeClient.listChromeTabs().stream()
                .filter(tab -> isCreatePageTab(tab, pageUrl))
                .max(Comparator.comparingInt((ChromeTab tab) -> tab.windowIndex)
                        .thenComparingInt(tab -> tab.tabIndex))
                .orElse(null);
    }

    private boolean isCreatePageTab(ChromeTab tab, String pageUrl) {
        if (tab == null || !StringUtils.hasText(tab.url)) {
            return false;
        }
        if (StringUtils.hasText(pageUrl) && tab.url.startsWith(pageUrl)) {
            return true;
        }

        String normalizedUrl = tab.url.toLowerCase(Locale.ROOT);
        return normalizedUrl.contains(AIR_1688_HOST)
                && !normalizedUrl.contains("inquiryresult")
                && (normalizedUrl.contains("ai-avatar")
                || normalizedUrl.contains("inquiry")
                || normalizedUrl.contains("bulk"));
    }

    private AliAiBulkInquiryCreatePageSnapshot failure(String code, String message, ChromeTab tab) {
        AliAiBulkInquiryCreatePageSnapshot snapshot = new AliAiBulkInquiryCreatePageSnapshot();
        snapshot.setOk(false);
        snapshot.setFailureCode(code);
        snapshot.setFailureMessage(message);
        if (tab != null) {
            snapshot.setUrl(tab.url);
            snapshot.setTitle(tab.title);
        }
        return snapshot;
    }

    private String buildReadCreatePageJavascript() {
        return "(function(){"
                + "function compact(value){return String(value || '').replace(/\\s+/g,' ').trim();}"
                + "function attr(el,name){return compact(el.getAttribute(name) || '');}"
                + "function visible(el){"
                + "var rect=el.getBoundingClientRect();"
                + "var style=window.getComputedStyle ? window.getComputedStyle(el) : null;"
                + "return !!((rect.width || rect.height) && (!style || (style.display!=='none' && style.visibility!=='hidden' && Number(style.opacity || 1)>0)));"
                + "}"
                + "function textOf(el){return compact(el.innerText || el.textContent || el.value || '');}"
                + "try {"
                + "var selector='button,a,input,textarea,select,[role=\"button\"],[contenteditable=\"true\"],[data-spm-click]';"
                + "var nodes=Array.prototype.slice.call(document.querySelectorAll(selector)).slice(0,160);"
                + "var elements=nodes.map(function(el){return {"
                + "tagName:compact(el.tagName).toLowerCase(),"
                + "type:attr(el,'type'),"
                + "text:textOf(el).slice(0,120),"
                + "placeholder:attr(el,'placeholder').slice(0,120),"
                + "name:attr(el,'name').slice(0,80),"
                + "elementId:attr(el,'id').slice(0,80),"
                + "className:compact(typeof el.className==='string' ? el.className : '').slice(0,120),"
                + "ariaLabel:attr(el,'aria-label').slice(0,120),"
                + "title:attr(el,'title').slice(0,120),"
                + "role:attr(el,'role').slice(0,80),"
                + "visible:visible(el)"
                + "};});"
                + "var text=compact(document.body ? document.body.innerText : '').slice(0,5000);"
                + "return JSON.stringify({ok:true,url:location.href,title:document.title || '',text:text,elements:elements});"
                + "} catch (error) {"
                + "return JSON.stringify({ok:false,failureCode:'ALI_AI_CREATE_PAGE_READ_FAILED',failureMessage:String(error && error.message || error)});"
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
